package com.github.app.dify.documentreader.service.impl;

import com.github.app.dify.documentreader.domain.DocumentReader;
import com.github.app.dify.documentreader.repository.DocumentReaderRepository;
import com.github.app.dify.documentreader.service.DocumentReaderRetrievalService;
import com.github.app.dify.knowledgebase.langchain4j.CustomEmbeddingModel;
import com.github.app.dify.knowledgebase.langchain4j.VectorStoreFactory;
import com.github.app.dify.knowledgebase.service.EmbeddingService;
import com.github.app.dify.system.config.RagConfig;
import com.github.app.dify.system.config.DocumentReaderConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文档解读RAG检索服务实现
 */
@Service
public class DocumentReaderRetrievalServiceImpl implements DocumentReaderRetrievalService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentReaderRetrievalServiceImpl.class);
    
    @Autowired
    private CustomEmbeddingModel embeddingModel;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private VectorStoreFactory vectorStoreFactory;
    
    @Autowired
    private RagConfig ragConfig;
    
    @Autowired
    private DocumentReaderConfig documentReaderConfig;
    
    @Autowired
    private DocumentReaderRepository documentRepository;
    
    /**
     * 检索相关文档chunks
     */
    @Override
    public List<RetrievalResult> retrieve(Long documentId, String query) {
        return retrieve(documentId, query, null, null);
    }
    
    /**
     * 检索相关文档chunks（指定向量化模型ID）
     */
    @Override
    public List<RetrievalResult> retrieve(Long documentId, String query, Long embeddingModelId) {
        return retrieve(documentId, query, embeddingModelId, null);
    }
    
    /**
     * 检索相关文档chunks（指定向量化模型ID和topK）
     */
    @Override
    public List<RetrievalResult> retrieve(Long documentId, String query, Long embeddingModelId, Integer topK) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("查询问题不能为空");
        }
        
        // 验证文档是否存在且已向量化
        Optional<DocumentReader> docOptional = documentRepository.findByIdAndDeleted(documentId, 0);
        if (!docOptional.isPresent()) {
            throw new RuntimeException("文档不存在: " + documentId);
        }
        
        DocumentReader document = docOptional.get();
        if (document.getVectorizedStatus() == null || document.getVectorizedStatus() != 2) {
            throw new RuntimeException("文档尚未完成向量化，无法进行检索。文档ID: " + documentId + ", 向量化状态: " + document.getVectorizedStatus());
        }
        
        try {
            // 1. 创建文档解读专用的EmbeddingStore（使用配置的向量库）
            EmbeddingStore<TextSegment> embeddingStore = vectorStoreFactory.createDocumentReaderEmbeddingStore();
            
            // 2. 将查询文本转换为Embedding
            Embedding queryEmbedding;
            Long effectiveEmbeddingModelId = embeddingModelId != null ? embeddingModelId : documentReaderConfig.getDefaultEmbeddingModelId();
            if (effectiveEmbeddingModelId != null) {
                List<Float> embeddingVector = embeddingService.embed(query, effectiveEmbeddingModelId);
                queryEmbedding = Embedding.from(convertToFloatArray(embeddingVector));
            } else {
                queryEmbedding = embeddingModel.embed(query).content();
            }
            
            // 3. 确定使用的topK值：优先使用参数，其次使用文档解读配置，最后使用全局配置
            int effectiveTopK = (topK != null && topK > 0) ? topK : 
                    (documentReaderConfig.getTopK() != null ? documentReaderConfig.getTopK() : ragConfig.getTopK());
            
            // 4. 检索向量
            int maxResults = effectiveTopK * 3;
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .build();
            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();
            
            logger.info("文档检索原始结果 - 文档ID: {}, 查询: {}, 原始结果数量: {}, 配置topK: {}", 
                    documentId, query, matches.size(), effectiveTopK);
            
            // 5. 转换为RetrievalResult，并过滤出指定文档的结果
            List<RetrievalResult> allResults = matches.stream()
                    .map(match -> {
                        RetrievalResult result = new RetrievalResult();
                        TextSegment segment = match.embedded();
                        result.setText(segment.text());
                        result.setScore(match.score());
                        
                        // 从metadata中提取信息
                        Object docIdObj = segment.metadata().toMap().get("documentId");
                        if (docIdObj != null) {
                            if (docIdObj instanceof Long) {
                                result.setDocumentId((Long) docIdObj);
                            } else if (docIdObj instanceof String) {
                                try {
                                    result.setDocumentId(Long.parseLong((String) docIdObj));
                                } catch (NumberFormatException e) {
                                    logger.warn("无法解析documentId: {}", docIdObj);
                                }
                            } else if (docIdObj instanceof Number) {
                                result.setDocumentId(((Number) docIdObj).longValue());
                            }
                        }
                        
                        Object chunkIndexObj = segment.metadata().toMap().get("chunkIndex");
                        if (chunkIndexObj != null) {
                            if (chunkIndexObj instanceof Integer) {
                                result.setChunkIndex((Integer) chunkIndexObj);
                            } else if (chunkIndexObj instanceof String) {
                                try {
                                    result.setChunkIndex(Integer.parseInt((String) chunkIndexObj));
                                } catch (NumberFormatException e) {
                                    logger.warn("无法解析chunkIndex: {}", chunkIndexObj);
                                }
                            } else if (chunkIndexObj instanceof Number) {
                                result.setChunkIndex(((Number) chunkIndexObj).intValue());
                            }
                        }
                        
                        return result;
                    })
                    .collect(Collectors.toList());
            
            logger.info("文档检索原始匹配结果 - 文档ID: {}, 查询: {}, 总匹配数: {}, 分数范围: {} - {}", 
                    documentId, query, allResults.size(),
                    allResults.stream().mapToDouble(RetrievalResult::getScore).max().orElse(0.0),
                    allResults.stream().mapToDouble(RetrievalResult::getScore).min().orElse(0.0));
            
            // 过滤出指定文档的结果
            List<RetrievalResult> documentResults = allResults.stream()
                    .filter(r -> r.getDocumentId() != null && r.getDocumentId().equals(documentId))
                    .collect(Collectors.toList());
            
            logger.info("文档检索文档过滤后 - 文档ID: {}, 查询: {}, 匹配文档的结果数: {}, 分数范围: {} - {}", 
                    documentId, query, documentResults.size(),
                    documentResults.stream().mapToDouble(RetrievalResult::getScore).max().orElse(0.0),
                    documentResults.stream().mapToDouble(RetrievalResult::getScore).min().orElse(0.0));
            
            // 应用相似度阈值
            double similarityThreshold = ragConfig.getSimilarityThreshold();
            List<RetrievalResult> thresholdResults = documentResults.stream()
                    .filter(r -> r.getScore() >= similarityThreshold)
                    .collect(Collectors.toList());
            
            logger.info("文档检索相似度过滤后 - 文档ID: {}, 查询: {}, 阈值: {}, 通过阈值的结果数: {}", 
                    documentId, query, similarityThreshold, thresholdResults.size());
            
            // 限制返回数量
            List<RetrievalResult> results = thresholdResults.stream()
                    .limit(effectiveTopK)
                    .collect(Collectors.toList());
            
            if (results.isEmpty()) {
                logger.warn("文档检索结果为空 - 文档ID: {}, 查询: {}, 原始匹配数: {}, 文档匹配数: {}, 阈值过滤后: {}, 相似度阈值: {}", 
                        documentId, query, allResults.size(), documentResults.size(), thresholdResults.size(), similarityThreshold);
                // 如果因为阈值过高导致结果为空，返回前几个结果（即使低于阈值）以便调试
                if (!documentResults.isEmpty() && thresholdResults.isEmpty()) {
                    logger.warn("相似度阈值 {} 过高，导致所有结果被过滤。返回前 {} 个结果（即使低于阈值）以便调试", 
                            similarityThreshold, Math.min(effectiveTopK, documentResults.size()));
                    results = documentResults.stream()
                            .limit(effectiveTopK)
                            .collect(Collectors.toList());
                }
            } else {
                logger.info("文档检索完成 - 文档ID: {}, 查询: {}, 最终结果数量: {}, 最高分数: {}, 最低分数: {}", 
                        documentId, query, results.size(), 
                        results.stream().mapToDouble(RetrievalResult::getScore).max().orElse(0.0),
                        results.stream().mapToDouble(RetrievalResult::getScore).min().orElse(0.0));
            }
            
            return results;
            
        } catch (RuntimeException e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("集合不存在") || 
                    errorMessage.contains("向量检索失败") || 
                    errorMessage.contains("400 Bad Request"))) {
                logger.warn("文档检索失败（集合可能不存在或为空）- 文档ID: {}, 查询: {}, 返回空结果", 
                        documentId, query);
                return new ArrayList<>();
            }
            logger.error("文档检索失败 - 文档ID: {}, 查询: {}", documentId, query, e);
            throw new RuntimeException("文档检索失败: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("文档检索失败 - 文档ID: {}, 查询: {}", documentId, query, e);
            throw new RuntimeException("文档检索失败: " + e.getMessage(), e);
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

