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
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.app.dify.knowledgebase.service.DocumentParserService;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import java.time.Duration;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;

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
     * 构建思维导图markdown生成提示词
     */
    private String buildMindMapMarkdownPrompt(String fileName, String documentText) {
        // 限制文档文本长度，避免超过模型上下文限制
        int maxTextLength = 12000; // 保留一些空间给提示词和响应
        String truncatedText = documentText;
        if (documentText.length() > maxTextLength) {
            truncatedText = documentText.substring(0, maxTextLength) + "\n\n[文档内容已截断...]";
        }
        
        return String.format(
            "请根据以下文档内容，生成一份结构化的Markdown格式思维导图。要求：\n\n" +
            "**格式要求**：\n" +
            "1. 必须使用Markdown标题层级结构（#、##、###、####等）\n" +
            "2. 第一级标题（#）必须是文档名称或核心主题\n" +
            "3. 使用多级标题来组织内容层次，建议不超过4级（####）\n" +
            "4. 每个标题下可以包含简要的说明文字或子标题\n" +
            "5. 使用列表（- 或 *）来组织同级内容\n\n" +
            "**内容要求**：\n" +
            "1. 必须严格按照文档的实际内容生成，不要添加文档中没有的信息\n" +
            "2. 提取文档的核心主题、主要章节、关键要点\n" +
            "3. 保持内容的逻辑层次和结构关系\n" +
            "4. 如果文档有明确的章节结构，请保持该结构\n" +
            "5. 内容要简洁，每个节点文字不要过长（建议不超过20字）\n" +
            "6. 重点突出文档的核心概念和关键信息\n\n" +
            "**输出要求**：\n" +
            "1. 只输出Markdown格式的文本，不要添加任何说明、注释或解释\n" +
            "2. 确保Markdown格式正确，标题层级清晰\n" +
            "3. 第一行必须是 # 开头的标题\n" +
            "4. 如果文档内容很多，请精选最重要的内容，保持思维导图简洁清晰\n\n" +
            "文档名称：%s\n\n" +
            "文档内容：\n%s\n\n" +
            "请生成Markdown格式的思维导图：",
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
     * 生成文档脑图（使用大模型生成markdown，然后调用mindMap服务）
     */
    @Override
    public String generateDocumentMindMap(Long documentId, Long userId, Long modelId) {
        validateDocumentAccess(documentId, userId);
        
        try {
            // 获取mindMap服务URL（思维导图服务位于mindmap目录，默认端口6066）
            String mindMapServiceUrl = documentReaderConfig.getMindMapServiceUrl();
            if (mindMapServiceUrl == null || mindMapServiceUrl.trim().isEmpty()) {
                throw new RuntimeException("未配置思维导图服务URL，请在系统配置中设置 documentReader.mindMapServiceUrl（思维导图服务位于mindmap目录，默认地址：http://localhost:6066）");
            }
            
            // 获取文档信息
            Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
            if (!optional.isPresent()) {
                throw new NotFoundException("文档不存在: " + documentId);
            }
            
            DocumentReader document = optional.get();
            String fileName = document.getOriginalFileName();
            
            // 读取文档内容
            String documentContent = extractDocumentText(document);
            if (documentContent == null || documentContent.trim().isEmpty()) {
                throw new RuntimeException("文档内容为空，无法生成思维导图");
            }
            
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
            
            // 限制文档文本长度，避免超过模型上下文限制
            int maxTextLength = 15000;
            String truncatedText = documentContent;
            if (documentContent.length() > maxTextLength) {
                truncatedText = documentContent.substring(0, maxTextLength) + "\n\n[文档内容已截断...]";
            }
            
            // 构建提示词，要求大模型生成适合思维导图的markdown格式
            String prompt = buildMindMapMarkdownPrompt(fileName, truncatedText);
            
            // 使用大模型生成markdown格式的思维导图内容
            logger.info("使用大模型生成思维导图markdown - 文档ID: {}, 模型ID: {}", documentId, effectiveModelId);
            ChatLanguageModel chatLanguageModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(UserMessage.from(prompt));
            
            Response<AiMessage> aiResponse = chatLanguageModel.generate(messages);
            String markdownContent = aiResponse.content().text();
            
            if (markdownContent == null || markdownContent.trim().isEmpty()) {
                throw new RuntimeException("大模型生成思维导图markdown失败：返回内容为空");
            }
            
            markdownContent = markdownContent.trim();
            
            // 确保markdown格式正确（至少有一个标题）
            if (!markdownContent.trim().startsWith("#")) {
                markdownContent = "# " + fileName + "\n\n" + markdownContent;
            }
            
            // 创建WebClient调用mindMap服务
            HttpClient httpClient = HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(60))
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);
            
            WebClient webClient = WebClient.builder()
                    .baseUrl(mindMapServiceUrl)
                    .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .build();
            
            // 调用mindMap服务（使用/upload-local接口，支持本地资源）
            logger.info("调用mindMap服务生成思维导图 - 服务URL: {}, 文档ID: {}, 模型ID: {}, markdown长度: {}", 
                    mindMapServiceUrl, documentId, effectiveModelId, markdownContent.length());
            String htmlUrl;
            try {
                htmlUrl = webClient.post()
                        .uri("/upload-local")
                        .bodyValue(markdownContent)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(60))
                        .block();
            } catch (WebClientResponseException e) {
                String errorDetail = e.getResponseBodyAsString();
                logger.error("mindMap服务返回错误 - 状态码: {}, 响应体: {}, 文档ID: {}", 
                        e.getStatusCode(), errorDetail, documentId);
                throw new RuntimeException(
                        String.format("mindMap服务返回错误 (状态码: %s): %s", 
                                e.getStatusCode(), 
                                errorDetail != null && !errorDetail.isEmpty() ? errorDetail : e.getMessage()),
                        e);
            }
            
            if (htmlUrl == null || htmlUrl.trim().isEmpty()) {
                throw new RuntimeException("mindMap服务返回空响应");
            }
            
            // 清理URL：去除首尾空白和引号
            String cleanUrl = htmlUrl.trim();
            // 去除首尾的双引号或单引号
            if ((cleanUrl.startsWith("\"") && cleanUrl.endsWith("\"")) ||
                (cleanUrl.startsWith("'") && cleanUrl.endsWith("'"))) {
                cleanUrl = cleanUrl.substring(1, cleanUrl.length() - 1).trim();
            }
            
            // 将HTML URL包装为jsMind格式的JSON（前端可以解析并显示）
            // 格式：{"type": "html_url", "url": "http://..."}
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> mindMapData = new HashMap<>();
            mindMapData.put("type", "html_url");
            mindMapData.put("url", cleanUrl);
            mindMapData.put("fileName", fileName);
            mindMapData.put("meta", Map.of("name", fileName, "author", "系统", "version", "1.0"));
            
            String mindMapJson = objectMapper.writeValueAsString(mindMapData);
            
            // 保存生成的脑图
            saveDocumentMindMap(documentId, userId, mindMapJson);
            
            logger.info("文档脑图生成成功 - 文档ID: {}, 模型ID: {}, HTML URL: {}", documentId, effectiveModelId, cleanUrl);
            return mindMapJson;
            
        } catch (RuntimeException e) {
            logger.error("生成文档脑图失败 - 文档ID: {}", documentId, e);
            throw e;
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
            Map<String, Object> data = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
            
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

