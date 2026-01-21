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
 * 表格分块策略
 * 按表格行分块，保持表格行的完整性
 */
@Component
public class TableChunkStrategy implements ChunkStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(TableChunkStrategy.class);
    
    // Markdown表格模式
    private static final Pattern MARKDOWN_TABLE = Pattern.compile(
            "\\|.*\\|\\s*\\n\\|[-:]+\\|.*\\|\\s*\\n(?:\\|.*\\|\\s*\\n?)*", Pattern.MULTILINE);
    
    @Override
    public String getName() {
        return "table";
    }
    
    @Override
    public boolean supports(String fileType, String contentType) {
        // 支持表格文件或表格类型内容
        if (ContentStructure.ContentType.TABLE.equals(contentType)) {
            return true;
        }
        
        // 支持表格文件扩展名
        if (fileType != null) {
            String lowerType = fileType.toLowerCase();
            return "xls".equals(lowerType) || 
                   "xlsx".equals(lowerType) || 
                   "csv".equals(lowerType);
        }
        
        return false;
    }
    
    @Override
    public List<ChunkResult> chunk(String text, ChunkConfig config) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        // 检测Markdown表格
        Matcher tableMatcher = MARKDOWN_TABLE.matcher(text);
        if (tableMatcher.find()) {
            return chunkMarkdownTable(text, config);
        }
        
        // 检测CSV格式
        if (text.contains(",") && text.contains("\n")) {
            return chunkCsvTable(text, config);
        }
        
        // 默认按行分块
        return chunkByLines(text, config);
    }
    
    /**
     * 分块Markdown表格
     */
    private List<ChunkResult> chunkMarkdownTable(String text, ChunkConfig config) {
        List<ChunkResult> chunks = new ArrayList<>();
        Matcher matcher = MARKDOWN_TABLE.matcher(text);
        int chunkIndex = 0;
        
        while (matcher.find()) {
            String tableText = matcher.group(0);
            int startIndex = matcher.start();
            int endIndex = matcher.end();
            
            // 按行分割表格
            String[] lines = tableText.split("\n");
            if (lines.length < 2) {
                continue;
            }
            
            // 表头
            String header = lines[0];
            String separator = lines[1];
            
            // 合并表头和分隔符
            StringBuilder currentChunk = new StringBuilder();
            currentChunk.append(header).append("\n").append(separator).append("\n");
            
            // 按行分块（保持表头）
            for (int i = 2; i < lines.length; i++) {
                String row = lines[i].trim();
                if (row.isEmpty() || !row.startsWith("|")) {
                    continue;
                }
                
                // 如果当前chunk加上新行会超过大小，保存当前chunk
                if (currentChunk.length() + row.length() + 1 > config.getChunkSize() && 
                    currentChunk.length() > header.length() + separator.length() + 2) {
                    ChunkResult chunk = new ChunkResult();
                    chunk.setContent(currentChunk.toString().trim());
                    chunk.setChunkIndex(chunkIndex++);
                    chunk.setStartIndex(startIndex);
                    chunk.setEndIndex(startIndex + chunk.getContent().length());
                    chunk.setContentType(ContentStructure.ContentType.TABLE);
                    chunk.addMetadata("hasHeader", "true");
                    chunks.add(chunk);
                    
                    // 开始新chunk，包含表头
                    currentChunk = new StringBuilder();
                    currentChunk.append(header).append("\n").append(separator).append("\n");
                }
                
                currentChunk.append(row).append("\n");
            }
            
            // 保存最后一个chunk
            if (currentChunk.length() > header.length() + separator.length() + 2) {
                ChunkResult chunk = new ChunkResult();
                chunk.setContent(currentChunk.toString().trim());
                chunk.setChunkIndex(chunkIndex++);
                chunk.setStartIndex(startIndex);
                chunk.setEndIndex(startIndex + chunk.getContent().length());
                chunk.setContentType(ContentStructure.ContentType.TABLE);
                chunk.addMetadata("hasHeader", "true");
                chunks.add(chunk);
            }
        }
        
        logger.debug("Markdown表格分块完成 - chunk数量: {}", chunks.size());
        
        return chunks;
    }
    
    /**
     * 分块CSV表格
     */
    private List<ChunkResult> chunkCsvTable(String text, ChunkConfig config) {
        List<ChunkResult> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        
        if (lines.length == 0) {
            return chunks;
        }
        
        // 第一行作为表头
        String header = lines[0];
        StringBuilder currentChunk = new StringBuilder();
        currentChunk.append(header).append("\n");
        
        int chunkIndex = 0;
        int startIndex = 0;
        
        for (int i = 1; i < lines.length; i++) {
            String row = lines[i].trim();
            if (row.isEmpty()) {
                continue;
            }
            
            // 如果当前chunk加上新行会超过大小，保存当前chunk
            if (currentChunk.length() + row.length() + 1 > config.getChunkSize() && 
                currentChunk.length() > header.length() + 1) {
                ChunkResult chunk = new ChunkResult();
                chunk.setContent(currentChunk.toString().trim());
                chunk.setChunkIndex(chunkIndex++);
                chunk.setStartIndex(startIndex);
                chunk.setEndIndex(startIndex + chunk.getContent().length());
                chunk.setContentType(ContentStructure.ContentType.TABLE);
                chunk.addMetadata("hasHeader", "true");
                chunks.add(chunk);
                
                // 开始新chunk，包含表头
                currentChunk = new StringBuilder();
                currentChunk.append(header).append("\n");
                startIndex = text.indexOf(row, startIndex);
            }
            
            currentChunk.append(row).append("\n");
        }
        
        // 保存最后一个chunk
        if (currentChunk.length() > header.length() + 1) {
            ChunkResult chunk = new ChunkResult();
            chunk.setContent(currentChunk.toString().trim());
            chunk.setChunkIndex(chunkIndex);
            chunk.setStartIndex(startIndex);
            chunk.setEndIndex(startIndex + chunk.getContent().length());
            chunk.setContentType(ContentStructure.ContentType.TABLE);
            chunk.addMetadata("hasHeader", "true");
            chunks.add(chunk);
        }
        
        logger.debug("CSV表格分块完成 - chunk数量: {}", chunks.size());
        
        return chunks;
    }
    
    /**
     * 按行分块（默认方式）
     */
    private List<ChunkResult> chunkByLines(String text, ChunkConfig config) {
        List<ChunkResult> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;
        int startIndex = 0;
        
        for (String line : lines) {
            if (currentChunk.length() + line.length() + 1 > config.getChunkSize() && 
                currentChunk.length() > 0) {
                ChunkResult chunk = new ChunkResult();
                chunk.setContent(currentChunk.toString().trim());
                chunk.setChunkIndex(chunkIndex++);
                chunk.setStartIndex(startIndex);
                chunk.setEndIndex(startIndex + chunk.getContent().length());
                chunk.setContentType(ContentStructure.ContentType.TABLE);
                chunks.add(chunk);
                
                currentChunk = new StringBuilder();
                startIndex = text.indexOf(line, startIndex);
            }
            
            if (currentChunk.length() > 0) {
                currentChunk.append("\n");
            }
            currentChunk.append(line);
        }
        
        // 保存最后一个chunk
        if (currentChunk.length() > 0) {
            ChunkResult chunk = new ChunkResult();
            chunk.setContent(currentChunk.toString().trim());
            chunk.setChunkIndex(chunkIndex);
            chunk.setStartIndex(startIndex);
            chunk.setEndIndex(startIndex + chunk.getContent().length());
            chunk.setContentType(ContentStructure.ContentType.TABLE);
            chunks.add(chunk);
        }
        
        return chunks;
    }
}
