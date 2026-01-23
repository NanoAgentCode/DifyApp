package com.github.app.dify.knowledgebase.service.impl;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.knowledgebase.domain.KnowledgeBase;
import com.github.app.dify.knowledgebase.domain.KnowledgeBaseDocument;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseDocumentRepository;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseRepository;
import com.github.app.dify.knowledgebase.req.CreateKnowledgeBaseReq;
import com.github.app.dify.knowledgebase.req.KnowledgeBaseImportRequest;
import com.github.app.dify.knowledgebase.resp.DocumentImportError;
import com.github.app.dify.knowledgebase.resp.FileInfo;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseImportResult;
import com.github.app.dify.knowledgebase.resp.ZipPreviewResult;
import com.github.app.dify.knowledgebase.service.FileStorageService;
import com.github.app.dify.knowledgebase.service.KnowledgeBaseDocumentService;
import com.github.app.dify.knowledgebase.service.KnowledgeBaseMigrationService;
import com.github.app.dify.knowledgebase.service.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 知识库迁移服务实现
 */
@Service
public class KnowledgeBaseMigrationServiceImpl implements KnowledgeBaseMigrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseMigrationServiceImpl.class);
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private KnowledgeBaseDocumentRepository documentRepository;
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    @Autowired
    private KnowledgeBaseDocumentService documentService;
    
    @Autowired
    private FileStorageService fileStorageService;
    
    // 支持的文件类型
    private static final Set<String> SUPPORTED_FILE_TYPES = Set.of(
        "pdf", "doc", "docx", "txt", "md", "xls", "xlsx", "ppt", "pptx", 
        "png", "jpg", "jpeg", "gif"
    );
    
    @Override
    public InputStream exportKnowledgeBase(Long knowledgeBaseId) throws Exception {
        logger.info("开始导出知识库 - 知识库ID: {}", knowledgeBaseId);
        
        // 1. 查询知识库信息
        Optional<KnowledgeBase> kbOptional = knowledgeBaseRepository.findById(knowledgeBaseId);
        if (kbOptional.isEmpty() || (kbOptional.get().getDeleted() != null && kbOptional.get().getDeleted() == 1)) {
            throw new NotFoundException("知识库不存在: " + knowledgeBaseId);
        }
        KnowledgeBase knowledgeBase = kbOptional.get();
        
        // 2. 查询知识库下的所有文档（未删除的）
        List<KnowledgeBaseDocument> documents = documentRepository.findByKnowledgeBaseIdAndDeleted(knowledgeBaseId, 0);
        if (documents.isEmpty()) {
            throw new BusinessException("知识库中没有文档", ErrorCode.RESOURCE_NOT_FOUND);
        }
        
        logger.info("找到 {} 个文档需要导出", documents.size());
        
        // 3. 创建临时ZIP文件
        Path tempZipFile = Files.createTempFile("kb-export-", ".zip");
        logger.debug("创建临时ZIP文件: {}", tempZipFile);
        
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZipFile.toFile()))) {
            // 4. 遍历文档，下载并添加到ZIP
            for (KnowledgeBaseDocument doc : documents) {
                try {
                    String filePath = doc.getFilePath();
                    if (filePath == null || filePath.isEmpty()) {
                        logger.warn("文档 {} 的文件路径为空，跳过", doc.getId());
                        continue;
                    }
                    
                    // 从MinIO下载文件
                    InputStream fileStream = fileStorageService.downloadFile(filePath);
                    
                    // 添加到ZIP（使用documents目录）
                    String zipEntryName = "documents/" + doc.getOriginalFileName();
                    zos.putNextEntry(new ZipEntry(zipEntryName));
                    
                    // 复制文件内容
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fileStream.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    
                    zos.closeEntry();
                    fileStream.close();
                    
                    logger.debug("已添加文档到ZIP: {}", zipEntryName);
                } catch (Exception e) {
                    logger.error("导出文档失败 - 文档ID: {}, 文件名: {}", doc.getId(), doc.getOriginalFileName(), e);
                    // 继续处理其他文档
                }
            }
        }
        
        logger.info("知识库导出完成 - 知识库ID: {}, ZIP文件大小: {} 字节", 
                knowledgeBaseId, Files.size(tempZipFile));
        
        // 5. 返回ZIP文件输入流
        return new FileInputStream(tempZipFile.toFile()) {
            @Override
            public void close() throws IOException {
                super.close();
                // 删除临时文件
                try {
                    Files.deleteIfExists(tempZipFile);
                } catch (IOException e) {
                    logger.warn("删除临时ZIP文件失败: {}", tempZipFile, e);
                }
            }
        };
    }
    
    @Override
    @Transactional
    public KnowledgeBaseImportResult importKnowledgeBase(
            MultipartFile zipFile,
            KnowledgeBaseImportRequest request,
            Long userId,
            String username,
            Integer tenantId) throws Exception {
        
        logger.info("开始导入知识库 - 文件名: {}, 知识库名称: {}", zipFile.getOriginalFilename(), request.getKnowledgeBaseName());
        
        KnowledgeBaseImportResult result = new KnowledgeBaseImportResult();
        List<DocumentImportError> errors = new ArrayList<>();
        
        // 1. 验证必填参数
        if (request.getKnowledgeBaseName() == null || request.getKnowledgeBaseName().trim().isEmpty()) {
            throw new BusinessException("知识库名称不能为空", ErrorCode.BAD_REQUEST);
        }
        
        // 2. 解压ZIP文件到临时目录
        Path tempDir = Files.createTempDirectory("kb-import-");
        logger.debug("创建临时目录: {}", tempDir);
        
        try {
            // 3. 扫描ZIP文件，提取文件列表
            List<FileInfo> fileInfos = scanZipFile(zipFile, tempDir);
            
            if (fileInfos.isEmpty()) {
                throw new BusinessException("ZIP文件中没有找到有效的文档文件", ErrorCode.FILE_NOT_FOUND);
            }
            
            logger.info("扫描到 {} 个文件", fileInfos.size());
            result.setFileCount(fileInfos.size());
            result.setTotalDocuments(fileInfos.size());
            
            // 4. 创建知识库
            CreateKnowledgeBaseReq createReq = new CreateKnowledgeBaseReq();
            createReq.setName(request.getKnowledgeBaseName().trim());
            createReq.setDescription(request.getDescription() != null ? request.getDescription().trim() : "");
            createReq.setVectorStoreType(request.getVectorStoreType() != null ? request.getVectorStoreType() : "qdrant");
            createReq.setIsPublic(request.getIsPublic() != null ? request.getIsPublic() : false);
            createReq.setTopK(request.getTopK());
            createReq.setEmbeddingModelId(request.getEmbeddingModelId());
            createReq.setTenantId(tenantId);
            createReq.setStatus(1);
            
            com.github.app.dify.knowledgebase.resp.KnowledgeBaseResp kbResp = 
                knowledgeBaseService.createKnowledgeBase(createReq, userId, username, null, false);
            
            Long newKbId = kbResp.getId();
            result.setKnowledgeBaseId(newKbId);
            result.setKnowledgeBaseName(kbResp.getName());
            
            logger.info("知识库创建成功 - 知识库ID: {}, 名称: {}", newKbId, kbResp.getName());
            
            // 5. 上传文件
            int successCount = 0;
            int failedCount = 0;
            
            for (FileInfo fileInfo : fileInfos) {
                try {
                    Path filePath = tempDir.resolve(fileInfo.getFilePath());
                    if (!Files.exists(filePath)) {
                        logger.warn("文件不存在: {}", filePath);
                        failedCount++;
                        DocumentImportError error = new DocumentImportError();
                        error.setFileName(fileInfo.getFileName());
                        error.setErrorMessage("文件不存在");
                        errors.add(error);
                        continue;
                    }
                    
                    // 创建MultipartFile包装类
                    MultipartFile multipartFile = new FileSystemMultipartFile(
                        filePath.toFile(), 
                        fileInfo.getFileName(),
                        fileInfo.getMimeType()
                    );
                    
                    // 上传文档
                    documentService.uploadDocument(newKbId, multipartFile, username, tenantId);
                    successCount++;
                    
                    logger.debug("文档上传成功: {}", fileInfo.getFileName());
                } catch (Exception e) {
                    logger.error("文档上传失败: {}", fileInfo.getFileName(), e);
                    failedCount++;
                    DocumentImportError error = new DocumentImportError();
                    error.setFileName(fileInfo.getFileName());
                    error.setErrorMessage(e.getMessage() != null ? e.getMessage() : "上传失败");
                    errors.add(error);
                }
            }
            
            result.setSuccessCount(successCount);
            result.setFailedCount(failedCount);
            result.setErrors(errors);
            
            // 6. 设置结果状态
            if (failedCount == 0) {
                result.setStatus("SUCCESS");
                result.setMessage("导入成功，共导入 " + successCount + " 个文档");
            } else if (successCount > 0) {
                result.setStatus("PARTIAL_SUCCESS");
                result.setMessage("部分导入成功，成功: " + successCount + "，失败: " + failedCount);
            } else {
                result.setStatus("ERROR");
                result.setMessage("导入失败，所有文档都未能导入");
            }
            
            logger.info("知识库导入完成 - 知识库ID: {}, 成功: {}, 失败: {}", 
                    newKbId, successCount, failedCount);
            
        } finally {
            // 清理临时目录
            try {
                deleteDirectory(tempDir);
            } catch (Exception e) {
                logger.warn("清理临时目录失败: {}", tempDir, e);
            }
        }
        
        return result;
    }
    
    @Override
    public ZipPreviewResult previewZipFile(MultipartFile zipFile) throws Exception {
        logger.info("预览ZIP文件: {}", zipFile.getOriginalFilename());
        
        Path tempDir = Files.createTempDirectory("kb-preview-");
        try {
            List<FileInfo> fileInfos = scanZipFile(zipFile, tempDir);
            
            ZipPreviewResult result = new ZipPreviewResult();
            result.setFileCount(fileInfos.size());
            result.setFiles(fileInfos);
            
            return result;
        } finally {
            deleteDirectory(tempDir);
        }
    }
    
    /**
     * 扫描ZIP文件，提取文件信息
     */
    private List<FileInfo> scanZipFile(MultipartFile zipFile, Path extractDir) throws Exception {
        List<FileInfo> fileInfos = new ArrayList<>();
        
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                
                // 跳过目录
                if (entry.isDirectory()) {
                    continue;
                }
                
                // 只处理documents目录下的文件，或者根目录下的文件
                String fileName;
                if (entryName.startsWith("documents/")) {
                    fileName = entryName.substring("documents/".length());
                } else if (!entryName.contains("/")) {
                    fileName = entryName;
                } else {
                    // 跳过其他目录的文件
                    continue;
                }
                
                // 检查文件类型
                String fileExtension = getFileExtension(fileName);
                if (fileExtension == null || !SUPPORTED_FILE_TYPES.contains(fileExtension.toLowerCase())) {
                    logger.debug("跳过不支持的文件类型: {}", fileName);
                    continue;
                }
                
                // 提取文件到临时目录
                Path targetPath = extractDir.resolve(entryName);
                Files.createDirectories(targetPath.getParent());
                
                try (OutputStream fos = Files.newOutputStream(targetPath)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                
                // 创建文件信息
                FileInfo fileInfo = new FileInfo();
                fileInfo.setFileName(fileName);
                fileInfo.setFilePath(entryName);
                fileInfo.setFileType(fileExtension);
                fileInfo.setFileSize(entry.getSize());
                fileInfo.setMimeType(getMimeType(fileExtension));
                
                fileInfos.add(fileInfo);
                
                zis.closeEntry();
            }
        }
        
        return fileInfos;
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return null;
    }
    
    /**
     * 根据文件扩展名获取MIME类型
     */
    private String getMimeType(String extension) {
        if (extension == null) {
            return "application/octet-stream";
        }
        
        Map<String, String> mimeTypes = new HashMap<>();
        mimeTypes.put("pdf", "application/pdf");
        mimeTypes.put("doc", "application/msword");
        mimeTypes.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        mimeTypes.put("txt", "text/plain");
        mimeTypes.put("md", "text/markdown");
        mimeTypes.put("xls", "application/vnd.ms-excel");
        mimeTypes.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        mimeTypes.put("ppt", "application/vnd.ms-powerpoint");
        mimeTypes.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        mimeTypes.put("png", "image/png");
        mimeTypes.put("jpg", "image/jpeg");
        mimeTypes.put("jpeg", "image/jpeg");
        mimeTypes.put("gif", "image/gif");
        
        return mimeTypes.getOrDefault(extension.toLowerCase(), "application/octet-stream");
    }
    
    /**
     * 删除目录及其所有内容
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.warn("删除文件失败: {}", path, e);
                    }
                });
        }
    }
    
    /**
     * 文件系统MultipartFile实现
     */
    private static class FileSystemMultipartFile implements MultipartFile {
        private final File file;
        private final String fileName;
        private final String contentType;
        
        public FileSystemMultipartFile(File file, String fileName, String contentType) {
            this.file = file;
            this.fileName = fileName;
            this.contentType = contentType;
        }
        
        @Override
        public String getName() {
            return "file";
        }
        
        @Override
        public String getOriginalFilename() {
            return fileName;
        }
        
        @Override
        public String getContentType() {
            return contentType;
        }
        
        @Override
        public boolean isEmpty() {
            return file.length() == 0;
        }
        
        @Override
        public long getSize() {
            return file.length();
        }
        
        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(file.toPath());
        }
        
        @Override
        public InputStream getInputStream() throws IOException {
            return new FileInputStream(file);
        }
        
        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.copy(file.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
