package com.github.app.dify.knowledgebase.service.chunking;

import com.github.app.dify.knowledgebase.service.chunking.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 分块策略选择器
 * 
 * <p>
 * 根据文件类型和内容特征自动选择合适的分块策略。这是智能分块策略系统的核心组件，
 * 负责分析文档特征并选择最优的分块方式。
 * 
 * <p>
 * 选择逻辑：
 * <ul>
 * <li>代码文件（.java, .py, .js等）→ 代码分块策略</li>
 * <li>表格文件（.csv, .xlsx等）→ 表格分块策略</li>
 * <li>Markdown文件 → 根据内容特征（标题、表格、代码块）选择</li>
 * <li>Word文档 → 根据内容特征（段落、表格）选择</li>
 * <li>PDF文档 → 根据内容特征选择</li>
 * <li>纯文本文件 → 段落分块策略</li>
 * <li>其他文件 → 固定大小分块策略（默认）</li>
 * </ul>
 * 
 * <p>
 * 性能优化：
 * <ul>
 * <li>使用HashSet存储代码文件扩展名，O(1)查找效率</li>
 * <li>预编译正则表达式，避免重复编译</li>
 * <li>快速检测机制，避免不必要的完整内容分析</li>
 * <li>使用字符计数替代split()，避免创建大数组</li>
 * </ul>
 * 
 * <p>
 * 混合内容处理：
 * 当检测到文档包含多种内容类型（如Markdown中的表格和代码块）时，
 * 会返回多个策略，由{@link MixedContentChunker}进行混合分块处理。
 * 
 * <p>
 * 使用场景：
 * <ul>
 * <li>知识库文档向量化前的分块处理</li>
 * <li>文档解读模块的文档分块</li>
 * </ul>
 * 
 * @see ChunkStrategy
 * @see ContentAnalyzer
 * @see MixedContentChunker
 * @author DifyApp Team
 * @since 1.0
 */
@Component
public class ChunkStrategySelector {

    private static final Logger logger = LoggerFactory.getLogger(ChunkStrategySelector.class);

    @Autowired
    private FixedSizeChunkStrategy fixedSizeStrategy;

    @Autowired
    private ParagraphChunkStrategy paragraphStrategy;

    @Autowired
    private HeadingChunkStrategy headingStrategy;

    @Autowired
    private CodeChunkStrategy codeStrategy;

    @Autowired
    private TableChunkStrategy tableStrategy;

    @Autowired
    private ContentAnalyzer contentAnalyzer;

    // 代码文件扩展名集合（优化：使用HashSet提升查找效率）
    private static final Set<String> CODE_EXTENSIONS = new HashSet<>();
    static {
        CODE_EXTENSIONS.add("java");
        CODE_EXTENSIONS.add("js");
        CODE_EXTENSIONS.add("javascript");
        CODE_EXTENSIONS.add("ts");
        CODE_EXTENSIONS.add("typescript");
        CODE_EXTENSIONS.add("py");
        CODE_EXTENSIONS.add("python");
        CODE_EXTENSIONS.add("cpp");
        CODE_EXTENSIONS.add("c");
        CODE_EXTENSIONS.add("h");
        CODE_EXTENSIONS.add("hpp");
        CODE_EXTENSIONS.add("cs");
        CODE_EXTENSIONS.add("go");
        CODE_EXTENSIONS.add("rust");
        CODE_EXTENSIONS.add("rb");
        CODE_EXTENSIONS.add("ruby");
        CODE_EXTENSIONS.add("php");
        CODE_EXTENSIONS.add("swift");
        CODE_EXTENSIONS.add("kt");
        CODE_EXTENSIONS.add("kotlin");
        CODE_EXTENSIONS.add("scala");
        CODE_EXTENSIONS.add("sh");
        CODE_EXTENSIONS.add("bash");
        CODE_EXTENSIONS.add("sql");
        CODE_EXTENSIONS.add("html");
        CODE_EXTENSIONS.add("css");
        CODE_EXTENSIONS.add("xml");
        CODE_EXTENSIONS.add("json");
        CODE_EXTENSIONS.add("yaml");
        CODE_EXTENSIONS.add("yml");
    }

