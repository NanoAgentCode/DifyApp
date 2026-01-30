package com.github.app.dify.knowledgebase.service.impl;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.knowledgebase.domain.KnowledgeBase;
import com.github.app.dify.knowledgebase.domain.KnowledgeBaseDocument;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseDocumentRepository;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseRepository;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseDocumentResp;
import com.github.app.dify.knowledgebase.service.DocumentVectorizationService;
import com.github.app.dify.knowledgebase.service.FileStorageService;
import com.github.app.dify.knowledgebase.service.KnowledgeBaseDocumentService;
import com.github.app.dify.knowledgebase.service.chunking.ChunkStrategySelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.github.app.dify.knowledgebase.util.KnowledgeBaseConverterUtil;
import com.github.app.dify.knowledgebase.util.KnowledgeBaseDateTimeUtil;
import com.github.app.dify.common.util.DateTimeUtil;
import com.github.app.dify.common.util.PageUtil;
import com.github.app.dify.knowledgebase.util.KnowledgeBaseSoftDeleteUtil;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
/**
 * 知识库文档服务
 */
@Service
public class KnowledgeBaseDocumentServiceImpl implements KnowledgeBaseDocumentService {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseDocumentServiceImpl.class);
    
    // 允许的文件类型
    private static final String[] ALLOWED_FILE_TYPES = {
        "pdf", "doc", "docx", "txt", "md", "xls", "xlsx", "ppt", "pptx",
        "png", "jpg", "jpeg", "gif"
    };
    
    @Autowired
    private KnowledgeBaseDocumentRepository documentRepository;
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private FileStorageService fileStorageService;
    
    @Autowired(required = false)
    private DocumentVectorizationService documentVectorizationService;
    
    @Autowired(required = false)
    private ChunkStrategySelector chunkStrategySelector;
    
    /**
     * 上传文档
     */
    @Transactional
    @Override
    public KnowledgeBaseDocumentResp uploadDocument(Long knowledgeBaseId, MultipartFile file, String uploadUser, Integer tenantId) {
        logger.debug("开始处理文档上传 - 知识库ID: {}, 文件名: {}", knowledgeBaseId, file.getOriginalFilename());
        
        // 验证知识库是否存在
        logger.debug("验证知识库是否存在 - 知识库ID: {}", knowledgeBaseId);
        Optional<KnowledgeBase> kbOptional = knowledgeBaseRepository.findById(knowledgeBaseId);
        if (!kbOptional.isPresent()) {
            logger.error("知识库不存在 - 知识库ID: {}", knowledgeBaseId);
            throw new NotFoundException("知识库不存在");
        }
        
        KnowledgeBase knowledgeBase = kbOptional.get();
        if (knowledgeBase.getDeleted() != null && knowledgeBase.getDeleted() == 1) {
            logger.error("知识库已删除 - 知识库ID: {}", knowledgeBaseId);
            throw new NotFoundException("知识库已删除");
        }
        logger.debug("知识库验证通过 - 知识库ID: {}, 知识库名称: {}", knowledgeBaseId, knowledgeBase.getName());
        
        // 验证文件
        logger.debug("开始验证文件 - 文件名: {}, 文件大小: {} 字节", file.getOriginalFilename(), file.getSize());
        validateFile(file);
        logger.debug("文件验证通过 - 文件名: {}, 文件类型: {}", file.getOriginalFilename(), getFileExtension(file.getOriginalFilename()));
        
        // 生成文件路径
        Integer finalTenantId = tenantId != null ? tenantId : knowledgeBase.getTenantId();
        String filePath = generateFilePath(knowledgeBaseId, finalTenantId, file.getOriginalFilename());
        logger.debug("生成文件路径 - 知识库ID: {}, 租户ID: {}, 文件路径: {}", knowledgeBaseId, finalTenantId, filePath);
        
        // 上传文件到MinIO
        logger.info("开始上传文件到MinIO - 知识库ID: {}, 文件路径: {}, 文件大小: {} 字节", 
                knowledgeBaseId, filePath, file.getSize());
        String fileUrl;
        long minioStartTime = System.currentTimeMillis();
        try {
            fileUrl = fileStorageService.uploadFile(file, filePath);
            long minioDuration = System.currentTimeMillis() - minioStartTime;
            logger.info("文件上传到MinIO成功 - 知识库ID: {}, 文件路径: {}, 文件URL: {}, 耗时: {} 毫秒", 
                    knowledgeBaseId, filePath, fileUrl, minioDuration);
        } catch (Exception e) {
            logger.error("文件上传到MinIO失败 - 知识库ID: {}, 文件路径: {}, 错误信息: {}", 
                    knowledgeBaseId, filePath, e.getMessage(), e);
            throw new BusinessException("文件上传失败", ErrorCode.FILE_UPLOAD_FAILED, e);
        }
        
        // 保存文档元数据
        logger.debug("开始保存文档元数据到数据库 - 知识库ID: {}", knowledgeBaseId);
        KnowledgeBaseDocument document = new KnowledgeBaseDocument();
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setFileName(filePath.substring(filePath.lastIndexOf("/") + 1));
        document.setOriginalFileName(file.getOriginalFilename());
        document.setFilePath(filePath);
        document.setFileUrl(fileUrl);
        document.setFileSize(file.getSize());
        document.setFileType(getFileExtension(file.getOriginalFilename()));
        document.setMimeType(file.getContentType());
        document.setStorageType("minio");
        document.setStatus(1);
        document.setUploadUser(uploadUser);
        KnowledgeBaseDateTimeUtil.setCreateAndUpdateTime(document);
        document.setDeleted(0);
        document.setTenantId(finalTenantId);
        // 初始化向量化状态为0（未向量化）
        document.setVectorizedStatus(0);
        
        document = documentRepository.save(document);
        logger.info("文档元数据保存成功 - 知识库ID: {}, 文档ID: {}, 文件名: {}, 文件类型: {}, 文件大小: {} 字节", 
                knowledgeBaseId, document.getId(), document.getOriginalFileName(), 
                document.getFileType(), document.getFileSize());
        
        // 异步触发向量化流程
        if (documentVectorizationService != null) {
            logger.info("准备触发异步向量化任务 - 知识库ID: {}, 文档ID: {}, 文件名: {}", 
                    knowledgeBaseId, document.getId(), document.getOriginalFileName());
            try {
                // 优化：不将文件加载到内存，直接传递null
                // 异步向量化方法会从MinIO下载文件，避免大文件内存溢出
                // 文件已上传到MinIO，异步方法可以通过document.getFilePath()从MinIO下载
                logger.debug("提交异步向量化任务（文件将从MinIO下载） - 知识库ID: {}, 文档ID: {}, 文件路径: {}", 
                        knowledgeBaseId, document.getId(), document.getFilePath());
                
                documentVectorizationService.vectorizeDocumentAsync(knowledgeBaseId, document.getId(), null);
                logger.info("异步向量化任务已提交 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, document.getId());
            } catch (Exception e) {
                logger.error("触发文档向量化失败 - 知识库ID: {}, 文档ID: {}, 错误信息: {}", 
                        knowledgeBaseId, document.getId(), e.getMessage(), e);
                // 更新向量化状态为失败
                document.setVectorizedStatus(3);
                document.setVectorizedError("触发向量化失败: " + e.getMessage());
                KnowledgeBaseDateTimeUtil.setUpdateTime(document);
                documentRepository.save(document);
                logger.warn("已更新向量化状态为失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, document.getId());
            }
        } else {
            logger.warn("DocumentVectorizationService未配置，跳过向量化 - 知识库ID: {}, 文档ID: {}", 
                    knowledgeBaseId, document.getId());
        }
        
        logger.info("文档上传流程完成 - 知识库ID: {}, 文档ID: {}, 文件名: {}", 
                knowledgeBaseId, document.getId(), document.getOriginalFileName());
        
        // 检测分块策略并添加到响应中
        KnowledgeBaseDocumentResp resp = KnowledgeBaseConverterUtil.convertToResp(document);
        if (chunkStrategySelector != null) {
            String fileType = document.getFileType();
            // 根据文件类型快速检测策略（不解析内容，性能更好）
            String strategyInfo = detectChunkStrategy(fileType);
            resp.setChunkStrategy(strategyInfo);
            logger.debug("检测到分块策略 - 文件类型: {}, 策略: {}", fileType, strategyInfo);
        }
        
        return resp;
    }
    
    /**
     * 检测分块策略（根据文件类型快速检测，不解析内容）
     */
    private String detectChunkStrategy(String fileType) {
        if (fileType == null || fileType.isEmpty()) {
            return "固定大小分块";
        }
        
        String lowerType = fileType.toLowerCase();
        
        // 代码文件
        String[] codeExtensions = {
                "java", "js", "javascript", "ts", "typescript", "py", "python",
                "cpp", "c", "h", "hpp", "cs", "go", "rust", "rb", "ruby",
                "php", "swift", "kt", "kotlin", "scala", "sh", "bash", "sql",
                "html", "css", "xml", "json", "yaml", "yml"
        };
        for (String ext : codeExtensions) {
            if (ext.equals(lowerType)) {
                return "代码分块";
            }
        }
        
        // 表格文件
        if ("xls".equals(lowerType) || "xlsx".equals(lowerType) || "csv".equals(lowerType)) {
            return "表格分块";
        }
        
        // Markdown文件
        if ("md".equals(lowerType) || "markdown".equals(lowerType)) {
            return "标题/段落分块（自动检测表格和代码）";
        }
        
        // Word文档
        if ("doc".equals(lowerType) || "docx".equals(lowerType)) {
            return "段落分块（自动检测表格）";
        }
        
        // PDF文档
        if ("pdf".equals(lowerType)) {
            return "段落分块（自动检测表格）";
        }
        
        // 纯文本文件
        if ("txt".equals(lowerType)) {
            return "段落分块";
        }
        
        // 默认策略
        return "固定大小分块";
    }
    
    /**
     * 删除文档
     */
    @Transactional
    @Override
    public void deleteDocument(Long knowledgeBaseId, Long documentId) {
        Optional<KnowledgeBaseDocument> optional = documentRepository.findById(documentId);
        if (!optional.isPresent()) {
            throw new NotFoundException("文档不存在");
        }
        
        KnowledgeBaseDocument document = optional.get();
        
        // 验证文档属于指定的知识库
        if (!document.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new BusinessException("文档不属于指定的知识库", ErrorCode.BAD_REQUEST);
        }
        
        // 检查是否已删除
        if (document.getDeleted() != null && document.getDeleted() == 1) {
            throw new NotFoundException("文档已删除");
        }
        
        // 从MinIO删除文件
        try {
            fileStorageService.deleteFile(document.getFilePath());
        } catch (Exception e) {
            logger.error("从MinIO删除文件失败: {}", document.getFilePath(), e);
            // 继续执行软删除，即使MinIO删除失败
        }
        
        // 删除向量数据
        if (documentVectorizationService != null) {
            try {
                documentVectorizationService.deleteDocumentVectors(knowledgeBaseId, documentId);
            } catch (Exception e) {
                logger.error("删除文档向量失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
                // 继续执行软删除，即使向量删除失败
            }
        }
        
        // 软删除
        KnowledgeBaseSoftDeleteUtil.softDelete(document, documentRepository);
        
        logger.info("文档删除成功 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
    }
    
    /**
     * 根据ID获取文档
     */
    @Override
    public KnowledgeBaseDocumentResp getDocumentById(Long knowledgeBaseId, Long documentId) {
        Optional<KnowledgeBaseDocument> optional = documentRepository.findById(documentId);
        if (!optional.isPresent()) {
            throw new NotFoundException("文档不存在");
        }
        
        KnowledgeBaseDocument document = optional.get();
        
        // 验证文档属于指定的知识库
        if (!document.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new BusinessException("文档不属于指定的知识库", ErrorCode.BAD_REQUEST);
        }
        
        // 检查是否已删除
        if (document.getDeleted() != null && document.getDeleted() == 1) {
            throw new NotFoundException("文档已删除");
        }
        
        return KnowledgeBaseConverterUtil.convertToResp(document);
    }
    
    /**
     * 获取知识库的文档列表
     */
    @Override
    public List<KnowledgeBaseDocumentResp> listDocuments(Long knowledgeBaseId) {
        List<KnowledgeBaseDocument> documents = documentRepository.findByKnowledgeBaseIdAndDeleted(knowledgeBaseId, 0);
        return documents.stream()
                .map(KnowledgeBaseConverterUtil::convertToResp)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取知识库的文档列表（分页，支持搜索和过滤）
     */
    @Override
    public PageResponse<KnowledgeBaseDocumentResp> listDocumentsWithPagination(
            Long knowledgeBaseId, 
            String keyword, 
            Integer vectorizedStatus, 
            String fileType, 
            int page, 
            int pageSize) {
        // 创建分页请求，按创建时间倒序
        Pageable pageable = PageUtil.createPageable(page, pageSize);
        
        Page<KnowledgeBaseDocument> documentPage;
        
        // 如果有过滤条件，使用带过滤的查询
        if (keyword != null || vectorizedStatus != null || (fileType != null && !fileType.isEmpty())) {
            documentPage = documentRepository.findByKnowledgeBaseIdAndDeletedAndFilters(
                    knowledgeBaseId,
                    0,
                    keyword != null ? keyword.trim() : null,
                    vectorizedStatus,
                    fileType != null && !fileType.isEmpty() ? fileType : null,
                    pageable);
        } else {
            // 否则使用简单查询
            documentPage = documentRepository.findByKnowledgeBaseIdAndDeleted(knowledgeBaseId, 0, pageable);
        }
        
        // 转换为响应对象
        return PageUtil.toPageResponse(documentPage, KnowledgeBaseConverterUtil::convertToResp);
    }
    
    /**
     * 获取知识库的文档数量
     */
    @Override
    public Long getDocumentCount(Long knowledgeBaseId) {
        Long count = documentRepository.countByKnowledgeBaseId(knowledgeBaseId);
        return count != null ? count : 0L;
    }
    
    /**
     * 下载文件
     */
    @Override
    public java.io.InputStream downloadDocument(Long knowledgeBaseId, Long documentId) {
        Optional<KnowledgeBaseDocument> optional = documentRepository.findById(documentId);
        if (!optional.isPresent()) {
            throw new NotFoundException("文档不存在");
        }
        
        KnowledgeBaseDocument document = optional.get();
        
        // 验证文档属于指定的知识库
        if (!document.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new BusinessException("文档不属于指定的知识库", ErrorCode.BAD_REQUEST);
        }
        
        // 检查是否已删除
        if (document.getDeleted() != null && document.getDeleted() == 1) {
            throw new NotFoundException("文档已删除");
        }
        
        return fileStorageService.downloadFile(document.getFilePath());
    }
    
    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空", ErrorCode.FILE_UPLOAD_FAILED);
        }
        
        // 验证文件大小（100MB）
        long maxSize = 100 * 1024 * 1024; // 100MB
        if (file.getSize() > maxSize) {
            throw new BusinessException("文件大小不能超过100MB", ErrorCode.FILE_TOO_LARGE);
        }
        
        // 验证文件类型
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new BusinessException("文件名不能为空", ErrorCode.FILE_UPLOAD_FAILED);
        }
        
        String fileExtension = getFileExtension(fileName).toLowerCase();
        boolean allowed = false;
        for (String allowedType : ALLOWED_FILE_TYPES) {
            if (allowedType.equalsIgnoreCase(fileExtension)) {
                allowed = true;
                break;
            }
        }
        
        if (!allowed) {
            throw new BusinessException("不支持的文件类型: " + fileExtension, ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1);
    }
    
    /**
     * 生成文件路径
     * 格式：kb/{tenantId}/{kbId}/{year}/{month}/{day}/{uuid}-{originalFileName}
     */
    private String generateFilePath(Long knowledgeBaseId, Integer tenantId, String originalFileName) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        String datePath = dateFormat.format(DateTimeUtil.now());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        
        // 清理文件名，防止路径遍历攻击
        String safeFileName = sanitizeFileName(originalFileName);
        
        return String.format("kb/%d/%d/%s/%s-%s", tenantId, knowledgeBaseId, datePath, uuid, safeFileName);
    }
    
    /**
     * 清理文件名，防止路径遍历攻击
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "file";
        }
        // 移除路径分隔符和特殊字符
        return fileName.replaceAll("[/\\\\:*?\"<>|]", "_");
    }
    
}
