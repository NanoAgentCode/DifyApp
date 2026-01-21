package com.github.app.dify.knowledgebase.service.chunking.impl;

import com.github.app.dify.knowledgebase.service.chunking.ChunkConfig;
import com.github.app.dify.knowledgebase.service.chunking.ChunkStrategy;
import com.github.app.dify.knowledgebase.service.chunking.ContentStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 段落分块策略
 * 按段落边界分块，支持段落合并和拆分
 */
@Component
public class ParagraphChunkStrategy implements ChunkStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(ParagraphChunkStrategy.class);
    
    // 段落分隔符：双换行、单换行+空行等
    private static final Pattern PARAGRAPH_SEPARATOR = Pattern.compile("\\n\\s*\\n");
    
    @Override
    public String getName() {
        return "paragraph";
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
        
        // 按段落分隔符分割
        String[] paragraphs = PARAGRAPH_SEPARATOR.split(text);
        List<String> processedParagraphs = new ArrayList<>();
        
        // 处理段落：合并小段落，拆分大段落
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            
            if (trimmed.length() <= chunkSize) {
                processedParagraphs.add(trimmed);
            } else {
                // 大段落需要拆分
                List<String> splitParagraphs = splitLargeParagraph(trimmed, chunkSize);
                processedParagraphs.addAll(splitParagraphs);
            }
        }
        
        // 合并小段落
        List<String> mergedParagraphs = mergeSmallParagraphs(processedParagraphs, chunkSize);
        
        // 生成分块结果
        List<ChunkResult> chunks = new ArrayList<>();
        int currentIndex = 0;
        int chunkIndex = 0;
        
        for (String para : mergedParagraphs) {
            int startIndex = text.indexOf(para, currentIndex);
            if (startIndex == -1) {
                startIndex = currentIndex;
            }
            int endIndex = startIndex + para.length();
            
            ChunkResult chunk = new ChunkResult();
            chunk.setContent(para);
            chunk.setChunkIndex(chunkIndex);
            chunk.setStartIndex(startIndex);
            chunk.setEndIndex(endIndex);
            chunk.setContentType(ContentStructure.ContentType.TEXT);
            chunks.add(chunk);
            
            currentIndex = endIndex;
            chunkIndex++;
        }
        
        logger.debug("段落分块完成 - 段落数: {}, chunk数量: {}", processedParagraphs.size(), chunks.size());
        
        return chunks;
    }
    
    /**
     * 拆分大段落
     */
    private List<String> splitLargeParagraph(String paragraph, int maxSize) {
        List<String> result = new ArrayList<>();
        int start = 0;
        
        while (start < paragraph.length()) {
            int end = Math.min(start + maxSize, paragraph.length());
            String chunk = paragraph.substring(start, end);
            
            // 尝试在句子边界截断
            if (end < paragraph.length()) {
                int sentenceEnd = findLastSentenceEnd(chunk);
                if (sentenceEnd > maxSize * 0.5) {
                    chunk = chunk.substring(0, sentenceEnd + 1);
                    end = start + sentenceEnd + 1;
                }
            }
            
            result.add(chunk.trim());
            start = end;
        }
        
        return result;
    }
    
    /**
     * 合并小段落
     */
    private List<String> mergeSmallParagraphs(List<String> paragraphs, int targetSize) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        
        for (String para : paragraphs) {
            if (current.length() + para.length() + 2 <= targetSize) {
                // 可以合并
                if (current.length() > 0) {
                    current.append("\n\n");
                }
                current.append(para);
            } else {
                // 不能合并，保存当前段落
                if (current.length() > 0) {
                    result.add(current.toString());
                    current = new StringBuilder();
                }
                current.append(para);
            }
        }
        
        if (current.length() > 0) {
            result.add(current.toString());
        }
        
        return result;
    }
    
    /**
     * 查找最后一个句子结束位置
     */
    private int findLastSentenceEnd(String text) {
        int lastChinesePunct = Math.max(
                Math.max(text.lastIndexOf('。'), text.lastIndexOf('！')),
                text.lastIndexOf('？')
        );
        
        int lastEnglishPunct = Math.max(
                Math.max(text.lastIndexOf('.'), text.lastIndexOf('!')),
                text.lastIndexOf('?')
        );
        
        return Math.max(lastChinesePunct, lastEnglishPunct);
    }
}
