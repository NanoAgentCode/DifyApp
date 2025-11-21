package com.github.app.dify.service;

import com.github.app.dify.domain.KnowledgeBase;
import com.github.app.dify.domain.KnowledgeBaseDocument;
import com.github.app.dify.repository.KnowledgeBaseDocumentRepository;
import com.github.app.dify.repository.KnowledgeBaseRepository;
import com.github.app.dify.resp.KnowledgeBaseDocumentResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库文档服务
 */
@Service
public class KnowledgeBaseDocumentService {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseDocumentService.class);
    
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
    
    /**
     * 上传文档
     */
    @Transactional
    public KnowledgeBaseDocumentResp uploadDocument(Long knowledgeBaseId, MultipartFile file, String uploadUser, Integer tenantId) {
        logger.debug("开始处理文档上传 - 知识库ID: {}, 文件名: {}", knowledgeBaseId, file.getOriginalFilename());
        
        // 验证知识库是否存在
        logger.debug("验证知识库是否存在 - 知识库ID: {}", knowledgeBaseId);
        Optional<KnowledgeBase> kbOptional = knowledgeBaseRepository.findById(knowledgeBaseId);
        if (!kbOptional.isPresent()) {
            logger.error("知识库不存在 - 知识库ID: {}", knowledgeBaseId);
            throw new RuntimeException("知识库不存在: " + knowledgeBaseId);
        }
        
        KnowledgeBase knowledgeBase = kbOptional.get();
        if (knowledgeBase.getDeleted() != null && knowledgeBase.getDeleted() == 1) {
            logger.error("知识库已删除 - 知识库ID: {}", knowledgeBaseId);
            throw new RuntimeException("知识库已删除: " + knowledgeBaseId);
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
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
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
        document.setCreateTime(new Date());
        document.setUpdateTime(new Date());
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
                documentVectorizationService.vectorizeDocumentAsync(knowledgeBaseId, document.getId(), file);
                logger.info("异步向量化任务已提交 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, document.getId());
            } catch (Exception e) {
                logger.error("触发文档向量化失败 - 知识库ID: {}, 文档ID: {}, 错误信息: {}", 
                        knowledgeBaseId, document.getId(), e.getMessage(), e);
                // 更新向量化状态为失败
                document.setVectorizedStatus(3);
                document.setVectorizedError("触发向量化失败: " + e.getMessage());
                document.setUpdateTime(new Date());
                documentRepository.save(document);
                logger.warn("已更新向量化状态为失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, document.getId());
            }
        } else {
            logger.warn("DocumentVectorizationService未配置，跳过向量化 - 知识库ID: {}, 文档ID: {}", 
                    knowledgeBaseId, document.getId());
        }
        
        logger.info("文档上传流程完成 - 知识库ID: {}, 文档ID: {}, 文件名: {}", 
                knowledgeBaseId, document.getId(), document.getOriginalFileName());
        return convertToResp(document);
    }
    
    /**
     * 删除文档
     */
    @Transactional
    public void deleteDocument(Long knowledgeBaseId, Long documentId) {
        Optional<KnowledgeBaseDocument> optional = documentRepository.findById(documentId);
        if (!optional.isPresent()) {
            throw new RuntimeException("文档不存在: " + documentId);
        }
        
        KnowledgeBaseDocument document = optional.get();
        
        // 验证文档属于指定的知识库
        if (!document.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new RuntimeException("文档不属于指定的知识库");
        }
        
        // 检查是否已删除
        if (document.getDeleted() != null && document.getDeleted() == 1) {
            throw new RuntimeException("文档已删除: " + documentId);
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
        document.setDeleted(1);
        document.setUpdateTime(new Date());
        documentRepository.save(document);
        
        logger.info("文档删除成功 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
    }
    
    /**
     * 根据ID获取文档
     */
    public KnowledgeBaseDocumentResp getDocumentById(Long knowledgeBaseId, Long documentId) {
        Optional<KnowledgeBaseDocument> optional = documentRepository.findById(documentId);
        if (!optional.isPresent()) {
            throw new RuntimeException("文档不存在: " + documentId);
        }
        
        KnowledgeBaseDocument document = optional.get();
        
        // 验证文档属于指定的知识库
        if (!document.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new RuntimeException("文档不属于指定的知识库");
        }
        
        // 检查是否已删除
        if (document.getDeleted() != null && document.getDeleted() == 1) {
            throw new RuntimeException("文档已删除: " + documentId);
        }
        
        return convertToResp(document);
    }
    
    /**
     * 获取知识库的文档列表
     */
    public List<KnowledgeBaseDocumentResp> listDocuments(Long knowledgeBaseId) {
        List<KnowledgeBaseDocument> documents = documentRepository.findByKnowledgeBaseIdAndDeleted(knowledgeBaseId, 0);
        return documents.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取知识库的文档数量
     */
    public Long getDocumentCount(Long knowledgeBaseId) {
        Long count = documentRepository.countByKnowledgeBaseId(knowledgeBaseId);
        return count != null ? count : 0L;
    }
    
    /**
     * 下载文件
     */
    public java.io.InputStream downloadDocument(Long knowledgeBaseId, Long documentId) {
        Optional<KnowledgeBaseDocument> optional = documentRepository.findById(documentId);
        if (!optional.isPresent()) {
            throw new RuntimeException("文档不存在: " + documentId);
        }
        
        KnowledgeBaseDocument document = optional.get();
        
        // 验证文档属于指定的知识库
        if (!document.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new RuntimeException("文档不属于指定的知识库");
        }
        
        // 检查是否已删除
        if (document.getDeleted() != null && document.getDeleted() == 1) {
            throw new RuntimeException("文档已删除: " + documentId);
        }
        
        return fileStorageService.downloadFile(document.getFilePath());
    }
    
    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }
        
        // 验证文件大小（100MB）
        long maxSize = 100 * 1024 * 1024; // 100MB
        if (file.getSize() > maxSize) {
            throw new RuntimeException("文件大小不能超过100MB");
        }
        
        // 验证文件类型
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new RuntimeException("文件名不能为空");
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
            throw new RuntimeException("不支持的文件类型: " + fileExtension);
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
        String datePath = dateFormat.format(new Date());
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
    
    /**
     * 转换为响应对象
     */
    private KnowledgeBaseDocumentResp convertToResp(KnowledgeBaseDocument document) {
        KnowledgeBaseDocumentResp resp = new KnowledgeBaseDocumentResp();
        BeanUtils.copyProperties(document, resp);
        return resp;
    }
}

