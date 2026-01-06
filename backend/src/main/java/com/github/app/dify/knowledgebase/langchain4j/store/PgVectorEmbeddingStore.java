package com.github.app.dify.knowledgebase.langchain4j.store;

import com.github.app.dify.knowledgebase.service.VectorStoreStrategy;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PgVector EmbeddingStore实现，适配PgVector存储
 * 支持按知识库ID隔离存储（每个知识库一个表）
 * 注意：此类通过工厂方法创建，不使用Spring管理
 */
public class PgVectorEmbeddingStore implements EmbeddingStore<TextSegment> {
    
    private static final Logger logger = LoggerFactory.getLogger(PgVectorEmbeddingStore.class);
    
    private VectorStoreStrategy strategy;

    /**
     * -- SETTER --
     *  设置知识库ID（用于隔离存储）
     */
    @Setter
    private Long knowledgeBaseId;

    /**
     * 创建指定知识库的EmbeddingStore实例
     */
    public static PgVectorEmbeddingStore forKnowledgeBase(Long knowledgeBaseId, VectorStoreStrategy strategy) {
        PgVectorEmbeddingStore store = new PgVectorEmbeddingStore();
        store.strategy = strategy;
        store.knowledgeBaseId = knowledgeBaseId;
        return store;
    }
    
    @Override
    public String add(Embedding embedding) {
        throw new UnsupportedOperationException("请使用add(String, Embedding, TextSegment)方法");
    }
    
    @Override
    public void add(String id, Embedding embedding) {
        throw new UnsupportedOperationException("请使用add(String, Embedding, TextSegment)方法");
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
        
        // 确保集合存在
        strategy.ensureCollection(knowledgeBaseId, vector.size());
        
        // 存储向量
        List<List<Float>> vectors = new ArrayList<>();
        vectors.add(vector);
        List<String> texts = new ArrayList<>();
        texts.add(textSegment.text());
        List<Integer> chunkIndices = new ArrayList<>();
        chunkIndices.add(chunkIndex != null ? chunkIndex : 0);
        
        strategy.upsertVectors(knowledgeBaseId, documentId, vectors, texts, chunkIndices);
        
        return String.valueOf(documentId);
    }
    
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        throw new UnsupportedOperationException("请使用addAll(List<Embedding>, List<TextSegment>)方法");
    }
    
    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (embeddings == null || embeddings.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (textSegments == null || textSegments.size() != embeddings.size()) {
            throw new IllegalArgumentException("embeddings和textSegments的数量必须相等");
        }
        
        // 从第一个embedding获取向量维度
        int vectorSize = embeddings.get(0).dimension();
        
        // 确保集合存在
        strategy.ensureCollection(knowledgeBaseId, vectorSize);
        
        // 按documentId分组
        java.util.Map<Long, List<VectorData>> vectorsByDocument = new java.util.HashMap<>();
        for (int i = 0; i < embeddings.size(); i++) {
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = textSegments.get(i);
            
            Long documentId = extractDocumentId(textSegment);
            Integer chunkIndex = extractChunkIndex(textSegment);
            
            if (documentId == null) {
                throw new IllegalArgumentException("TextSegment metadata中必须包含documentId");
            }
            
            vectorsByDocument.computeIfAbsent(documentId, k -> new ArrayList<>()).add(
                new VectorData(convertEmbeddingToFloatList(embedding), textSegment.text(), 
                              chunkIndex != null ? chunkIndex : i));
        }
        
        // 按文档批量存储
        for (java.util.Map.Entry<Long, List<VectorData>> entry : vectorsByDocument.entrySet()) {
            Long documentId = entry.getKey();
            List<VectorData> vectorDataList = entry.getValue();
            
            List<List<Float>> vectors = vectorDataList.stream()
                    .map(vd -> vd.vector)
                    .collect(Collectors.toList());
            List<String> texts = vectorDataList.stream()
                    .map(vd -> vd.text)
                    .collect(Collectors.toList());
            List<Integer> chunkIndices = vectorDataList.stream()
                    .map(vd -> vd.chunkIndex)
                    .collect(Collectors.toList());
            
            strategy.upsertVectors(knowledgeBaseId, documentId, vectors, texts, chunkIndices);
        }
        
        return vectorsByDocument.keySet().stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
    }
    
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        if (request == null || request.queryEmbedding() == null) {
            return new EmbeddingSearchResult<>(new ArrayList<>());
        }
        
        // 转换为List<Float>
        List<Float> queryVector = convertEmbeddingToFloatList(request.queryEmbedding());
        
        // 搜索
        int maxResults = request.maxResults();
        List<VectorStoreStrategy.SearchResult> searchResults = 
                strategy.searchVectors(knowledgeBaseId, queryVector, maxResults);
        
        // 转换为EmbeddingMatch
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (VectorStoreStrategy.SearchResult result : searchResults) {
            TextSegment segment = TextSegment.from(result.getText());
            if (result.getDocumentId() != null) {
                segment.metadata().put("documentId", String.valueOf(result.getDocumentId()));
            }
            if (result.getChunkIndex() != null) {
                segment.metadata().put("chunkIndex", String.valueOf(result.getChunkIndex()));
            }
            
            EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(
                    result.getScore(),
                    String.valueOf(result.getDocumentId()),
                    null, // embedding可以为null
                    segment
            );
            matches.add(match);
        }
        
        return new EmbeddingSearchResult<>(matches);
    }
    
    @Override
    public void remove(String id) {
        // PgVector使用documentId作为标识，这里需要解析id
        try {
            Long documentId = Long.parseLong(id);
            strategy.deleteDocumentVectors(knowledgeBaseId, documentId);
        } catch (NumberFormatException e) {
            logger.warn("无法解析文档ID: {}", id, e);
        }
    }
    
    @Override
    public void removeAll(java.util.Collection<String> ids) {
        for (String id : ids) {
            remove(id);
        }
    }
    
    @Override
    public void removeAll() {
        throw new UnsupportedOperationException("PgVector不支持删除所有向量，请通过文档ID删除");
    }
    
    /**
     * 从TextSegment的metadata中提取documentId
     */
    private Long extractDocumentId(TextSegment segment) {
        if (segment.metadata() == null) {
            return null;
        }
        Object docIdObj = segment.metadata().toMap().get("documentId");
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
                logger.warn("无法解析documentId: {}", docIdObj, e);
            }
        }
        return null;
    }
    
    /**
     * 从TextSegment的metadata中提取chunkIndex
     */
    private Integer extractChunkIndex(TextSegment segment) {
        if (segment.metadata() == null) {
            return null;
        }
        Object chunkIndexObj = segment.metadata().toMap().get("chunkIndex");
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
                logger.warn("无法解析chunkIndex: {}", chunkIndexObj, e);
            }
        }
        return null;
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
     * 向量数据内部类
     */
    private static class VectorData {
        List<Float> vector;
        String text;
        Integer chunkIndex;
        
        VectorData(List<Float> vector, String text, Integer chunkIndex) {
            this.vector = vector;
            this.text = text;
            this.chunkIndex = chunkIndex;
        }
    }
}

