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
 * 代码分块策略
 * 按代码块边界分块，保持代码完整性
 */
@Component
public class CodeChunkStrategy implements ChunkStrategy {

    private static final Logger logger = LoggerFactory.getLogger(CodeChunkStrategy.class);

    // Markdown代码块模式：```language ... ```
    private static final Pattern MARKDOWN_CODE_BLOCK = Pattern.compile(
            "```(\\w+)?\\n([\\s\\S]*?)```", Pattern.MULTILINE);

    // 代码文件扩展名
    private static final String[] CODE_EXTENSIONS = {
            "java", "js", "javascript", "ts", "typescript", "py", "python",
            "cpp", "c", "h", "hpp", "cs", "go", "rust", "rb", "ruby",
            "php", "swift", "kt", "kotlin", "scala", "sh", "bash", "sql"
    };

    @Override
    public String getName() {
        return "code";
    }

    @Override
    public boolean supports(String fileType, String contentType) {
        // 支持代码文件或代码类型内容
        if (ContentStructure.ContentType.CODE.equals(contentType)) {
            return true;
        }

        if (fileType != null) {
            String lowerType = fileType.toLowerCase();
            for (String ext : CODE_EXTENSIONS) {
                if (lowerType.equals(ext)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public List<ChunkResult> chunk(String text, ChunkConfig config) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // 检测是否为Markdown代码块
        Matcher codeBlockMatcher = MARKDOWN_CODE_BLOCK.matcher(text);
        if (codeBlockMatcher.find()) {
            return chunkMarkdownCodeBlocks(text, config);
        }

        // 否则按代码文件处理（按函数/类边界分块）
        return chunkCodeFile(text, config);
    }

    /**
     * 分块Markdown代码块
     */
    private List<ChunkResult> chunkMarkdownCodeBlocks(String text, ChunkConfig config) {
        List<ChunkResult> chunks = new ArrayList<>();
        Matcher matcher = MARKDOWN_CODE_BLOCK.matcher(text);
        int chunkIndex = 0;

        while (matcher.find()) {
            String language = matcher.group(1);
            String code = matcher.group(2);
            int startIndex = matcher.start();
            int endIndex = matcher.end();

            // 如果代码块太大，需要拆分
            if (code.length() > config.getChunkSize()) {
                List<String> splitCode = splitLargeCode(code, config.getChunkSize());
                for (String part : splitCode) {
                    ChunkResult chunk = new ChunkResult();
                    chunk.setContent("```" + (language != null ? language : "") + "\n" +
                            part + "\n```");
                    chunk.setChunkIndex(chunkIndex++);
                    chunk.setStartIndex(startIndex);
                    chunk.setEndIndex(startIndex + chunk.getContent().length());
                    chunk.setContentType(ContentStructure.ContentType.CODE);
                    chunk.addMetadata("language", language != null ? language : "");
                    chunks.add(chunk);
                }
            } else {
                ChunkResult chunk = new ChunkResult();
                chunk.setContent(matcher.group(0));
                chunk.setChunkIndex(chunkIndex++);
                chunk.setStartIndex(startIndex);
                chunk.setEndIndex(endIndex);
                chunk.setContentType(ContentStructure.ContentType.CODE);
                chunk.addMetadata("language", language != null ? language : "");
                chunks.add(chunk);
            }
        }

        return chunks;
    }

    /**
     * 分块代码文件（按函数/类边界）
     * 优化：避免使用 split() 创建大数组
     */
    private List<ChunkResult> chunkCodeFile(String text, ChunkConfig config) {
        List<ChunkResult> chunks = new ArrayList<>();
        int chunkSize = config.getChunkSize();

        // 简单的实现：按空行和函数/类定义分块
        // 更复杂的实现可以使用AST解析器
        // 优化：使用更高效的方式分割行，同时记录位置信息
        List<LineInfo> lineInfos = splitLinesWithPosition(text);
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;
        int chunkStartIndex = 0;

        // 预编译正则表达式（优化：避免每次调用都编译）
        Pattern definitionPattern = Pattern.compile(
                ".*\\b(class|interface|function|def|public|private|protected)\\s+\\w+.*");

        for (LineInfo lineInfo : lineInfos) {
            String line = lineInfo.content;

            // 检测函数/类定义（使用预编译的正则表达式）
            boolean isDefinition = definitionPattern.matcher(line).matches();

            if (isDefinition && !currentChunk.isEmpty()) {
                // 保存当前chunk
                ChunkResult chunk = new ChunkResult();
                chunk.setContent(currentChunk.toString().trim());
                chunk.setChunkIndex(chunkIndex++);
                chunk.setStartIndex(chunkStartIndex);
                chunk.setEndIndex(chunkStartIndex + chunk.getContent().length());
                chunk.setContentType(ContentStructure.ContentType.CODE);
                chunks.add(chunk);

                // 开始新chunk
                currentChunk = new StringBuilder();
                chunkStartIndex = lineInfo.startIndex;
            }

            // 如果是第一个chunk，记录起始位置
            if (currentChunk.isEmpty()) {
                chunkStartIndex = lineInfo.startIndex;
            }

            currentChunk.append(line);

            // 如果chunk太大，强制分割
            if (currentChunk.length() > chunkSize) {
                ChunkResult chunk = new ChunkResult();
                chunk.setContent(currentChunk.toString().trim());
                chunk.setChunkIndex(chunkIndex++);
                chunk.setStartIndex(chunkStartIndex);
                chunk.setEndIndex(chunkStartIndex + chunk.getContent().length());
                chunk.setContentType(ContentStructure.ContentType.CODE);
                chunks.add(chunk);

                currentChunk = new StringBuilder();
                // 下一个chunk从当前行开始（考虑重叠）
                chunkStartIndex = lineInfo.startIndex;
            }
        }

        // 处理最后一个chunk
        if (!currentChunk.isEmpty()) {
            ChunkResult chunk = new ChunkResult();
            chunk.setContent(currentChunk.toString().trim());
            chunk.setChunkIndex(chunkIndex);
            chunk.setStartIndex(chunkStartIndex);
            chunk.setEndIndex(chunkStartIndex + chunk.getContent().length());
            chunk.setContentType(ContentStructure.ContentType.CODE);
            chunks.add(chunk);
        }

        logger.debug("代码文件分块完成 - chunk数量: {}", chunks.size());

        return chunks;
    }

    /**
     * 行信息（包含内容和位置）
     */
    private static class LineInfo {
        String content;
        int startIndex;

        LineInfo(String content, int startIndex) {
            this.content = content;
            this.startIndex = startIndex;
        }
    }

    /**
     * 分割文本为行（优化：避免使用 split() 创建大数组，同时记录位置信息）
     */
    private List<LineInfo> splitLinesWithPosition(String text) {
        List<LineInfo> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }

        int start = 0;
        int textLength = text.length();

        while (start < textLength) {
            int newlineIndex = text.indexOf('\n', start);
            int end = (newlineIndex != -1) ? newlineIndex + 1 : textLength;
            String line = text.substring(start, end);
            lines.add(new LineInfo(line, start));
            start = end;
        }

        return lines;
    }

    /**
     * 拆分大代码块
     */
    private List<String> splitLargeCode(String code, int maxSize) {
        List<String> result = new ArrayList<>();
        // 使用 splitLinesWithPosition 获取行信息，然后提取内容
        List<LineInfo> lineInfos = splitLinesWithPosition(code);
        StringBuilder current = new StringBuilder();

        for (LineInfo lineInfo : lineInfos) {
            String line = lineInfo.content;
            if (current.length() + line.length() + 1 > maxSize && !current.isEmpty()) {
                result.add(current.toString());
                current = new StringBuilder();
            }
            if (!current.isEmpty()) {
                current.append("\n");
            }
            current.append(line);
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        return result;
    }
}
