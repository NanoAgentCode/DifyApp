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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Base class for all EmbeddingStore implementations
 * Provides common functionality for vector storage operations
 */
public abstract class BaseEmbeddingStore implements EmbeddingStore<TextSegment> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected VectorStoreStrategy strategy;

    @Setter
    protected Long knowledgeBaseId;

    @Override
    public String add(Embedding embedding) {
        throw new UnsupportedOperationException("Please use add(Embedding, TextSegment) method");
    }

    @Override
    public void add(String id, Embedding embedding) {
        throw new UnsupportedOperationException("Please use add(Embedding, TextSegment) method");
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        // Extract documentId and chunkIndex from metadata
        Long documentId = EmbeddingStoreUtils.extractDocumentId(textSegment, logger);
        Integer chunkIndex = EmbeddingStoreUtils.extractChunkIndex(textSegment, logger);

        if (documentId == null) {
            throw new IllegalArgumentException("TextSegment metadata must contain documentId");
        }

        // Convert to List<Float>
        List<Float> vector = EmbeddingStoreUtils.convertEmbeddingToFloatList(embedding);

        // Ensure collection exists
        strategy.ensureCollection(knowledgeBaseId, vector.size());

        // Store vector
        List<List<Float>> vectors = new ArrayList<>();
        vectors.add(vector);
        List<String> texts = new ArrayList<>();
        texts.add(textSegment.text());
        List<Integer> chunkIndices = new ArrayList<>();
        chunkIndices.add(chunkIndex != null ? chunkIndex : 0);

        strategy.upsertVectors(knowledgeBaseId, documentId, vectors, texts, chunkIndices);

        // Return ID (combination of documentId and chunkIndex)
        return EmbeddingStoreUtils.generateId(documentId, chunkIndex != null ? chunkIndex : 0);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        throw new UnsupportedOperationException("Please use addAll(List<Embedding>, List<TextSegment>) method");
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (embeddings.size() != textSegments.size()) {
            throw new IllegalArgumentException("embeddings and textSegments must have the same size");
        }

        // Group by documentId
        Map<Long, List<BatchItem>> grouped = new HashMap<>();
        for (int i = 0; i < embeddings.size(); i++) {
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = textSegments.get(i);

            Long documentId = EmbeddingStoreUtils.extractDocumentId(textSegment, logger);
            Integer chunkIndex = EmbeddingStoreUtils.extractChunkIndex(textSegment, logger);

            if (documentId == null) {
                throw new IllegalArgumentException("TextSegment metadata must contain documentId");
            }

            grouped.computeIfAbsent(documentId, k -> new ArrayList<>()).add(
                    new BatchItem(embedding, textSegment, chunkIndex != null ? chunkIndex : i));
        }

        // Batch storage
        List<String> ids = new ArrayList<>();
        for (Map.Entry<Long, List<BatchItem>> entry : grouped.entrySet()) {
            Long documentId = entry.getKey();
            List<BatchItem> items = entry.getValue();

            List<List<Float>> vectors = new ArrayList<>();
            for (BatchItem item : items) {
                vectors.add(EmbeddingStoreUtils.convertEmbeddingToFloatList(item.embedding));
            }
            List<String> texts = items.stream()
                    .map(item -> item.textSegment.text())
                    .collect(Collectors.toList());
            List<Integer> chunkIndices = items.stream()
                    .map(item -> item.chunkIndex)
                    .collect(Collectors.toList());

            // Ensure collection exists
            if (!vectors.isEmpty()) {
                strategy.ensureCollection(knowledgeBaseId, vectors.get(0).size());
            }

            strategy.upsertVectors(knowledgeBaseId, documentId, vectors, texts, chunkIndices);

            // Generate IDs
            for (BatchItem item : items) {
                ids.add(EmbeddingStoreUtils.generateId(documentId, item.chunkIndex));
            }
        }

        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest embeddingSearchRequest) {
        Embedding referenceEmbedding = embeddingSearchRequest.queryEmbedding();
        int maxResults = embeddingSearchRequest.maxResults();
        double effectiveMinScore = embeddingSearchRequest.minScore();

        List<Float> queryVector = EmbeddingStoreUtils.convertEmbeddingToFloatList(referenceEmbedding);

        // Search
        List<VectorStoreStrategy.SearchResult> searchResults = strategy.searchVectors(knowledgeBaseId, queryVector,
                maxResults);

        return convertToSearchResult(searchResults, effectiveMinScore, queryVector);
    }

    /**
     * Perform hybrid search
     */
    public EmbeddingSearchResult<TextSegment> hybridSearch(String query, Embedding queryEmbedding, int maxResults,
            double minScore) {
        List<Float> queryVector = EmbeddingStoreUtils.convertEmbeddingToFloatList(queryEmbedding);

        List<VectorStoreStrategy.SearchResult> searchResults;
        if (strategy.isHybridSearchSupported()) {
            searchResults = strategy.hybridSearch(knowledgeBaseId, query, queryVector, maxResults);
        } else {
            searchResults = strategy.searchVectors(knowledgeBaseId, queryVector, maxResults);
        }

        return convertToSearchResult(searchResults, minScore, queryVector);
    }

    private EmbeddingSearchResult<TextSegment> convertToSearchResult(
            List<VectorStoreStrategy.SearchResult> searchResults, double minScore, List<Float> queryVector) {
        // Convert to EmbeddingMatch
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
                        EmbeddingStoreUtils.generateId(result.getDocumentId(), result.getChunkIndex()),
                        Embedding.from(EmbeddingStoreUtils.convertToFloatArray(queryVector)),
                        segment);
                matches.add(match);
            }
        }

        return new EmbeddingSearchResult<>(matches);
    }

    /**
     * Delete all vectors for a document
     */
    public void deleteByDocumentId(Long documentId) {
        strategy.deleteDocumentVectors(knowledgeBaseId, documentId);
    }

    /**
     * Batch item for storing embeddings
     */
    protected static class BatchItem {
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
