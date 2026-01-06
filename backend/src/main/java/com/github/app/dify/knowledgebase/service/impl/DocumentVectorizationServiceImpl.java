package com.github.app.dify.knowledgebase.service.impl;

import com.github.app.dify.knowledgebase.domain.KnowledgeBaseDocument;
import com.github.app.dify.knowledgebase.langchain4j.*;
import com.github.app.dify.knowledgebase.langchain4j.store.*;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseDocumentRepository;
import com.github.app.dify.knowledgebase.service.DocumentVectorizationService;
import com.github.app.dify.knowledgebase.service.FileStorageService;
import com.github.app.dify.knowledgebase.service.VectorStoreService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
/**
 * 文档向量化服务（使用LangChain4j）
 */
@Service
public class DocumentVectorizationServiceImpl implements DocumentVectorizationService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentVectorizationServiceImpl.class);
    
    @Autowired
    private TikaDocumentLoader tikaDocumentLoader;
    
    @Autowired
    private ConfigurableDocumentSplitter documentSplitter;
    
    @Autowired
    private CustomEmbeddingModel embeddingModel;
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    @Autowired
    private VectorStoreFactory vectorStoreFactory;
    
    @Autowired
    private KnowledgeBaseDocumentRepository documentRepository;
    
    @Autowired(required = false)
    private FileStorageService fileStorageService;
    
    /**
     * 异步向量化文档
     */
    @Async
    @Override
    public void vectorizeDocumentAsync(Long knowledgeBaseId, Long documentId, MultipartFile file) {
        Optional<KnowledgeBaseDocument> docOptional = documentRepository.findById(documentId);
        if (docOptional.isEmpty()) {
            logger.error("文档不存在，无法向量化 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            return;
        }
        
        KnowledgeBaseDocument doc = docOptional.get();
        
        try {
            logger.info("开始向量化文档 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            
            // 更新状态为向量化中（1）
            doc.setVectorizedStatus(1);
            doc.setVectorizedError(null);
            doc.setUpdateTime(new java.util.Date());
            documentRepository.save(doc);
            logger.info("已更新状态为向量化中 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            
            // 1. 使用LangChain4j加载文档
            logger.info("开始加载文档 - 知识库ID: {}, 文档ID: {}, 文件名: {}", 
                    knowledgeBaseId, documentId, file.getOriginalFilename());
            Document document;
            try {
                document = tikaDocumentLoader.load(file);
            } catch (RuntimeException e) {
                // 捕获文档加载异常（如OCR结果为空）
                logger.error("文档加载失败 - 知识库ID: {}, 文档ID: {}, 错误: {}", 
                        knowledgeBaseId, documentId, e.getMessage());
                // 更新状态为失败（3）
                doc.setVectorizedStatus(3);
                doc.setVectorizedError(e.getMessage());
                doc.setUpdateTime(new java.util.Date());
                documentRepository.save(doc);
                return;
            }
            
            // 双重检查（虽然TikaDocumentLoader已经检查过，但为了安全起见）
            if (document.text() == null || document.text().trim().isEmpty()) {
                logger.warn("文档内容为空，跳过向量化 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
                // 更新状态为失败（3）
                doc.setVectorizedStatus(3);
                doc.setVectorizedError("文档内容为空");
                doc.setUpdateTime(new java.util.Date());
                documentRepository.save(doc);
                return;
            }
            logger.info("文档加载完成，文本长度: {} - 知识库ID: {}, 文档ID: {}", 
                    document.text().length(), knowledgeBaseId, documentId);
            
            // 2. 添加documentId到metadata
            document.metadata().put("documentId", String.valueOf(documentId));
            document.metadata().put("knowledgeBaseId", String.valueOf(knowledgeBaseId));
            
            // 3. 使用LangChain4j分割文档
            logger.info("开始分割文档 - 知识库ID: {}, 文档ID: {}, 文本长度: {}", 
                    knowledgeBaseId, documentId, document.text().length());
            long splitStartTime = System.currentTimeMillis();
            List<TextSegment> segments;
            try {
                segments = documentSplitter.split(document);
                long splitDuration = System.currentTimeMillis() - splitStartTime;
                logger.info("文档分割完成，耗时: {} 毫秒 - 知识库ID: {}, 文档ID: {}", 
                        splitDuration, knowledgeBaseId, documentId);
            } catch (Exception e) {
                logger.error("文档分割失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
                throw new RuntimeException("文档分割失败: " + e.getMessage(), e);
            }
            
            if (segments.isEmpty()) {
                logger.warn("文档分割为空，跳过向量化 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
                // 更新状态为失败（3）
                doc.setVectorizedStatus(3);
                doc.setVectorizedError("文档分割为空");
                doc.setUpdateTime(new java.util.Date());
                documentRepository.save(doc);
                return;
            }
            logger.info("文档分割完成，segment数量: {} - 知识库ID: {}, 文档ID: {}", 
                    segments.size(), knowledgeBaseId, documentId);
            
            // 4. 为每个segment添加documentId和chunkIndex
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                segment.metadata().put("documentId", String.valueOf(documentId));
                segment.metadata().put("chunkIndex", String.valueOf(i));
                segment.metadata().put("knowledgeBaseId", String.valueOf(knowledgeBaseId));
            }
            
            // 5. 创建知识库专用的EmbeddingStore
            logger.info("创建EmbeddingStore - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            EmbeddingStore<TextSegment> embeddingStore = vectorStoreFactory.createEmbeddingStore(knowledgeBaseId);
            logger.info("EmbeddingStore创建成功 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            
            // 6. 向量化segments
            logger.info("开始向量化segments，数量: {} - 知识库ID: {}, 文档ID: {}", 
                    segments.size(), knowledgeBaseId, documentId);
            List<dev.langchain4j.data.embedding.Embedding> embeddings;
            try {
                embeddings = embeddingModel.embedAll(segments).content();
                logger.info("向量化完成，embedding数量: {} - 知识库ID: {}, 文档ID: {}", 
                        embeddings.size(), knowledgeBaseId, documentId);
            } catch (Exception e) {
                logger.error("向量化API调用失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
                throw new RuntimeException("向量化API调用失败: " + e.getMessage(), e);
            }
            
            // 7. 批量存储到EmbeddingStore
            logger.info("开始存储向量到Qdrant，数量: {} - 知识库ID: {}, 文档ID: {}", 
                    embeddings.size(), knowledgeBaseId, documentId);
            try {
                embeddingStore.addAll(embeddings, segments);
                logger.info("向量存储完成 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            } catch (Exception e) {
                logger.error("存储向量到Qdrant失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
                throw new RuntimeException("存储向量失败: " + e.getMessage(), e);
            }
            
            // 更新状态为成功（2）
            docOptional = documentRepository.findById(documentId);
            if (docOptional.isEmpty()) {
                logger.error("文档不存在，无法更新状态 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
                return;
            }
            doc = docOptional.get();
            doc.setVectorizedStatus(2);
            doc.setVectorizedTime(new java.util.Date());
            doc.setVectorizedError(null);
            doc.setUpdateTime(new java.util.Date());
            documentRepository.save(doc);
            
            logger.info("文档向量化完成 - 知识库ID: {}, 文档ID: {}, segment数量: {}", 
                    knowledgeBaseId, documentId, segments.size());
            
        } catch (Exception e) {
            logger.error("文档向量化失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
            
            // 更新状态为失败（3）
            try {
                docOptional = documentRepository.findById(documentId);
                if (docOptional.isPresent()) {
                    doc = docOptional.get();
                    doc.setVectorizedStatus(3);
                    String errorMsg = e.getMessage();
                    if (errorMsg == null) {
                        errorMsg = e.getClass().getSimpleName();
                    }
                    doc.setVectorizedError(errorMsg.length() > 500 
                            ? errorMsg.substring(0, 500) : errorMsg);
                    doc.setUpdateTime(new java.util.Date());
                    documentRepository.save(doc);
                    logger.info("已更新向量化状态为失败 - 知识库ID: {}, 文档ID: {}, 错误: {}", 
                            knowledgeBaseId, documentId, errorMsg);
                } else {
                    logger.error("文档不存在，无法更新失败状态 - 知识库ID: {}, 文档ID: {}", 
                            knowledgeBaseId, documentId);
                }
            } catch (Exception updateException) {
                logger.error("更新向量化失败状态时出错 - 知识库ID: {}, 文档ID: {}", 
                        knowledgeBaseId, documentId, updateException);
            }
            // 不抛出异常，避免影响主流程
        }
    }
    
    /**
     * 重新向量化文档
     */
    @Async
    @Override
    public void reindexDocument(Long knowledgeBaseId, Long documentId) {
        long startTime = System.currentTimeMillis();
        logger.info("=== 开始重新向量化文档流程 ===");
        logger.info("知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
        
        Optional<KnowledgeBaseDocument> optional = documentRepository.findById(documentId);
        if (optional.isEmpty()) {
            logger.error("文档不存在，无法重新向量化 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            throw new RuntimeException("文档不存在: " + documentId);
        }
        
        KnowledgeBaseDocument document = optional.get();
        logger.info("文档信息 - 知识库ID: {}, 文档ID: {}, 文件名: {}, 当前向量化状态: {}, 文件大小: {} 字节", 
                knowledgeBaseId, documentId, document.getOriginalFileName(), 
                document.getVectorizedStatus(), document.getFileSize());
        
        if (!document.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            logger.error("文档不属于指定的知识库 - 知识库ID: {}, 文档ID: {}, 文档所属知识库ID: {}", 
                    knowledgeBaseId, documentId, document.getKnowledgeBaseId());
            throw new RuntimeException("文档不属于指定的知识库");
        }
        
        try {
            // 更新状态为向量化中（1）
            logger.debug("更新文档状态为向量化中 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            document.setVectorizedStatus(1);
            document.setVectorizedError(null);
            document.setUpdateTime(new java.util.Date());
            documentRepository.save(document);
            logger.debug("文档状态已更新为向量化中 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            
            // 删除旧向量
            logger.info("开始删除旧向量数据 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            long deleteStartTime = System.currentTimeMillis();
            deleteDocumentVectors(knowledgeBaseId, documentId);
            long deleteDuration = System.currentTimeMillis() - deleteStartTime;
            logger.info("旧向量数据删除完成 - 知识库ID: {}, 文档ID: {}, 耗时: {} 毫秒", 
                    knowledgeBaseId, documentId, deleteDuration);
            
            // 从MinIO下载文件
            if (fileStorageService == null) {
                logger.error("FileStorageService未配置，无法下载文件 - 知识库ID: {}, 文档ID: {}", 
                        knowledgeBaseId, documentId);
                throw new RuntimeException("FileStorageService未配置");
            }
            
            logger.info("开始从MinIO下载文件 - 知识库ID: {}, 文档ID: {}, 文件路径: {}", 
                    knowledgeBaseId, documentId, document.getFilePath());
            long downloadStartTime = System.currentTimeMillis();
            InputStream inputStream = fileStorageService.downloadFile(document.getFilePath());
            long downloadDuration = System.currentTimeMillis() - downloadStartTime;
            logger.info("文件下载完成 - 知识库ID: {}, 文档ID: {}, 耗时: {} 毫秒", 
                    knowledgeBaseId, documentId, downloadDuration);
            
            // 将InputStream转换为MultipartFile
            logger.debug("开始读取文件内容 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            long readStartTime = System.currentTimeMillis();
            byte[] fileBytes = readAllBytes(inputStream);
            inputStream.close();
            long readDuration = System.currentTimeMillis() - readStartTime;
            logger.info("文件内容读取完成 - 知识库ID: {}, 文档ID: {}, 文件大小: {} 字节, 耗时: {} 毫秒", 
                    knowledgeBaseId, documentId, fileBytes.length, readDuration);
            
            MultipartFile multipartFile = new InputStreamMultipartFile(
                    fileBytes,
                    document.getOriginalFileName(),
                    document.getMimeType()
            );
            
            // 重新向量化
            logger.info("提交重新向量化任务 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            vectorizeDocumentAsync(knowledgeBaseId, documentId, multipartFile);
            
            long totalDuration = System.currentTimeMillis() - startTime;
            logger.info("=== 重新向量化任务已提交 ===");
            logger.info("知识库ID: {}, 文档ID: {}, 文件名: {}, 总耗时: {} 毫秒", 
                    knowledgeBaseId, documentId, document.getOriginalFileName(), totalDuration);
        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            logger.error("=== 重新向量化文档失败 ===");
            logger.error("知识库ID: {}, 文档ID: {}, 总耗时: {} 毫秒, 错误信息: {}", 
                    knowledgeBaseId, documentId, totalDuration, e.getMessage(), e);
            
            // 更新状态为失败（3）
            try {
                optional = documentRepository.findById(documentId);
                if (optional.isPresent()) {
                    document = optional.get();
                    document.setVectorizedStatus(3);
                    String errorMsg = e.getMessage();
                    if (errorMsg == null) {
                        errorMsg = e.getClass().getSimpleName();
                    }
                    document.setVectorizedError(errorMsg.length() > 500 
                            ? errorMsg.substring(0, 500) : errorMsg);
                    document.setUpdateTime(new java.util.Date());
                    documentRepository.save(document);
                    logger.info("已更新向量化状态为失败 - 知识库ID: {}, 文档ID: {}, 错误: {}", 
                            knowledgeBaseId, documentId, errorMsg);
                }
            } catch (Exception updateException) {
                logger.error("更新向量化失败状态时出错 - 知识库ID: {}, 文档ID: {}", 
                        knowledgeBaseId, documentId, updateException);
            }
            throw new RuntimeException("重新向量化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 读取InputStream的所有字节（Java 8兼容）
     */
    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
    
    /**
     * MultipartFile实现类，用于将InputStream转换为MultipartFile
     */
    private static class InputStreamMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String fileName;
        private final String contentType;
        
        public InputStreamMultipartFile(byte[] content, String fileName, String contentType) {
            this.content = content;
            this.fileName = fileName;
            this.contentType = contentType;
        }
        
        @NotNull
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
            return content == null || content.length == 0;
        }
        
        @Override
        public long getSize() {
            return content != null ? content.length : 0;
        }
        
        @NotNull
        @Override
        public byte[] getBytes(){
            return content != null ? content : new byte[0];
        }
        
        @NotNull
        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content != null ? content : new byte[0]);
        }
        
        @Override
        public void transferTo(@NotNull java.io.File dest) throws IllegalStateException {
            throw new UnsupportedOperationException("transferTo not supported");
        }
    }
    
    /**
     * 删除文档向量
     */
    @Override
    public void deleteDocumentVectors(Long knowledgeBaseId, Long documentId) {
        try {
            EmbeddingStore<TextSegment> embeddingStore = vectorStoreFactory.createEmbeddingStore(knowledgeBaseId);

            if (embeddingStore instanceof QdrantEmbeddingStore) {
                ((QdrantEmbeddingStore) embeddingStore).deleteByDocumentId(documentId);
            } else if (embeddingStore instanceof FaissEmbeddingStore) {
                ((FaissEmbeddingStore) embeddingStore).deleteByDocumentId(documentId);
            } else if (embeddingStore instanceof MilvusEmbeddingStore) {
                ((MilvusEmbeddingStore) embeddingStore).deleteByDocumentId(documentId);
            } else if (embeddingStore instanceof ChromaEmbeddingStore) {
                ((ChromaEmbeddingStore) embeddingStore).deleteByDocumentId(documentId);
            } else if (embeddingStore instanceof WeaviateEmbeddingStore) {
                ((WeaviateEmbeddingStore) embeddingStore).deleteByDocumentId(documentId);
            } else {
                // 回退到原有方法
                vectorStoreService.deleteDocumentVectors(knowledgeBaseId, documentId);
            }
            
            logger.info("删除文档向量成功 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
        } catch (Exception e) {
            logger.error("删除文档向量失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
            throw new RuntimeException("删除文档向量失败: " + e.getMessage(), e);
        }
    }
}