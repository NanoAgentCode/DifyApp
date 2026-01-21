package com.github.app.dify.knowledgebase.service.chunking;

import com.github.app.dify.knowledgebase.service.chunking.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 分块策略选择器
 * 根据文件类型和内容特征自动选择合适的分块策略
 */
@Component
public class ChunkStrategySelector {
    
    private static final Logger logger = LoggerFactory.getLogger(ChunkStrategySelector.class);
    
    @Autowired
    private FixedSizeChunkStrategy fixedSizeStrategy;
    
    @Autowired
    private ParagraphChunkStrategy paragraphStrategy;
    
    @Autowired
    private SentenceChunkStrategy sentenceStrategy;
    
    @Autowired
    private HeadingChunkStrategy headingStrategy;
    
    @Autowired
    private CodeChunkStrategy codeStrategy;
    
    @Autowired
    private TableChunkStrategy tableStrategy;
    
    @Autowired
    private RecursiveChunkStrategy recursiveStrategy;
    
    @Autowired
    private ContentAnalyzer contentAnalyzer;
    
    /**
     * 选择分块策略
     * 
     * @param fileType 文件类型（扩展名）
     * @param text 文本内容（用于分析）
     * @return 选中的策略列表（可能返回多个策略用于混合内容）
     */
    public List<ChunkStrategy> selectStrategy(String fileType, String text) {
        if (fileType == null) {
            fileType = "";
        }
        
        String lowerFileType = fileType.toLowerCase();
        
        // 1. 代码文件
        if (isCodeFile(lowerFileType)) {
            logger.debug("选择代码分块策略 - 文件类型: {}", fileType);
            return List.of(codeStrategy);
        }
        
        // 2. 表格文件
        if (isTableFile(lowerFileType)) {
            logger.debug("选择表格分块策略 - 文件类型: {}", fileType);
            return List.of(tableStrategy);
        }
        
        // 3. Markdown文件
        if ("md".equals(lowerFileType) || "markdown".equals(lowerFileType)) {
            return selectStrategyForMarkdown(text);
        }
        
        // 4. Word文档
        if ("doc".equals(lowerFileType) || "docx".equals(lowerFileType)) {
            return selectStrategyForWord(text);
        }
        
        // 5. PDF文档
        if ("pdf".equals(lowerFileType)) {
            return selectStrategyForPdf(text);
        }
        
        // 6. 纯文本文件
        if ("txt".equals(lowerFileType)) {
            logger.debug("选择段落分块策略 - 文件类型: {}", fileType);
            return List.of(paragraphStrategy);
        }
        
        // 7. 默认策略：固定大小分块
        logger.debug("使用默认固定大小分块策略 - 文件类型: {}", fileType);
        return List.of(fixedSizeStrategy);
    }
    
    /**
     * 为Markdown文件选择策略
     */
    private List<ChunkStrategy> selectStrategyForMarkdown(String text) {
        ContentStructure structure = contentAnalyzer.analyzeText(text, "md");
        
        boolean hasTable = structure.getSegments().stream()
                .anyMatch(s -> ContentStructure.ContentType.TABLE.equals(s.getType()));
        boolean hasCode = structure.getSegments().stream()
                .anyMatch(s -> ContentStructure.ContentType.CODE.equals(s.getType()));
        boolean hasHeading = text.matches(".*^#{1,6}\\s+.*$.*");
        
        // 如果包含多种内容类型，使用混合策略
        if ((hasTable || hasCode) && (hasHeading || text.length() > 1000)) {
            logger.debug("Markdown文件包含混合内容，将使用混合分块策略");
            return createMixedStrategies(hasTable, hasCode, hasHeading);
        }
        
        // 如果只有标题，使用标题策略
        if (hasHeading && !hasTable && !hasCode) {
            logger.debug("选择标题分块策略 - Markdown文件");
            return List.of(headingStrategy);
        }
        
        // 默认使用段落策略
        logger.debug("选择段落分块策略 - Markdown文件");
        return List.of(paragraphStrategy);
    }
    
    /**
     * 为Word文档选择策略
     */
    private List<ChunkStrategy> selectStrategyForWord(String text) {
        ContentStructure structure = contentAnalyzer.analyzeText(text, "docx");
        
        boolean hasTable = structure.getSegments().stream()
                .anyMatch(s -> ContentStructure.ContentType.TABLE.equals(s.getType()));
        
        if (hasTable) {
            logger.debug("Word文档包含表格，将使用混合分块策略");
            return createMixedStrategies(true, false, false);
        }
        
        // 默认使用段落策略
        logger.debug("选择段落分块策略 - Word文档");
        return List.of(paragraphStrategy);
    }
    
    /**
     * 为PDF文档选择策略
     */
    private List<ChunkStrategy> selectStrategyForPdf(String text) {
        // PDF通常解析为纯文本，检测是否有表格特征
        boolean hasTableLike = text.contains("|") && text.split("\n").length > 5;
        
        if (hasTableLike) {
            logger.debug("PDF文档可能包含表格，将使用混合分块策略");
            return createMixedStrategies(true, false, false);
        }
        
        // 默认使用段落策略
        logger.debug("选择段落分块策略 - PDF文档");
        return List.of(paragraphStrategy);
    }
    
    /**
     * 创建混合策略列表
     */
    private List<ChunkStrategy> createMixedStrategies(boolean hasTable, boolean hasCode, boolean hasHeading) {
        List<ChunkStrategy> strategies = new ArrayList<>();
        
        if (hasTable) {
            strategies.add(tableStrategy);
        }
        if (hasCode) {
            strategies.add(codeStrategy);
        }
        if (hasHeading) {
            strategies.add(headingStrategy);
        }
        
        // 总是包含段落策略用于普通文本
        strategies.add(paragraphStrategy);
        
        return strategies;
    }
    
    /**
     * 判断是否为代码文件
     */
    private boolean isCodeFile(String fileType) {
        String[] codeExtensions = {
                "java", "js", "javascript", "ts", "typescript", "py", "python",
                "cpp", "c", "h", "hpp", "cs", "go", "rust", "rb", "ruby",
                "php", "swift", "kt", "kotlin", "scala", "sh", "bash", "sql",
                "html", "css", "xml", "json", "yaml", "yml"
        };
        
        for (String ext : codeExtensions) {
            if (ext.equals(fileType)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 判断是否为表格文件
     */
    private boolean isTableFile(String fileType) {
        return "xls".equals(fileType) || 
               "xlsx".equals(fileType) || 
               "csv".equals(fileType);
    }
}
