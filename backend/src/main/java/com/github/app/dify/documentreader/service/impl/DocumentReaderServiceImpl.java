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
import com.github.app.dify.model.service.ModelConfigService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.github.app.dify.documentreader.util.DocumentReaderConverterUtil;
import com.github.app.dify.documentreader.util.DocumentReaderDateTimeUtil;
import com.github.app.dify.documentreader.util.DocumentReaderPageUtil;
import com.github.app.dify.documentreader.util.DocumentReaderSoftDeleteUtil;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    
    // 常量定义
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final int MAX_GUIDE_WORDS = 1500; // 导读字数上限：800-1500字
    private static final int MAX_TEXT_LENGTH_FOR_GUIDE = 8000;
    private static final int MAX_TEXT_LENGTH_FOR_MINDMAP = 12000;
    private static final int MAX_TEXT_LENGTH_FOR_MINDMAP_FINAL = 15000;
    private static final int TRANSLATION_SEGMENT_SIZE = 5000;
    private static final int TRANSLATION_PAGE_LINES = 80; // 每页约80行（按页面翻译）
    private static final int LANGUAGE_DETECTION_SAMPLE_SIZE = 2000;
    private static final double LANGUAGE_DETECTION_THRESHOLD = 0.3;
    private static final int WEB_CLIENT_TIMEOUT_SECONDS = 60;
    private static final int WEB_CLIENT_CONNECT_TIMEOUT_MS = 30000;
    
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
        
        validateFile(file);
        String filePath = generateFilePath(userId, file.getOriginalFilename());
        String fileUrl = uploadFileToStorage(file, filePath);
        DocumentReader document = createDocumentEntity(file, userId, filePath, fileUrl);
        document = documentRepository.save(document);
        logger.info("文档上传成功 - 文档ID: {}, 文件名: {}", document.getId(), document.getOriginalFileName());
        
        triggerVectorizationAsync(document.getId(), file);
        
        return DocumentReaderConverterUtil.convertToResp(document);
    }
    
    /**
     * 上传文件到存储
     */
    private String uploadFileToStorage(MultipartFile file, String filePath) {
        try {
            return fileStorageService.uploadFile(file, filePath);
        } catch (Exception e) {
            logger.error("文件上传到MinIO失败: {}", filePath, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建文档实体
     */
    private DocumentReader createDocumentEntity(MultipartFile file, Long userId, String filePath, String fileUrl) {
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
        DocumentReaderDateTimeUtil.setCreateAndUpdateTime(document);
        document.setDeleted(0);
        document.setTotalPages(1);
        document.setVectorizedStatus(0);
        return document;
    }
    
    /**
     * 异步触发向量化
     */
    private void triggerVectorizationAsync(Long documentId, MultipartFile file) {
        try {
            documentReaderVectorizationService.vectorizeDocumentAsync(documentId, file);
            logger.info("已提交文档向量化任务 - 文档ID: {}", documentId);
        } catch (Exception e) {
            logger.error("提交文档向量化任务失败 - 文档ID: {}", documentId, e);
            // 不抛出异常，避免影响文档上传
        }
    }
    
    /**
     * 删除文档
     */
    @Transactional
    @Override
    public void deleteDocument(Long documentId, Long userId) {
        DocumentReader document = getDocumentByIdAndValidateAccess(documentId, userId);
        
        deleteFileFromStorage(document.getFilePath());
        deleteRelatedData(documentId);
        deleteDocumentVectors(documentId);
        softDeleteDocument(document);
        
        logger.info("文档删除成功 - 文档ID: {}", documentId);
    }
    
    /**
     * 获取文档并验证访问权限
     */
    private DocumentReader getDocumentByIdAndValidateAccess(Long documentId, Long userId) {
        Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
        if (!optional.isPresent()) {
            throw new NotFoundException("文档不存在: " + documentId);
        }
        
        DocumentReader document = optional.get();
        if (!document.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问此文档");
        }
        
        return document;
    }
    
    /**
     * 从存储删除文件
     */
    private void deleteFileFromStorage(String filePath) {
        try {
            fileStorageService.deleteFile(filePath);
        } catch (Exception e) {
            logger.error("从MinIO删除文件失败: {}", filePath, e);
        }
    }
    
    /**
     * 删除相关数据
     */
    private void deleteRelatedData(Long documentId) {
        guideRepository.findByDocumentId(documentId).ifPresent(guideRepository::delete);
        translationRepository.findByDocumentId(documentId).forEach(translationRepository::delete);
        mindMapRepository.findByDocumentId(documentId).ifPresent(mindMapRepository::delete);
        notesRepository.findByDocumentId(documentId).ifPresent(notesRepository::delete);
    }
    
    /**
     * 删除文档向量
     */
    private void deleteDocumentVectors(Long documentId) {
        try {
            documentReaderVectorizationService.deleteDocumentVectors(documentId);
        } catch (Exception e) {
            logger.error("删除文档向量失败 - 文档ID: {}", documentId, e);
            // 不抛出异常，避免影响删除流程
        }
    }
    
    /**
     * 软删除文档
     */
    private void softDeleteDocument(DocumentReader document) {
        DocumentReaderSoftDeleteUtil.softDelete(document, documentRepository);
    }
    
    /**
     * 根据ID获取文档
     */
    @Override
    public DocumentReaderResp getDocumentById(Long documentId, Long userId) {
        DocumentReader document = getDocumentByIdAndValidateAccess(documentId, userId);
        return DocumentReaderConverterUtil.convertToResp(document);
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
        Pageable pageable = DocumentReaderPageUtil.createPageable(page, pageSize);
        
        Page<DocumentReader> documentPage = documentRepository.findByUserIdAndDeletedAndKeywordAndFileType(
                userId,
                0,
                keyword != null ? keyword.trim() : null,
                fileType != null && !fileType.isEmpty() ? fileType : null,
                pageable);
        
        return DocumentReaderPageUtil.toPageResponse(documentPage, DocumentReaderConverterUtil::convertToResp);
    }
    
    /**
     * 获取文档内容
     */
    @Override
    public InputStream getDocumentContent(Long documentId, Long userId, Integer page) {
        DocumentReader document = getDocumentByIdAndValidateAccess(documentId, userId);
        
        try {
            return fileStorageService.downloadFile(document.getFilePath());
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
            DocumentReaderDateTimeUtil.setUpdateTime(guide);
        } else {
            guide = new DocumentGuide();
            guide.setDocumentId(documentId);
            guide.setContent(content);
            DocumentReaderDateTimeUtil.setCreateAndUpdateTime(guide);
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
            
            // 判断文档长度，用于调整提示词
            int documentLength = documentText != null ? documentText.length() : 0;
            boolean isLongDocument = documentLength > 5000; // 超过5000字符视为长文档
            
            // 构建提示词
            String prompt = buildGuidePrompt(document.getOriginalFileName(), documentText, isLongDocument);
            
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
            
            // 不再强制截断，由大模型根据提示词要求自行控制字数（800-1500字）
            // guideContent = truncateToMaxWordsSafely(guideContent, MAX_GUIDE_WORDS);
            
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
    private String buildGuidePrompt(String fileName, String documentText, boolean isLongDocument) {
        String truncatedText = truncateText(documentText, MAX_TEXT_LENGTH_FOR_GUIDE);
        
        // 根据文档长度调整展开程度
        String expandInstruction = isLongDocument 
            ? "文档内容较多，请高度概括，不要展开详细内容，只列出主要标题和核心要点"
            : "文档内容较少，可以适当展开，但保持简洁，避免冗余";
        
        return String.format(
            "请为以下文档生成一份高度概括的导读。要求：\n" +
            "1. **字数限制**：控制在800-1500字之间（中文字符计数），但必须保证导读部分完整\n" +
            "2. **内容要求**：高度概括，避免冗余，只包含核心信息\n" +
            "3. **导读应包括以下完整部分**（每个部分都要有，但内容要简洁）：\n" +
            "   - 文档的核心主题和主要内容概述（1-2句话）\n" +
            "   - 文档的关键要点和重要信息（3-5个要点，用列表形式）\n" +
            "   - 文档的主要结构或章节（简要说明主要标题和内容）\n" +
            "   - 适合的读者群体（1句话）\n" +
            "4. **展开程度**：%s\n" +
            "5. **内容范围**：\n" +
            "   - 主要关注文档的标题结构和主要内容\n" +
            "   - **不包含**：数学公式、代码示例、详细的技术细节\n" +
            "   - **只包含**：文档的主要标题、章节结构、核心观点和关键信息\n" +
            "6. **语言要求**：使用简洁明了的语言，直接说明要点，不要展开详细描述\n" +
            "7. **格式要求**：\n" +
            "   - 使用标准的Markdown标题格式（#、##、###），确保层次清晰\n" +
            "   - 一级标题（#）用于主要部分，二级标题（##）用于子部分\n" +
            "   - 使用列表（-、*）来组织要点，支持嵌套列表\n" +
            "   - 重要内容使用**加粗**或*斜体*来强调\n" +
            "   - 可以使用引用（>）来突出关键信息\n" +
            "   - 保持整体格式统一，层次分明\n" +
            "   - 段落之间使用空行分隔，提高可读性\n" +
            "8. **Markdown渲染优化要求**：\n" +
            "   - 确保所有Markdown语法正确，避免渲染错误\n" +
            "   - 列表项前使用统一的符号（- 或 *），保持一致性\n" +
            "   - 标题前后留空行，确保正确渲染\n" +
            "   - 避免使用特殊字符，确保兼容性\n" +
            "9. **完整性要求**：\n" +
            "   - 确保导读的所有部分都完整（核心主题、关键要点、主要结构、读者群体）\n" +
            "   - 如果字数接近限制，优先保证结构完整，每个部分都要有\n" +
            "   - 在保证完整性的前提下，控制总字数在800-1500字之间\n\n" +
            "文档名称：%s\n\n" +
            "文档内容：\n%s\n\n" +
            "请生成一份高度概括、结构完整的导读，字数控制在800-1500字之间，使用标准Markdown格式确保良好的渲染效果。",
            expandInstruction,
            fileName,
            truncatedText
        );
    }
    
    /**
     * 构建思维导图markdown生成提示词
     */
    private String buildMindMapMarkdownPrompt(String fileName, String documentText) {
        String truncatedText = truncateText(documentText, MAX_TEXT_LENGTH_FOR_MINDMAP);
        
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
            "6. 重点突出文档的核心概念和关键信息\n" +
            "7. **重要：如果文档内容很多，请精选最重要的内容，优先保留主要章节和核心概念，次要细节可以省略**\n" +
            "8. **重要：控制思维导图的规模，建议总节点数不超过100个，确保思维导图清晰可读**\n\n" +
            "**输出要求**：\n" +
            "1. 只输出Markdown格式的文本，不要添加任何说明、注释或解释\n" +
            "2. 确保Markdown格式正确，标题层级清晰\n" +
            "3. 第一行必须是 # 开头的标题\n" +
            "4. **重要：生成的Markdown内容总长度应控制在30000字符以内，如果内容过多，请精简次要内容**\n" +
            "5. 优先保留文档的主要结构和核心信息，细节内容可以省略或简化\n\n" +
            "文档名称：%s\n\n" +
            "文档内容：\n%s\n\n" +
            "请生成Markdown格式的思维导图（注意控制规模和长度，保持简洁清晰）：",
            fileName,
            truncatedText
        );
    }
    
    /**
     * 安全截断文本到指定字数（中文字符计数），确保在完整部分之后截断
     * @param text 原始文本
     * @param maxWords 最大字数
     * @return 截断后的文本
     */
    private String truncateToMaxWordsSafely(String text, int maxWords) {
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
        
        // 如果内容未超过限制，直接返回
        if (lastValidIndex >= text.length()) {
            return text;
        }
        
        // 尝试在完整部分之后截断（优先在标题、段落、列表项之后）
        int bestCutPoint = lastValidIndex;
        
        // 1. 优先在标题之后截断（查找最后一个 # 开头的行）
        int lastHeadingEnd = -1;
        for (int i = lastValidIndex - 1; i >= Math.max(0, lastValidIndex - 200); i--) {
            if (text.charAt(i) == '\n') {
                // 检查是否是标题行
                int lineStart = i + 1;
                if (lineStart < text.length() && text.charAt(lineStart) == '#') {
                    // 找到标题行，查找标题结束位置（下一个换行或文本结束）
                    for (int j = lineStart + 1; j < text.length() && j < lastValidIndex + 100; j++) {
                        if (text.charAt(j) == '\n') {
                            lastHeadingEnd = j;
                            break;
                        }
                    }
                    if (lastHeadingEnd == -1 && lineStart + 50 < text.length()) {
                        lastHeadingEnd = Math.min(lineStart + 50, text.length());
                    }
                    break;
                }
            }
        }
        
        // 2. 其次在列表项之后截断
        if (lastHeadingEnd == -1) {
            for (int i = lastValidIndex - 1; i >= Math.max(0, lastValidIndex - 100); i--) {
                if (text.charAt(i) == '\n' && i + 1 < text.length()) {
                    char nextChar = text.charAt(i + 1);
                    if (nextChar == '-' || nextChar == '*' || nextChar == '+' || 
                        (nextChar >= '0' && nextChar <= '9')) {
                        // 找到列表项结束，查找下一个换行
                        for (int j = i + 1; j < text.length() && j < lastValidIndex + 50; j++) {
                            if (text.charAt(j) == '\n') {
                                lastHeadingEnd = j;
                                break;
                            }
                        }
                        if (lastHeadingEnd == -1) {
                            lastHeadingEnd = Math.min(i + 100, text.length());
                        }
                        break;
                    }
                }
            }
        }
        
        // 3. 再次在段落结束（句号、问号、感叹号）之后截断
        if (lastHeadingEnd == -1) {
            for (int i = lastValidIndex - 1; i >= Math.max(0, lastValidIndex - 50); i--) {
                char c = text.charAt(i);
                if (c == '。' || c == '！' || c == '？' || 
                    c == '.' || c == '!' || c == '?') {
                    lastHeadingEnd = i + 1;
                    break;
                }
            }
        }
        
        // 4. 如果都没找到，在换行处截断
        if (lastHeadingEnd == -1) {
            for (int i = lastValidIndex - 1; i >= Math.max(0, lastValidIndex - 20); i--) {
                if (text.charAt(i) == '\n') {
                    lastHeadingEnd = i + 1;
                    break;
                }
            }
        }
        
        // 使用最佳截断点
        if (lastHeadingEnd > 0 && lastHeadingEnd <= lastValidIndex + 50) {
            bestCutPoint = lastHeadingEnd;
        } else {
            bestCutPoint = lastValidIndex;
        }
        
        return text.substring(0, bestCutPoint).trim() + "\n\n---\n\n> **提示**：导读内容已截断至1500字以内，如需查看完整内容，请阅读原文档。";
    }
    
    /**
     * 翻译文档（懒加载模式：只翻译第一段）
     */
    @Override
    public void translateDocument(Long documentId, Long userId, String targetLang, boolean forceRetranslate) {
        validateDocumentAccess(documentId, userId);
        
        logger.info("开始翻译文档（懒加载模式） - 文档ID: {}, 目标语言: {}, 强制重新翻译: {}", documentId, targetLang, forceRetranslate);
        
        try {
            // 如果强制重新翻译，先删除旧的翻译记录
            if (forceRetranslate) {
                Optional<DocumentTranslation> existingTranslation = translationRepository.findByDocumentIdAndTargetLanguage(documentId, targetLang);
                if (existingTranslation.isPresent()) {
                    translationRepository.delete(existingTranslation.get());
                    logger.info("已删除旧的翻译记录 - 文档ID: {}, 目标语言: {}", documentId, targetLang);
                }
            }
            
            // 检查是否已有翻译内容
            List<DocumentSegment> existingSegments = loadDocumentTranslationSegments(documentId, targetLang);
            if (existingSegments != null && !existingSegments.isEmpty()) {
                // 检查是否所有段落都已翻译
                boolean allTranslated = existingSegments.stream()
                    .allMatch(seg -> seg.getTranslatedText() != null && !seg.getTranslatedText().trim().isEmpty());
                
                if (allTranslated && !forceRetranslate) {
                    logger.info("文档已完全翻译，无需重新翻译 - 文档ID: {}, 目标语言: {}", documentId, targetLang);
                    return; // 已完全翻译，直接返回
                } else {
                    logger.info("文档部分已翻译，继续翻译未完成部分 - 文档ID: {}, 目标语言: {}", documentId, targetLang);
                    // 继续翻译未完成的段落（由前端懒加载触发）
                    return;
                }
            }
            
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
            
            // 去除页眉页脚
            documentContent = removeHeaderFooter(documentContent);
            
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
     * 翻译指定分段（懒加载）- 优化版本
     */
    @Override
    public String translateDocumentSegment(Long documentId, Long userId, String targetLang, int segmentIndex) {
        validateDocumentAccess(documentId, userId);
        
        logger.info("开始翻译指定分段 - 文档ID: {}, 目标语言: {}, 段索引: {}", documentId, targetLang, segmentIndex);
        
        try {
            // 先尝试加载已有分段信息（避免重复提取文档内容）
            List<DocumentSegment> segments = loadDocumentTranslationSegments(documentId, targetLang);
            
            // 如果分段信息不存在或为空，需要创建（此时才提取文档内容）
            if (segments == null || segments.isEmpty()) {
                logger.info("分段信息不存在，开始创建分段 - 文档ID: {}, 目标语言: {}", documentId, targetLang);
                
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
                
                // 去除页眉页脚
                documentContent = removeHeaderFooter(documentContent);
                
                // 检查是否为同种语言翻译（禁止同种语言翻译）
                if (isSameLanguageTranslation(documentContent, targetLang)) {
                    String detectedLang = detectDocumentLanguage(documentContent);
                    throw new IllegalArgumentException(
                        String.format("不能将%s文档翻译为%s，翻译功能仅支持不同语言之间的翻译", 
                                     detectedLang, targetLang));
                }
                
                // 创建分段信息
                segments = splitDocumentForTranslation(documentContent);
                saveDocumentTranslationSegments(documentId, userId, targetLang, segments);
                logger.info("分段信息创建完成 - 文档ID: {}, 总段数: {}", documentId, segments.size());
            }
            
            // 按索引排序，确保段落顺序一致
            segments.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
            
            // 检查索引有效性
            if (segmentIndex < 0 || segmentIndex >= segments.size()) {
                logger.warn("分段索引无效 - 文档ID: {}, 目标语言: {}, 请求索引: {}, 总段数: {}", 
                           documentId, targetLang, segmentIndex, segments.size());
                throw new IllegalArgumentException("分段索引无效: " + segmentIndex + ", 总段数: " + segments.size());
            }
            
            // 使用索引直接访问分段（已排序，索引即数组位置）
            DocumentSegment segment = segments.get(segmentIndex);
            
            // 验证分段索引是否匹配（双重检查）
            if (segment.getIndex() != segmentIndex) {
                // 如果索引不匹配，使用线性查找
                segment = null;
                for (DocumentSegment seg : segments) {
                    if (seg.getIndex() == segmentIndex) {
                        segment = seg;
                        break;
                    }
                }
                if (segment == null) {
                    throw new IllegalArgumentException("找不到索引为 " + segmentIndex + " 的分段");
                }
            }
            
            // 检查是否已翻译
            if (segment.getTranslatedText() != null && !segment.getTranslatedText().trim().isEmpty()) {
                logger.debug("分段已翻译，直接返回 - 文档ID: {}, 段索引: {}", documentId, segmentIndex);
                return segment.getTranslatedText();
            }
            
            // 获取模型配置（延迟到真正需要翻译时才获取）
            QAModel qaModel = modelConfigService.getDefaultQAModelForRAG();
            if (qaModel == null) {
                throw new RuntimeException("未配置可用的模型，无法进行翻译");
            }
            
            // 翻译该分段
            String translated = translateTextSegment(segment.getText(), targetLang, qaModel);
            segment.setTranslatedText(translated);
            
            // 保存更新后的分段信息（异步保存，提高响应速度）
            saveDocumentTranslationSegments(documentId, userId, targetLang, segments);
            
            logger.info("分段翻译完成 - 文档ID: {}, 段索引: {}, 原文长度: {}, 译文长度: {}", 
                documentId, segmentIndex, segment.getText().length(), translated.length());
            
            return translated;
            
        } catch (IllegalArgumentException e) {
            // 参数错误，直接抛出
            throw e;
        } catch (NotFoundException e) {
            // 资源不存在，直接抛出
            throw e;
        } catch (Exception e) {
            logger.error("翻译分段失败 - 文档ID: {}, 段索引: {}, 错误: {}", 
                        documentId, segmentIndex, e.getMessage(), e);
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
            DocumentReaderDateTimeUtil.setUpdateTime(translation);
        } else {
            translation = new DocumentTranslation();
            translation.setDocumentId(documentId);
            translation.setTargetLanguage(targetLang);
            translation.setContent(content);
            DocumentReaderDateTimeUtil.setCreateAndUpdateTime(translation);
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
            DocumentReaderDateTimeUtil.setUpdateTime(mindMap);
        } else {
            mindMap = new DocumentMindMap();
            mindMap.setDocumentId(documentId);
            mindMap.setMindMapData(mindMapData);
            DocumentReaderDateTimeUtil.setCreateAndUpdateTime(mindMap);
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
            // 获取mindMap服务URL（从系统配置读取，可在系统配置页面中配置）
            String mindMapServiceUrl = getMindMapServiceUrl();
            
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
            
            String truncatedText = truncateText(documentContent, MAX_TEXT_LENGTH_FOR_MINDMAP_FINAL);
            
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
            
            HttpClient httpClient = createWebClientHttpClient();
            
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
                        .timeout(Duration.ofSeconds(WEB_CLIENT_TIMEOUT_SECONDS))
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
            
            // 保存生成的脑图（即使保存失败也不影响返回结果，因为思维导图已经生成）
            try {
                saveDocumentMindMap(documentId, userId, mindMapJson);
                logger.info("文档脑图生成并保存成功 - 文档ID: {}, 模型ID: {}, HTML URL: {}", documentId, effectiveModelId, cleanUrl);
            } catch (Exception saveException) {
                // 保存失败只记录警告，不影响返回结果
                logger.warn("文档脑图生成成功但保存失败 - 文档ID: {}, 模型ID: {}, HTML URL: {}, 错误: {}", 
                        documentId, effectiveModelId, cleanUrl, saveException.getMessage(), saveException);
            }
            
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
            DocumentReaderDateTimeUtil.setUpdateTime(notes);
        } else {
            notes = new DocumentNotes();
            notes.setDocumentId(documentId);
            notes.setContent(content);
            DocumentReaderDateTimeUtil.setCreateAndUpdateTime(notes);
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
        
        if (file.getSize() > MAX_FILE_SIZE) {
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
        String datePath = sdf.format(DocumentReaderDateTimeUtil.now());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileExtension = getFileExtension(originalFileName);
        return String.format("document-reader/%d/%s/%s.%s", userId, datePath, uuid, fileExtension);
    }
    
    private void validateDocumentAccess(Long documentId, Long userId) {
        getDocumentByIdAndValidateAccess(documentId, userId);
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
        
        String sampleText = text.length() > LANGUAGE_DETECTION_SAMPLE_SIZE 
                ? text.substring(0, LANGUAGE_DETECTION_SAMPLE_SIZE) : text;
        
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
        
        if (chineseRatio >= LANGUAGE_DETECTION_THRESHOLD) {
            return "zh";
        } else if (japaneseRatio >= LANGUAGE_DETECTION_THRESHOLD) {
            return "ja";
        } else if (koreanRatio >= LANGUAGE_DETECTION_THRESHOLD) {
            return "ko";
        } else if (englishRatio >= LANGUAGE_DETECTION_THRESHOLD) {
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
     * 去除文档的页眉页脚
     * 页眉页脚通常出现在文档的开头和结尾，包含页码、标题、日期等信息
     */
    private String removeHeaderFooter(String documentContent) {
        if (documentContent == null || documentContent.trim().isEmpty()) {
            return documentContent;
        }
        
        String[] lines = documentContent.split("\n");
        if (lines.length <= 3) {
            // 内容太短，不处理
            return documentContent;
        }
        
        int startIndex = 0;
        int endIndex = lines.length;
        
        // 识别并去除页眉（通常在前几行）
        // 页眉特征：短行、包含页码、日期、标题等
        int headerLines = 0;
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            
            // 检查是否是页眉特征
            boolean isHeader = false;
            
            // 1. 短行（通常页眉较短）
            if (line.length() < 50) {
                // 2. 包含页码模式
                if (line.matches(".*[第]?[\\d]+[页]?.*") || 
                    line.matches(".*[Pp]age\\s*[\\d]+.*") ||
                    line.matches(".*[\\d]+\\s*[页]?.*")) {
                    isHeader = true;
                }
                // 3. 包含日期模式
                else if (line.matches(".*\\d{4}[年\\-/]\\d{1,2}[月\\-/]\\d{1,2}[日]?.*") ||
                         line.matches(".*\\d{1,2}/\\d{1,2}/\\d{4}.*") ||
                         line.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
                    isHeader = true;
                }
                // 4. 只包含数字、标点或少量文字
                else if (line.matches("^[\\d\\s\\p{Punct}]+$") && line.length() < 30) {
                    isHeader = true;
                }
                // 5. 重复出现的短行（可能是页眉）
                else if (i > 0 && line.equals(lines[i-1].trim()) && line.length() < 40) {
                    isHeader = true;
                }
            }
            
            if (isHeader) {
                headerLines = i + 1;
            } else {
                // 遇到非页眉内容，停止检查
                break;
            }
        }
        
        // 识别并去除页脚（通常在最后几行）
        int footerLines = 0;
        for (int i = lines.length - 1; i >= Math.max(lines.length - 5, headerLines); i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            
            // 检查是否是页脚特征
            boolean isFooter = false;
            
            // 1. 短行
            if (line.length() < 50) {
                // 2. 包含页码模式
                if (line.matches(".*[第]?[\\d]+[页]?.*") || 
                    line.matches(".*[Pp]age\\s*[\\d]+.*") ||
                    line.matches(".*[\\d]+\\s*[页]?.*")) {
                    isFooter = true;
                }
                // 3. 包含日期模式
                else if (line.matches(".*\\d{4}[年\\-/]\\d{1,2}[月\\-/]\\d{1,2}[日]?.*") ||
                         line.matches(".*\\d{1,2}/\\d{1,2}/\\d{4}.*") ||
                         line.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
                    isFooter = true;
                }
                // 4. 只包含数字、标点或少量文字
                else if (line.matches("^[\\d\\s\\p{Punct}]+$") && line.length() < 30) {
                    isFooter = true;
                }
                // 5. 重复出现的短行（可能是页脚）
                else if (i < lines.length - 1 && line.equals(lines[i+1].trim()) && line.length() < 40) {
                    isFooter = true;
                }
            }
            
            if (isFooter) {
                footerLines = lines.length - i;
            } else {
                // 遇到非页脚内容，停止检查
                break;
            }
        }
        
        // 提取去除页眉页脚后的内容
        startIndex = headerLines;
        endIndex = lines.length - footerLines;
        
        // 确保至少保留一些内容
        if (endIndex <= startIndex) {
            // 如果去除后没有内容，保留原内容
            return documentContent;
        }
        
        // 重新组合内容
        StringBuilder result = new StringBuilder();
        for (int i = startIndex; i < endIndex; i++) {
            result.append(lines[i]);
            if (i < endIndex - 1) {
                result.append("\n");
            }
        }
        
        String cleanedContent = result.toString().trim();
        
        // 如果去除后内容太少，保留原内容
        if (cleanedContent.length() < documentContent.length() * 0.3) {
            logger.warn("去除页眉页脚后内容过少，保留原内容 - 原长度: {}, 处理后长度: {}", 
                       documentContent.length(), cleanedContent.length());
            return documentContent;
        }
        
        logger.debug("去除页眉页脚 - 原行数: {}, 去除页眉: {}行, 去除页脚: {}行, 处理后行数: {}", 
                    lines.length, headerLines, footerLines, endIndex - startIndex);
        
        return cleanedContent;
    }
    
    /**
     * 将文档按页面分段（用于按页面翻译）- 优化版本
     * 考虑字符数限制，避免分段过大导致翻译超时
     */
    private List<DocumentSegment> splitDocumentForTranslation(String documentContent) {
        List<DocumentSegment> segments = new ArrayList<>();
        
        if (documentContent == null || documentContent.trim().isEmpty()) {
            logger.warn("文档内容为空，无法分段");
            return segments;
        }
        
        // 按行分割文档
        String[] lines = documentContent.split("\n", -1);
        int totalLines = lines.length;
        int linesPerPage = TRANSLATION_PAGE_LINES;
        int maxCharsPerSegment = TRANSLATION_SEGMENT_SIZE; // 最大字符数限制
        int segmentIndex = 0;
        int lineStart = 0;
        
        while (lineStart < totalLines) {
            int lineEnd = Math.min(lineStart + linesPerPage, totalLines);
            
            // 计算字符位置
            int charStart = 0;
            for (int i = 0; i < lineStart; i++) {
                charStart += lines[i].length() + 1; // +1 for newline
            }
            
            // 构建页面内容（用于检查字符数）
            StringBuilder pageTextBuilder = new StringBuilder();
            int currentChars = 0;
            int actualLineEnd = lineStart;
            
            for (int i = lineStart; i < lineEnd; i++) {
                String line = lines[i];
                int lineLength = line.length() + (i > lineStart ? 1 : 0); // +1 for newline except first line
                
                // 如果添加这一行会超过字符限制，且不是第一行，则停止
                if (currentChars + lineLength > maxCharsPerSegment && i > lineStart) {
                    break;
                }
                
                if (i > lineStart) {
                    pageTextBuilder.append("\n");
                }
                pageTextBuilder.append(line);
                currentChars += lineLength;
                actualLineEnd = i + 1;
            }
            
            // 如果实际结束行小于预期，尝试在段落边界处优化截断点
            if (actualLineEnd < totalLines && actualLineEnd < lineEnd) {
                // 向前查找更好的截断点（在空行或句号处）
                int bestLineEnd = actualLineEnd;
                int searchStart = Math.max(lineStart + (actualLineEnd - lineStart) / 2, lineStart + 1);
                for (int i = actualLineEnd - 1; i >= searchStart; i--) {
                    String line = lines[i].trim();
                    // 如果是空行，或者以句号、问号、感叹号结尾
                    if (line.isEmpty() || 
                        line.endsWith("。") || line.endsWith(".") || 
                        line.endsWith("？") || line.endsWith("?") ||
                        line.endsWith("！") || line.endsWith("!")) {
                        bestLineEnd = i + 1;
                        break;
                    }
                }
                actualLineEnd = bestLineEnd;
                
                // 重新构建页面内容
                pageTextBuilder = new StringBuilder();
                for (int i = lineStart; i < actualLineEnd; i++) {
                    if (i > lineStart) {
                        pageTextBuilder.append("\n");
                    }
                    pageTextBuilder.append(lines[i]);
                }
            }
            
            // 计算结束位置
            int charEnd = charStart;
            for (int i = lineStart; i < actualLineEnd; i++) {
                charEnd += lines[i].length() + (i < actualLineEnd - 1 ? 1 : 0); // +1 for newline except last line
            }
            
            String segmentText = pageTextBuilder.toString();
            DocumentSegment segment = new DocumentSegment();
            segment.setIndex(segmentIndex);
            segment.setStartIndex(charStart);
            segment.setEndIndex(charEnd);
            segment.setText(segmentText);
            segment.setTranslatedText(null); // 初始未翻译
            
            segments.add(segment);
            segmentIndex++;
            lineStart = actualLineEnd; // 移动到下一页
        }
        
        logger.info("文档按页面分段完成 - 总行数: {}, 每页行数: {}, 最大字符数: {}, 总页数: {}, 平均每段字符数: {}", 
                   totalLines, linesPerPage, maxCharsPerSegment, segments.size(),
                   segments.isEmpty() ? 0 : documentContent.length() / segments.size());
        
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
        DocumentReader document = getDocumentByIdAndValidateAccess(documentId, userId);
        return extractDocumentText(document);
    }
    
    /**
     * 截断文本到指定长度
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n\n[文档内容已截断...]";
    }
    
    /**
     * 创建WebClient的HttpClient
     */
    private HttpClient createWebClientHttpClient() {
        return HttpClient.create()
                .responseTimeout(Duration.ofSeconds(WEB_CLIENT_TIMEOUT_SECONDS))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, WEB_CLIENT_CONNECT_TIMEOUT_MS);
    }
    
    /**
     * 获取思维导图服务URL（从配置读取，如果未配置则使用默认值）
     */
    private String getMindMapServiceUrl() {
        String mindMapServiceUrl = documentReaderConfig.getMindMapServiceUrl();
        if (mindMapServiceUrl == null || mindMapServiceUrl.trim().isEmpty()) {
            String defaultUrl = "http://localhost:6066";
            logger.warn("未配置思维导图服务URL，使用默认值: {}。可在系统配置页面设置 documentReader.mindMapServiceUrl 来修改", defaultUrl);
            return defaultUrl;
        }
        return mindMapServiceUrl.trim();
    }
}

