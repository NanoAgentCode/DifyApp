package com.github.app.dify.langchain4j;

import com.github.app.dify.service.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义EmbeddingModel，适配现有的向量化API
 */
@Component
public class CustomEmbeddingModel implements EmbeddingModel {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomEmbeddingModel.class);
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Override
    public Response<Embedding> embed(String text) {
        try {
            List<Float> embeddingVector = embeddingService.embed(text);
            Embedding embedding = Embedding.from(convertToFloatArray(embeddingVector));
            return Response.from(embedding);
        } catch (Exception e) {
            logger.error("向量化失败", e);
            throw new RuntimeException("向量化失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return embed(textSegment.text());
    }
    
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        try {
            List<String> texts = textSegments.stream()
                    .map(TextSegment::text)
                    .collect(Collectors.toList());
            
            List<List<Float>> embeddingVectors = embeddingService.embedBatch(texts);
            
            List<Embedding> embeddings = embeddingVectors.stream()
                    .map(vector -> Embedding.from(convertToFloatArray(vector)))
                    .collect(Collectors.toList());
            
            return Response.from(embeddings);
        } catch (Exception e) {
            logger.error("批量向量化失败", e);
            throw new RuntimeException("批量向量化失败: " + e.getMessage(), e);
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

