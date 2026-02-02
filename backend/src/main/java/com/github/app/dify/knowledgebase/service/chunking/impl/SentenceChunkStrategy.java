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

        // 分割句子并记录位置信息
        List<SentenceInfo> sentenceInfos = splitSentencesWithInfo(text);

        // 合并句子为分块，支持 overlap
        List<ChunkResult> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int i = 0;

        while (i < sentenceInfos.size()) {
            StringBuilder currentContent = new StringBuilder();
            int startIndex = sentenceInfos.get(i).startIndex;
            int lastEndIndex = startIndex;

            int j = i;
            while (j < sentenceInfos.size()) {
                SentenceInfo s = sentenceInfos.get(j);
                // 检查加上这个句子是否超出 chunkSize
                if (currentContent.length() > 0 && currentContent.length() + s.content.length() + 1 > chunkSize) {
                    break;
                }

                if (currentContent.length() > 0) {
                    currentContent.append(" ");
                }
                currentContent.append(s.content);
                lastEndIndex = s.endIndex;
                j++;
            }

            ChunkResult result = new ChunkResult();
            result.setContent(currentContent.toString());
            result.setChunkIndex(chunkIndex++);
            result.setStartIndex(startIndex);
            result.setEndIndex(lastEndIndex);
            result.setContentType(ContentStructure.ContentType.TEXT);
            chunks.add(result);

            if (j >= sentenceInfos.size()) {
                break;
            }

            // 移动到下一个chunk的起始句子（支持重叠）
            if (chunkOverlap > 0) {
                // 回退若干个句子以满足重叠要求
                int nextI = j;
                int overlapLength = 0;
                // 至少向前推进一个句子，避免死循环
                while (nextI > i + 1) {
                    int sLen = sentenceInfos.get(nextI - 1).content.length();
                    // 如果加上当前句子会超出重叠大小，则停止回退
                    if (overlapLength + sLen + 1 > chunkOverlap) {
                        break;
                    }
                    overlapLength += sLen + 1;
                    nextI--;
                }
                i = nextI;
            } else {
                i = j;
            }
        }

        logger.debug("句子分块完成 - 句子数: {}, chunk数量: {}", sentenceInfos.size(), chunks.size());

        return chunks;
    }

    /**
     * 分割句子并记录位置信息
     */
    private List<SentenceInfo> splitSentencesWithInfo(String text) {
        List<SentenceInfo> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_END.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            int end = matcher.end();
            String fullMatch = text.substring(lastEnd, end);
            String trimmed = fullMatch.trim();
            if (!trimmed.isEmpty()) {
                // 计算实际内容的起始位置（跳过前导空格）
                int actualStart = lastEnd;
                while (actualStart < end && Character.isWhitespace(text.charAt(actualStart))) {
                    actualStart++;
                }
                sentences.add(new SentenceInfo(trimmed, actualStart, actualStart + trimmed.length()));
            }
            lastEnd = end;
        }

        // 处理最后一段
        if (lastEnd < text.length()) {
            String fullMatch = text.substring(lastEnd);
            String trimmed = fullMatch.trim();
            if (!trimmed.isEmpty()) {
                int actualStart = lastEnd;
                while (actualStart < text.length() && Character.isWhitespace(text.charAt(actualStart))) {
                    actualStart++;
                }
                sentences.add(new SentenceInfo(trimmed, actualStart, actualStart + trimmed.length()));
            }
        }

        return sentences;
    }

    /**
     * 句子信息内部类
     */
    private static class SentenceInfo {
        final String content;
        final int startIndex;
        final int endIndex;

        SentenceInfo(String content, int startIndex, int endIndex) {
            this.content = content;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }
}
