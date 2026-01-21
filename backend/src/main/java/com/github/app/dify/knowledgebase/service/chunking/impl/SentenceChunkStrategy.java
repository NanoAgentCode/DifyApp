package com.github.app.dify.knowledgebase.service.chunking.impl;

import com.github.app.dify.knowledgebase.service.chunking.ChunkConfig;
import com.github.app.dify.knowledgebase.service.chunking.ChunkStrategy;
import com.github.app.dify.knowledgebase.service.chunking.ContentStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 句子分块策略
 * 按句子边界分块，支持句子合并
 */
@Component
public class SentenceChunkStrategy implements ChunkStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(SentenceChunkStrategy.class);
    
    // 句子结束标点：中英文句号、问号、感叹号
    private static final Pattern SENTENCE_END = Pattern.compile("[。！？.!?]\\s*");
    
    @Override
    public String getName() {
        return "sentence";
    }
    
    @Override
    public boolean supports(String fileType, String contentType) {
        // 支持文本类型的内容
        return ContentStructure.ContentType.TEXT.equals(contentType) || contentType == null;
    }
    
    @Override
    public List<ChunkResult> chunk(String text, ChunkConfig config) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        int chunkSize = config.getChunkSize();
        int chunkOverlap = config.getChunkOverlap();
        
        // 分割句子
        List<String> sentences = splitSentences(text);
        
        // 合并句子以接近目标chunkSize
        List<String> mergedChunks = mergeSentences(sentences, chunkSize);
        
        // 生成分块结果（优化：直接使用分割时的位置信息，避免重复查找）
        List<ChunkResult> chunks = new ArrayList<>();
        int currentIndex = 0;
        int chunkIndex = 0;
        
        for (String chunk : mergedChunks) {
            // 优化：使用 trim() 后的内容，但保持原始位置
            String trimmedChunk = chunk.trim();
            int startIndex = currentIndex;
            // 跳过前导空白
            while (startIndex < text.length() && 
                   Character.isWhitespace(text.charAt(startIndex))) {
                startIndex++;
            }
            int endIndex = startIndex + trimmedChunk.length();
            
            ChunkResult result = new ChunkResult();
            result.setContent(trimmedChunk);
            result.setChunkIndex(chunkIndex);
            result.setStartIndex(startIndex);
            result.setEndIndex(endIndex);
            result.setContentType(ContentStructure.ContentType.TEXT);
            chunks.add(result);
            
            // 移动到下一个chunk的起始位置（考虑重叠）
            currentIndex = endIndex - config.getChunkOverlap();
            if (currentIndex < startIndex) {
                currentIndex = endIndex;
            }
            chunkIndex++;
        }
        
        logger.debug("句子分块完成 - 句子数: {}, chunk数量: {}", sentences.size(), chunks.size());
        
        return chunks;
    }
    
    /**
     * 分割句子
     */
    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_END.matcher(text);
        int lastEnd = 0;
        
        while (matcher.find()) {
            int end = matcher.end();
            String sentence = text.substring(lastEnd, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
            lastEnd = end;
        }
        
        // 处理最后一段（可能没有句号）
        if (lastEnd < text.length()) {
            String lastSentence = text.substring(lastEnd).trim();
            if (!lastSentence.isEmpty()) {
                sentences.add(lastSentence);
            }
        }
        
        return sentences;
    }
    
    /**
     * 合并句子以接近目标大小
     */
    private List<String> mergeSentences(List<String> sentences, int targetSize) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        
        for (String sentence : sentences) {
            if (current.length() + sentence.length() + 1 <= targetSize) {
                // 可以合并
                if (current.length() > 0) {
                    current.append(" ");
                }
                current.append(sentence);
            } else {
                // 不能合并，保存当前chunk
                if (current.length() > 0) {
                    result.add(current.toString());
                    current = new StringBuilder();
                }
                // 如果单个句子就超过目标大小，直接添加
                if (sentence.length() > targetSize) {
                    result.add(sentence);
                } else {
                    current.append(sentence);
                }
            }
        }
        
        if (current.length() > 0) {
            result.add(current.toString());
        }
        
        return result;
    }
}
