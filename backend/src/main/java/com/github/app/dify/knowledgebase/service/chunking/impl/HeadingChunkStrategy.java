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
 * 标题分块策略
 * 按标题层级组织分块，适用于Markdown等结构化文档
 */
@Component
public class HeadingChunkStrategy implements ChunkStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(HeadingChunkStrategy.class);
    
    // Markdown标题模式：#, ##, ###等
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    
    @Override
    public String getName() {
        return "heading";
    }
    
    @Override
    public boolean supports(String fileType, String contentType) {
        // 支持Markdown文件或包含标题的内容
        return "md".equalsIgnoreCase(fileType) || 
               "markdown".equalsIgnoreCase(fileType) ||
               ContentStructure.ContentType.HEADING.equals(contentType);
    }
    
    @Override
    public List<ChunkResult> chunk(String text, ChunkConfig config) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        int chunkSize = config.getChunkSize();
        
        // 识别标题和内容块
        List<HeadingBlock> blocks = extractHeadingBlocks(text);
        
        // 生成分块结果
        List<ChunkResult> chunks = new ArrayList<>();
        int chunkIndex = 0;
        
        for (HeadingBlock block : blocks) {
            String content = block.getContent();
            
            // 如果内容块太大，需要拆分
            if (content.length() > chunkSize) {
                List<String> splitContent = splitLargeContent(content, chunkSize);
                for (String part : splitContent) {
                    ChunkResult chunk = createChunk(block.getHeading(), part, 
                            block.getStartIndex(), chunkIndex++);
                    chunks.add(chunk);
                }
            } else {
                ChunkResult chunk = createChunk(block.getHeading(), content, 
                        block.getStartIndex(), chunkIndex++);
                chunks.add(chunk);
            }
        }
        
        logger.debug("标题分块完成 - 标题块数: {}, chunk数量: {}", blocks.size(), chunks.size());
        
        return chunks;
    }
    
    /**
     * 提取标题块
     */
    private List<HeadingBlock> extractHeadingBlocks(String text) {
        List<HeadingBlock> blocks = new ArrayList<>();
        Matcher matcher = MARKDOWN_HEADING.matcher(text);
        
        int lastEnd = 0;
        String currentHeading = null;
        int currentHeadingLevel = 0;
        int currentHeadingStart = 0;
        int currentHeadingEnd = 0;
        
        while (matcher.find()) {
            int headingStart = matcher.start();
            int headingEnd = matcher.end();
            String headingMark = matcher.group(1);
            String headingText = matcher.group(2);
            int headingLevel = headingMark.length();
            
            // 保存前一个标题块
            if (currentHeading != null) {
                // 优化：直接使用位置信息，避免重复查找
                int contentStart = currentHeadingEnd;
                int contentEnd = headingStart;
                String content = text.substring(contentStart, contentEnd).trim();
                blocks.add(new HeadingBlock(currentHeading, content, currentHeadingStart));
            }
            
            // 开始新的标题块
            currentHeading = headingText;
            currentHeadingLevel = headingLevel;
            currentHeadingStart = headingStart;
            currentHeadingEnd = headingEnd;
        }
        
        // 处理最后一个标题块
        if (currentHeading != null) {
            int contentStart = currentHeadingEnd;
            int contentEnd = text.length();
            String content = text.substring(contentStart, contentEnd).trim();
            blocks.add(new HeadingBlock(currentHeading, content, currentHeadingStart));
        }
        
        // 如果没有找到标题，将整个文档作为一个块
        if (blocks.isEmpty()) {
            blocks.add(new HeadingBlock("", text, 0));
        }
        
        return blocks;
    }
    
    /**
     * 拆分大内容块
     */
    private List<String> splitLargeContent(String content, int maxSize) {
        List<String> result = new ArrayList<>();
        int start = 0;
        
        while (start < content.length()) {
            int end = Math.min(start + maxSize, content.length());
            String chunk = content.substring(start, end);
            
            // 尝试在段落边界截断
            if (end < content.length()) {
                int paraBreak = chunk.lastIndexOf("\n\n");
                if (paraBreak > maxSize * 0.5) {
                    chunk = chunk.substring(0, paraBreak);
                    end = start + paraBreak;
                }
            }
            
            result.add(chunk.trim());
            start = end;
        }
        
        return result;
    }
    
    /**
     * 创建分块结果
     */
    private ChunkResult createChunk(String heading, String content, int startIndex, int chunkIndex) {
        ChunkResult chunk = new ChunkResult();
        
        // 如果有标题，将标题和内容组合
        if (heading != null && !heading.isEmpty()) {
            chunk.setContent("## " + heading + "\n\n" + content);
        } else {
            chunk.setContent(content);
        }
        
        chunk.setChunkIndex(chunkIndex);
        chunk.setStartIndex(startIndex);
        chunk.setEndIndex(startIndex + chunk.getContent().length());
        chunk.setContentType(ContentStructure.ContentType.HEADING);
        chunk.addMetadata("heading", heading != null ? heading : "");
        
        return chunk;
    }
    
    /**
     * 标题块
     */
    private static class HeadingBlock {
        private String heading;
        private String content;
        private int startIndex;
        
        public HeadingBlock(String heading, String content, int startIndex) {
            this.heading = heading;
            this.content = content;
            this.startIndex = startIndex;
        }
        
        public String getHeading() {
            return heading;
        }
        
        public String getContent() {
            return content;
        }
        
        public int getStartIndex() {
            return startIndex;
        }
    }
}