    // Markdown标题模式（预编译，避免重复编译）
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile(
            "^#{1,6}\\s+.*$", Pattern.MULTILINE);

    /**
     * 选择分块策略
     * 
     * @param fileType 文件类型（扩展名）
     * @param text     文本内容（用于分析）
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
     * 优化：快速检测，避免完整分析（如果不需要混合策略）
     */
    private List<ChunkStrategy> selectStrategyForMarkdown(String text) {
        // 快速检测标题（使用预编译的正则表达式）
        boolean hasHeading = MARKDOWN_HEADING_PATTERN.matcher(text).find();

        // 快速检测表格和代码块（只检查关键特征，不完整分析）
        boolean hasTableLike = text.contains("|") && text.contains("---");
        boolean hasCodeLike = text.contains("```");

        // 如果检测到表格或代码块特征，进行完整分析
        if (hasTableLike || hasCodeLike) {
            ContentStructure structure = contentAnalyzer.analyzeText(text, "md");

            boolean hasTable = structure.getSegments().stream()
                    .anyMatch(s -> ContentStructure.ContentType.TABLE.equals(s.getType()));
            boolean hasCode = structure.getSegments().stream()
                    .anyMatch(s -> ContentStructure.ContentType.CODE.equals(s.getType()));

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
        } else if (hasHeading) {
            // 没有表格和代码，但有标题，直接使用标题策略
            logger.debug("选择标题分块策略 - Markdown文件");
            return List.of(headingStrategy);
        }

        // 默认使用段落策略
        logger.debug("选择段落分块策略 - Markdown文件");
        return List.of(paragraphStrategy);
    }

    /**
     * 为Word文档选择策略
     * 优化：快速检测表格特征，避免完整分析（如果不需要）
     */
    private List<ChunkStrategy> selectStrategyForWord(String text) {
        // 快速检测：Word文档解析后如果包含表格，通常会有 "--- 文档中的表格 ---" 标记
        // 或者包含表格特征（| 分隔符和换行）
        boolean hasTableLike = text.contains("--- 文档中的表格 ---") ||
                (text.contains("|") && text.split("\n").length > 10);

        if (hasTableLike) {
            // 进行完整分析确认
            ContentStructure structure = contentAnalyzer.analyzeText(text, "docx");
            boolean hasTable = structure.getSegments().stream()
                    .anyMatch(s -> ContentStructure.ContentType.TABLE.equals(s.getType()));

            if (hasTable) {
                logger.debug("Word文档包含表格，将使用混合分块策略");
                return createMixedStrategies(true, false, false);
            }
        }

        // 默认使用段落策略
        logger.debug("选择段落分块策略 - Word文档");
        return List.of(paragraphStrategy);
    }

    /**
     * 为PDF文档选择策略
     * 优化：避免使用 split()，改用字符计数
     */
    private List<ChunkStrategy> selectStrategyForPdf(String text) {
        // PDF通常解析为纯文本，检测是否有表格特征
        // 优化：使用字符计数替代 split()，避免创建大数组
        boolean hasTableLike = text.contains("|") && countLines(text) > 5;

        if (hasTableLike) {
            logger.debug("PDF文档可能包含表格，将使用混合分块策略");
            return createMixedStrategies(true, false, false);
        }

        // 默认使用段落策略
        logger.debug("选择段落分块策略 - PDF文档");
        return List.of(paragraphStrategy);
    }

    /**
     * 快速计算文本行数（优化：避免使用 split()）
     */
    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
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
     * 优化：使用HashSet，O(1)查找
     */
    private boolean isCodeFile(String fileType) {
        return CODE_EXTENSIONS.contains(fileType);
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
