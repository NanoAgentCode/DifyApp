package com.github.app.dify.langchain4j;

import com.github.app.dify.service.VectorStoreStrategy;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
/**
 * FAISS EmbeddingStore实现，适配FAISS存储
 * 支持按知识库ID隔离存储（每个知识库一个索引文件）
 * 注意：此类通过工厂方法创建，不使用Spring管理
 */
public class FaissEmbeddingStore implements EmbeddingStore<TextSegment> {
    
    private static final Logger logger = LoggerFactory.getLogger(FaissEmbeddingStore.class);
    
    private VectorStoreStrategy strategy;
    
    private Long knowledgeBaseId;
    
    /**
     * 设置知识库ID（用于隔离存储）
     */
    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }
    
    /**
     * 创建指定知识库的EmbeddingStore实例
     */
    public static FaissEmbeddingStore forKnowledgeBase(Long knowledgeBaseId, 
                                                        VectorStoreStrategy strategy) {
        FaissEmbeddingStore store = new FaissEmbeddingStore();
        store.strategy = strategy;
        store.knowledgeBaseId = knowledgeBaseId;
        return store;
    }
    
    @Override
    public String add(Embedding embedding) {
        throw new UnsupportedOperationException("请使用add(Embedding, TextSegment)方法");
    }
    
    @Override
    public void add(String id, Embedding embedding) {
        throw new UnsupportedOperationException("请使用add(Embedding, TextSegment)方法");
    }
    
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        // 从metadata中提取documentId和chunkIndex
        Long documentId = extractDocumentId(textSegment);
        Integer chunkIndex = extractChunkIndex(textSegment);
        
        if (documentId == null) {
            throw new IllegalArgumentException("TextSegment metadata中必须包含documentId");
        }
        
        // 转换为List<Float>
        List<Float> vector = convertEmbeddingToFloatList(embedding);
        
        // 确保索引存在
        strategy.ensureCollection(knowledgeBaseId, vector.size());
        
        // 存储向量
        List<List<Float>> vectors = new ArrayList<>();
        vectors.add(vector);
        List<String> texts = new ArrayList<>();
        texts.add(textSegment.text());
        List<Integer> chunkIndices = new ArrayList<>();
        chunkIndices.add(chunkIndex != null ? chunkIndex : 0);
        
        strategy.upsertVectors(knowledgeBaseId, documentId, vectors, texts, chunkIndices);
        
        // 返回ID（使用documentId和chunkIndex组合）
        return generateId(documentId, chunkIndex != null ? chunkIndex : 0);
    }
    
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        throw new UnsupportedOperationException("请使用addAll(List<Embedding>, List<TextSegment>)方法");
    }
    
    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (embeddings.size() != textSegments.size()) {
            throw new IllegalArgumentException("embeddings和textSegments数量必须相同");
        }
        
        // 按documentId分组
        java.util.Map<Long, List<BatchItem>> grouped = new java.util.HashMap<>();
        for (int i = 0; i < embeddings.size(); i++) {
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = textSegments.get(i);
            
            Long documentId = extractDocumentId(textSegment);
            Integer chunkIndex = extractChunkIndex(textSegment);
            
            if (documentId == null) {
                throw new IllegalArgumentException("TextSegment metadata中必须包含documentId");
            }
            
            grouped.computeIfAbsent(documentId, k -> new ArrayList<>()).add(
                    new BatchItem(embedding, textSegment, chunkIndex != null ? chunkIndex : i)
            );
        }
        
        // 批量存储
        List<String> ids = new ArrayList<>();
        for (java.util.Map.Entry<Long, List<BatchItem>> entry : grouped.entrySet()) {
            Long documentId = entry.getKey();
            List<BatchItem> items = entry.getValue();
            
            List<List<Float>> vectors = new ArrayList<>();
            for (BatchItem item : items) {
                vectors.add(convertEmbeddingToFloatList(item.embedding));
            }
            List<String> texts = items.stream()
                    .map(item -> item.textSegment.text())
                    .collect(Collectors.toList());
            List<Integer> chunkIndices = items.stream()
                    .map(item -> item.chunkIndex)
                    .collect(Collectors.toList());
            
            // 确保索引存在
            if (!vectors.isEmpty()) {
                strategy.ensureCollection(knowledgeBaseId, vectors.get(0).size());
            }
            
            strategy.upsertVectors(knowledgeBaseId, documentId, vectors, texts, chunkIndices);
            
            // 生成IDs
            for (int i = 0; i < items.size(); i++) {
                ids.add(generateId(documentId, items.get(i).chunkIndex));
            }
        }
        
        return ids;
    }
    
    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
        return findRelevant(referenceEmbedding, maxResults, 0.0);
    }
    
    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        List<Float> queryVector = convertEmbeddingToFloatList(referenceEmbedding);
        
        // 检索
        List<VectorStoreStrategy.SearchResult> searchResults = 
                strategy.searchVectors(knowledgeBaseId, queryVector, maxResults);
        
        // 转换为EmbeddingMatch
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (VectorStoreStrategy.SearchResult result : searchResults) {
            if (result.getScore() >= minScore) {
                TextSegment segment = TextSegment.from(result.getText());
                if (result.getDocumentId() != null) {
                    segment.metadata().put("documentId", result.getDocumentId().toString());
                }
                if (result.getChunkIndex() != null) {
                    segment.metadata().put("chunkIndex", result.getChunkIndex().toString());
                }
                
                EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(
                        result.getScore(),
                        generateId(result.getDocumentId(), result.getChunkIndex()),
                        Embedding.from(convertToFloatArray(queryVector)), // 这里应该存储实际的embedding，简化处理
                        segment
                );
                matches.add(match);
            }
        }
        
        return matches;
    }
    
    /**
     * 删除文档的所有向量
     */
    public void deleteByDocumentId(Long documentId) {
        strategy.deleteDocumentVectors(knowledgeBaseId, documentId);
    }
    
    /**
     * 从TextSegment metadata中提取documentId
     */
    private Long extractDocumentId(TextSegment textSegment) {
        // 使用toMap方法获取metadata值
        Object docIdObj = textSegment.metadata().toMap().get("documentId");
        if (docIdObj != null) {
            try {
                if (docIdObj instanceof Long) {
                    return (Long) docIdObj;
                } else if (docIdObj instanceof String) {
                    return Long.parseLong((String) docIdObj);
                } else if (docIdObj instanceof Number) {
                    return ((Number) docIdObj).longValue();
                }
            } catch (Exception e) {
                logger.warn("无法解析documentId: {}", docIdObj);
            }
        }
        return null;
    }
    
    /**
     * 从TextSegment metadata中提取chunkIndex
     */
    private Integer extractChunkIndex(TextSegment textSegment) {
        // 使用toMap方法获取metadata值
        Object chunkIndexObj = textSegment.metadata().toMap().get("chunkIndex");
        if (chunkIndexObj != null) {
            try {
                if (chunkIndexObj instanceof Integer) {
                    return (Integer) chunkIndexObj;
                } else if (chunkIndexObj instanceof String) {
                    return Integer.parseInt((String) chunkIndexObj);
                } else if (chunkIndexObj instanceof Number) {
                    return ((Number) chunkIndexObj).intValue();
                }
            } catch (Exception e) {
                logger.warn("无法解析chunkIndex: {}", chunkIndexObj);
            }
        }
        return null;
    }
    
    /**
     * 生成ID
     */
    private String generateId(Long documentId, Integer chunkIndex) {
        return documentId + "_" + chunkIndex;
    }
    
    /**
     * 将Embedding转换为List<Float>
     */
    private List<Float> convertEmbeddingToFloatList(Embedding embedding) {
        // Embedding.vectorAsList()返回List<Double>，需要转换为List<Float>
        List<? extends Number> vectorList = embedding.vectorAsList();
        if (vectorList == null || vectorList.isEmpty()) {
            throw new IllegalArgumentException("Embedding向量为空");
        }
        List<Float> floatList = new ArrayList<>();
        for (Number num : vectorList) {
            floatList.add(num.floatValue());
        }
        return floatList;
    }
    
    /**
     * 将List<Float>转换为float[]
     */
    private float[] convertToFloatArray(List<Float> floatList) {
        float[] array = new float[floatList.size()];
        for (int i = 0; i < floatList.size(); i++) {
            array[i] = floatList.get(i);
        }
        return array;
    }
    
    /**
     * 批量项
     */
    private static class BatchItem {
        Embedding embedding;
        TextSegment textSegment;
        Integer chunkIndex;
        
        BatchItem(Embedding embedding, TextSegment textSegment, Integer chunkIndex) {
            this.embedding = embedding;
            this.textSegment = textSegment;
            this.chunkIndex = chunkIndex;
        }
    }
}