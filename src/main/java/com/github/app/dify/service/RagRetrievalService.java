package com.github.app.dify.service;

import com.github.app.dify.config.RagConfig;
import com.github.app.dify.langchain4j.CustomEmbeddingModel;
import com.github.app.dify.langchain4j.QdrantEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG检索服务（使用LangChain4j）
 */
@Service
public class RagRetrievalService {
    
    private static final Logger logger = LoggerFactory.getLogger(RagRetrievalService.class);
    
    @Autowired
    private CustomEmbeddingModel embeddingModel;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    @Autowired
    private RagConfig ragConfig;
    
    /**
     * 检索相关文档chunks（使用LangChain4j）
     */
    public List<RetrievalResult> retrieve(Long knowledgeBaseId, String query) {
        return retrieve(knowledgeBaseId, query, null, null);
    }
    
    /**
     * 检索相关文档chunks（使用LangChain4j，指定向量化模型ID）
     */
    public List<RetrievalResult> retrieve(Long knowledgeBaseId, String query, Long embeddingModelId) {
        return retrieve(knowledgeBaseId, query, embeddingModelId, null);
    }
    
    /**
     * 检索相关文档chunks（使用LangChain4j，指定向量化模型ID和topK）
     * @param knowledgeBaseId 知识库ID
     * @param query 查询文本
     * @param embeddingModelId 向量化模型ID（可选，如果为null则使用默认模型）
     * @param topK Top-K检索数量（可选，如果为null则使用全局配置）
     */
    public List<RetrievalResult> retrieve(Long knowledgeBaseId, String query, Long embeddingModelId, Integer topK) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("查询问题不能为空");
        }
        
        try {
            // 1. 创建知识库专用的EmbeddingStore
            EmbeddingStore<TextSegment> embeddingStore = QdrantEmbeddingStore.forKnowledgeBase(
                    knowledgeBaseId, vectorStoreService);
            
            // 2. 将查询文本转换为Embedding（使用指定的模型）
            Embedding queryEmbedding;
            if (embeddingModelId != null) {
                // 使用指定的模型进行向量化
                List<Float> embeddingVector = embeddingService.embed(query, embeddingModelId);
                queryEmbedding = Embedding.from(convertToFloatArray(embeddingVector));
            } else {
                // 使用默认模型
                queryEmbedding = embeddingModel.embed(query).content();
            }
            
            // 3. 确定使用的topK值：优先使用知识库配置，否则使用全局配置
            int effectiveTopK = (topK != null && topK > 0) ? topK : ragConfig.getTopK();
            
            // 直接使用EmbeddingStore检索，这样可以获取完整的相似度分数
            // 检索更多结果用于调试和过滤
            int maxResults = effectiveTopK * 3; // 检索更多结果
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
                    queryEmbedding, maxResults, 0.0); // 先不设置阈值，获取所有结果
            
            logger.info("RAG检索原始结果 - 知识库ID: {}, 查询: {}, 原始结果数量: {}, 配置topK: {} (知识库配置: {}, 全局配置: {}), 相似度阈值: {}", 
                    knowledgeBaseId, query, matches.size(), effectiveTopK, topK, ragConfig.getTopK(), ragConfig.getSimilarityThreshold());
            
            // 记录所有结果的分数（用于调试）
            if (!matches.isEmpty()) {
                List<Double> allScores = matches.stream()
                        .map(EmbeddingMatch::score)
                        .collect(Collectors.toList());
                logger.debug("RAG检索所有结果分数 - 知识库ID: {}, 查询: {}, 分数列表: {}", 
                        knowledgeBaseId, query, allScores);
            }
            
            // 4. 转换为RetrievalResult，并应用相似度阈值过滤
            List<RetrievalResult> results = matches.stream()
                    .map(match -> {
                        RetrievalResult result = new RetrievalResult();
                        TextSegment segment = match.embedded();
                        result.setText(segment.text());
                        result.setScore(match.score()); // 直接使用EmbeddingMatch的score
                        
                        // 从metadata中提取信息
                        Object docIdObj = segment.metadata("documentId");
                        if (docIdObj != null) {
                            if (docIdObj instanceof Long) {
                                result.setDocumentId((Long) docIdObj);
                            } else if (docIdObj instanceof String) {
                                result.setDocumentId(Long.parseLong((String) docIdObj));
                            } else if (docIdObj instanceof Number) {
                                result.setDocumentId(((Number) docIdObj).longValue());
                            }
                        }
                        
                        Object chunkIndexObj = segment.metadata("chunkIndex");
                        if (chunkIndexObj != null) {
                            if (chunkIndexObj instanceof Integer) {
                                result.setChunkIndex((Integer) chunkIndexObj);
                            } else if (chunkIndexObj instanceof String) {
                                result.setChunkIndex(Integer.parseInt((String) chunkIndexObj));
                            } else if (chunkIndexObj instanceof Number) {
                                result.setChunkIndex(((Number) chunkIndexObj).intValue());
                            }
                        }
                        
                        return result;
                    })
                    .filter(r -> r.getScore() >= ragConfig.getSimilarityThreshold()) // 应用相似度阈值过滤
                    .limit(effectiveTopK) // 限制返回数量（使用知识库配置或全局配置）
                    .collect(Collectors.toList());
            
            // 记录详细的检索信息
            if (results.isEmpty()) {
                if (!matches.isEmpty()) {
                    double maxScore = matches.stream().mapToDouble(EmbeddingMatch::score).max().orElse(0.0);
                    double minScore = matches.stream().mapToDouble(EmbeddingMatch::score).min().orElse(0.0);
                    logger.warn("RAG检索结果为空（被阈值过滤）- 知识库ID: {}, 查询: {}, 原始结果数: {}, " +
                            "相似度阈值: {}, 最高分数: {}, 最低分数: {}. " +
                            "建议：1) 降低相似度阈值（当前: {}）到 {} 或更低；2) 检查查询问题是否与知识库内容相关", 
                            knowledgeBaseId, query, matches.size(), ragConfig.getSimilarityThreshold(), 
                            maxScore, minScore, ragConfig.getSimilarityThreshold(), maxScore * 0.8);
                } else {
                    logger.warn("RAG检索结果为空（无原始结果）- 知识库ID: {}, 查询: {}. " +
                            "建议：1) 检查知识库是否有向量数据；2) 检查知识库是否已完成向量化", 
                            knowledgeBaseId, query);
                }
            } else {
                logger.info("RAG检索完成 - 知识库ID: {}, 查询: {}, 过滤后结果数量: {}, 最高分数: {}, 最低分数: {}", 
                        knowledgeBaseId, query, results.size(), 
                        results.stream().mapToDouble(RetrievalResult::getScore).max().orElse(0.0),
                        results.stream().mapToDouble(RetrievalResult::getScore).min().orElse(0.0));
            }
            
            return results;
            
        } catch (RuntimeException e) {
            // 检查是否是向量检索失败（集合不存在或为空）
            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("集合不存在") || 
                    errorMessage.contains("向量检索失败") || 
                    errorMessage.contains("400 Bad Request"))) {
                logger.warn("RAG检索失败（集合可能不存在或为空）- 知识库ID: {}, 查询: {}, 返回空结果", 
                        knowledgeBaseId, query);
                return new ArrayList<>();
            }
            logger.error("RAG检索失败 - 知识库ID: {}, 查询: {}", knowledgeBaseId, query, e);
            throw new RuntimeException("RAG检索失败: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("RAG检索失败 - 知识库ID: {}, 查询: {}", knowledgeBaseId, query, e);
            throw new RuntimeException("RAG检索失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检索结果
     */
    public static class RetrievalResult {
        private double score;
        private String text;
        private Long documentId;
        private Integer chunkIndex;
        
        public double getScore() {
            return score;
        }
        
        public void setScore(double score) {
            this.score = score;
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
}
