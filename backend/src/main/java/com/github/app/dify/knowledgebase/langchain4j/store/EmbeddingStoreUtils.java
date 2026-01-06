package com.github.app.dify.knowledgebase.langchain4j.store;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for EmbeddingStore operations
 * Provides common conversion and extraction methods
 */
public class EmbeddingStoreUtils {
    
    /**
     * Extract documentId from TextSegment metadata
     * 
     * @param textSegment the text segment containing metadata
     * @param logger logger for warning messages
     * @return documentId as Long, or null if not found
     */
    public static Long extractDocumentId(TextSegment textSegment, Logger logger) {
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
                if (logger != null) {
                    logger.warn("Failed to parse documentId: {}", docIdObj);
                }
            }
        }
        return null;
    }
    
    /**
     * Extract chunkIndex from TextSegment metadata
     * 
     * @param textSegment the text segment containing metadata
     * @param logger logger for warning messages
     * @return chunkIndex as Integer, or null if not found
     */
    public static Integer extractChunkIndex(TextSegment textSegment, Logger logger) {
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
                if (logger != null) {
                    logger.warn("Failed to parse chunkIndex: {}", chunkIndexObj);
                }
            }
        }
        return null;
    }
    
    /**
     * Generate a unique ID from documentId and chunkIndex
     * 
     * @param documentId the document ID
     * @param chunkIndex the chunk index
     * @return generated ID string
     */
    public static String generateId(Long documentId, Integer chunkIndex) {
        return documentId + "_" + chunkIndex;
    }
    
    /**
     * Convert Embedding to List<Float>
     * Embedding.vectorAsList() returns List<Double>, needs conversion to List<Float>
     * 
     * @param embedding the embedding to convert
     * @return List of Float values
     * @throws IllegalArgumentException if embedding vector is empty
     */
    public static List<Float> convertEmbeddingToFloatList(Embedding embedding) {
        List<? extends Number> vectorList = embedding.vectorAsList();
        if (vectorList == null || vectorList.isEmpty()) {
            throw new IllegalArgumentException("Embedding vector is empty");
        }
        List<Float> floatList = new ArrayList<>();
        for (Number num : vectorList) {
            floatList.add(num.floatValue());
        }
        return floatList;
    }
    
    /**
     * Convert List<Float> to float[]
     * 
     * @param floatList the list of floats to convert
     * @return float array
     */
    public static float[] convertToFloatArray(List<Float> floatList) {
        float[] array = new float[floatList.size()];
        for (int i = 0; i < floatList.size(); i++) {
            array[i] = floatList.get(i);
        }
        return array;
    }
}
