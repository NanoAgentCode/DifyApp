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

        // 优化：使用更高效的方式分割段落，同时记录位置信息
        List<ParagraphInfo> paragraphInfos = splitParagraphsWithPosition(text);
        List<ParagraphInfo> processedParagraphs = new ArrayList<>();

        // 处理段落：合并小段落，拆分大段落
        for (ParagraphInfo paraInfo : paragraphInfos) {
            String trimmed = paraInfo.content.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.length() <= chunkSize) {
                processedParagraphs.add(new ParagraphInfo(trimmed, paraInfo.startIndex, paraInfo.endIndex));
            } else {
                // 大段落需要拆分
                List<ParagraphInfo> splitParagraphs = splitLargeParagraphWithPosition(
                        trimmed, paraInfo.startIndex, chunkSize, chunkOverlap);
                processedParagraphs.addAll(splitParagraphs);
            }
        }

        // 合并段落（同时保持位置信息，支持 overlap）
        List<ParagraphInfo> mergedParagraphs = mergeParagraphsWithOverlap(
                processedParagraphs, chunkSize, chunkOverlap);

        // 生成分块结果（优化：使用记录的位置信息）
        List<ChunkResult> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (ParagraphInfo paraInfo : mergedParagraphs) {
            ChunkResult chunk = new ChunkResult();
            chunk.setContent(paraInfo.content);
            chunk.setChunkIndex(chunkIndex);
            chunk.setStartIndex(paraInfo.startIndex);
            chunk.setEndIndex(paraInfo.endIndex);
            chunk.setContentType(ContentStructure.ContentType.TEXT);
            chunks.add(chunk);
            chunkIndex++;
        }

        logger.debug("段落分块完成 - 段落数: {}, chunk数量: {}", processedParagraphs.size(), chunks.size());

        return chunks;
    }

    /**
     * 段落信息（包含内容和位置）
     */
    private static class ParagraphInfo {
        String content;
        int startIndex;
        int endIndex;

        ParagraphInfo(String content, int startIndex, int endIndex) {
            this.content = content;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    /**
     * 分割段落（优化：避免使用 split() 创建大数组，同时记录位置信息）
     */
    private List<ParagraphInfo> splitParagraphsWithPosition(String text) {
        List<ParagraphInfo> paragraphs = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return paragraphs;
        }

        int start = 0;
        int textLength = text.length();
        java.util.regex.Matcher matcher = PARAGRAPH_SEPARATOR.matcher(text);

        while (start < textLength) {
            int end;
            if (matcher.find(start)) {
                end = matcher.end();
            } else {
                end = textLength;
            }

            String paragraph = text.substring(start, end);
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                // 计算trim后的实际位置
                int trimmedStart = start;
                while (trimmedStart < end && Character.isWhitespace(text.charAt(trimmedStart))) {
                    trimmedStart++;
                }
                int trimmedEnd = end;
                while (trimmedEnd > trimmedStart && Character.isWhitespace(text.charAt(trimmedEnd - 1))) {
                    trimmedEnd--;
                }
                paragraphs.add(new ParagraphInfo(trimmed, trimmedStart, trimmedEnd));
            }

            start = end;
        }

        // 如果没有找到段落分隔符，整个文本作为一个段落
        if (paragraphs.isEmpty()) {
            paragraphs.add(new ParagraphInfo(text.trim(), 0, text.length()));
        }

        return paragraphs;
    }

    /**
     * 拆分大段落（同时保持位置信息）
     */
    private List<ParagraphInfo> splitLargeParagraphWithPosition(
            String paragraph, int baseStartIndex, int maxSize, int overlap) {
        List<ParagraphInfo> result = new ArrayList<>();
        int start = 0;

        while (start < paragraph.length()) {
            int end = Math.min(start + maxSize, paragraph.length());

            // 尝试在句子边界截断
            if (end < paragraph.length()) {
                String potentialChunk = paragraph.substring(start, end);
                int sentenceEnd = findLastSentenceEnd(potentialChunk);
                if (sentenceEnd > maxSize * 0.5) {
                    end = start + sentenceEnd + 1;
                }
            }

            String chunk = paragraph.substring(start, end);
            String trimmed = chunk.trim();
            if (!trimmed.isEmpty()) {
                // 计算实际内容的起始和结束位置
                int actualStart = start;
                while (actualStart < end && Character.isWhitespace(paragraph.charAt(actualStart))) {
                    actualStart++;
                }
                int actualEnd = end;
                while (actualEnd > actualStart && Character.isWhitespace(paragraph.charAt(actualEnd - 1))) {
                    actualEnd--;
                }

                result.add(new ParagraphInfo(paragraph.substring(actualStart, actualEnd),
                        baseStartIndex + actualStart, baseStartIndex + actualEnd));
            }

            if (end >= paragraph.length()) {
                break;
            }

            // 移动到下一个起始位置（支持 overlap）
            // 确保至少前进一个字符，避免死循环
            start = Math.max(start + 1, end - overlap);
        }

        return result;
    }

    /**
     * 合并小段落（同时保持位置信息）
     */
    private List<ParagraphInfo> mergeParagraphsWithOverlap(
            List<ParagraphInfo> paragraphs, int targetSize, int overlap) {
        List<ParagraphInfo> result = new ArrayList<>();
        int i = 0;

        while (i < paragraphs.size()) {
            StringBuilder currentContent = new StringBuilder();
            int currentStart = paragraphs.get(i).startIndex;
            int currentEnd = -1;

            int j = i;
            while (j < paragraphs.size()) {
                ParagraphInfo p = paragraphs.get(j);
                int separatorLen = currentContent.length() > 0 ? 2 : 0; // "\n\n"

                if (currentContent.length() > 0 &&
                        currentContent.length() + separatorLen + p.content.length() > targetSize) {
                    break;
                }

                if (currentContent.length() > 0) {
                    currentContent.append("\n\n");
                }
                currentContent.append(p.content);
                currentEnd = p.endIndex;
                j++;
            }

            result.add(new ParagraphInfo(currentContent.toString(), currentStart, currentEnd));

            if (j >= paragraphs.size()) {
                break;
            }

            // 下一个 chunk 的起始点（支持 overlap）
            if (overlap > 0) {
                int nextI = j;
                int currentOverlap = 0;
                // 至少向前推进一步，避免死循环
                while (nextI > i + 1) {
                    int pLen = paragraphs.get(nextI - 1).content.length();
                    int sLen = 2; // "\n\n"
                    if (currentOverlap + pLen + sLen > overlap) {
                        break;
                    }
                    currentOverlap += pLen + sLen;
                    nextI--;
                }
                i = nextI;
            } else {
                i = j;
            }
        }

        return result;
    }

    /**
     * 查找最后一个句子结束位置
     */
    private int findLastSentenceEnd(String text) {
        int lastChinesePunct = Math.max(
                Math.max(text.lastIndexOf('。'), text.lastIndexOf('！')),
                text.lastIndexOf('？'));

        int lastEnglishPunct = Math.max(
                Math.max(text.lastIndexOf('.'), text.lastIndexOf('!')),
                text.lastIndexOf('?'));

        return Math.max(lastChinesePunct, lastEnglishPunct);
    }
}
