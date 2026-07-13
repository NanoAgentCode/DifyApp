package com.github.app.dify.documentreader.service.impl;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.documentreader.domain.DocumentGuide;
import com.github.app.dify.documentreader.domain.DocumentMindMap;
import com.github.app.dify.documentreader.domain.DocumentReader;
import com.github.app.dify.documentreader.repository.DocumentGuideRepository;
import com.github.app.dify.documentreader.repository.DocumentMindMapRepository;
import com.github.app.dify.documentreader.repository.DocumentReaderRepository;
import com.github.app.dify.documentreader.util.DocumentReaderDateTimeUtil;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.service.DocumentParserService;
import com.github.app.dify.knowledgebase.service.FileStorageService;
import com.github.app.dify.model.service.ModelConfigService;
import com.github.app.dify.system.config.DocumentReaderConfig;
import com.github.app.dify.system.service.SystemConfigService;
import com.github.app.dify.system.util.SkillLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Duration;

@Service
class DocumentReaderGuideMindMapService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentReaderGuideMindMapService.class);
    private static final int MAX_TEXT_LENGTH_FOR_GUIDE = 8000;
    private static final int MAX_TEXT_LENGTH_FOR_MINDMAP = 12000;
    private static final int MAX_TEXT_LENGTH_FOR_MINDMAP_FINAL = 15000;
    private static final int WEB_CLIENT_TIMEOUT_SECONDS = 60;
    private static final int WEB_CLIENT_CONNECT_TIMEOUT_MS = 30000;

    @Autowired private DocumentReaderRepository documentRepository;
    @Autowired private DocumentGuideRepository guideRepository;
    @Autowired private DocumentMindMapRepository mindMapRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private ModelConfigService modelConfigService;
    @Autowired private ModelLanguageModelFactory modelLanguageModelFactory;
    @Autowired private DocumentParserService documentParserService;
    @Autowired private DocumentReaderConfig documentReaderConfig;
    @Autowired private SystemConfigService systemConfigService;
    @Autowired private DocumentReaderAccessService documentReaderAccessService;
    /**
     * 获取文档导读
     */
    public String getDocumentGuide(Long documentId, Long userId) {
        documentReaderAccessService.validateAccess(documentId, userId);
        return guideRepository.findByDocumentId(documentId)
                .map(DocumentGuide::getContent)
                .orElse("");
    }

    /**
     * 保存文档导读
     */
    @Transactional
    public void saveDocumentGuide(Long documentId, Long userId, String content) {
        documentReaderAccessService.validateAccess(documentId, userId);

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
    public String generateDocumentGuide(Long documentId, Long userId, Long modelId) {
        documentReaderAccessService.validateAccess(documentId, userId);

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
                    throw new NotFoundException("模型不存在");
                }
            } else {
                // 使用默认RAG模型
                qaModel = modelConfigService.getDefaultQAModelForRAG();
                if (qaModel == null) {
                    throw new BusinessException("未配置默认问答模型，请在系统配置中设置documentReader.defaultQAModelId",
                            ErrorCode.MODEL_NOT_FOUND);
                }
            }

            // 验证模型是否启用
            if (qaModel.getEnabled() == null || !qaModel.getEnabled()) {
                throw new BusinessException("模型未启用", ErrorCode.MODEL_NOT_FOUND);
            }

            // 读取文档内容（尝试提取文本）
            String documentText = extractDocumentText(document);

            // 判断文档长度，用于调整提示词
            int documentLength = documentText != null ? documentText.length() : 0;
            boolean isLongDocument = documentLength > 5000; // 超过5000字符视为长文档

            // 构建提示词
            String prompt = buildGuidePrompt(document.getOriginalFileName(), documentText, isLongDocument);

            // 使用通用的 LLM API 生成导读（支持 OpenAI、Ollama、vLLM 等）
            modelLanguageModelFactory.setTraceSource("Document Guide Generation");
            ChatLanguageModel chatLanguageModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);
            modelLanguageModelFactory.clearTraceSource();

            // 构建消息列表
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(UserMessage.from(prompt));

            // 调用 LLM API
            Response<AiMessage> aiResponse = chatLanguageModel.generate(messages);
            String guideContent = aiResponse.content().text();

            if (guideContent == null || guideContent.trim().isEmpty()) {
                throw new BusinessException("大模型生成导读失败：返回内容为空", ErrorCode.API_CALL_FAILED);
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
            throw new BusinessException("生成文档导读失败", ErrorCode.API_CALL_FAILED, e);
        }
    }

    /**
     * 提取文档文本内容（简化版本，仅支持文本文件）
     */
    String extractDocumentText(DocumentReader document) {
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

        return SkillLoader.loadSkillWithTemplate("document_reader/guide_prompt_template", Map.of(
                "expandInstruction", expandInstruction,
                "fileName", String.valueOf(fileName),
                "documentText", truncatedText));
    }

    /**
     * 构建思维导图markdown生成提示词
     */
    private String buildMindMapMarkdownPrompt(String fileName, String documentText) {
        String truncatedText = truncateText(documentText, MAX_TEXT_LENGTH_FOR_MINDMAP);

        return SkillLoader.loadSkillWithTemplate("document_reader/mindmap_prompt_template", Map.of(
                "fileName", String.valueOf(fileName),
                "documentText", truncatedText));
    }


    /**
     * 获取文档脑图
     */
    public String getDocumentMindMap(Long documentId, Long userId) {
        documentReaderAccessService.validateAccess(documentId, userId);
        return mindMapRepository.findByDocumentId(documentId)
                .map(DocumentMindMap::getMindMapData)
                .orElse(null);
    }

    /**
     * 保存文档脑图
     */
    @Transactional
    public void saveDocumentMindMap(Long documentId, Long userId, String mindMapData) {
        documentReaderAccessService.validateAccess(documentId, userId);

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
    public String generateDocumentMindMap(Long documentId, Long userId, Long modelId) {
        documentReaderAccessService.validateAccess(documentId, userId);

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
                throw new BusinessException("文档内容为空，无法生成思维导图", ErrorCode.BAD_REQUEST);
            }

            // 获取模型配置：优先使用参数，其次使用文档解读配置，最后使用默认RAG模型
            QAModel qaModel;
            Long effectiveModelId = modelId != null ? modelId : documentReaderConfig.getDefaultQAModelId();
            if (effectiveModelId != null) {
                qaModel = modelConfigService.getQAModelById(effectiveModelId);
                if (qaModel == null) {
                    throw new NotFoundException("模型不存在");
                }
            } else {
                // 使用默认RAG模型
                qaModel = modelConfigService.getDefaultQAModelForRAG();
                if (qaModel == null) {
                    throw new BusinessException("未配置默认问答模型，请在系统配置中设置documentReader.defaultQAModelId",
                            ErrorCode.MODEL_NOT_FOUND);
                }
            }

            // 验证模型是否启用
            if (qaModel.getEnabled() == null || !qaModel.getEnabled()) {
                throw new BusinessException("模型未启用", ErrorCode.MODEL_NOT_FOUND);
            }

            String truncatedText = truncateText(documentContent, MAX_TEXT_LENGTH_FOR_MINDMAP_FINAL);

            // 构建提示词，要求大模型生成适合思维导图的markdown格式
            String prompt = buildMindMapMarkdownPrompt(fileName, truncatedText);

            // 使用大模型生成markdown格式的思维导图内容
            logger.info("使用大模型生成思维导图markdown - 文档ID: {}, 模型ID: {}", documentId, effectiveModelId);
            modelLanguageModelFactory.setTraceSource("Document MindMap Generation");
            ChatLanguageModel chatLanguageModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);
            modelLanguageModelFactory.clearTraceSource();
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(UserMessage.from(prompt));

            Response<AiMessage> aiResponse = chatLanguageModel.generate(messages);
            String markdownContent = aiResponse.content().text();

            if (markdownContent == null || markdownContent.trim().isEmpty()) {
                throw new BusinessException("大模型生成思维导图markdown失败：返回内容为空", ErrorCode.API_CALL_FAILED);
            }

            markdownContent = markdownContent.trim();

            // 确保markdown格式正确（至少有一个标题）
            if (!markdownContent.trim().startsWith("#")) {
                markdownContent = "# " + fileName + "\n\n" + markdownContent;
            }

            HttpClient httpClient = createWebClientHttpClient();

            WebClient webClient = WebClient.builder()
                    .baseUrl(mindMapServiceUrl)
                    .clientConnector(
                            new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
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
                throw new BusinessException(
                        String.format("mindMap服务返回错误 (状态码: %s)", e.getStatusCode()),
                        ErrorCode.EXTERNAL_SERVICE_TIMEOUT, e);
            }

            if (htmlUrl == null || htmlUrl.trim().isEmpty()) {
                throw new BusinessException("mindMap服务返回空响应", ErrorCode.EXTERNAL_SERVICE_TIMEOUT);
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

        } catch (BusinessException e) {
            logger.error("生成文档脑图失败 - 文档ID: {}", documentId, e);
            throw e;
        } catch (Exception e) {
            logger.error("生成文档脑图失败 - 文档ID: {}", documentId, e);
            throw new BusinessException("生成文档脑图失败", ErrorCode.API_CALL_FAILED, e);
        }
    }


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

    /** 系统配置中思维导图服务 URL 的 key（与前端/管理端一致） */
    private static final String CONFIG_KEY_MIND_MAP_SERVICE_URL = "documentReader.mindMapServiceUrl";

    /**
     * 获取思维导图服务URL：优先从系统配置表（管理端设置的 documentReader.mindMapServiceUrl）读取，
     * 其次从 application 配置 document-reader.mind-map-service-url 读取，未配置则使用默认值。
     */
    private String getMindMapServiceUrl() {
        String mindMapServiceUrl = systemConfigService.getConfigValue(CONFIG_KEY_MIND_MAP_SERVICE_URL);
        if (mindMapServiceUrl == null || mindMapServiceUrl.trim().isEmpty()) {
            mindMapServiceUrl = documentReaderConfig.getMindMapServiceUrl();
        }
        if (mindMapServiceUrl == null || mindMapServiceUrl.trim().isEmpty()) {
            String defaultUrl = "http://localhost:6066";
            logger.warn("未配置思维导图服务URL，使用默认值: {}。可在系统配置页面设置 documentReader.mindMapServiceUrl 来修改", defaultUrl);
            return defaultUrl;
        }
        return mindMapServiceUrl.trim();
    }

}
