package com.github.app.dify.documentreader.service.impl;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.documentreader.domain.*;
import com.github.app.dify.documentreader.repository.*;
import com.github.app.dify.documentreader.resp.DocumentReaderResp;
import com.github.app.dify.documentreader.service.DocumentReaderService;
import com.github.app.dify.documentreader.service.DocumentReaderVectorizationService;
import com.github.app.dify.knowledgebase.service.FileStorageService;
import com.github.app.dify.system.config.DocumentReaderConfig;
import com.github.app.dify.system.service.SystemConfigService;
import com.github.app.dify.system.util.SkillLoader;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.github.app.dify.documentreader.util.DocumentReaderConverterUtil;
import com.github.app.dify.documentreader.util.DocumentReaderDateTimeUtil;
import com.github.app.dify.common.util.DateTimeUtil;
import com.github.app.dify.common.util.PageUtil;
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

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private DocumentReaderAccessService documentReaderAccessService;

    @Autowired
    private DocumentReaderNotesService documentReaderNotesService;

    @Autowired
    private DocumentReaderUploadDeleteService uploadDeleteService;

    @Autowired
    private DocumentReaderGuideMindMapService guideMindMapService;

    @Autowired
    private DocumentReaderTranslationService translationService;

    @Autowired
    private DocumentReaderReadService readService;

    /**
     * 上传文档
     */
    @Transactional
    @Override
    public DocumentReaderResp uploadDocument(MultipartFile file, Long userId) {
        return uploadDeleteService.uploadDocument(file, userId);
    }

    /**
     * 删除文档
     */
    @Transactional
    @Override
    public void deleteDocument(Long documentId, Long userId) {
        uploadDeleteService.deleteDocument(documentId, userId);
    }

    /**
     * 根据ID获取文档
     */
    @Override
    public DocumentReaderResp getDocumentById(Long documentId, Long userId) {
        return readService.getDocumentById(documentId, userId);
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
        return readService.listDocuments(userId, keyword, fileType, page, pageSize);
    }

    /**
     * 获取文档内容
     */
    @Override
    public InputStream getDocumentContent(Long documentId, Long userId, Integer page) {
        return readService.getDocumentContent(documentId, userId, page);
    }

    @Override
    public String getDocumentGuide(Long documentId, Long userId) {
        return guideMindMapService.getDocumentGuide(documentId, userId);
    }

    @Override
    public void saveDocumentGuide(Long documentId, Long userId, String content) {
        guideMindMapService.saveDocumentGuide(documentId, userId, content);
    }

    @Override
    public String generateDocumentGuide(Long documentId, Long userId, Long modelId) {
        return guideMindMapService.generateDocumentGuide(documentId, userId, modelId);
    }

    @Override
    public void translateDocument(Long documentId, Long userId, String targetLang, boolean forceRetranslate) {
        translationService.translateDocument(documentId, userId, targetLang, forceRetranslate);
    }

    @Override
    public String getDocumentTranslation(Long documentId, Long userId, String targetLang) {
        return translationService.getDocumentTranslation(documentId, userId, targetLang);
    }

    @Override
    public String getDocumentTranslationRange(Long documentId, Long userId, String targetLang, int startSegment, int endSegment) {
        return translationService.getDocumentTranslationRange(documentId, userId, targetLang, startSegment, endSegment);
    }

    @Override
    public Map<String, Object> getDocumentSegments(Long documentId, Long userId) {
        return translationService.getDocumentSegments(documentId, userId);
    }

    @Override
    public String translateDocumentSegment(Long documentId, Long userId, String targetLang, int segmentIndex) {
        return translationService.translateDocumentSegment(documentId, userId, targetLang, segmentIndex);
    }

    @Override
    public void saveDocumentTranslation(Long documentId, Long userId, String targetLang, String content) {
        translationService.saveDocumentTranslation(documentId, userId, targetLang, content);
    }

    @Override
    public String getDocumentMindMap(Long documentId, Long userId) {
        return guideMindMapService.getDocumentMindMap(documentId, userId);
    }

    @Override
    public void saveDocumentMindMap(Long documentId, Long userId, String mindMapData) {
        guideMindMapService.saveDocumentMindMap(documentId, userId, mindMapData);
    }

    @Override
    public String generateDocumentMindMap(Long documentId, Long userId, Long modelId) {
        return guideMindMapService.generateDocumentMindMap(documentId, userId, modelId);
    }

    /**
     * 获取文档笔记
     */
    @Override
    public String getDocumentNotes(Long documentId, Long userId) {
        return documentReaderNotesService.getNotes(documentId, userId);
    }

    /**
     * 保存文档笔记
     */
    @Transactional
    @Override
    public void saveDocumentNotes(Long documentId, Long userId, String content) {
        documentReaderNotesService.saveNotes(documentId, userId, content);
    }

    /**
     * 获取文档原文文本内容
     */
    @Override
    public String getDocumentText(Long documentId, Long userId) {
        return readService.getDocumentText(documentId, userId);
    }

}
