package com.github.app.dify.documentreader.service.impl;

import com.github.app.dify.documentreader.domain.DocumentReader;
import com.github.app.dify.documentreader.repository.DocumentReaderRepository;
import com.github.app.dify.documentreader.service.DocumentReaderVectorizationService;
import com.github.app.dify.knowledgebase.langchain4j.*;
import com.github.app.dify.knowledgebase.service.FileStorageService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 文档解读向量化服务实现（使用LangChain4j）
 */
@Service
public class DocumentReaderVectorizationServiceImpl implements DocumentReaderVectorizationService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentReaderVectorizationServiceImpl.class);
    
    @Autowired
    private TikaDocumentLoader tikaDocumentLoader;
    
    @Autowired
    private ConfigurableDocumentSplitter documentSplitter;
    
    @Autowired
    private CustomEmbeddingModel embeddingModel;
    
    @Autowired
    private com.github.app.dify.knowledgebase.service.EmbeddingService embeddingService;
    
    @Autowired
    private VectorStoreFactory vectorStoreFactory;
    
    @Autowired
    private com.github.app.dify.knowledgebase.service.VectorStoreService vectorStoreService;
    
    @Autowired
    private DocumentReaderRepository documentRepository;
    
    @Autowired(required = false)
    private FileStorageService fileStorageService;
    
    @Autowired
    private com.github.app.dify.system.config.DocumentReaderConfig documentReaderConfig;
    
    /**
     * 异步向量化文档
     */
    @Async
    @Override
    public void vectorizeDocumentAsync(Long documentId, MultipartFile file) {
        Optional<DocumentReader> docOptional = documentRepository.findByIdAndDeleted(documentId, 0);
        if (!docOptional.isPresent()) {
            logger.error("文档不存在，无法向量化 - 文档ID: {}", documentId);
            return;
        }
        
        DocumentReader doc = docOptional.get();
        
        try {
            logger.info("开始向量化文档 - 文档ID: {}", documentId);
            
            // 更新状态为向量化中（1）
            doc.setVectorizedStatus(1);
            doc.setVectorizedError(null);
            doc.setUpdateTime(new java.util.Date());
            documentRepository.save(doc);
            logger.info("已更新状态为向量化中 - 文档ID: {}", documentId);
            
            // 1. 使用LangChain4j加载文档
            logger.info("开始加载文档 - 文档ID: {}, 文件名: {}", documentId, file.getOriginalFilename());
            Document document;
            try {
                document = tikaDocumentLoader.load(file);
            } catch (RuntimeException e) {
                logger.error("文档加载失败 - 文档ID: {}, 错误: {}", documentId, e.getMessage());
                doc.setVectorizedStatus(3);
                doc.setVectorizedError(e.getMessage());
                doc.setUpdateTime(new java.util.Date());
                documentRepository.save(doc);
                return;
            }
            
            if (document.text() == null || document.text().trim().isEmpty()) {
                logger.warn("文档内容为空，跳过向量化 - 文档ID: {}", documentId);
                doc.setVectorizedStatus(3);
                doc.setVectorizedError("文档内容为空");
                doc.setUpdateTime(new java.util.Date());
                documentRepository.save(doc);
                return;
            }
            logger.info("文档加载完成，文本长度: {} - 文档ID: {}", document.text().length(), documentId);
            
            // 2. 添加documentId到metadata
            document.metadata().put("documentId", String.valueOf(documentId));
            
            // 3. 使用LangChain4j分割文档
            logger.info("开始分割文档 - 文档ID: {}, 文本长度: {}", documentId, document.text().length());
            long splitStartTime = System.currentTimeMillis();
            List<TextSegment> segments;
            try {
                segments = documentSplitter.split(document);
                long splitDuration = System.currentTimeMillis() - splitStartTime;
                logger.info("文档分割完成，耗时: {} 毫秒 - 文档ID: {}", splitDuration, documentId);
            } catch (Exception e) {
                logger.error("文档分割失败 - 文档ID: {}", documentId, e);
                throw new RuntimeException("文档分割失败: " + e.getMessage(), e);
            }
            
            if (segments.isEmpty()) {
                logger.warn("文档分割为空，跳过向量化 - 文档ID: {}", documentId);
                doc.setVectorizedStatus(3);
                doc.setVectorizedError("文档分割为空");
                doc.setUpdateTime(new java.util.Date());
                documentRepository.save(doc);
                return;
            }
            logger.info("文档分割完成，segment数量: {} - 文档ID: {}", segments.size(), documentId);
            
            // 4. 为每个segment添加documentId和chunkIndex
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                segment.metadata().put("documentId", String.valueOf(documentId));
                segment.metadata().put("chunkIndex", String.valueOf(i));
            }
            
            // 5. 创建文档解读专用的EmbeddingStore
            logger.info("创建EmbeddingStore - 文档ID: {}", documentId);
            EmbeddingStore<TextSegment> embeddingStore = createDocumentReaderEmbeddingStore();
            logger.info("EmbeddingStore创建成功 - 文档ID: {}", documentId);
            
            // 6. 向量化segments
            logger.info("开始向量化segments，数量: {} - 文档ID: {}", segments.size(), documentId);
            List<dev.langchain4j.data.embedding.Embedding> embeddings;
            try {
                // 使用文档解读配置的embedding模型ID，确保与检索时使用相同的模型
                Long embeddingModelId = documentReaderConfig.getDefaultEmbeddingModelId();
                if (embeddingModelId != null) {
                    logger.info("使用配置的embedding模型ID: {} - 文档ID: {}", embeddingModelId, documentId);
                    // 使用EmbeddingService来向量化，确保与检索时使用相同的模型
                    List<List<Float>> embeddingVectors = new ArrayList<>();
                    for (TextSegment segment : segments) {
                        List<Float> vector = embeddingService.embed(segment.text(), embeddingModelId);
                        embeddingVectors.add(vector);
                    }
                    // 转换为Embedding对象
                    embeddings = embeddingVectors.stream()
                            .map(vector -> {
                                float[] array = new float[vector.size()];
                                for (int i = 0; i < vector.size(); i++) {
                                    array[i] = vector.get(i);
                                }
                                return dev.langchain4j.data.embedding.Embedding.from(array);
                            })
                            .collect(java.util.stream.Collectors.toList());
                } else {
                    // 如果没有配置，使用默认的CustomEmbeddingModel
                    logger.warn("未配置embedding模型ID，使用默认CustomEmbeddingModel - 文档ID: {}", documentId);
                    embeddings = embeddingModel.embedAll(segments).content();
                }
                logger.info("向量化完成，embedding数量: {} - 文档ID: {}", embeddings.size(), documentId);
            } catch (Exception e) {
                logger.error("向量化API调用失败 - 文档ID: {}", documentId, e);
                throw new RuntimeException("向量化API调用失败: " + e.getMessage(), e);
            }
            
            // 7. 批量存储到EmbeddingStore
            logger.info("开始存储向量，数量: {} - 文档ID: {}", embeddings.size(), documentId);
            try {
                embeddingStore.addAll(embeddings, segments);
                logger.info("向量存储完成 - 文档ID: {}", documentId);
            } catch (Exception e) {
                logger.error("存储向量失败 - 文档ID: {}", documentId, e);
                throw new RuntimeException("存储向量失败: " + e.getMessage(), e);
            }
            
            // 更新状态为成功（2）
            docOptional = documentRepository.findByIdAndDeleted(documentId, 0);
            if (!docOptional.isPresent()) {
                logger.error("文档不存在，无法更新状态 - 文档ID: {}", documentId);
                return;
            }
            doc = docOptional.get();
            doc.setVectorizedStatus(2);
            doc.setVectorizedError(null);
            doc.setUpdateTime(new java.util.Date());
            documentRepository.save(doc);
            
            logger.info("文档向量化完成 - 文档ID: {}, segment数量: {}", documentId, segments.size());
            
        } catch (Exception e) {
            logger.error("文档向量化失败 - 文档ID: {}", documentId, e);
            
            // 更新状态为失败（3）
            try {
                docOptional = documentRepository.findByIdAndDeleted(documentId, 0);
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
                    logger.info("已更新向量化状态为失败 - 文档ID: {}, 错误: {}", documentId, errorMsg);
                }
            } catch (Exception updateException) {
                logger.error("更新向量化失败状态时出错 - 文档ID: {}", documentId, updateException);
            }
        }
    }
    
    /**
     * 创建文档解读专用的EmbeddingStore
     * 使用固定的集合名称，所有文档解读的向量存储在同一个集合中
     * 向量库类型从DocumentReaderConfig读取
     */
    private EmbeddingStore<TextSegment> createDocumentReaderEmbeddingStore() {
        // 使用一个虚拟的知识库ID（0）来标识文档解读集合
        // 向量库类型从DocumentReaderConfig读取
        return vectorStoreFactory.createDocumentReaderEmbeddingStore();
    }
    
    /**
     * 重新索引文档
     */
    @Async
    @Override
    public void reindexDocument(Long documentId) {
        Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
        if (!optional.isPresent()) {
            logger.error("文档不存在，无法重新向量化 - 文档ID: {}", documentId);
            throw new RuntimeException("文档不存在: " + documentId);
        }
        
        DocumentReader document = optional.get();
        
        try {
            // 更新状态为向量化中（1）
            document.setVectorizedStatus(1);
            document.setVectorizedError(null);
            document.setUpdateTime(new java.util.Date());
            documentRepository.save(document);
            
            // 删除旧向量
            deleteDocumentVectors(documentId);
            
            // 从MinIO下载文件
            if (fileStorageService == null) {
                throw new RuntimeException("FileStorageService未配置");
            }
            
            InputStream inputStream = fileStorageService.downloadFile(document.getFilePath());
            byte[] fileBytes = readAllBytes(inputStream);
            inputStream.close();
            
            MultipartFile multipartFile = new InputStreamMultipartFile(
                    fileBytes,
                    document.getOriginalFileName(),
                    document.getMimeType()
            );
            
            // 重新向量化
            vectorizeDocumentAsync(documentId, multipartFile);
            
        } catch (Exception e) {
            logger.error("重新向量化文档失败 - 文档ID: {}", documentId, e);
            
            // 更新状态为失败（3）
            try {
                optional = documentRepository.findByIdAndDeleted(documentId, 0);
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
                }
            } catch (Exception updateException) {
                logger.error("更新向量化失败状态时出错 - 文档ID: {}", documentId, updateException);
            }
            throw new RuntimeException("重新向量化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除文档的所有向量
     */
    @Override
    public void deleteDocumentVectors(Long documentId) {
        try {
            logger.info("开始删除文档向量 - 文档ID: {}", documentId);
            
            // 使用VectorStoreService删除文档向量
            // 注意：这里使用0L作为知识库ID，实际存储时使用固定的集合名称
            vectorStoreService.deleteDocumentVectors(0L, documentId);
            
            logger.info("文档向量删除完成 - 文档ID: {}", documentId);
        } catch (Exception e) {
            logger.error("删除文档向量失败 - 文档ID: {}", documentId, e);
            throw new RuntimeException("删除文档向量失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 读取InputStream的所有字节
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
        
        @Override
        public byte[] getBytes() throws IOException {
            return content != null ? content : new byte[0];
        }
        
        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content != null ? content : new byte[0]);
        }
        
        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException("transferTo not supported");
        }
    }
}

