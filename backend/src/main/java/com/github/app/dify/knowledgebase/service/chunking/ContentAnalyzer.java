package com.github.app.dify.knowledgebase.service.chunking;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内容分析器
 * 识别文档中的表格、代码块、标题等结构
 */
@Component
public class ContentAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(ContentAnalyzer.class);
    
    // Markdown表格模式
    private static final Pattern MARKDOWN_TABLE = Pattern.compile(
            "\\|.*\\|\\s*\\n\\|[-:]+\\|.*\\|\\s*\\n(?:\\|.*\\|\\s*\\n?)*", Pattern.MULTILINE);
    
    // Markdown代码块模式
    private static final Pattern MARKDOWN_CODE_BLOCK = Pattern.compile(
            "```(\\w+)?\\n([\\s\\S]*?)```", Pattern.MULTILINE);
    
    // Markdown标题模式
    private static final Pattern MARKDOWN_HEADING = Pattern.compile(
            "^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    
    /**
     * 分析文本内容，识别结构
     */
    public ContentStructure analyzeText(String text, String fileType) {
        ContentStructure structure = new ContentStructure();
        
        if (text == null || text.isEmpty()) {
            return structure;
        }
        
        // 检测Markdown表格
        if (hasMarkdownTable(text)) {
            List<ContentStructure.ContentSegment> tableSegments = extractMarkdownTables(text);
            structure.getSegments().addAll(tableSegments);
        }
        
        // 检测Markdown代码块
        if (hasMarkdownCodeBlock(text)) {
            List<ContentStructure.ContentSegment> codeSegments = extractMarkdownCodeBlocks(text);
            structure.getSegments().addAll(codeSegments);
        }
        
        // 检测Markdown标题
        if ("md".equalsIgnoreCase(fileType) || "markdown".equalsIgnoreCase(fileType)) {
            if (hasMarkdownHeading(text)) {
                // 标题信息会在分块时使用，这里可以标记
                logger.debug("检测到Markdown标题");
            }
        }
        
        // 如果没有检测到特殊结构，将整个文本作为普通文本
        if (structure.getSegments().isEmpty()) {
            ContentStructure.ContentSegment textSegment = new ContentStructure.ContentSegment(
                    ContentStructure.ContentType.TEXT, text, 0, text.length());
            structure.addSegment(textSegment);
        } else {
            // 填充非特殊结构的部分为普通文本
            fillTextSegments(structure, text);
        }
        
        return structure;
    }
    
    /**
     * 分析Word文档内容
     */
    public ContentStructure analyzeWordDocument(InputStream inputStream) {
        ContentStructure structure = new ContentStructure();
        
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            int currentIndex = 0;
            StringBuilder textBuilder = new StringBuilder();
            
            // 提取表格
            List<XWPFTable> tables = document.getTables();
            for (XWPFTable table : tables) {
                // 保存表格前的文本
                if (textBuilder.length() > 0) {
                    ContentStructure.ContentSegment textSegment = 
                            new ContentStructure.ContentSegment(
                                    ContentStructure.ContentType.TEXT, 
                                    textBuilder.toString(), 
                                    currentIndex, 
                                    currentIndex + textBuilder.length());
                    structure.addSegment(textSegment);
                    currentIndex += textBuilder.length();
                    textBuilder = new StringBuilder();
                }
                
                // 提取表格内容
                StringBuilder tableBuilder = new StringBuilder();
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cellTexts = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        cellTexts.add(cell.getText().trim());
                    }
                    tableBuilder.append(String.join(" | ", cellTexts)).append("\n");
                }
                
                String tableText = tableBuilder.toString();
                ContentStructure.ContentSegment tableSegment = 
                        new ContentStructure.ContentSegment(
                                ContentStructure.ContentType.TABLE, 
                                tableText, 
                                currentIndex, 
                                currentIndex + tableText.length());
                tableSegment.addMetadata("rowCount", String.valueOf(table.getRows().size()));
                structure.addSegment(tableSegment);
                currentIndex += tableText.length();
            }
            
            // 处理剩余文本（如果有）
            if (textBuilder.length() > 0) {
                ContentStructure.ContentSegment textSegment = 
                        new ContentStructure.ContentSegment(
                                ContentStructure.ContentType.TEXT, 
                                textBuilder.toString(), 
                                currentIndex, 
                                currentIndex + textBuilder.length());
                structure.addSegment(textSegment);
            }
            
        } catch (Exception e) {
            logger.error("分析Word文档失败", e);
            // 返回空结构，将使用默认策略
        }
        
        return structure;
    }
    
    /**
     * 检测是否有Markdown表格
     */
    private boolean hasMarkdownTable(String text) {
        return MARKDOWN_TABLE.matcher(text).find();
    }
    
    /**
     * 提取Markdown表格
     */
    private List<ContentStructure.ContentSegment> extractMarkdownTables(String text) {
        List<ContentStructure.ContentSegment> segments = new ArrayList<>();
        Matcher matcher = MARKDOWN_TABLE.matcher(text);
        
        while (matcher.find()) {
            String tableText = matcher.group(0);
            ContentStructure.ContentSegment segment = 
                    new ContentStructure.ContentSegment(
                            ContentStructure.ContentType.TABLE, 
                            tableText, 
                            matcher.start(), 
                            matcher.end());
            segments.add(segment);
        }
        
        return segments;
    }
    
    /**
     * 检测是否有Markdown代码块
     */
    private boolean hasMarkdownCodeBlock(String text) {
        return MARKDOWN_CODE_BLOCK.matcher(text).find();
    }
    
    /**
     * 提取Markdown代码块
     */
    private List<ContentStructure.ContentSegment> extractMarkdownCodeBlocks(String text) {
        List<ContentStructure.ContentSegment> segments = new ArrayList<>();
        Matcher matcher = MARKDOWN_CODE_BLOCK.matcher(text);
        
        while (matcher.find()) {
            String codeBlock = matcher.group(0);
            String language = matcher.group(1);
            ContentStructure.ContentSegment segment = 
                    new ContentStructure.ContentSegment(
                            ContentStructure.ContentType.CODE, 
                            codeBlock, 
                            matcher.start(), 
                            matcher.end());
            if (language != null) {
                segment.addMetadata("language", language);
            }
            segments.add(segment);
        }
        
        return segments;
    }
    
    /**
     * 检测是否有Markdown标题
     */
    private boolean hasMarkdownHeading(String text) {
        return MARKDOWN_HEADING.matcher(text).find();
    }
    
    /**
     * 填充文本片段（填充非特殊结构的部分）
     */
    private void fillTextSegments(ContentStructure structure, String text) {
        List<ContentStructure.ContentSegment> segments = structure.getSegments();
        List<ContentStructure.ContentSegment> textSegments = new ArrayList<>();
        
        int lastEnd = 0;
        for (ContentStructure.ContentSegment segment : segments) {
            // 添加特殊结构前的文本
            if (segment.getStartIndex() > lastEnd) {
                String textContent = text.substring(lastEnd, segment.getStartIndex()).trim();
                if (!textContent.isEmpty()) {
                    ContentStructure.ContentSegment textSegment = 
                            new ContentStructure.ContentSegment(
                                    ContentStructure.ContentType.TEXT, 
                                    textContent, 
                                    lastEnd, 
                                    segment.getStartIndex());
                    textSegments.add(textSegment);
                }
            }
            lastEnd = segment.getEndIndex();
        }
        
        // 添加最后一个特殊结构后的文本
        if (lastEnd < text.length()) {
            String textContent = text.substring(lastEnd).trim();
            if (!textContent.isEmpty()) {
                ContentStructure.ContentSegment textSegment = 
                        new ContentStructure.ContentSegment(
                                ContentStructure.ContentType.TEXT, 
                                textContent, 
                                lastEnd, 
                                text.length());
                textSegments.add(textSegment);
            }
        }
        
        // 合并并排序所有片段
        segments.addAll(textSegments);
        segments.sort((a, b) -> Integer.compare(a.getStartIndex(), b.getStartIndex()));
    }
}
