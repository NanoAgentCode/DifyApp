package com.github.app.dify.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.config.FaissConfig;
import com.github.app.dify.service.VectorStoreStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * FAISS向量存储服务
 * 使用基于文件的向量存储，支持向量插入、检索和删除
 */
@Service
public class FaissVectorStoreStrategy implements VectorStoreStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(FaissVectorStoreStrategy.class);
    
    @Override
    public String getType() {
        return "faiss";
    }
    
    @Autowired
    private FaissConfig faissConfig;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // 使用读写锁保证线程安全
    private final Map<Long, ReadWriteLock> indexLocks = new HashMap<>();
    private final Map<Long, VectorIndex> indexes = new HashMap<>();
    
    /**
     * 确保索引存在
     */
    @Override
    public void ensureCollection(Long knowledgeBaseId, int vectorSize) {
        synchronized (indexLocks) {
            indexLocks.putIfAbsent(knowledgeBaseId, new ReentrantReadWriteLock());
        }
        
        ReadWriteLock lock = indexLocks.get(knowledgeBaseId);
        lock.writeLock().lock();
        try {
            VectorIndex index = indexes.get(knowledgeBaseId);
            if (index == null) {
                // 尝试从文件加载
                index = loadIndexFromFile(knowledgeBaseId, vectorSize);
                if (index == null) {
                    // 创建新索引
                    index = new VectorIndex(knowledgeBaseId, vectorSize);
                    logger.info("创建新的FAISS索引 - 知识库ID: {}, 向量维度: {}", knowledgeBaseId, vectorSize);
                } else {
                    logger.info("从文件加载FAISS索引 - 知识库ID: {}, 向量维度: {}, 向量数量: {}", 
                            knowledgeBaseId, vectorSize, index.getVectorCount());
                }
                indexes.put(knowledgeBaseId, index);
            } else {
                // 检查向量维度是否匹配
                if (index.getVectorSize() != vectorSize) {
                    throw new IllegalArgumentException(
                            String.format("向量维度不匹配 - 知识库ID: %d, 期望: %d, 实际: %d", 
                                    knowledgeBaseId, vectorSize, index.getVectorSize()));
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 批量插入/更新向量
     */
    @Override
    public void upsertVectors(Long knowledgeBaseId, Long documentId, 
                              List<List<Float>> vectors, List<String> texts, 
                              List<Integer> chunkIndices) {
        if (vectors == null || vectors.isEmpty()) {
            logger.warn("向量列表为空，跳过插入 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            return;
        }
        
        ReadWriteLock lock = getLock(knowledgeBaseId);
        lock.writeLock().lock();
        try {
            VectorIndex index = indexes.get(knowledgeBaseId);
            if (index == null) {
                int vectorSize = vectors.get(0).size();
                ensureCollection(knowledgeBaseId, vectorSize);
                index = indexes.get(knowledgeBaseId);
            }
            
            // 先删除该文档的旧向量
            index.deleteByDocumentId(documentId);
            
            // 插入新向量
            for (int i = 0; i < vectors.size(); i++) {
                List<Float> vector = vectors.get(i);
                String text = i < texts.size() ? texts.get(i) : "";
                int chunkIndex = i < chunkIndices.size() ? chunkIndices.get(i) : i;
                
                String vectorId = generateVectorId(knowledgeBaseId, documentId, chunkIndex);
                VectorEntry entry = new VectorEntry(vectorId, vector, text, documentId, chunkIndex, knowledgeBaseId);
                index.addVector(entry);
            }
            
            // 持久化到文件
            saveIndexToFile(knowledgeBaseId, index);
            
            logger.info("FAISS向量插入完成 - 知识库ID: {}, 文档ID: {}, 向量数量: {}", 
                    knowledgeBaseId, documentId, vectors.size());
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 向量检索
     */
    @Override
    public List<VectorStoreStrategy.SearchResult> searchVectors(Long knowledgeBaseId, List<Float> queryVector, int topK) {
        ReadWriteLock lock = getLock(knowledgeBaseId);
        lock.readLock().lock();
        try {
            VectorIndex index = indexes.get(knowledgeBaseId);
            if (index == null || index.getVectorCount() == 0) {
                logger.warn("FAISS索引不存在或为空 - 知识库ID: {}, 返回空结果", knowledgeBaseId);
                return new ArrayList<>();
            }
            
            // 检查向量维度
            if (queryVector.size() != index.getVectorSize()) {
                logger.error("查询向量维度不匹配 - 知识库ID: {}, 期望: {}, 实际: {}", 
                        knowledgeBaseId, index.getVectorSize(), queryVector.size());
                return new ArrayList<>();
            }
            
            // 计算相似度并排序
            List<VectorScore> scores = new ArrayList<>();
            for (VectorEntry entry : index.getAllVectors()) {
                double score = cosineSimilarity(queryVector, entry.getVector());
                scores.add(new VectorScore(entry, score));
            }
            
            // 按相似度降序排序，取topK
            scores.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            int resultCount = Math.min(topK, scores.size());
            
            List<VectorStoreStrategy.SearchResult> results = new ArrayList<>();
            for (int i = 0; i < resultCount; i++) {
                VectorScore vs = scores.get(i);
                VectorEntry entry = vs.getEntry();
                VectorStoreStrategy.SearchResult result = new VectorStoreStrategy.SearchResult();
                result.setScore(vs.getScore());
                result.setText(entry.getText());
                result.setDocumentId(entry.getDocumentId());
                result.setChunkIndex(entry.getChunkIndex());
                results.add(result);
            }
            
            logger.debug("FAISS向量检索完成 - 知识库ID: {}, topK: {}, 结果数量: {}", 
                    knowledgeBaseId, topK, results.size());
            
            return results;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 删除文档的所有向量
     */
    @Override
    public void deleteDocumentVectors(Long knowledgeBaseId, Long documentId) {
        ReadWriteLock lock = getLock(knowledgeBaseId);
        lock.writeLock().lock();
        try {
            VectorIndex index = indexes.get(knowledgeBaseId);
            if (index == null) {
                logger.info("FAISS索引不存在，跳过删除操作 - 知识库ID: {}, 文档ID: {}", 
                        knowledgeBaseId, documentId);
                return;
            }
            
            int deletedCount = index.deleteByDocumentId(documentId);
            if (deletedCount > 0) {
                // 持久化到文件
                saveIndexToFile(knowledgeBaseId, index);
                logger.info("删除文档向量成功 - 知识库ID: {}, 文档ID: {}, 删除数量: {}", 
                        knowledgeBaseId, documentId, deletedCount);
            } else {
                logger.debug("未找到需要删除的向量 - 知识库ID: {}, 文档ID: {}", 
                        knowledgeBaseId, documentId);
            }
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取锁
     */
    private ReadWriteLock getLock(Long knowledgeBaseId) {
        synchronized (indexLocks) {
            return indexLocks.computeIfAbsent(knowledgeBaseId, k -> new ReentrantReadWriteLock());
        }
    }
    
    /**
     * 从文件加载索引
     */
    private VectorIndex loadIndexFromFile(Long knowledgeBaseId, int vectorSize) {
        try {
            String metadataPath = faissConfig.getMetadataFilePath(knowledgeBaseId);
            File metadataFile = new File(metadataPath);
            if (!metadataFile.exists()) {
                return null;
            }
            
            String json = new String(Files.readAllBytes(Paths.get(metadataPath)));
            List<VectorEntry> entries = objectMapper.readValue(json, new TypeReference<List<VectorEntry>>() {});
            
            VectorIndex index = new VectorIndex(knowledgeBaseId, vectorSize);
            for (VectorEntry entry : entries) {
                index.addVector(entry);
            }
            
            return index;
        } catch (Exception e) {
            logger.warn("从文件加载FAISS索引失败 - 知识库ID: {}", knowledgeBaseId, e);
            return null;
        }
    }
    
    /**
     * 保存索引到文件
     */
    private void saveIndexToFile(Long knowledgeBaseId, VectorIndex index) {
        try {
            String metadataPath = faissConfig.getMetadataFilePath(knowledgeBaseId);
            File metadataFile = new File(metadataPath);
            File parentDir = metadataFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            List<VectorEntry> entries = index.getAllVectors();
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(entries);
            Files.write(Paths.get(metadataPath), json.getBytes());
            
            logger.debug("保存FAISS索引到文件 - 知识库ID: {}, 向量数量: {}", 
                    knowledgeBaseId, entries.size());
        } catch (Exception e) {
            logger.error("保存FAISS索引到文件失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new RuntimeException("保存FAISS索引失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException("向量维度必须相同");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.size(); i++) {
            float ai = a.get(i);
            float bi = b.get(i);
            dotProduct += ai * bi;
            normA += ai * ai;
            normB += bi * bi;
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * 生成向量ID
     */
    private String generateVectorId(Long knowledgeBaseId, Long documentId, int chunkIndex) {
        return knowledgeBaseId + "_" + documentId + "_" + chunkIndex;
    }
    
    /**
     * 检索结果
     */
    
    
    /**
     * 向量索引（内存中的索引结构）
     */
    private static class VectorIndex {
        private final Long knowledgeBaseId;
        private final int vectorSize;
        private final Map<String, VectorEntry> vectors = new HashMap<>();
        private final Map<Long, Set<String>> documentVectors = new HashMap<>();
        
        public VectorIndex(Long knowledgeBaseId, int vectorSize) {
            this.knowledgeBaseId = knowledgeBaseId;
            this.vectorSize = vectorSize;
        }
        
        public void addVector(VectorEntry entry) {
            vectors.put(entry.getVectorId(), entry);
            documentVectors.computeIfAbsent(entry.getDocumentId(), k -> new HashSet<>()).add(entry.getVectorId());
        }
        
        public int deleteByDocumentId(Long documentId) {
            Set<String> vectorIds = documentVectors.remove(documentId);
            if (vectorIds == null) {
                return 0;
            }
            int count = 0;
            for (String vectorId : vectorIds) {
                if (vectors.remove(vectorId) != null) {
                    count++;
                }
            }
            return count;
        }
        
        public List<VectorEntry> getAllVectors() {
            return new ArrayList<>(vectors.values());
        }
        
        public int getVectorCount() {
            return vectors.size();
        }
        
        public int getVectorSize() {
            return vectorSize;
        }
    }
    
    /**
     * 向量条目
     */
    private static class VectorEntry {
        private String vectorId;
        private List<Float> vector;
        private String text;
        private Long documentId;
        private Integer chunkIndex;
        private Long knowledgeBaseId;
        
        public VectorEntry() {
        }
        
        public VectorEntry(String vectorId, List<Float> vector, String text, 
                          Long documentId, Integer chunkIndex, Long knowledgeBaseId) {
            this.vectorId = vectorId;
            this.vector = vector;
            this.text = text;
            this.documentId = documentId;
            this.chunkIndex = chunkIndex;
            this.knowledgeBaseId = knowledgeBaseId;
        }
        
        public String getVectorId() {
            return vectorId;
        }
        
        public void setVectorId(String vectorId) {
            this.vectorId = vectorId;
        }
        
        public List<Float> getVector() {
            return vector;
        }
        
        public void setVector(List<Float> vector) {
            this.vector = vector;
        }
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public Long getDocumentId() {
            return documentId;
        }
        
        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }
        
        public Integer getChunkIndex() {
            return chunkIndex;
        }
        
        public void setChunkIndex(Integer chunkIndex) {
            this.chunkIndex = chunkIndex;
        }
        
        public Long getKnowledgeBaseId() {
            return knowledgeBaseId;
        }
        
        public void setKnowledgeBaseId(Long knowledgeBaseId) {
            this.knowledgeBaseId = knowledgeBaseId;
        }
    }
    
    /**
     * 向量分数（用于排序）
     */
    private static class VectorScore {
        private final VectorEntry entry;
        private final double score;
        
        public VectorScore(VectorEntry entry, double score) {
            this.entry = entry;
            this.score = score;
        }
        
        public VectorEntry getEntry() {
            return entry;
        }
        
        public double getScore() {
            return score;
        }
    }
}

