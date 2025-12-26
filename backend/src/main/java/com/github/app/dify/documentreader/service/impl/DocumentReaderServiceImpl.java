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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     * 翻译文档（懒加载模式：只翻译第一段）
     */
    @Override
    public void translateDocument(Long documentId, Long userId, String targetLang) {
        validateDocumentAccess(documentId, userId);
        
        logger.info("开始翻译文档（懒加载模式） - 文档ID: {}, 目标语言: {}", documentId, targetLang);
        
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
            
            // 检查是否为同种语言翻译（禁止同种语言翻译）
            if (isSameLanguageTranslation(documentContent, targetLang)) {
                String detectedLang = detectDocumentLanguage(documentContent);
                logger.warn("禁止同种语言翻译 - 文档ID: {}, 文档语言: {}, 目标语言: {}", 
                           documentId, detectedLang, targetLang);
                throw new IllegalArgumentException(
                    String.format("不能将%s文档翻译为%s，翻译功能仅支持不同语言之间的翻译", 
                                 detectedLang, targetLang));
            }
            
            // 获取模型配置（使用默认RAG模型）
            QAModel qaModel = modelConfigService.getDefaultQAModelForRAG();
            if (qaModel == null) {
                throw new RuntimeException("未配置可用的模型，无法进行翻译");
            }
            
            // 将文档分段（用于懒加载）
            List<DocumentSegment> segments = splitDocumentForTranslation(documentContent);
            logger.info("文档分段完成 - 文档ID: {}, 总段数: {}", documentId, segments.size());
            
            // 只翻译第一段（懒加载）
            if (!segments.isEmpty()) {
                DocumentSegment firstSegment = segments.get(0);
                String firstTranslated = translateTextSegment(firstSegment.getText(), targetLang, qaModel);
                firstSegment.setTranslatedText(firstTranslated);
                logger.info("第一段翻译完成 - 文档ID: {}, 段索引: 0, 原文长度: {}, 译文长度: {}", 
                    documentId, firstSegment.getText().length(), firstTranslated.length());
            }
            
            // 保存分段信息和翻译结果（JSON格式）
            saveDocumentTranslationSegments(documentId, userId, targetLang, segments);
            
            logger.info("文档翻译初始化完成（懒加载模式） - 文档ID: {}, 目标语言: {}, 总段数: {}, 已翻译: 1", 
                documentId, targetLang, segments.size());
            
        } catch (Exception e) {
            logger.error("翻译文档失败 - 文档ID: {}, 目标语言: {}", documentId, targetLang, e);
            throw new RuntimeException("翻译文档失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取文档翻译内容（兼容旧版本，返回所有已翻译的内容）
     */
    @Override
    public String getDocumentTranslation(Long documentId, Long userId, String targetLang) {
        validateDocumentAccess(documentId, userId);
        
        // 尝试获取分段翻译
        List<DocumentSegment> segments = loadDocumentTranslationSegments(documentId, targetLang);
        if (segments != null && !segments.isEmpty()) {
            // 按索引排序，确保段落顺序一致
            segments.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
            
            // 拼接所有已翻译的段落（按顺序）
            StringBuilder result = new StringBuilder();
            for (DocumentSegment segment : segments) {
                if (segment.getTranslatedText() != null && !segment.getTranslatedText().trim().isEmpty()) {
                    if (result.length() > 0) {
                        result.append("\n\n");
                    }
                    result.append(segment.getTranslatedText());
                }
            }
            return result.toString();
        }
        
        // 如果没有分段翻译，返回旧格式的翻译内容
        return translationRepository.findByDocumentIdAndTargetLanguage(documentId, targetLang)
                .map(DocumentTranslation::getContent)
                .orElse("");
    }
    
    /**
     * 获取文档翻译内容（懒加载模式，返回指定范围的翻译）
     */
    @Override
    public String getDocumentTranslationRange(Long documentId, Long userId, String targetLang, int startSegment, int endSegment) {
        validateDocumentAccess(documentId, userId);
        
        List<DocumentSegment> segments = loadDocumentTranslationSegments(documentId, targetLang);
        if (segments == null || segments.isEmpty()) {
            return "";
        }
        
        // 按索引排序，确保段落顺序一致
        segments.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
        
        // 确保索引有效
        startSegment = Math.max(0, startSegment);
        endSegment = Math.min(segments.size(), endSegment);
        
        if (startSegment >= endSegment) {
            return "";
        }
        
        // 拼接指定范围的翻译（按顺序）
        StringBuilder result = new StringBuilder();
        for (int i = startSegment; i < endSegment; i++) {
            DocumentSegment segment = segments.get(i);
            if (segment.getTranslatedText() != null && !segment.getTranslatedText().trim().isEmpty()) {
                if (result.length() > 0) {
                    result.append("\n\n");
                }
                result.append(segment.getTranslatedText());
            }
        }
        
        return result.toString();
    }
    
    /**
     * 获取文档分段信息
     */
    @Override
    public Map<String, Object> getDocumentSegments(Long documentId, Long userId) {
        validateDocumentAccess(documentId, userId);
        
        // 获取文档内容
        Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
        if (!optional.isPresent()) {
            throw new NotFoundException("文档不存在: " + documentId);
        }
        
        DocumentReader document = optional.get();
        String documentContent = extractDocumentText(document);
        
        if (documentContent == null || documentContent.trim().isEmpty()) {
            throw new RuntimeException("文档内容为空");
        }
        
        // 分段
        List<DocumentSegment> segments = splitDocumentForTranslation(documentContent);
        
        // 按索引排序，确保段落顺序一致
        segments.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
        
        // 构建返回信息
        Map<String, Object> result = new HashMap<>();
        result.put("totalSegments", segments.size());
        result.put("totalLength", documentContent.length());
        
        List<Map<String, Object>> segmentInfos = new ArrayList<>();
        for (DocumentSegment segment : segments) {
            Map<String, Object> info = new HashMap<>();
            info.put("index", segment.getIndex()); // 使用分段的实际索引
            info.put("start", segment.getStartIndex());
            info.put("end", segment.getEndIndex());
            info.put("length", segment.getText().length());
            segmentInfos.add(info);
        }
        result.put("segments", segmentInfos);
        
        return result;
    }
    
    /**
     * 翻译指定分段（懒加载）
     */
    @Override
    public String translateDocumentSegment(Long documentId, Long userId, String targetLang, int segmentIndex) {
        validateDocumentAccess(documentId, userId);
        
        logger.info("开始翻译指定分段 - 文档ID: {}, 目标语言: {}, 段索引: {}", documentId, targetLang, segmentIndex);
        
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
                throw new RuntimeException("文档内容为空，无法翻译");
            }
            
            // 检查是否为同种语言翻译（禁止同种语言翻译）
            if (isSameLanguageTranslation(documentContent, targetLang)) {
                String detectedLang = detectDocumentLanguage(documentContent);
                throw new IllegalArgumentException(
                    String.format("不能将%s文档翻译为%s，翻译功能仅支持不同语言之间的翻译", 
                                 detectedLang, targetLang));
            }
            
            // 获取模型配置
            QAModel qaModel = modelConfigService.getDefaultQAModelForRAG();
            if (qaModel == null) {
                throw new RuntimeException("未配置可用的模型，无法进行翻译");
            }
            
            // 加载或创建分段信息
            List<DocumentSegment> segments = loadDocumentTranslationSegments(documentId, targetLang);
            if (segments == null || segments.isEmpty()) {
                // 如果还没有分段信息，先创建
                segments = splitDocumentForTranslation(documentContent);
                saveDocumentTranslationSegments(documentId, userId, targetLang, segments);
            }
            
            // 按索引排序，确保段落顺序一致
            segments.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
            
            // 检查索引有效性
            if (segmentIndex < 0 || segmentIndex >= segments.size()) {
                throw new IllegalArgumentException("分段索引无效: " + segmentIndex + ", 总段数: " + segments.size());
            }
            
            // 检查是否已翻译（使用索引查找对应的分段）
            DocumentSegment segment = null;
            for (DocumentSegment seg : segments) {
                if (seg.getIndex() == segmentIndex) {
                    segment = seg;
                    break;
                }
            }
            
            if (segment == null) {
                throw new IllegalArgumentException("找不到索引为 " + segmentIndex + " 的分段");
            }
            if (segment.getTranslatedText() != null && !segment.getTranslatedText().trim().isEmpty()) {
                logger.info("分段已翻译，直接返回 - 文档ID: {}, 段索引: {}", documentId, segmentIndex);
                return segment.getTranslatedText();
            }
            
            // 翻译该分段
            String translated = translateTextSegment(segment.getText(), targetLang, qaModel);
            segment.setTranslatedText(translated);
            
            // 保存更新后的分段信息
            saveDocumentTranslationSegments(documentId, userId, targetLang, segments);
            
            logger.info("分段翻译完成 - 文档ID: {}, 段索引: {}, 原文长度: {}, 译文长度: {}", 
                documentId, segmentIndex, segment.getText().length(), translated.length());
            
            return translated;
            
        } catch (Exception e) {
            logger.error("翻译分段失败 - 文档ID: {}, 段索引: {}", documentId, segmentIndex, e);
            throw new RuntimeException("翻译分段失败: " + e.getMessage(), e);
        }
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
            int maxTextLength = Math.min(textLength, 15000);
            String truncatedText = documentContent;
            if (documentContent.length() > maxTextLength) {
                truncatedText = documentContent.substring(0, maxTextLength) + "\n\n[文档内容已截断，仅用于生成思维导图核心结构...]";
            }
            
            // 构建提示词（优化后的简洁版本）
            String prompt = buildMindMapPrompt(fileName, textLength, contentAssessment, truncatedText, maxLevel, maxNodesPerLevel);
            
            // 调用大模型生成脑图（带重试机制）
            String mindMapJson = generateMindMapWithRetry(qaModel, prompt, fileName, documentId, 3);
            
            // 保存生成的脑图
            saveDocumentMindMap(documentId, userId, mindMapJson);
            
            logger.info("文档脑图生成成功 - 文档ID: {}", documentId);
            return mindMapJson;
            
        } catch (RuntimeException e) {
            // 如果是RuntimeException，直接抛出（可能已经包含友好的错误信息）
            logger.error("生成文档脑图失败 - 文档ID: {}", documentId, e);
            throw e;
        } catch (Exception e) {
            logger.error("生成文档脑图失败 - 文档ID: {}", documentId, e);
            // 提供更友好的错误信息
            String errorMessage = "生成文档脑图失败";
            if (e.getMessage() != null) {
                if (e.getMessage().contains("JSON") || e.getMessage().contains("格式")) {
                    errorMessage = "生成文档脑图失败：JSON格式错误，已尝试自动修复但未成功。请稍后重试或联系管理员。";
                } else if (e.getMessage().contains("超时") || e.getMessage().contains("timeout")) {
                    errorMessage = "生成文档脑图失败：请求超时，请稍后重试。";
                } else if (e.getMessage().contains("模型") || e.getMessage().contains("model")) {
                    errorMessage = "生成文档脑图失败：模型配置错误，请检查系统配置。";
                } else {
                    errorMessage = "生成文档脑图失败：" + e.getMessage();
                }
            }
            throw new RuntimeException(errorMessage, e);
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
     * 从大模型响应中提取JSON部分（增强版）
     * 处理可能包含说明文字的响应，更准确地识别JSON边界
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new RuntimeException("大模型返回内容为空");
        }
        
        String trimmed = response.trim();
        
        // 移除可能的BOM标记
        if (trimmed.startsWith("\uFEFF")) {
            trimmed = trimmed.substring(1);
        }
        
        // 如果整个响应就是JSON（以{开头，以}结尾）
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            // 验证是否真的是完整JSON
            if (isCompleteJson(trimmed)) {
                return trimmed;
            }
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
            
            // 查找代码块结束（从codeStart之后开始查找，避免找到开始标记）
            int codeEnd = trimmed.indexOf("```", codeStart);
            if (codeEnd != -1) {
                String jsonContent = trimmed.substring(codeStart, codeEnd).trim();
                if (jsonContent.startsWith("{")) {
                    return jsonContent;
                }
            }
        }
        
        // 尝试提取第一个完整的JSON对象(考虑字符串内的括号和转义)
        int firstBrace = trimmed.indexOf("{");
        if (firstBrace != -1) {
            // 从第一个{开始，向后查找匹配的}
            int lastBrace = findMatchingBrace(trimmed, firstBrace);
            if (lastBrace != -1) {
                String jsonContent = trimmed.substring(firstBrace, lastBrace + 1).trim();
                return jsonContent;
            } else {
                // 如果找不到完整闭合，返回从第一个 { 开始到结尾的内容(后续会尝试修复)
                logger.warn("JSON可能被截断，提取不完整的JSON，长度: {}", trimmed.length());
                return trimmed.substring(firstBrace);
            }
        }
        
        // 如果都找不到，返回原始响应（让前端处理）
        logger.warn("无法从响应中提取JSON，返回原始内容，长度: {}", trimmed.length());
        return trimmed;
    }
    
    /**
     * 检查字符串是否是完整的JSON
     */
    private boolean isCompleteJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;
        boolean escaped = false;
        
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
            } else if (c == '[') {
                bracketCount++;
            } else if (c == ']') {
                bracketCount--;
            }
        }
        
        return braceCount == 0 && bracketCount == 0 && !inString;
    }
    
    /**
     * 查找匹配的闭合括号位置
     */
    private int findMatchingBrace(String text, int startPos) {
        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = startPos; i < text.length(); i++) {
            char c = text.charAt(i);
            
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
                    return i;
                }
            } else if (c == '[') {
                bracketCount++;
            } else if (c == ']') {
                bracketCount--;
            }
        }
        
        return -1; // 未找到匹配的闭合括号
    }
    
    /**
     * 尝试修复常见的JSON格式问题（增强版）
     * 处理未闭合的字符串、数组和对象，以及更多边界情况
     */
    private String tryFixJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }
        
        String fixed = json;
        
        // 第一步：移除JSON注释（虽然不应该有，但LLM可能生成）
        fixed = removeJsonComments(fixed);
        
        // 第二步：修复未闭合的字符串
        fixed = fixUnclosedStrings(fixed);
        
        // 第三步：修复字段名缺少引号的问题
        fixed = fixUnquotedFieldNames(fixed);
        
        // 第四步：修复未闭合的数组和对象
        fixed = fixUnclosedBrackets(fixed);
        
        // 第五步：修复多余的逗号
        fixed = fixTrailingCommas(fixed);
        
        // 第六步：确保JSON以 { 开头，以 } 结尾
        if (!fixed.startsWith("{")) {
            int firstBrace = fixed.indexOf("{");
            if (firstBrace != -1) {
                fixed = fixed.substring(firstBrace);
            }
        }
        
        // 第七步：如果JSON被截断，尝试找到最后一个有效位置并闭合
        if (!fixed.endsWith("}")) {
            // 尝试找到最后一个完整的闭合括号
            int lastBrace = findLastCompleteBrace(fixed);
            if (lastBrace != -1 && lastBrace > 0) {
                fixed = fixed.substring(0, lastBrace + 1);
            } else {
                // 如果找不到完整闭合，尝试智能截断并闭合
                fixed = smartTruncateAndClose(fixed);
            }
        }
        
        return fixed;
    }
    

    private String removeJsonComments(String json) {
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        int i = 0;
        
        while (i < json.length()) {
            char c = json.charAt(i);
            
            if (escaped) {
                sb.append(c);
                escaped = false;
                i++;
                continue;
            }
            
            if (c == '\\') {
                sb.append(c);
                escaped = true;
                i++;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                sb.append(c);
                i++;
                continue;
            }
            
            if (inString) {
                sb.append(c);
                i++;
                continue;
            }
            
            // 检查单行注释 //
            if (i + 1 < json.length() && c == '/' && json.charAt(i + 1) == '/') {
                // 跳过到行尾
                while (i < json.length() && json.charAt(i) != '\n' && json.charAt(i) != '\r') {
                    i++;
                }
                // 保留换行符
                if (i < json.length()) {
                    sb.append(json.charAt(i));
                    i++;
                }
                continue;
            }
            
            // 检查多行注释 /* */
            if (i + 1 < json.length() && c == '/' && json.charAt(i + 1) == '*') {
                // 跳过到 */
                i += 2;
                while (i + 1 < json.length()) {
                    if (json.charAt(i) == '*' && json.charAt(i + 1) == '/') {
                        i += 2;
                        break;
                    }
                    i++;
                }
                continue;
            }
            
            sb.append(c);
            i++;
        }
        
        return sb.toString();
    }
    
    /**
     * 修复字段名缺少引号的问题
     * 处理类似 { id: "value" } 的情况，应该改为 { "id": "value" }
     */
    private String fixUnquotedFieldNames(String json) {
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        boolean expectingFieldName = false; // 在对象中，期望字段名
        int braceDepth = 0;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            char nextChar = (i + 1 < json.length()) ? json.charAt(i + 1) : '\0';
            
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
                inString = !inString;
                expectingFieldName = false;
                sb.append(c);
                continue;
            }
            
            if (inString) {
                sb.append(c);
                continue;
            }
            
            if (c == '{') {
                braceDepth++;
                expectingFieldName = true;
                sb.append(c);
            } else if (c == '}') {
                braceDepth--;
                expectingFieldName = false;
                sb.append(c);
            } else if (c == '[' || c == ']') {
                expectingFieldName = false;
                sb.append(c);
            } else if (c == ':') {
                expectingFieldName = false;
                sb.append(c);
            } else if (c == ',') {
                expectingFieldName = true;
                sb.append(c);
            } else if (expectingFieldName && Character.isLetterOrDigit(c)) {
                // 如果期望字段名但遇到字母或数字（没有引号），添加引号
                // 查找字段名的结束位置（遇到冒号或空白）
                int fieldNameEnd = i;
                while (fieldNameEnd < json.length()) {
                    char ch = json.charAt(fieldNameEnd);
                    if (ch == ':' || Character.isWhitespace(ch)) {
                        break;
                    }
                    if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != '-') {
                        break;
                    }
                    fieldNameEnd++;
                }
                
                // 提取字段名并添加引号
                String fieldName = json.substring(i, fieldNameEnd);
                sb.append('"').append(fieldName).append('"');
                i = fieldNameEnd - 1; // 循环会自增，所以减1
                expectingFieldName = false;
            } else {
                sb.append(c);
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 修复多余的尾随逗号
     */
    private String fixTrailingCommas(String json) {
        StringBuilder sb = new StringBuilder(json);
        boolean inString = false;
        boolean escaped = false;
        
        // 从后往前查找并删除多余的逗号
        for (int i = sb.length() - 1; i >= 0; i--) {
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
            
            // 如果遇到逗号，检查后面是否跟着 } 或 ]
            if (c == ',') {
                // 跳过空白字符
                int j = i + 1;
                while (j < sb.length() && Character.isWhitespace(sb.charAt(j))) {
                    j++;
                }
                
                // 如果逗号后面直接是 } 或 ]，删除逗号
                if (j < sb.length() && (sb.charAt(j) == '}' || sb.charAt(j) == ']')) {
                    sb.deleteCharAt(i);
                }
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 智能截断并闭合JSON
     * 当JSON被截断时，尝试在合适的位置截断并闭合所有未闭合的结构
     */
    private String smartTruncateAndClose(String json) {
        StringBuilder sb = new StringBuilder(json);
        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;
        boolean escaped = false;
        int lastValidPos = -1;
        
        // 从后往前查找最后一个有效位置
        for (int i = sb.length() - 1; i >= 0; i--) {
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
            
            // 如果遇到闭合括号，记录位置
            if (c == '}' && braceCount == 0 && bracketCount == 0) {
                lastValidPos = i;
                break;
            } else if (c == ']' && bracketCount == 0 && braceCount == 0) {
                lastValidPos = i;
                break;
            }
            
            // 计算括号深度
            if (c == '}') {
                braceCount++;
            } else if (c == '{') {
                braceCount--;
            } else if (c == ']') {
                bracketCount++;
            } else if (c == '[') {
                bracketCount--;
            }
        }
        
        // 如果找到了有效位置，截断到该位置
        if (lastValidPos > 0) {
            sb.setLength(lastValidPos + 1);
        }
        
        // 重新计算需要闭合的括号
        braceCount = 0;
        bracketCount = 0;
        inString = false;
        escaped = false;
        
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
        
        // 如果字符串未闭合，先闭合字符串
        if (inString) {
            sb.append('"');
        }
        
        // 闭合未闭合的数组
        while (bracketCount > 0) {
            sb.append(']');
            bracketCount--;
        }
        
        // 闭合未闭合的对象
        while (braceCount > 0) {
            sb.append('}');
            braceCount--;
        }
        
        return sb.toString();
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
     * 检测文档的主要语言
     * 返回语言代码：zh（中文）、en（英文）、ja（日文）、ko（韩文）等
     * 如果无法确定，返回 null
     */
    private String detectDocumentLanguage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null; // 空文本无法检测
        }
        
        // 取前2000个字符进行检测
        String sampleText = text.length() > 2000 ? text.substring(0, 2000) : text;
        
        int totalChars = 0;
        int chineseChars = 0;
        int japaneseChars = 0;
        int koreanChars = 0;
        int englishChars = 0;
        
        for (char c : sampleText.toCharArray()) {
            // 跳过空白字符和标点符号
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                continue;
            }
            
            totalChars++;
            
            // 检测中文（简体中文范围：\u4e00-\u9fa5）
            if (c >= 0x4e00 && c <= 0x9fa5) {
                chineseChars++;
            }
            // 检测日文（平假名：\u3040-\u309F，片假名：\u30A0-\u30FF，日文汉字：\u4E00-\u9FAF）
            else if ((c >= 0x3040 && c <= 0x309F) || (c >= 0x30A0 && c <= 0x30FF) || 
                     (c >= 0x4E00 && c <= 0x9FAF)) {
                japaneseChars++;
            }
            // 检测韩文（\uAC00-\uD7AF）
            else if (c >= 0xAC00 && c <= 0xD7AF) {
                koreanChars++;
            }
            // 检测英文（ASCII字母）
            else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                englishChars++;
            }
        }
        
        if (totalChars == 0) {
            return null; // 没有有效字符，无法检测
        }
        
        // 计算各语言占比
        double chineseRatio = (double) chineseChars / totalChars;
        double japaneseRatio = (double) japaneseChars / totalChars;
        double koreanRatio = (double) koreanChars / totalChars;
        double englishRatio = (double) englishChars / totalChars;
        
        // 如果某种语言占比超过30%，认为是该语言
        if (chineseRatio >= 0.3) {
            return "zh";
        } else if (japaneseRatio >= 0.3) {
            return "ja";
        } else if (koreanRatio >= 0.3) {
            return "ko";
        } else if (englishRatio >= 0.3) {
            return "en";
        }
        
        // 如果无法确定主要语言，返回 null
        return null;
    }
    
    /**
     * 检查是否为同种语言翻译（禁止同种语言翻译）
     * @param documentContent 文档内容
     * @param targetLang 目标语言代码
     * @return true 如果是同种语言，false 如果不是
     */
    private boolean isSameLanguageTranslation(String documentContent, String targetLang) {
        String detectedLang = detectDocumentLanguage(documentContent);
        if (detectedLang == null) {
            // 无法检测语言，允许翻译（保守处理）
            return false;
        }
        // 检查检测到的语言是否与目标语言相同
        return detectedLang.equalsIgnoreCase(targetLang);
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
     * 将文档分段（用于懒加载翻译）
     */
    private List<DocumentSegment> splitDocumentForTranslation(String documentContent) {
        List<DocumentSegment> segments = new ArrayList<>();
        
        // 分段大小：每段约5000字符（适合翻译）
        int segmentSize = 5000;
        int totalLength = documentContent.length();
        int processedLength = 0;
        int segmentIndex = 0;
        
        while (processedLength < totalLength) {
            int segmentStart = processedLength;
            int segmentEnd = Math.min(processedLength + segmentSize, totalLength);
            
            // 尝试在段落边界处截断
            if (segmentEnd < totalLength) {
                int bestBreakPoint = findBestBreakPoint(documentContent, segmentStart, segmentEnd);
                if (bestBreakPoint > segmentStart) {
                    segmentEnd = bestBreakPoint;
                }
            }
            
            String segmentText = documentContent.substring(segmentStart, segmentEnd);
            DocumentSegment segment = new DocumentSegment();
            segment.setIndex(segmentIndex);
            segment.setStartIndex(segmentStart);
            segment.setEndIndex(segmentEnd);
            segment.setText(segmentText);
            segment.setTranslatedText(null); // 初始未翻译
            
            segments.add(segment);
            processedLength = segmentEnd;
            segmentIndex++;
        }
        
        return segments;
    }
    
    /**
     * 保存文档分段翻译信息（JSON格式）
     */
    @Transactional
    protected void saveDocumentTranslationSegments(Long documentId, Long userId, String targetLang, List<DocumentSegment> segments) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            
            // 构建分段数据
            Map<String, Object> segmentsData = new HashMap<>();
            segmentsData.put("version", "1.0");
            segmentsData.put("totalSegments", segments.size());
            segmentsData.put("targetLang", targetLang);
            
            // 按索引排序，确保保存时顺序一致
            List<DocumentSegment> sortedSegments = new ArrayList<>(segments);
            sortedSegments.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
            
            List<Map<String, Object>> segmentList = new ArrayList<>();
            for (DocumentSegment segment : sortedSegments) {
                Map<String, Object> segData = new HashMap<>();
                segData.put("index", segment.getIndex());
                segData.put("start", segment.getStartIndex());
                segData.put("end", segment.getEndIndex());
                segData.put("text", segment.getText());
                segData.put("translated", segment.getTranslatedText());
                segmentList.add(segData);
            }
            segmentsData.put("segments", segmentList);
            
            // 转换为JSON字符串
            String jsonContent = objectMapper.writeValueAsString(segmentsData);
            
            // 保存到数据库
            saveDocumentTranslation(documentId, userId, targetLang, jsonContent);
            
        } catch (Exception e) {
            logger.error("保存分段翻译信息失败 - 文档ID: {}, 目标语言: {}", documentId, targetLang, e);
            throw new RuntimeException("保存分段翻译信息失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加载文档分段翻译信息
     */
    private List<DocumentSegment> loadDocumentTranslationSegments(Long documentId, String targetLang) {
        try {
            Optional<DocumentTranslation> optional = translationRepository.findByDocumentIdAndTargetLanguage(documentId, targetLang);
            if (!optional.isPresent()) {
                return null;
            }
            
            String content = optional.get().getContent();
            if (content == null || content.trim().isEmpty()) {
                return null;
            }
            
            // 尝试解析JSON格式的分段数据
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> data = objectMapper.readValue(content, Map.class);
            
            // 检查是否是分段格式
            if (!data.containsKey("segments") || !data.containsKey("version")) {
                // 旧格式，返回null，让调用者使用旧方法
                return null;
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> segmentList = (List<Map<String, Object>>) data.get("segments");
            List<DocumentSegment> segments = new ArrayList<>();
            
            for (Map<String, Object> segData : segmentList) {
                DocumentSegment segment = new DocumentSegment();
                segment.setIndex(((Number) segData.get("index")).intValue());
                segment.setStartIndex(((Number) segData.get("start")).intValue());
                segment.setEndIndex(((Number) segData.get("end")).intValue());
                segment.setText((String) segData.get("text"));
                segment.setTranslatedText((String) segData.get("translated"));
                segments.add(segment);
            }
            
            // 按索引排序，确保段落顺序一致
            segments.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
            
            return segments;
            
        } catch (Exception e) {
            logger.warn("加载分段翻译信息失败，可能使用旧格式 - 文档ID: {}, 目标语言: {}", documentId, targetLang, e);
            return null;
        }
    }
    
    /**
     * 文档分段信息（内部类）
     */
    private static class DocumentSegment {
        private int index;
        private int startIndex;
        private int endIndex;
        private String text;
        private String translatedText;
        
        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        
        public int getStartIndex() { return startIndex; }
        public void setStartIndex(int startIndex) { this.startIndex = startIndex; }
        
        public int getEndIndex() { return endIndex; }
        public void setEndIndex(int endIndex) { this.endIndex = endIndex; }
        
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        
        public String getTranslatedText() { return translatedText; }
        public void setTranslatedText(String translatedText) { this.translatedText = translatedText; }
    }
    
    /**
     * 尝试更激进的JSON修复策略（增强版）
     * 当常规修复失败时，尝试删除最后一个不完整的节点，处理更多边界情况
     */
    private String tryAggressiveFix(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }
        
        String fixed = json;
        
        // 策略1：尝试找到最后一个完整的children数组
        int lastCompleteChildrenEnd = findLastCompleteChildrenArray(fixed);
        if (lastCompleteChildrenEnd > 0) {
            String truncated = fixed.substring(0, lastCompleteChildrenEnd + 1);
            String closed = closeJsonStructure(truncated);
            if (isValidJsonStructure(closed)) {
                return closed;
            }
        }
        
        // 策略2：尝试找到最后一个完整的节点（有topic字段且已闭合）
        int lastCompleteNodeEnd = findLastCompleteNode(fixed);
        if (lastCompleteNodeEnd > 0) {
            String truncated = fixed.substring(0, lastCompleteNodeEnd + 1);
            String closed = closeJsonStructure(truncated);
            if (isValidJsonStructure(closed)) {
                return closed;
            }
        }
        
        // 策略3：删除最后一个不完整的对象
        String removed = removeLastIncompleteObject(fixed);
        if (!removed.equals(fixed)) {
            String closed = closeJsonStructure(removed);
            if (isValidJsonStructure(closed)) {
                return closed;
            }
        }
        
        // 策略4：如果都失败，尝试从后往前删除不完整的部分，直到找到有效的JSON
        String prefix = findValidJsonPrefix(fixed);
        if (prefix != null && !prefix.equals(fixed)) {
            return prefix;
        }
        
        // 策略5：如果所有策略都失败，返回原始JSON（让调用者处理）
        return fixed;
    }
    
    /**
     * 闭合JSON结构（添加缺失的括号）
     */
    private String closeJsonStructure(String json) {
        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;
        boolean escaped = false;
        
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
            } else if (c == '[') {
                bracketCount++;
            } else if (c == ']') {
                bracketCount--;
            }
        }
        
        StringBuilder sb = new StringBuilder(json);
        if (inString) {
            sb.append('"');
        }
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
     * 检查JSON结构是否有效（基本结构检查）
     */
    private boolean isValidJsonStructure(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false;
        }
        
        // 检查基本结构：至少包含meta、format、data字段
        return trimmed.contains("\"meta\"") && 
               trimmed.contains("\"format\"") && 
               trimmed.contains("\"data\"");
    }
    
    /**
     * 查找最后一个完整的节点（有topic字段且已闭合）
     */
    private int findLastCompleteNode(String json) {
        int lastNodeEnd = -1;
        boolean inString = false;
        boolean escaped = false;
        int braceCount = 0;
        boolean foundTopic = false;
        int topicStart = -1;
        
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
                if (!inString) {
                    inString = true;
                    // 检查是否是"topic"字段
                    if (i + 6 < json.length() && json.substring(i, i + 7).equals("\"topic\"")) {
                        foundTopic = true;
                        topicStart = i;
                    }
                } else {
                    inString = false;
                }
                continue;
            }
            
            if (inString) {
                continue;
            }
            
            if (c == '{') {
                braceCount++;
                if (braceCount == 1) {
                    foundTopic = false; // 新对象开始，重置标志
                }
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && foundTopic) {
                    // 找到了一个完整的节点（有topic字段且已闭合）
                    lastNodeEnd = i;
                }
            }
        }
        
        return lastNodeEnd;
    }
    
    /**
     * 从后往前查找有效的JSON前缀
     */
    private String findValidJsonPrefix(String json) {
        if (json == null || json.length() < 50) {
            return null;
        }
        
        // 从后往前，逐步删除字符，直到找到有效的JSON结构
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (int end = json.length(); end > 100; end -= 50) {
            String candidate = json.substring(0, end);
            String closed = closeJsonStructure(candidate);
            if (isValidJsonStructure(closed)) {
                try {
                    mapper.readTree(closed);
                    logger.info("找到有效的JSON前缀，长度: {}", closed.length());
                    return closed;
                } catch (Exception e) {
                    // 继续尝试
                }
            }
        }
        
        // 如果都失败，返回null（让调用者使用其他策略）
        return null;
    }
    
    /**
     * 查找最后一个完整的children数组闭合位置
     */
    private int findLastCompleteChildrenArray(String json) {
        int lastPos = -1;
        boolean inString = false;
        boolean escaped = false;
        int bracketCount = 0;
        boolean inChildrenArray = false;
        int childrenArrayStart = -1;
        
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
            
            // 查找 "children": [ 模式（不在字符串中）
            if (!inString && i + 10 < json.length() && json.substring(i, i + 10).equals("\"children\"")) {
                // 跳过冒号和空白
                int j = i + 10;
                while (j < json.length() && (Character.isWhitespace(json.charAt(j)) || json.charAt(j) == ':')) {
                    j++;
                }
                if (j < json.length() && json.charAt(j) == '[') {
                    inChildrenArray = true;
                    bracketCount = 1; // 已经遇到了 [
                    childrenArrayStart = j;
                }
            }
            
            if (inChildrenArray) {
                if (c == '[') {
                    bracketCount++;
                } else if (c == ']') {
                    bracketCount--;
                    if (bracketCount == 0) {
                        lastPos = i;
                        inChildrenArray = false;
                    }
                }
            }
        }
        
        return lastPos;
    }
    
    /**
     * 删除最后一个不完整的对象
     */
    private String removeLastIncompleteObject(String json) {
        // 从后往前查找，找到最后一个完整的对象闭合位置
        int lastBrace = findLastCompleteBrace(json);
        if (lastBrace > 0) {
            return json.substring(0, lastBrace + 1);
        }
        
        // 如果找不到，尝试删除最后一个不完整的节点
        // 查找最后一个完整的节点（以 } 结尾，且前面有 "topic" 字段）
        int lastNodeEnd = -1;
        boolean inString = false;
        boolean escaped = false;
        int braceCount = 0;
        boolean foundTopic = false;
        
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
                // 检查是否是 "topic" 字段
                if (i + 5 < json.length() && json.substring(i - 1, i + 5).equals("\"topic\"")) {
                    foundTopic = true;
                }
                continue;
            }
            
            if (c == '{') {
                braceCount++;
                foundTopic = false; // 新对象开始，重置标志
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && foundTopic) {
                    // 找到了一个完整的节点（有topic字段且已闭合）
                    lastNodeEnd = i;
                }
            }
        }
        
        if (lastNodeEnd > 0) {
            // 截断到最后一个完整节点，并正确闭合结构
            String truncated = json.substring(0, lastNodeEnd + 1);
            return smartTruncateAndClose(truncated);
        }
        
        return json;
    }
    
    /**
     * 创建一个最小有效的JSON结构（重载方法，支持传入文件名）
     * 当所有修复都失败时，尝试从原始响应中提取基本信息并构建一个简单的有效JSON
     */
    private String createMinimalValidJson(String rawResponse, String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "文档";
        }
        
        // 尝试从原始响应中提取文档名称（如果响应中有）
        if (rawResponse != null) {
            int nameStart = rawResponse.indexOf("\"name\":");
            if (nameStart != -1) {
                int nameValueStart = rawResponse.indexOf("\"", nameStart + 7);
                if (nameValueStart != -1) {
                    int nameValueEnd = rawResponse.indexOf("\"", nameValueStart + 1);
                    if (nameValueEnd != -1) {
                        String extractedName = rawResponse.substring(nameValueStart + 1, nameValueEnd);
                        if (extractedName != null && !extractedName.trim().isEmpty()) {
                            fileName = extractedName;
                        }
                    }
                }
            }
        }
        
        // 创建一个最小有效的JSON结构
        return String.format(
            "{\"meta\":{\"name\":\"%s\",\"author\":\"系统\",\"version\":\"1.0\"},\"format\":\"node_tree\",\"data\":{\"id\":\"root\",\"topic\":\"%s\",\"children\":[]}}",
            escapeJsonString(fileName),
            escapeJsonString(fileName)
        );
    }
    
    /**
     * 创建一个最小有效的JSON结构（兼容旧方法）
     */
    private String createMinimalValidJson(String rawResponse) {
        return createMinimalValidJson(rawResponse, "文档");
    }
    
    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
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
    
    /**
     * 带重试机制的脑图生成方法
     */
    private String generateMindMapWithRetry(QAModel qaModel, String basePrompt, String fileName, 
                                            Long documentId, int maxRetries) {
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ChatLanguageModel chatModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("生成脑图 - 文档ID: {}, 尝试次数: {}/{}", documentId, attempt, maxRetries);
                
                // 如果是重试，在提示词中强调格式要求
                String prompt = basePrompt;
                if (attempt > 1) {
                    prompt = basePrompt + "\n\n**⚠️ 重要提醒(第" + attempt + "次尝试)**：\n" +
                             "请确保输出的是完整、有效的JSON格式，不要有任何格式错误！\n" +
                             "输出前请仔细检查：所有字符串用双引号、无尾随逗号、括号匹配、JSON完整！";
                }
                
                List<ChatMessage> messages = new ArrayList<>();
                messages.add(new UserMessage(prompt));
                
                Response<AiMessage> response = chatModel.generate(messages);
                String rawResponse = response.content().text();
                
                if (rawResponse == null || rawResponse.trim().isEmpty()) {
                    throw new RuntimeException("大模型返回内容为空");
                }
                
                // 从响应中提取JSON部分
                String mindMapJson = extractJsonFromResponse(rawResponse);
                
                // 验证并修复JSON格式
                try {
                    objectMapper.readTree(mindMapJson);
                    logger.info("JSON验证成功 - 文档ID: {}, 尝试次数: {}", documentId, attempt);
                    return mindMapJson;
                } catch (Exception e) {
                    logger.warn("首次JSON验证失败，尝试修复JSON格式 - 文档ID: {}, 尝试次数: {}, 错误: {}", 
                               documentId, attempt, e.getMessage());
                    
                    // 尝试修复常见的JSON问题
                    String fixedJson = tryFixJson(mindMapJson);
                    try {
                        objectMapper.readTree(fixedJson);
                        logger.info("JSON修复成功 - 文档ID: {}, 尝试次数: {}", documentId, attempt);
                        return fixedJson;
                    } catch (Exception e2) {
                        logger.warn("第一次修复失败，尝试更激进的修复策略 - 文档ID: {}, 尝试次数: {}, 错误: {}", 
                                   documentId, attempt, e2.getMessage());
                        
                        // 尝试更激进的修复
                        String aggressiveFixed = tryAggressiveFix(fixedJson);
                        try {
                            objectMapper.readTree(aggressiveFixed);
                            logger.info("激进修复成功 - 文档ID: {}, 尝试次数: {}", documentId, attempt);
                            return aggressiveFixed;
                        } catch (Exception e3) {
                            // 如果是最后一次尝试，记录详细错误并抛出异常
                            if (attempt == maxRetries) {
                                logDetailedError(rawResponse, mindMapJson, fixedJson, aggressiveFixed, 
                                                e, e2, e3, documentId, attempt);
                                // 尝试创建最小有效JSON作为最后的备选方案
                                String minimalJson = createMinimalValidJson(rawResponse, fileName);
                                try {
                                    objectMapper.readTree(minimalJson);
                                    logger.warn("使用最小有效JSON结构 - 文档ID: {}, 尝试次数: {}", documentId, attempt);
                                    return minimalJson;
                                } catch (Exception e4) {
                                    throw new RuntimeException("生成的脑图数据格式错误，所有修复尝试均失败: " + 
                                        e.getMessage() + 
                                        (e2.getMessage() != null ? "，修复后: " + e2.getMessage() : "") +
                                        (e3.getMessage() != null ? "，激进修复后: " + e3.getMessage() : "") +
                                        (e4.getMessage() != null ? "，最小JSON也失败: " + e4.getMessage() : ""));
                                }
                            } else {
                                // 不是最后一次尝试，记录警告并继续重试
                                logger.warn("修复失败，将重试 - 文档ID: {}, 尝试次数: {}/{}, 错误: {}", 
                                           documentId, attempt, maxRetries, e3.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("生成脑图失败 - 文档ID: {}, 尝试次数: {}/{}", documentId, attempt, maxRetries, e);
                if (attempt == maxRetries) {
                    // 最后一次尝试失败，抛出异常
                    throw new RuntimeException("生成脑图失败（已重试" + (maxRetries - 1) + "次）: " + e.getMessage(), e);
                }
                // 否则继续重试
            }
            
            // 重试前等待一小段时间
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(1000 * attempt); // 递增等待时间：1秒、2秒、3秒...
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试等待被中断", ie);
                }
            }
        }
        
        // 理论上不会到达这里，但为了编译通过
        throw new RuntimeException("生成脑图失败：所有重试均失败");
    }
    
    /**
     * 记录详细的错误信息
     */
    private void logDetailedError(String rawResponse, String mindMapJson, String fixedJson, 
                                  String aggressiveFixed, Exception e1, Exception e2, Exception e3,
                                  Long documentId, int attempt) {
        logger.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        logger.error("脑图生成失败 - 文档ID: {}, 尝试次数: {}", documentId, attempt);
        logger.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        logger.error("原始响应长度: {}, 前1000字符: {}", 
            rawResponse.length(), 
            rawResponse.length() > 1000 ? rawResponse.substring(0, 1000) + "..." : rawResponse);
        logger.error("提取的JSON长度: {}, 前500字符: {}", 
            mindMapJson.length(), 
            mindMapJson.length() > 500 ? mindMapJson.substring(0, 500) + "..." : mindMapJson);
        logger.error("修复后的JSON长度: {}, 前500字符: {}", 
            fixedJson.length(), 
            fixedJson.length() > 500 ? fixedJson.substring(0, 500) + "..." : fixedJson);
        logger.error("激进修复后的JSON长度: {}, 前500字符: {}", 
            aggressiveFixed.length(), 
            aggressiveFixed.length() > 500 ? aggressiveFixed.substring(0, 500) + "..." : aggressiveFixed);
        logger.error("首次验证错误: {}", e1.getMessage());
        if (e2 != null) {
            logger.error("修复后错误: {}", e2.getMessage());
        }
        if (e3 != null) {
            logger.error("激进修复后错误: {}", e3.getMessage());
        }
        logger.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
    
    /**
     * 构建脑图生成提示词（优化后的简洁版本）
     */
    private String buildMindMapPrompt(String fileName, int textLength, String contentAssessment, 
                                      String truncatedText, int maxLevel, int maxNodesPerLevel) {
        // 简化后的提示词，将格式要求前置，减少重复
        return String.format(
            "你是一个专业的思维导图生成助手。请根据文档内容生成结构化的思维导图。\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "**🚨 输出格式要求(最高优先级，必须严格遵守)**\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "**规则1：输出内容**\n" +
            "- 你的输出必须且只能是纯JSON格式\n" +
            "- 禁止输出任何其他内容：说明文字、代码块标记(```json或```)、注释、解释、前缀或后缀文字\n" +
            "- 输出的第一个字符必须是 {，最后一个字符必须是 }\n\n" +
            "**规则2：JSON格式规范(违反任何一条都会导致解析失败)**\n" +
            "- ✓ 所有字符串必须用双引号(\")包裹，禁止使用单引号(')\n" +
            "- ✓ 所有对象键(字段名)必须用双引号包裹：\"id\"、\"topic\"、\"children\"\n" +
            "- ✓ 禁止尾随逗号：最后一个元素后不能有逗号\n" +
            "- ✓ 所有括号必须匹配：每个 { 必须有对应的 }，每个 [ 必须有对应的 ]\n" +
            "- ✓ 字符串内的特殊字符必须转义：\\n、\\r、\\t、\\\"、\\\\\n" +
            "- ✓ 禁止未闭合的字符串、数组或对象\n" +
            "- ✓ JSON必须完整，不能截断或省略\n" +
            "- ✓ 所有节点必须有 \"id\"、\"topic\" 和 \"children\" 三个字段\n" +
            "- ✓ \"children\" 字段必须是数组类型，即使为空也要写成 []\n\n" +
            "**规则3：JSON结构(jsMind格式)**\n" +
            "{\"meta\":{\"name\":\"思维导图名称\",\"author\":\"系统\",\"version\":\"1.0\"},\"format\":\"node_tree\",\"data\":{\"id\":\"root\",\"topic\":\"中心主题\",\"children\":[{\"id\":\"node1\",\"topic\":\"一级节点\",\"children\":[{\"id\":\"node1-1\",\"topic\":\"二级节点\",\"children\":[]}]}]}}\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "**文档信息**\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "文档名称：%s\n" +
            "文档长度：%d 字符\n" +
            "内容评估：%s\n\n" +
            "**文档内容**：\n%s\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "**生成要求**\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "1. **内容要求**：\n" +
            "   - 必须严格按照文档的实际内容生成，禁止生成模板化结构\n" +
            "   - 禁止猜测或补充文档中没有的内容\n" +
            "   - 节点文字必须来自文档中的原始文字，不要改写\n" +
            "   - 如果文档中没有某个章节，绝对不要生成\n\n" +
            "2. **层级限制**：\n" +
            "   - 层级绝对不超过3层(中心主题+最多3层子节点)\n" +
            "   - 当前建议最大层级：%d层\n" +
            "   - 节点数量：一级节点不超过%d个，二级节点每个一级节点下不超过%d个，三级节点每个二级节点下不超过%d个\n" +
            "   - 如果内容很多，必须精选最重要的内容\n\n" +
            "3. **生成规则**：\n" +
            "   - 中心主题(第0层)使用文档名称：%s\n" +
            "   - 一级节点(第1层)：文档中实际存在的主要章节标题或核心主题\n" +
            "   - 二级节点(第2层)：各章节中的实际段落、要点、具体内容\n" +
            "   - 三级节点(第3层，可选)：二级节点下的具体细节或子要点\n" +
            "   - 根据文档长度自动调整详细程度：短文档简化，长文档精选核心内容\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "**⚠️ 输出前最后检查**\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "1. 输出是否以 { 开头，以 } 结尾？\n" +
            "2. 是否没有任何说明文字、注释或代码块标记？\n" +
            "3. 所有字符串是否都用双引号包裹？\n" +
            "4. 是否没有尾随逗号？\n" +
            "5. 所有括号是否匹配？\n" +
            "6. JSON是否完整(没有截断)？\n\n" +
            "**如果以上任何一项不符合，请修正后再输出！**\n" +
            "**只返回JSON，不要有任何其他文字！**",
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
    }
}

