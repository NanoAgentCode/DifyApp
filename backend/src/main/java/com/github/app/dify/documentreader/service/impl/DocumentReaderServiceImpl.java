package com.github.app.dify.documentreader.service.impl;

import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.documentreader.domain.*;
import com.github.app.dify.documentreader.repository.*;
import com.github.app.dify.documentreader.resp.DocumentReaderResp;
import com.github.app.dify.documentreader.service.DocumentReaderService;
import com.github.app.dify.documentreader.service.DocumentReaderVectorizationService;
import com.github.app.dify.knowledgebase.service.FileStorageService;
import com.github.app.dify.system.config.DocumentReaderConfig;
import com.github.app.dify.system.service.ModelConfigService;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import com.github.app.dify.knowledgebase.service.DocumentParserService;

/**
 * 文档解读服务实现
 */
@Service
public class DocumentReaderServiceImpl implements DocumentReaderService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentReaderServiceImpl.class);
    
    // 允许的文件类型
    private static final String[] ALLOWED_FILE_TYPES = {
        "pdf", "doc", "docx", "txt", "md", "xls", "xlsx", "ppt", "pptx",
        "png", "jpg", "jpeg", "gif"
    };
    
    @Autowired
    private DocumentReaderRepository documentRepository;
    
    @Autowired
    private DocumentGuideRepository guideRepository;
    
    @Autowired
    private DocumentTranslationRepository translationRepository;
    
    @Autowired
    private DocumentMindMapRepository mindMapRepository;
    
    @Autowired
    private DocumentNotesRepository notesRepository;
    
    @Autowired
    private FileStorageService fileStorageService;
    
    @Autowired
    private ModelConfigService modelConfigService;
    
    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;
    
    @Autowired
    private DocumentReaderVectorizationService documentReaderVectorizationService;
    
    @Autowired
    private DocumentParserService documentParserService;
    
    @Autowired
    private DocumentReaderConfig documentReaderConfig;
    
    /**
     * 上传文档
     */
    @Transactional
    @Override
    public DocumentReaderResp uploadDocument(MultipartFile file, Long userId) {
        logger.info("开始上传文档 - 文件名: {}, 用户ID: {}", file.getOriginalFilename(), userId);
        
        // 验证文件
        validateFile(file);
        
        // 生成文件路径
        String filePath = generateFilePath(userId, file.getOriginalFilename());
        
        // 上传文件到MinIO
        String fileUrl;
        try {
            fileUrl = fileStorageService.uploadFile(file, filePath);
        } catch (Exception e) {
            logger.error("文件上传到MinIO失败: {}", filePath, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
        
        // 保存文档元数据
        DocumentReader document = new DocumentReader();
        document.setFileName(filePath.substring(filePath.lastIndexOf("/") + 1));
        document.setOriginalFileName(file.getOriginalFilename());
        document.setFilePath(filePath);
        document.setFileUrl(fileUrl);
        document.setFileSize(file.getSize());
        document.setFileType(getFileExtension(file.getOriginalFilename()));
        document.setMimeType(file.getContentType());
        document.setStorageType("minio");
        document.setStatus(1);
        document.setUserId(userId);
        document.setCreateTime(new Date());
        document.setUpdateTime(new Date());
        document.setDeleted(0);
        document.setTotalPages(1); // 默认1页，后续可以根据文件类型计算
        
        document.setVectorizedStatus(0); // 初始状态：未向量化
        document = documentRepository.save(document);
        logger.info("文档上传成功 - 文档ID: {}, 文件名: {}", document.getId(), document.getOriginalFileName());
        
        // 异步触发向量化
        try {
            documentReaderVectorizationService.vectorizeDocumentAsync(document.getId(), file);
            logger.info("已提交文档向量化任务 - 文档ID: {}", document.getId());
        } catch (Exception e) {
            logger.error("提交文档向量化任务失败 - 文档ID: {}", document.getId(), e);
            // 不抛出异常，避免影响文档上传
        }
        
        return convertToResp(document);
    }
    
    /**
     * 删除文档
     */
    @Transactional
    @Override
    public void deleteDocument(Long documentId, Long userId) {
        Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
        if (!optional.isPresent()) {
            throw new NotFoundException("文档不存在: " + documentId);
        }
        
        DocumentReader document = optional.get();
        
        // 验证文档属于当前用户
        if (!document.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此文档");
        }
        
        // 从MinIO删除文件
        try {
            fileStorageService.deleteFile(document.getFilePath());
        } catch (Exception e) {
            logger.error("从MinIO删除文件失败: {}", document.getFilePath(), e);
        }
        
        // 删除相关数据
        guideRepository.findByDocumentId(documentId).ifPresent(guideRepository::delete);
        translationRepository.findByDocumentId(documentId).forEach(translationRepository::delete);
        mindMapRepository.findByDocumentId(documentId).ifPresent(mindMapRepository::delete);
        notesRepository.findByDocumentId(documentId).ifPresent(notesRepository::delete);
        
        // 删除向量数据
        try {
            documentReaderVectorizationService.deleteDocumentVectors(documentId);
        } catch (Exception e) {
            logger.error("删除文档向量失败 - 文档ID: {}", documentId, e);
            // 不抛出异常，避免影响删除流程
        }
        
        // 软删除
        document.setDeleted(1);
        document.setUpdateTime(new Date());
        documentRepository.save(document);
        
        logger.info("文档删除成功 - 文档ID: {}", documentId);
    }
    
    /**
     * 根据ID获取文档
     */
    @Override
    public DocumentReaderResp getDocumentById(Long documentId, Long userId) {
        Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
        if (!optional.isPresent()) {
            throw new NotFoundException("文档不存在: " + documentId);
        }
        
        DocumentReader document = optional.get();
        
        // 验证文档属于当前用户
        if (!document.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问此文档");
        }
        
        return convertToResp(document);
    }
    
    /**
     * 获取文档列表（分页）
     */
    @Override
    public PageResponse<DocumentReaderResp> listDocumentsWithPagination(
            Long userId,
            String keyword,
            String fileType,
            int page,
            int pageSize) {
        Pageable pageable = PageRequest.of(
                page - 1,
                pageSize,
                Sort.by("createTime").descending());
        
        Page<DocumentReader> documentPage = documentRepository.findByUserIdAndDeletedAndKeywordAndFileType(
                userId,
                0,
                keyword != null ? keyword.trim() : null,
                fileType != null && !fileType.isEmpty() ? fileType : null,
                pageable);
        
        PageResponse<DocumentReaderResp> response = new PageResponse<>();
        response.setContent(documentPage.getContent().stream()
                .map(this::convertToResp)
                .collect(Collectors.toList()));
        response.setTotal(documentPage.getTotalElements());
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotalPages(documentPage.getTotalPages());
        
        return response;
    }
    
    /**
     * 获取文档内容
     */
    @Override
    public InputStream getDocumentContent(Long documentId, Long userId, Integer page) {
        Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
        if (!optional.isPresent()) {
            throw new NotFoundException("文档不存在: " + documentId);
        }
        
        DocumentReader document = optional.get();
        
        // 验证文档属于当前用户
        if (!document.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问此文档");
        }
        
        try {
            InputStream inputStream = fileStorageService.downloadFile(document.getFilePath());
            return inputStream;
        } catch (Exception e) {
            logger.error("获取文档内容失败: {}", document.getFilePath(), e);
            throw new RuntimeException("获取文档内容失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取文档导读
     */
    @Override
    public String getDocumentGuide(Long documentId, Long userId) {
        validateDocumentAccess(documentId, userId);
        return guideRepository.findByDocumentId(documentId)
                .map(DocumentGuide::getContent)
                .orElse("");
    }
    
    /**
     * 保存文档导读
     */
    @Transactional
    @Override
    public void saveDocumentGuide(Long documentId, Long userId, String content) {
        validateDocumentAccess(documentId, userId);
        
        Optional<DocumentGuide> optional = guideRepository.findByDocumentId(documentId);
        DocumentGuide guide;
        if (optional.isPresent()) {
            guide = optional.get();
            guide.setContent(content);
            guide.setUpdateTime(new Date());
        } else {
            guide = new DocumentGuide();
            guide.setDocumentId(documentId);
            guide.setContent(content);
            guide.setCreateTime(new Date());
            guide.setUpdateTime(new Date());
        }
        guideRepository.save(guide);
    }
    
    /**
     * 生成文档导读（使用大模型）
     */
    @Override
    public String generateDocumentGuide(Long documentId, Long userId, Long modelId) {
        validateDocumentAccess(documentId, userId);
        
        try {
            // 获取文档信息
            Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
            if (!optional.isPresent()) {
                throw new NotFoundException("文档不存在: " + documentId);
            }
            DocumentReader document = optional.get();
            
            // 获取模型配置：优先使用参数，其次使用文档解读配置，最后使用默认RAG模型
            QAModel qaModel;
            Long effectiveModelId = modelId != null ? modelId : documentReaderConfig.getDefaultQAModelId();
            if (effectiveModelId != null) {
                qaModel = modelConfigService.getQAModelById(effectiveModelId);
                if (qaModel == null) {
                    throw new RuntimeException("模型不存在: " + effectiveModelId);
                }
            } else {
                // 使用默认RAG模型
                qaModel = modelConfigService.getDefaultQAModelForRAG();
                if (qaModel == null) {
                    throw new RuntimeException("未配置默认问答模型，请在系统配置中设置documentReader.defaultQAModelId");
                }
            }
            
            // 验证模型是否启用
            if (qaModel.getEnabled() == null || !qaModel.getEnabled()) {
                throw new RuntimeException("模型未启用: " + qaModel.getName());
            }
            
            // 读取文档内容（尝试提取文本）
            String documentText = extractDocumentText(document);
            
            // 构建提示词
            String prompt = buildGuidePrompt(document.getOriginalFileName(), documentText);
            
            // 使用通用的 LLM API 生成导读（支持 OpenAI、Ollama、vLLM 等）
            ChatLanguageModel chatLanguageModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);
            
            // 构建消息列表
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(UserMessage.from(prompt));
            
            // 调用 LLM API
            Response<AiMessage> aiResponse = chatLanguageModel.generate(messages);
            String guideContent = aiResponse.content().text();
            
            if (guideContent == null || guideContent.trim().isEmpty()) {
                throw new RuntimeException("大模型生成导读失败：返回内容为空");
            }
            
            guideContent = guideContent.trim();
            
            // 确保字数不超过500字（中文字符计数）
            guideContent = truncateToMaxWords(guideContent, 500);
            
            // 保存生成的导读
            saveDocumentGuide(documentId, userId, guideContent);
            
            logger.info("成功生成文档导读 - 文档ID: {}, 模型ID: {}", documentId, modelId);
            return guideContent;
            
        } catch (Exception e) {
            logger.error("生成文档导读失败 - 文档ID: {}, 模型ID: {}", documentId, modelId, e);
            throw new RuntimeException("生成文档导读失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 提取文档文本内容（简化版本，仅支持文本文件）
     */
    private String extractDocumentText(DocumentReader document) {
        try {
            InputStream inputStream = fileStorageService.downloadFile(document.getFilePath());
            
            // 根据文件类型提取文本
            String fileType = document.getFileType() != null ? document.getFileType().toLowerCase() : "";
            
            if ("txt".equals(fileType) || "md".equals(fileType) || "markdown".equals(fileType)) {
                // 文本文件，直接读取
                StringBuilder text = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        text.append(line).append("\n");
                    }
                }
                return text.toString().trim();
            } else {
                // 对于 docx、pdf 等文件，使用 DocumentParserService 提取文本
                try {
                    // 使用 DocumentParserService 解析文档（支持 InputStream）
                    String text = documentParserService.parseDocument(inputStream, document.getOriginalFileName());
                    
                    if (text != null && !text.trim().isEmpty()) {
                        return text.trim();
                    } else {
                        logger.warn("DocumentParserService 提取的文本为空，文档ID: {}", document.getId());
                        return String.format("文档名称: %s\n文档类型: %s\n文档大小: %d 字节\n\n注意：无法提取文档文本内容", 
                            document.getOriginalFileName(), 
                            fileType, 
                            document.getFileSize());
                    }
                } catch (Exception e) {
                    logger.warn("使用 DocumentParserService 提取文档文本失败: {}", e.getMessage(), e);
                    // 如果解析失败，返回基本信息
                    return String.format("文档名称: %s\n文档类型: %s\n文档大小: %d 字节\n\n注意：无法提取文档文本内容（%s）", 
                        document.getOriginalFileName(), 
                        fileType, 
                        document.getFileSize(),
                        e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("提取文档文本失败: {}", e.getMessage(), e);
            return String.format("文档名称: %s\n文档类型: %s\n\n注意：提取文档内容时发生错误：%s", 
                document.getOriginalFileName(), 
                document.getFileType(),
                e.getMessage());
        }
    }
    
    /**
     * 构建导读生成提示词
     */
    private String buildGuidePrompt(String fileName, String documentText) {
        // 限制文档文本长度，避免超过模型上下文限制
        int maxTextLength = 8000; // 保留一些空间给提示词和响应
        String truncatedText = documentText;
        if (documentText.length() > maxTextLength) {
            truncatedText = documentText.substring(0, maxTextLength) + "\n\n[文档内容已截断...]";
        }
        
        return String.format(
            "请为以下文档生成一份简洁、概括性的导读。要求：\n" +
            "1. 字数严格控制在500字以内（中文字符计数）\n" +
            "2. 内容要高度概括，突出核心要点，避免冗余描述\n" +
            "3. 导读应包括：\n" +
            "   - 文档的核心主题和主要内容概述（1-2句话）\n" +
            "   - 文档的关键要点和重要信息（3-5个要点，用列表形式）\n" +
            "   - 文档的主要结构或章节（简要说明，1-2句话）\n" +
            "   - 适合的读者群体（1句话）\n" +
            "4. 使用简洁明了的语言，直接说明要点，不要展开详细描述\n" +
            "5. 使用Markdown格式，但保持简洁\n" +
            "6. 重点突出文档的核心价值和关键信息\n" +
            "7. **重要格式要求**：\n" +
            "   - 不要使用标题格式（#、##、###等）\n" +
            "   - 标题必须使用编号格式：**1. 标题内容**、**2. 标题内容**、**3. 标题内容**等\n" +
            "   - 每个主要部分都要有编号（1、2、3、4、5等），并使用**加粗**格式突出显示\n" +
            "   - 标题下的内容可以使用列表（-、*）来组织\n" +
            "   - 保持整体格式简洁统一，编号要连续\n\n" +
            "文档名称：%s\n\n" +
            "文档内容：\n%s\n\n" +
            "请生成一份简洁、概括性的导读，字数严格控制在500字以内。",
            fileName,
            truncatedText
        );
    }
    
    /**
     * 截断文本到指定字数（中文字符计数）
     * @param text 原始文本
     * @param maxWords 最大字数
     * @return 截断后的文本
     */
    private String truncateToMaxWords(String text, int maxWords) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // 计算中文字符数（中文字符、中文标点等）
        int charCount = 0;
        int lastValidIndex = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 中文字符、中文标点、全角字符都算作1个字
            if (c >= 0x4E00 && c <= 0x9FFF || // 中文字符
                c >= 0x3000 && c <= 0x303F || // 中文标点
                c >= 0xFF00 && c <= 0xFFEF) {  // 全角字符
                charCount++;
            } else if (c > 0x007F) {
                // 其他非ASCII字符也算作1个字
                charCount++;
            } else {
                // ASCII字符（英文、数字、标点）按0.5个字计算
                charCount += 0.5;
            }
            
            if (charCount <= maxWords) {
                lastValidIndex = i + 1;
            } else {
                break;
            }
        }
        
        if (lastValidIndex < text.length()) {
            // 尝试在句号、问号、感叹号等标点处截断
            int lastPunctuation = -1;
            for (int i = lastValidIndex - 1; i >= Math.max(0, lastValidIndex - 50); i--) {
                char c = text.charAt(i);
                if (c == '。' || c == '！' || c == '？' || c == '\n' || 
                    c == '.' || c == '!' || c == '?') {
                    lastPunctuation = i + 1;
                    break;
                }
            }
            
            if (lastPunctuation > 0) {
                return text.substring(0, lastPunctuation) + "\n\n[导读内容已截断至500字]";
            } else {
                return text.substring(0, lastValidIndex) + "...\n\n[导读内容已截断至500字]";
            }
        }
        
        return text;
    }
    
    /**
     * 翻译文档
     */
    @Override
    public void translateDocument(Long documentId, Long userId, String targetLang) {
        validateDocumentAccess(documentId, userId);
        
        logger.info("开始翻译文档 - 文档ID: {}, 目标语言: {}", documentId, targetLang);
        
        try {
            // 获取文档
            Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
            if (!optional.isPresent()) {
                throw new NotFoundException("文档不存在: " + documentId);
            }
            
            DocumentReader document = optional.get();
            
            // 提取文档内容
            String documentContent = extractDocumentText(document);
            if (documentContent == null || documentContent.trim().isEmpty()) {
                logger.warn("文档内容为空，无法翻译 - 文档ID: {}", documentId);
                throw new RuntimeException("文档内容为空，无法翻译");
            }
            
            // 获取模型配置（使用默认RAG模型）
            QAModel qaModel = modelConfigService.getDefaultQAModelForRAG();
            if (qaModel == null) {
                throw new RuntimeException("未配置可用的模型，无法进行翻译");
            }
            
            // 执行翻译
            String translatedContent = translateText(documentContent, targetLang, qaModel);
            
            // 保存翻译结果
            saveDocumentTranslation(documentId, userId, targetLang, translatedContent);
            
            logger.info("文档翻译完成 - 文档ID: {}, 目标语言: {}, 原文长度: {}, 译文长度: {}", 
                documentId, targetLang, documentContent.length(), translatedContent.length());
            
        } catch (Exception e) {
            logger.error("翻译文档失败 - 文档ID: {}, 目标语言: {}", documentId, targetLang, e);
            throw new RuntimeException("翻译文档失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取文档翻译内容
     */
    @Override
    public String getDocumentTranslation(Long documentId, Long userId, String targetLang) {
        validateDocumentAccess(documentId, userId);
        return translationRepository.findByDocumentIdAndTargetLanguage(documentId, targetLang)
                .map(DocumentTranslation::getContent)
                .orElse("");
    }
    
    /**
     * 保存文档翻译内容
     */
    @Transactional
    @Override
    public void saveDocumentTranslation(Long documentId, Long userId, String targetLang, String content) {
        validateDocumentAccess(documentId, userId);
        
        Optional<DocumentTranslation> optional = translationRepository.findByDocumentIdAndTargetLanguage(documentId, targetLang);
        DocumentTranslation translation;
        if (optional.isPresent()) {
            translation = optional.get();
            translation.setContent(content);
            translation.setUpdateTime(new Date());
        } else {
            translation = new DocumentTranslation();
            translation.setDocumentId(documentId);
            translation.setTargetLanguage(targetLang);
            translation.setContent(content);
            translation.setCreateTime(new Date());
            translation.setUpdateTime(new Date());
        }
        translationRepository.save(translation);
    }
    
    /**
     * 获取文档脑图
     */
    @Override
    public String getDocumentMindMap(Long documentId, Long userId) {
        validateDocumentAccess(documentId, userId);
        return mindMapRepository.findByDocumentId(documentId)
                .map(DocumentMindMap::getMindMapData)
                .orElse(null);
    }
    
    /**
     * 保存文档脑图
     */
    @Transactional
    @Override
    public void saveDocumentMindMap(Long documentId, Long userId, String mindMapData) {
        validateDocumentAccess(documentId, userId);
        
        Optional<DocumentMindMap> optional = mindMapRepository.findByDocumentId(documentId);
        DocumentMindMap mindMap;
        if (optional.isPresent()) {
            mindMap = optional.get();
            mindMap.setMindMapData(mindMapData);
            mindMap.setUpdateTime(new Date());
        } else {
            mindMap = new DocumentMindMap();
            mindMap.setDocumentId(documentId);
            mindMap.setMindMapData(mindMapData);
            mindMap.setCreateTime(new Date());
            mindMap.setUpdateTime(new Date());
        }
        mindMapRepository.save(mindMap);
    }
    
    /**
     * 生成文档脑图（使用大模型）
     */
    @Override
    public String generateDocumentMindMap(Long documentId, Long userId, Long modelId) {
        validateDocumentAccess(documentId, userId);
        
        try {
            // 获取文档内容
            Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
            if (!optional.isPresent()) {
                throw new NotFoundException("文档不存在: " + documentId);
            }
            
            DocumentReader document = optional.get();
            String fileName = document.getOriginalFileName();
            
            // 读取文档内容（使用与导读相同的方法）
            String documentContent = extractDocumentText(document);
            
            // 获取模型配置
            QAModel qaModel = null;
            Long effectiveModelId = modelId != null ? modelId : documentReaderConfig.getDefaultQAModelId();
            if (effectiveModelId != null) {
                qaModel = modelConfigService.getQAModelById(effectiveModelId);
                if (qaModel == null) {
                    logger.warn("指定的模型不存在，使用默认模型");
                }
            }
            
            if (qaModel == null) {
                // 使用默认RAG模型
                qaModel = modelConfigService.getDefaultQAModelForRAG();
            }
            
            if (qaModel == null) {
                throw new RuntimeException("未配置可用的模型，无法生成脑图");
            }
            
            // 检测文档语言，如果不是简体中文，先翻译为中文
            if (!isSimplifiedChinese(documentContent)) {
                logger.info("检测到文档内容为非简体中文，开始翻译为中文 - 文档ID: {}", documentId);
                try {
                    documentContent = translateToChinese(documentContent, qaModel);
                    logger.info("文档翻译完成，翻译后长度: {} 字符", documentContent.length());
                } catch (Exception e) {
                    logger.warn("翻译文档失败，将使用原文生成脑图: {}", e.getMessage());
                    // 翻译失败时继续使用原文
                }
            }
            
            // 根据文档长度评估内容详细程度
            int textLength = documentContent.length();
            String contentAssessment;
            int maxLevel;
            int maxNodesPerLevel;
            
            if (textLength < 1000) {
                // 短文档：简单结构，2层即可
                contentAssessment = "文档内容较少，请生成简洁的思维导图，重点关注主要章节和核心要点。";
                maxLevel = 2;
                maxNodesPerLevel = 5;
            } else if (textLength < 5000) {
                // 中等文档：标准结构，3层
                contentAssessment = "文档内容中等，请生成结构化的思维导图，包含主要章节、重要段落和关键要点。";
                maxLevel = 3;
                maxNodesPerLevel = 8;
            } else if (textLength < 15000) {
                // 长文档：详细结构，3层，但需要精选内容
                contentAssessment = "文档内容较长，请生成详细的思维导图，精选最重要的章节、段落和要点，避免过于冗长。";
                maxLevel = 3;
                maxNodesPerLevel = 10;
            } else {
                // 超长文档：精简结构，3层，只选核心内容
                contentAssessment = "文档内容很长，请生成精简但完整的思维导图，只选择最核心的章节和最重要的要点，确保思维导图清晰易读。";
                maxLevel = 3;
                maxNodesPerLevel = 12;
            }
            
            // 限制文档文本长度（根据评估结果调整）
            int maxTextLength = textLength < 15000 ? textLength : 15000;
            String truncatedText = documentContent;
            if (documentContent.length() > maxTextLength) {
                truncatedText = documentContent.substring(0, maxTextLength) + "\n\n[文档内容已截断，仅用于生成思维导图核心结构...]";
            }
            
            // 构建提示词
            String prompt = String.format(
                "你是一个专业的思维导图生成助手。请仔细阅读以下文档内容，然后根据文档长度自动评估并生成一个结构化的思维导图。\n\n" +
                "**文档信息**：\n" +
                "文档名称：%s\n" +
                "文档长度：%d 字符\n" +
                "内容评估：%s\n\n" +
                "**文档内容**：\n%s\n\n" +
                "**核心要求（非常重要）**：\n" +
                "1. **必须严格按照上述文档的实际内容生成思维导图**\n" +
                "2. **禁止生成通用的模板结构**（如：个人简介、工作业绩、团队管理等通用章节）\n" +
                "3. **禁止猜测或补充文档中没有的内容**\n" +
                "4. **思维导图的每个节点必须对应文档中实际存在的章节、段落或内容**\n" +
                "5. **如果文档中没有某个章节，绝对不要生成该章节**\n" +
                "6. **节点文字必须来自文档中的实际文字，不要自己编造**\n" +
                "7. **不要生成标准的述职报告模板结构，必须基于文档的实际内容**\n\n" +
                "**层级限制（严格遵循）**：\n" +
                "1. **思维导图层级绝对不超过3层**（中心主题为第0层，一级节点为第1层，二级节点为第2层，三级节点为第3层）\n" +
                "2. 根据文档长度，当前建议最大层级：%d层\n" +
                "3. 每层节点数量建议：一级节点不超过%d个，二级节点每个一级节点下不超过%d个，三级节点每个二级节点下不超过%d个\n" +
                "4. 如果文档内容较少，可以减少层级和节点数量\n" +
                "5. 如果文档内容较多，必须精选最重要的内容，确保不超过层级和节点限制\n\n" +
                "**生成规则**：\n" +
                "1. 中心主题（第0层）使用文档名称：%s\n" +
                "2. 一级节点（第1层）应该是文档中实际存在的主要章节标题或核心主题\n" +
                "3. 二级节点（第2层）应该是各章节中的实际段落、要点、具体内容\n" +
                "4. 三级节点（第3层，可选）应该是二级节点下的具体细节或子要点\n" +
                "5. 节点名称必须使用文档中的原始文字，不要改写或概括\n" +
                "6. 根据文档长度自动调整详细程度：短文档简化，长文档精选核心内容\n" +
                "7. **绝对不要超过3层，如果内容很多，请合并或精选**\n\n" +
                "**输出格式要求**：\n" +
                "1. 必须只返回JSON格式的数据，不要包含任何其他文字说明\n" +
                "2. 返回的JSON必须是有效的JSON格式，可以直接被JSON.parse()解析\n" +
                "3. 必须使用jsMind格式，格式如下：\n\n" +
                "{\n" +
                "  \"meta\": {\n" +
                "    \"name\": \"思维导图名称\",\n" +
                "    \"author\": \"系统\",\n" +
                "    \"version\": \"1.0\"\n" +
                "  },\n" +
                "  \"format\": \"node_tree\",\n" +
                "  \"data\": {\n" +
                "    \"id\": \"root\",\n" +
                "    \"topic\": \"中心主题（文档名称）\",\n" +
                "    \"children\": [\n" +
                "      {\n" +
                "        \"id\": \"node1\",\n" +
                "        \"topic\": \"一级节点1（文档中实际存在的章节标题）\",\n" +
                "        \"children\": [\n" +
                "          {\n" +
                "            \"id\": \"node1-1\",\n" +
                "            \"topic\": \"二级节点1-1（该章节中的实际内容）\",\n" +
                "            \"children\": [\n" +
                "              {\n" +
                "                \"id\": \"node1-1-1\",\n" +
                "                \"topic\": \"三级节点1-1-1（可选，具体细节）\",\n" +
                "                \"children\": []\n" +
                "              }\n" +
                "            ]\n" +
                "          }\n" +
                "        ]\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}\n\n" +
                "**再次强调**：\n" +
                "- 思维导图的内容必须100%%来自上述文档的实际内容\n" +
                "- **层级绝对不超过3层**（中心主题+最多3层子节点）\n" +
                "- 根据文档长度自动评估并调整详细程度\n" +
                "- 禁止生成任何模板化的结构（如标准的述职报告、工作总结等模板）\n" +
                "- 如果文档中没有某个内容，绝对不要生成\n" +
                "- 节点文字必须使用文档中的原始文字\n" +
                "- **只返回JSON，不要有任何其他文字！**",
                fileName,
                textLength,
                contentAssessment,
                truncatedText,
                maxLevel,
                maxNodesPerLevel,
                maxNodesPerLevel / 2,
                maxNodesPerLevel / 3,
                fileName
            );
            
            // 调用大模型生成脑图
            ChatLanguageModel chatModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new UserMessage(prompt));
            
            Response<AiMessage> response = chatModel.generate(messages);
            String rawResponse = response.content().text();
            
            // 从响应中提取JSON部分
            String mindMapJson = extractJsonFromResponse(rawResponse);
            
            // 验证并修复JSON格式
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            try {
                objectMapper.readTree(mindMapJson);
            } catch (Exception e) {
                logger.warn("首次JSON验证失败，尝试修复JSON格式: {}", e.getMessage());
                // 尝试修复常见的JSON问题
                String fixedJson = tryFixJson(mindMapJson);
                try {
                    objectMapper.readTree(fixedJson);
                    mindMapJson = fixedJson; // 使用修复后的JSON
                    logger.info("JSON修复成功");
                } catch (Exception e2) {
                    logger.error("生成的脑图数据不是有效的JSON格式，原始响应长度: {}, 前500字符: {}", 
                        rawResponse.length(), 
                        rawResponse.length() > 500 ? rawResponse.substring(0, 500) + "..." : rawResponse);
                    logger.error("提取的JSON长度: {}, 前500字符: {}", 
                        mindMapJson.length(), 
                        mindMapJson.length() > 500 ? mindMapJson.substring(0, 500) + "..." : mindMapJson);
                    logger.error("修复后的JSON长度: {}, 前500字符: {}", 
                        fixedJson.length(), 
                        fixedJson.length() > 500 ? fixedJson.substring(0, 500) + "..." : fixedJson);
                    throw new RuntimeException("生成的脑图数据格式错误，不是有效的JSON: " + e.getMessage() + 
                        (e2.getMessage() != null ? "，修复后仍然失败: " + e2.getMessage() : ""));
                }
            }
            
            // 保存生成的脑图
            saveDocumentMindMap(documentId, userId, mindMapJson);
            
            logger.info("文档脑图生成成功 - 文档ID: {}", documentId);
            return mindMapJson;
            
        } catch (Exception e) {
            logger.error("生成文档脑图失败 - 文档ID: {}", documentId, e);
            throw new RuntimeException("生成文档脑图失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取文档笔记
     */
    @Override
    public String getDocumentNotes(Long documentId, Long userId) {
        validateDocumentAccess(documentId, userId);
        return notesRepository.findByDocumentId(documentId)
                .map(DocumentNotes::getContent)
                .orElse("");
    }
    
    /**
     * 保存文档笔记
     */
    @Transactional
    @Override
    public void saveDocumentNotes(Long documentId, Long userId, String content) {
        validateDocumentAccess(documentId, userId);
        
        Optional<DocumentNotes> optional = notesRepository.findByDocumentId(documentId);
        DocumentNotes notes;
        if (optional.isPresent()) {
            notes = optional.get();
            notes.setContent(content);
            notes.setUpdateTime(new Date());
        } else {
            notes = new DocumentNotes();
            notes.setDocumentId(documentId);
            notes.setContent(content);
            notes.setCreateTime(new Date());
            notes.setUpdateTime(new Date());
        }
        notesRepository.save(notes);
    }
    
    // 私有辅助方法
    
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }
        
        String fileExtension = getFileExtension(file.getOriginalFilename());
        if (fileExtension == null) {
            throw new RuntimeException("无法识别文件类型");
        }
        
        boolean allowed = false;
        for (String allowedType : ALLOWED_FILE_TYPES) {
            if (allowedType.equalsIgnoreCase(fileExtension)) {
                allowed = true;
                break;
            }
        }
        
        if (!allowed) {
            throw new RuntimeException("不支持的文件类型: " + fileExtension);
        }
        
        long maxSize = 100 * 1024 * 1024; // 100MB
        if (file.getSize() > maxSize) {
            throw new RuntimeException("文件大小不能超过100MB");
        }
    }
    
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }
    
    private String generateFilePath(Long userId, String originalFileName) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        String datePath = sdf.format(new Date());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileExtension = getFileExtension(originalFileName);
        return String.format("document-reader/%d/%s/%s.%s", userId, datePath, uuid, fileExtension);
    }
    
    private void validateDocumentAccess(Long documentId, Long userId) {
        Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
        if (!optional.isPresent()) {
            throw new NotFoundException("文档不存在: " + documentId);
        }
        
        DocumentReader document = optional.get();
        if (!document.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问此文档");
        }
    }
    
    private DocumentReaderResp convertToResp(DocumentReader document) {
        DocumentReaderResp resp = new DocumentReaderResp();
        BeanUtils.copyProperties(document, resp);
        resp.setUploadTime(document.getCreateTime());
        return resp;
    }
    
    /**
     * 从大模型响应中提取JSON部分
     * 处理可能包含说明文字的响应
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new RuntimeException("大模型返回内容为空");
        }
        
        String trimmed = response.trim();
        
        // 如果整个响应就是JSON（以{开头，以}结尾）
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        
        // 尝试提取JSON代码块
        // 查找 ```json ... ``` 或 ``` ... ```
        int jsonStart = trimmed.indexOf("```json");
        if (jsonStart == -1) {
            jsonStart = trimmed.indexOf("```");
        }
        
        if (jsonStart != -1) {
            // 找到代码块开始
            int codeStart = trimmed.indexOf("\n", jsonStart);
            if (codeStart == -1) {
                codeStart = jsonStart + (trimmed.substring(jsonStart).startsWith("```json") ? 7 : 3);
            } else {
                codeStart += 1; // 跳过换行符
            }
            
            // 查找代码块结束
            int codeEnd = trimmed.indexOf("```", codeStart);
            if (codeEnd != -1) {
                String jsonContent = trimmed.substring(codeStart, codeEnd).trim();
                if (jsonContent.startsWith("{")) {
                    return jsonContent;
                }
            }
        }
        
        // 尝试提取第一个完整的JSON对象
        int firstBrace = trimmed.indexOf("{");
        if (firstBrace != -1) {
            int braceCount = 0;
            int lastBrace = -1;
            for (int i = firstBrace; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        lastBrace = i;
                        break;
                    }
                }
            }
            
            if (lastBrace != -1) {
                String jsonContent = trimmed.substring(firstBrace, lastBrace + 1).trim();
                return jsonContent;
            }
        }
        
        // 如果都找不到，返回原始响应（让前端处理）
        logger.warn("无法从响应中提取JSON，返回原始内容: {}", trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed);
        return trimmed;
    }
    
    /**
     * 尝试修复常见的JSON格式问题
     * 处理未闭合的字符串、数组和对象
     */
    private String tryFixJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }
        
        // 第一步：修复未闭合的字符串
        String fixed = fixUnclosedStrings(json);
        
        // 第二步：修复未闭合的数组和对象
        fixed = fixUnclosedBrackets(fixed);
        
        // 第三步：确保JSON以 { 开头，以 } 结尾
        if (!fixed.startsWith("{")) {
            int firstBrace = fixed.indexOf("{");
            if (firstBrace != -1) {
                fixed = fixed.substring(firstBrace);
            }
        }
        
        if (!fixed.endsWith("}")) {
            // 尝试找到最后一个完整的闭合括号
            int lastBrace = findLastCompleteBrace(fixed);
            if (lastBrace != -1 && lastBrace > 0) {
                fixed = fixed.substring(0, lastBrace + 1);
            }
        }
        
        return fixed;
    }
    
    /**
     * 修复未闭合的字符串
     */
    private String fixUnclosedStrings(String json) {
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        int stringStart = -1;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                sb.append(c);
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                if (!inString) {
                    inString = true;
                    stringStart = i;
                } else {
                    inString = false;
                    stringStart = -1;
                }
                sb.append(c);
                continue;
            }
            
            // 如果在字符串中，确保特殊字符被转义
            if (inString) {
                if (c == '\n') {
                    sb.append("\\n");
                } else if (c == '\r') {
                    sb.append("\\r");
                } else if (c == '\t') {
                    sb.append("\\t");
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        
        // 如果字符串未闭合，尝试修复
        if (inString && stringStart != -1) {
            // 查找字符串开始位置到当前位置之间是否有明显的结束标记
            // 如果字符串太长（超过1000字符），可能是截断了，尝试在合适位置闭合
            int stringLength = sb.length() - stringStart;
            if (stringLength > 1000) {
                // 在当前位置之前查找可能的结束位置（遇到换行、逗号、}、]等）
                boolean foundEnd = false;
                for (int i = sb.length() - 1; i >= stringStart; i--) {
                    char ch = sb.charAt(i);
                    if (ch == '\n' || ch == ',' || ch == '}' || ch == ']') {
                        // 在这些字符之前闭合字符串
                        sb.insert(i, '"');
                        foundEnd = true;
                        break;
                    }
                }
                if (!foundEnd) {
                    // 如果找不到合适位置，直接闭合
                    sb.append('"');
                }
            } else {
                // 简单情况：直接闭合字符串
                sb.append('"');
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 修复未闭合的数组和对象括号
     */
    private String fixUnclosedBrackets(String json) {
        StringBuilder sb = new StringBuilder(json);
        int braceCount = 0;  // { }
        int bracketCount = 0; // [ ]
        boolean inString = false;
        boolean escaped = false;
        
        // 计算括号深度
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (inString) {
                continue;
            }
            
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
            } else if (c == '[') {
                bracketCount++;
            } else if (c == ']') {
                bracketCount--;
            }
        }
        
        // 修复未闭合的括号
        // 先修复数组，再修复对象
        while (bracketCount > 0) {
            sb.append(']');
            bracketCount--;
        }
        
        while (braceCount > 0) {
            sb.append('}');
            braceCount--;
        }
        
        return sb.toString();
    }
    
    /**
     * 找到最后一个完整的闭合括号位置
     */
    private int findLastCompleteBrace(String json) {
        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;
        boolean escaped = false;
        int lastValidBrace = -1;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (inString) {
                continue;
            }
            
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && bracketCount == 0) {
                    lastValidBrace = i;
                }
            } else if (c == '[') {
                bracketCount++;
            } else if (c == ']') {
                bracketCount--;
                if (braceCount == 0 && bracketCount == 0) {
                    // 如果这是数组的结束，但我们需要对象的结束
                    // 继续查找
                }
            }
        }
        
        return lastValidBrace;
    }
    
    /**
     * 检测文本是否为简体中文
     * 通过统计中文字符的比例来判断
     */
    private boolean isSimplifiedChinese(String text) {
        if (text == null || text.trim().isEmpty()) {
            return true; // 空文本默认为中文
        }
        
        int totalChars = 0;
        int chineseChars = 0;
        
        for (char c : text.toCharArray()) {
            // 跳过空白字符和标点符号
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                continue;
            }
            
            totalChars++;
            
            // 检测是否为中文字符（包括简体中文、繁体中文、日文汉字等）
            // 简体中文范围：\u4e00-\u9fa5
            if (c >= 0x4e00 && c <= 0x9fa5) {
                chineseChars++;
            }
        }
        
        if (totalChars == 0) {
            return true; // 没有有效字符，默认为中文
        }
        
        // 如果中文字符占比超过30%，认为是中文文档
        double chineseRatio = (double) chineseChars / totalChars;
        return chineseRatio >= 0.3;
    }
    
    /**
     * 将文本翻译为简体中文
     */
    private String translateToChinese(String text, QAModel qaModel) {
        return translateText(text, "zh", qaModel);
    }
    
    /**
     * 通用翻译方法，支持多种目标语言
     * 如果文本过长，会自动分段翻译并拼接
     * @param text 原文
     * @param targetLang 目标语言代码 (zh: 中文, en: 英文, ja: 日文, ko: 韩文)
     * @param qaModel 模型配置
     * @return 翻译后的文本
     */
    private String translateText(String text, String targetLang, QAModel qaModel) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        // 单次翻译的最大长度
        int maxTranslateLength = 10000;
        
        // 如果文本长度小于等于最大长度，直接翻译
        if (text.length() <= maxTranslateLength) {
            return translateTextSegment(text, targetLang, qaModel);
        }
        
        // 文本过长，需要分段翻译
        logger.info("文本过长 ({} 字符)，开始分段翻译", text.length());
        String targetLanguageName = getTargetLanguageName(targetLang);
        StringBuilder translatedResult = new StringBuilder();
        
        // 分段处理
        int totalLength = text.length();
        int segmentIndex = 0;
        int processedLength = 0;
        
        while (processedLength < totalLength) {
            segmentIndex++;
            int segmentStart = processedLength;
            int segmentEnd = Math.min(processedLength + maxTranslateLength, totalLength);
            
            // 尝试在段落边界处截断，避免在句子中间截断
            if (segmentEnd < totalLength) {
                // 向前查找最近的段落分隔符（换行符、句号等）
                int bestBreakPoint = findBestBreakPoint(text, segmentStart, segmentEnd);
                if (bestBreakPoint > segmentStart) {
                    segmentEnd = bestBreakPoint;
                }
            }
            
            String segment = text.substring(segmentStart, segmentEnd);
            logger.info("翻译第 {}/? 段，起始位置: {}, 结束位置: {}, 长度: {}", 
                segmentIndex, segmentStart, segmentEnd, segment.length());
            
            try {
                // 翻译当前段
                String translatedSegment = translateTextSegment(segment, targetLang, qaModel);
                translatedResult.append(translatedSegment);
                
                // 如果不是最后一段，添加段落分隔
                if (segmentEnd < totalLength) {
                    translatedResult.append("\n\n");
                }
                
                processedLength = segmentEnd;
                logger.info("第 {} 段翻译完成，已处理: {}/{} 字符", 
                    segmentIndex, processedLength, totalLength);
                
            } catch (Exception e) {
                logger.error("翻译第 {} 段失败", segmentIndex, e);
                // 如果某段翻译失败，添加错误标记并继续
                translatedResult.append("\n\n[第 ").append(segmentIndex).append(" 段翻译失败: ")
                    .append(e.getMessage()).append("]\n\n");
                processedLength = segmentEnd;
            }
        }
        
        String finalResult = translatedResult.toString();
        logger.info("分段翻译完成，目标语言: {}, 原文总长度: {}, 译文总长度: {}, 共 {} 段", 
            targetLanguageName, text.length(), finalResult.length(), segmentIndex);
        
        return finalResult;
    }
    
    /**
     * 翻译单个文本段
     */
    private String translateTextSegment(String textSegment, String targetLang, QAModel qaModel) {
        if (textSegment == null || textSegment.trim().isEmpty()) {
            return textSegment;
        }
        
        // 获取目标语言名称
        String targetLanguageName = getTargetLanguageName(targetLang);
        
        // 构建翻译提示词
        String translatePrompt = String.format(
            "请将以下文本翻译为%s。要求：\n" +
            "1. **严格保持原文的布局和格式**：\n" +
            "   - 保持所有换行符（\\n）的位置和数量\n" +
            "   - 保持段落之间的空行\n" +
            "   - 保持缩进和空格\n" +
            "   - 保持列表、标题等格式结构\n" +
            "2. **准确翻译内容**：\n" +
            "   - 准确翻译，不要遗漏任何内容\n" +
            "   - 专业术语要准确翻译\n" +
            "   - 保持原文的语气和风格\n" +
            "3. **格式要求**：\n" +
            "   - 只返回翻译后的文本，不要添加任何说明、注释或标记\n" +
            "   - 译文的行数和段落结构必须与原文完全一致\n" +
            "   - 如果原文某行是空行，译文对应位置也必须是空行\n" +
            "   - 如果原文有多个连续换行，译文也要保持相同数量的换行\n\n" +
            "原文：\n%s",
            targetLanguageName,
            textSegment
        );
        
        try {
            // 使用大模型进行翻译
            ChatLanguageModel chatModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new UserMessage(translatePrompt));
            
            Response<AiMessage> response = chatModel.generate(messages);
            String translatedText = response.content().text();
            
            logger.info("单段翻译完成，目标语言: {}, 原文长度: {}, 译文长度: {}", 
                targetLanguageName, textSegment.length(), translatedText.length());
            return translatedText;
            
        } catch (Exception e) {
            logger.error("单段翻译失败，目标语言: {}", targetLanguageName, e);
            throw new RuntimeException("翻译失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 查找最佳的分段截断点，尽量在段落或句子边界处截断
     */
    private int findBestBreakPoint(String text, int start, int end) {
        // 优先在段落边界（双换行）处截断
        int lastDoubleNewline = text.lastIndexOf("\n\n", end - 1);
        if (lastDoubleNewline > start && lastDoubleNewline > end - 2000) {
            return lastDoubleNewline + 2;
        }
        
        // 其次在单换行处截断
        int lastNewline = text.lastIndexOf("\n", end - 1);
        if (lastNewline > start && lastNewline > end - 1000) {
            return lastNewline + 1;
        }
        
        // 再次在句号、问号、感叹号处截断
        int lastSentenceEnd = -1;
        for (int i = end - 1; i >= start && i >= end - 500; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '.' || c == '！' || c == '!' || c == '？' || c == '?') {
                lastSentenceEnd = i + 1;
                break;
            }
        }
        if (lastSentenceEnd > start) {
            return lastSentenceEnd;
        }
        
        // 如果找不到合适的截断点，返回原结束位置
        return end;
    }
    
    /**
     * 获取目标语言的显示名称
     */
    private String getTargetLanguageName(String targetLang) {
        if (targetLang == null) {
            return "简体中文";
        }
        
        switch (targetLang.toLowerCase()) {
            case "zh":
            case "zh-cn":
            case "zh_cn":
                return "简体中文";
            case "en":
            case "en-us":
            case "en_us":
                return "英文";
            case "ja":
            case "ja-jp":
            case "ja_jp":
                return "日文";
            case "ko":
            case "ko-kr":
            case "ko_kr":
                return "韩文";
            default:
                return "简体中文"; // 默认返回中文
        }
    }
    
    /**
     * 获取文档原文文本内容
     */
    @Override
    public String getDocumentText(Long documentId, Long userId) {
        validateDocumentAccess(documentId, userId);
        
        Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
        if (!optional.isPresent()) {
            throw new NotFoundException("文档不存在: " + documentId);
        }
        
        DocumentReader document = optional.get();
        return extractDocumentText(document);
    }
}

