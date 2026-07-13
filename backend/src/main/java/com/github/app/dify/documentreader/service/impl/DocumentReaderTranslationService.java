package com.github.app.dify.documentreader.service.impl;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.documentreader.domain.DocumentReader;
import com.github.app.dify.documentreader.domain.DocumentTranslation;
import com.github.app.dify.documentreader.repository.DocumentReaderRepository;
import com.github.app.dify.documentreader.repository.DocumentTranslationRepository;
import com.github.app.dify.documentreader.util.DocumentReaderDateTimeUtil;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.service.FileStorageService;
import com.github.app.dify.model.service.ModelConfigService;
import com.github.app.dify.system.util.SkillLoader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
class DocumentReaderTranslationService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentReaderTranslationService.class);
    private static final int TRANSLATION_SEGMENT_SIZE = 5000;
    private static final int TRANSLATION_PAGE_LINES = 80;
    private static final int LANGUAGE_DETECTION_SAMPLE_SIZE = 2000;
    private static final double LANGUAGE_DETECTION_THRESHOLD = 0.3;

    @Autowired private DocumentReaderRepository documentRepository;
    @Autowired private DocumentTranslationRepository translationRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private ModelConfigService modelConfigService;
    @Autowired private ModelLanguageModelFactory modelLanguageModelFactory;
    @Autowired private DocumentReaderAccessService documentReaderAccessService;
    @Autowired private DocumentReaderGuideMindMapService guideMindMapService;
    /**
     * 翻译文档（懒加载模式：只翻译第一段）
     */
    public void translateDocument(Long documentId, Long userId, String targetLang, boolean forceRetranslate) {
        documentReaderAccessService.validateAccess(documentId, userId);

        logger.info("开始翻译文档（懒加载模式） - 文档ID: {}, 目标语言: {}, 强制重新翻译: {}", documentId, targetLang, forceRetranslate);

        try {
            // 如果强制重新翻译，先删除旧的翻译记录
            if (forceRetranslate) {
                Optional<DocumentTranslation> existingTranslation = translationRepository
                        .findByDocumentIdAndTargetLanguage(documentId, targetLang);
                if (existingTranslation.isPresent()) {
                    translationRepository.delete(existingTranslation.get());
                    logger.info("已删除旧的翻译记录 - 文档ID: {}, 目标语言: {}", documentId, targetLang);
                }
            }

            // 检查是否已有翻译内容
            List<DocumentSegment> existingSegments = loadDocumentTranslationSegments(documentId, targetLang);
            if (existingSegments != null && !existingSegments.isEmpty()) {
                // 检查是否所有段落都已翻译
                boolean allTranslated = existingSegments.stream()
                        .allMatch(seg -> seg.getTranslatedText() != null && !seg.getTranslatedText().trim().isEmpty());

                if (allTranslated && !forceRetranslate) {
                    logger.info("文档已完全翻译，无需重新翻译 - 文档ID: {}, 目标语言: {}", documentId, targetLang);
                    return; // 已完全翻译，直接返回
                } else {
                    logger.info("文档部分已翻译，继续翻译未完成部分 - 文档ID: {}, 目标语言: {}", documentId, targetLang);
                    // 继续翻译未完成的段落（由前端懒加载触发）
                    return;
                }
            }

            // 获取文档
            Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
            if (!optional.isPresent()) {
                throw new NotFoundException("文档不存在: " + documentId);
            }

            DocumentReader document = optional.get();

            // 提取文档内容
            String documentContent = extractDocumentText(document);
            if (documentContent == null || documentContent.trim().isEmpty()) {
                logger.warn("文档内容为空，无法翻译 - 文档ID: {}", documentId);
                throw new BusinessException("文档内容为空，无法翻译", ErrorCode.BAD_REQUEST);
            }

            // 去除页眉页脚
            documentContent = removeHeaderFooter(documentContent);

            // 检查是否为同种语言翻译（禁止同种语言翻译）
            if (isSameLanguageTranslation(documentContent, targetLang)) {
                String detectedLang = detectDocumentLanguage(documentContent);
                logger.warn("禁止同种语言翻译 - 文档ID: {}, 文档语言: {}, 目标语言: {}",
                        documentId, detectedLang, targetLang);
                throw new BusinessException(
                        String.format("不能将%s文档翻译为%s，翻译功能仅支持不同语言之间的翻译",
                                detectedLang, targetLang),
                        ErrorCode.BAD_REQUEST);
            }

            // 获取模型配置（使用默认RAG模型）
            QAModel qaModel = modelConfigService.getDefaultQAModelForRAG();
            if (qaModel == null) {
                throw new BusinessException("未配置可用的模型，无法进行翻译", ErrorCode.MODEL_NOT_FOUND);
            }

            // 将文档分段（用于懒加载）
            List<DocumentSegment> segments = splitDocumentForTranslation(documentContent);
            logger.info("文档分段完成 - 文档ID: {}, 总段数: {}", documentId, segments.size());

            // 只翻译第一段（懒加载）
            if (!segments.isEmpty()) {
                DocumentSegment firstSegment = segments.get(0);
                String firstTranslated = translateTextSegment(firstSegment.getText(), targetLang, qaModel);
                firstSegment.setTranslatedText(firstTranslated);
                logger.info("第一段翻译完成 - 文档ID: {}, 段索引: 0, 原文长度: {}, 译文长度: {}",
                        documentId, firstSegment.getText().length(), firstTranslated.length());
            }

            // 保存分段信息和翻译结果（JSON格式）
            saveDocumentTranslationSegments(documentId, userId, targetLang, segments);

            logger.info("文档翻译初始化完成（懒加载模式） - 文档ID: {}, 目标语言: {}, 总段数: {}, 已翻译: 1",
                    documentId, targetLang, segments.size());

        } catch (Exception e) {
            logger.error("翻译文档失败 - 文档ID: {}, 目标语言: {}", documentId, targetLang, e);
            throw new BusinessException("翻译文档失败", ErrorCode.API_CALL_FAILED, e);
        }
    }

    /**
     * 获取文档翻译内容（兼容旧版本，返回所有已翻译的内容）
     */
    public String getDocumentTranslation(Long documentId, Long userId, String targetLang) {
        documentReaderAccessService.validateAccess(documentId, userId);

        // 尝试获取分段翻译
        List<DocumentSegment> segments = loadDocumentTranslationSegments(documentId, targetLang);
        if (segments != null && !segments.isEmpty()) {
            // 按索引排序，确保段落顺序一致
            segments.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));

            // 拼接所有已翻译的段落（按顺序）
            StringBuilder result = new StringBuilder();
            for (DocumentSegment segment : segments) {
                if (segment.getTranslatedText() != null && !segment.getTranslatedText().trim().isEmpty()) {
                    if (result.length() > 0) {
                        result.append("\n\n");
                    }
                    result.append(segment.getTranslatedText());
                }
            }
            return result.toString();
        }

        // 如果没有分段翻译，返回旧格式的翻译内容
        return translationRepository.findByDocumentIdAndTargetLanguage(documentId, targetLang)
                .map(DocumentTranslation::getContent)
                .orElse("");
    }

    /**
     * 获取文档翻译内容（懒加载模式，返回指定范围的翻译）
     */
    public String getDocumentTranslationRange(Long documentId, Long userId, String targetLang, int startSegment,
            int endSegment) {
        documentReaderAccessService.validateAccess(documentId, userId);

        List<DocumentSegment> segments = loadDocumentTranslationSegments(documentId, targetLang);
        if (segments == null || segments.isEmpty()) {
            return "";
        }

        // 按索引排序，确保段落顺序一致
        segments.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));

        // 确保索引有效
        startSegment = Math.max(0, startSegment);
        endSegment = Math.min(segments.size(), endSegment);

        if (startSegment >= endSegment) {
            return "";
        }

        // 拼接指定范围的翻译（按顺序）
        StringBuilder result = new StringBuilder();
        for (int i = startSegment; i < endSegment; i++) {
            DocumentSegment segment = segments.get(i);
            if (segment.getTranslatedText() != null && !segment.getTranslatedText().trim().isEmpty()) {
                if (result.length() > 0) {
                    result.append("\n\n");
                }
                result.append(segment.getTranslatedText());
            }
        }

        return result.toString();
    }

    /**
     * 获取文档分段信息
     */
    public Map<String, Object> getDocumentSegments(Long documentId, Long userId) {
        documentReaderAccessService.validateAccess(documentId, userId);

        // 获取文档内容
        Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
        if (!optional.isPresent()) {
            throw new NotFoundException("文档不存在: " + documentId);
        }

        DocumentReader document = optional.get();
        String documentContent = extractDocumentText(document);

        if (documentContent == null || documentContent.trim().isEmpty()) {
            throw new BusinessException("文档内容为空", ErrorCode.BAD_REQUEST);
        }

        // 分段
        List<DocumentSegment> segments = splitDocumentForTranslation(documentContent);

        // 按索引排序，确保段落顺序一致
        segments.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));

        // 构建返回信息
        Map<String, Object> result = new HashMap<>();
        result.put("totalSegments", segments.size());
        result.put("totalLength", documentContent.length());

        List<Map<String, Object>> segmentInfos = new ArrayList<>();
        for (DocumentSegment segment : segments) {
            Map<String, Object> info = new HashMap<>();
            info.put("index", segment.getIndex()); // 使用分段的实际索引
            info.put("start", segment.getStartIndex());
            info.put("end", segment.getEndIndex());
            info.put("length", segment.getText().length());
            segmentInfos.add(info);
        }
        result.put("segments", segmentInfos);

        return result;
    }

    /**
     * 翻译指定分段（懒加载）- 优化版本
     */
    public String translateDocumentSegment(Long documentId, Long userId, String targetLang, int segmentIndex) {
        documentReaderAccessService.validateAccess(documentId, userId);

        logger.info("开始翻译指定分段 - 文档ID: {}, 目标语言: {}, 段索引: {}", documentId, targetLang, segmentIndex);

        try {
            // 先尝试加载已有分段信息（避免重复提取文档内容）
            List<DocumentSegment> segments = loadDocumentTranslationSegments(documentId, targetLang);

            // 如果分段信息不存在或为空，需要创建（此时才提取文档内容）
            if (segments == null || segments.isEmpty()) {
                logger.info("分段信息不存在，开始创建分段 - 文档ID: {}, 目标语言: {}", documentId, targetLang);

                // 获取文档
                Optional<DocumentReader> optional = documentRepository.findByIdAndDeleted(documentId, 0);
                if (!optional.isPresent()) {
                    throw new NotFoundException("文档不存在: " + documentId);
                }

                DocumentReader document = optional.get();

                // 提取文档内容
                String documentContent = extractDocumentText(document);
                if (documentContent == null || documentContent.trim().isEmpty()) {
                    throw new BusinessException("文档内容为空，无法翻译", ErrorCode.BAD_REQUEST);
                }

                // 去除页眉页脚
                documentContent = removeHeaderFooter(documentContent);

                // 检查是否为同种语言翻译（禁止同种语言翻译）
                if (isSameLanguageTranslation(documentContent, targetLang)) {
                    String detectedLang = detectDocumentLanguage(documentContent);
                    throw new BusinessException(
                            String.format("不能将%s文档翻译为%s，翻译功能仅支持不同语言之间的翻译",
                                    detectedLang, targetLang),
                            ErrorCode.BAD_REQUEST);
                }

                // 创建分段信息
                segments = splitDocumentForTranslation(documentContent);
                saveDocumentTranslationSegments(documentId, userId, targetLang, segments);
                logger.info("分段信息创建完成 - 文档ID: {}, 总段数: {}", documentId, segments.size());
            }

            // 按索引排序，确保段落顺序一致
            segments.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));

            // 检查索引有效性
            if (segmentIndex < 0 || segmentIndex >= segments.size()) {
                logger.warn("分段索引无效 - 文档ID: {}, 目标语言: {}, 请求索引: {}, 总段数: {}",
                        documentId, targetLang, segmentIndex, segments.size());
                throw new BusinessException("分段索引无效: " + segmentIndex + ", 总段数: " + segments.size(),
                        ErrorCode.BAD_REQUEST);
            }

            // 使用索引直接访问分段（已排序，索引即数组位置）
            DocumentSegment segment = segments.get(segmentIndex);

            // 验证分段索引是否匹配（双重检查）
            if (segment.getIndex() != segmentIndex) {
                // 如果索引不匹配，使用线性查找
                segment = null;
                for (DocumentSegment seg : segments) {
                    if (seg.getIndex() == segmentIndex) {
                        segment = seg;
                        break;
                    }
                }
                if (segment == null) {
                    throw new BusinessException("找不到索引为 " + segmentIndex + " 的分段", ErrorCode.BAD_REQUEST);
                }
            }

            // 检查是否已翻译
            if (segment.getTranslatedText() != null && !segment.getTranslatedText().trim().isEmpty()) {
                logger.debug("分段已翻译，直接返回 - 文档ID: {}, 段索引: {}", documentId, segmentIndex);
                return segment.getTranslatedText();
            }

            // 获取模型配置（延迟到真正需要翻译时才获取）
            QAModel qaModel = modelConfigService.getDefaultQAModelForRAG();
            if (qaModel == null) {
                throw new BusinessException("未配置可用的模型，无法进行翻译", ErrorCode.MODEL_NOT_FOUND);
            }

            // 翻译该分段
            String translated = translateTextSegment(segment.getText(), targetLang, qaModel);
            segment.setTranslatedText(translated);

            // 保存更新后的分段信息（异步保存，提高响应速度）
            saveDocumentTranslationSegments(documentId, userId, targetLang, segments);

            logger.info("分段翻译完成 - 文档ID: {}, 段索引: {}, 原文长度: {}, 译文长度: {}",
                    documentId, segmentIndex, segment.getText().length(), translated.length());

            return translated;

        } catch (BusinessException e) {
            // 业务异常，直接抛出
            throw e;
        } catch (Exception e) {
            logger.error("翻译分段失败 - 文档ID: {}, 段索引: {}, 错误: {}",
                    documentId, segmentIndex, e.getMessage(), e);
            throw new BusinessException("翻译分段失败", ErrorCode.API_CALL_FAILED, e);
        }
    }

    /**
     * 保存文档翻译内容
     */
    @Transactional
    public void saveDocumentTranslation(Long documentId, Long userId, String targetLang, String content) {
        documentReaderAccessService.validateAccess(documentId, userId);

        Optional<DocumentTranslation> optional = translationRepository.findByDocumentIdAndTargetLanguage(documentId,
                targetLang);
        DocumentTranslation translation;
        if (optional.isPresent()) {
            translation = optional.get();
            translation.setContent(content);
            DocumentReaderDateTimeUtil.setUpdateTime(translation);
        } else {
            translation = new DocumentTranslation();
            translation.setDocumentId(documentId);
            translation.setTargetLanguage(targetLang);
            translation.setContent(content);
            DocumentReaderDateTimeUtil.setCreateAndUpdateTime(translation);
        }
        translationRepository.save(translation);
    }

    private String detectDocumentLanguage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null; // 空文本无法检测
        }

        String sampleText = text.length() > LANGUAGE_DETECTION_SAMPLE_SIZE
                ? text.substring(0, LANGUAGE_DETECTION_SAMPLE_SIZE)
                : text;

        int totalChars = 0;
        int chineseChars = 0;
        int japaneseChars = 0;
        int koreanChars = 0;
        int englishChars = 0;

        for (char c : sampleText.toCharArray()) {
            // 跳过空白字符和标点符号
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                continue;
            }

            totalChars++;

            // 检测中文（简体中文范围：\u4e00-\u9fa5）
            if (c >= 0x4e00 && c <= 0x9fa5) {
                chineseChars++;
            }
            // 检测日文（平假名：\u3040-\u309F，片假名：\u30A0-\u30FF，日文汉字：\u4E00-\u9FAF）
            else if ((c >= 0x3040 && c <= 0x309F) || (c >= 0x30A0 && c <= 0x30FF) ||
                    (c >= 0x4E00 && c <= 0x9FAF)) {
                japaneseChars++;
            }
            // 检测韩文（\uAC00-\uD7AF）
            else if (c >= 0xAC00 && c <= 0xD7AF) {
                koreanChars++;
            }
            // 检测英文（ASCII字母）
            else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                englishChars++;
            }
        }

        if (totalChars == 0) {
            return null; // 没有有效字符，无法检测
        }

        // 计算各语言占比
        double chineseRatio = (double) chineseChars / totalChars;
        double japaneseRatio = (double) japaneseChars / totalChars;
        double koreanRatio = (double) koreanChars / totalChars;
        double englishRatio = (double) englishChars / totalChars;

        if (chineseRatio >= LANGUAGE_DETECTION_THRESHOLD) {
            return "zh";
        } else if (japaneseRatio >= LANGUAGE_DETECTION_THRESHOLD) {
            return "ja";
        } else if (koreanRatio >= LANGUAGE_DETECTION_THRESHOLD) {
            return "ko";
        } else if (englishRatio >= LANGUAGE_DETECTION_THRESHOLD) {
            return "en";
        }

        // 如果无法确定主要语言，返回 null
        return null;
    }

    /**
     * 检查是否为同种语言翻译（禁止同种语言翻译）
     *
     * @param documentContent 文档内容
     * @param targetLang      目标语言代码
     * @return true 如果是同种语言，false 如果不是
     */
    private boolean isSameLanguageTranslation(String documentContent, String targetLang) {
        String detectedLang = detectDocumentLanguage(documentContent);
        if (detectedLang == null) {
            // 无法检测语言，允许翻译（保守处理）
            return false;
        }
        // 检查检测到的语言是否与目标语言相同
        return detectedLang.equalsIgnoreCase(targetLang);
    }

    /**
     * 翻译单个文本段
     */
    private String translateTextSegment(String textSegment, String targetLang, QAModel qaModel) {
        if (textSegment == null || textSegment.trim().isEmpty()) {
            return textSegment;
        }

        // 获取目标语言名称
        String targetLanguageName = getTargetLanguageName(targetLang);

        String translatePrompt = SkillLoader.loadSkillWithTemplate("document_reader/translate_prompt_template", Map.of(
                "targetLanguageName", targetLanguageName,
                "textSegment", String.valueOf(textSegment)));

        try {
            // 使用大模型进行翻译
            modelLanguageModelFactory.setTraceSource("Document Translation");
            ChatLanguageModel chatModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);
            modelLanguageModelFactory.clearTraceSource();
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new UserMessage(translatePrompt));

            Response<AiMessage> response = chatModel.generate(messages);
            String translatedText = response.content().text();

            logger.info("单段翻译完成，目标语言: {}, 原文长度: {}, 译文长度: {}",
                    targetLanguageName, textSegment.length(), translatedText.length());
            return translatedText;

        } catch (Exception e) {
            logger.error("单段翻译失败，目标语言: {}", targetLanguageName, e);
            throw new BusinessException("翻译失败", ErrorCode.API_CALL_FAILED, e);
        }
    }

    /**
     * 去除文档的页眉页脚
     * 页眉页脚通常出现在文档的开头和结尾，包含页码、标题、日期等信息
     */
    private String removeHeaderFooter(String documentContent) {
        if (documentContent == null || documentContent.trim().isEmpty()) {
            return documentContent;
        }

        String[] lines = documentContent.split("\n");
        if (lines.length <= 3) {
            // 内容太短，不处理
            return documentContent;
        }

        int startIndex = 0;
        int endIndex = lines.length;

        // 识别并去除页眉（通常在前几行）
        // 页眉特征：短行、包含页码、日期、标题等
        int headerLines = 0;
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            // 检查是否是页眉特征
            boolean isHeader = false;

            // 1. 短行（通常页眉较短）
            if (line.length() < 50) {
                // 2. 包含页码模式
                if (line.matches(".*[第]?[\\d]+[页]?.*") ||
                        line.matches(".*[Pp]age\\s*[\\d]+.*") ||
                        line.matches(".*[\\d]+\\s*[页]?.*")) {
                    isHeader = true;
                }
                // 3. 包含日期模式
                else if (line.matches(".*\\d{4}[年\\-/]\\d{1,2}[月\\-/]\\d{1,2}[日]?.*") ||
                        line.matches(".*\\d{1,2}/\\d{1,2}/\\d{4}.*") ||
                        line.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
                    isHeader = true;
                }
                // 4. 只包含数字、标点或少量文字
                else if (line.matches("^[\\d\\s\\p{Punct}]+$") && line.length() < 30) {
                    isHeader = true;
                }
                // 5. 重复出现的短行（可能是页眉）
                else if (i > 0 && line.equals(lines[i - 1].trim()) && line.length() < 40) {
                    isHeader = true;
                }
            }

            if (isHeader) {
                headerLines = i + 1;
            } else {
                // 遇到非页眉内容，停止检查
                break;
            }
        }

        // 识别并去除页脚（通常在最后几行）
        int footerLines = 0;
        for (int i = lines.length - 1; i >= Math.max(lines.length - 5, headerLines); i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            // 检查是否是页脚特征
            boolean isFooter = false;

            // 1. 短行
            if (line.length() < 50) {
                // 2. 包含页码模式
                if (line.matches(".*[第]?[\\d]+[页]?.*") ||
                        line.matches(".*[Pp]age\\s*[\\d]+.*") ||
                        line.matches(".*[\\d]+\\s*[页]?.*")) {
                    isFooter = true;
                }
                // 3. 包含日期模式
                else if (line.matches(".*\\d{4}[年\\-/]\\d{1,2}[月\\-/]\\d{1,2}[日]?.*") ||
                        line.matches(".*\\d{1,2}/\\d{1,2}/\\d{4}.*") ||
                        line.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
                    isFooter = true;
                }
                // 4. 只包含数字、标点或少量文字
                else if (line.matches("^[\\d\\s\\p{Punct}]+$") && line.length() < 30) {
                    isFooter = true;
                }
                // 5. 重复出现的短行（可能是页脚）
                else if (i < lines.length - 1 && line.equals(lines[i + 1].trim()) && line.length() < 40) {
                    isFooter = true;
                }
            }

            if (isFooter) {
                footerLines = lines.length - i;
            } else {
                // 遇到非页脚内容，停止检查
                break;
            }
        }

        // 提取去除页眉页脚后的内容
        startIndex = headerLines;
        endIndex = lines.length - footerLines;

        // 确保至少保留一些内容
        if (endIndex <= startIndex) {
            // 如果去除后没有内容，保留原内容
            return documentContent;
        }

        // 重新组合内容
        StringBuilder result = new StringBuilder();
        for (int i = startIndex; i < endIndex; i++) {
            result.append(lines[i]);
            if (i < endIndex - 1) {
                result.append("\n");
            }
        }

        String cleanedContent = result.toString().trim();

        // 如果去除后内容太少，保留原内容
        if (cleanedContent.length() < documentContent.length() * 0.3) {
            logger.warn("去除页眉页脚后内容过少，保留原内容 - 原长度: {}, 处理后长度: {}",
                    documentContent.length(), cleanedContent.length());
            return documentContent;
        }

        logger.debug("去除页眉页脚 - 原行数: {}, 去除页眉: {}行, 去除页脚: {}行, 处理后行数: {}",
                lines.length, headerLines, footerLines, endIndex - startIndex);

        return cleanedContent;
    }

    /**
     * 将文档按页面分段（用于按页面翻译）- 优化版本
     * 考虑字符数限制，避免分段过大导致翻译超时
     */
    private List<DocumentSegment> splitDocumentForTranslation(String documentContent) {
        List<DocumentSegment> segments = new ArrayList<>();

        if (documentContent == null || documentContent.trim().isEmpty()) {
            logger.warn("文档内容为空，无法分段");
            return segments;
        }

        // 按行分割文档
        String[] lines = documentContent.split("\n", -1);
        int totalLines = lines.length;
        int linesPerPage = TRANSLATION_PAGE_LINES;
        int maxCharsPerSegment = TRANSLATION_SEGMENT_SIZE; // 最大字符数限制
        int segmentIndex = 0;
        int lineStart = 0;

        while (lineStart < totalLines) {
            int lineEnd = Math.min(lineStart + linesPerPage, totalLines);

            // 计算字符位置
            int charStart = 0;
            for (int i = 0; i < lineStart; i++) {
                charStart += lines[i].length() + 1; // +1 for newline
            }

            // 构建页面内容（用于检查字符数）
            StringBuilder pageTextBuilder = new StringBuilder();
            int currentChars = 0;
            int actualLineEnd = lineStart;

            for (int i = lineStart; i < lineEnd; i++) {
                String line = lines[i];
                int lineLength = line.length() + (i > lineStart ? 1 : 0); // +1 for newline except first line

                // 如果添加这一行会超过字符限制，且不是第一行，则停止
                if (currentChars + lineLength > maxCharsPerSegment && i > lineStart) {
                    break;
                }

                if (i > lineStart) {
                    pageTextBuilder.append("\n");
                }
                pageTextBuilder.append(line);
                currentChars += lineLength;
                actualLineEnd = i + 1;
            }

            // 如果实际结束行小于预期，尝试在段落边界处优化截断点
            if (actualLineEnd < totalLines && actualLineEnd < lineEnd) {
                // 向前查找更好的截断点（在空行或句号处）
                int bestLineEnd = actualLineEnd;
                int searchStart = Math.max(lineStart + (actualLineEnd - lineStart) / 2, lineStart + 1);
                for (int i = actualLineEnd - 1; i >= searchStart; i--) {
                    String line = lines[i].trim();
                    // 如果是空行，或者以句号、问号、感叹号结尾
                    if (line.isEmpty() ||
                            line.endsWith("。") || line.endsWith(".") ||
                            line.endsWith("？") || line.endsWith("?") ||
                            line.endsWith("！") || line.endsWith("!")) {
                        bestLineEnd = i + 1;
                        break;
                    }
                }
                actualLineEnd = bestLineEnd;

                // 重新构建页面内容
                pageTextBuilder = new StringBuilder();
                for (int i = lineStart; i < actualLineEnd; i++) {
                    if (i > lineStart) {
                        pageTextBuilder.append("\n");
                    }
                    pageTextBuilder.append(lines[i]);
                }
            }

            // 计算结束位置
            int charEnd = charStart;
            for (int i = lineStart; i < actualLineEnd; i++) {
                charEnd += lines[i].length() + (i < actualLineEnd - 1 ? 1 : 0); // +1 for newline except last line
            }

            String segmentText = pageTextBuilder.toString();
            DocumentSegment segment = new DocumentSegment();
            segment.setIndex(segmentIndex);
            segment.setStartIndex(charStart);
            segment.setEndIndex(charEnd);
            segment.setText(segmentText);
            segment.setTranslatedText(null); // 初始未翻译

            segments.add(segment);
            segmentIndex++;
            lineStart = actualLineEnd; // 移动到下一页
        }

        logger.info("文档按页面分段完成 - 总行数: {}, 每页行数: {}, 最大字符数: {}, 总页数: {}, 平均每段字符数: {}",
                totalLines, linesPerPage, maxCharsPerSegment, segments.size(),
                segments.isEmpty() ? 0 : documentContent.length() / segments.size());

        return segments;
    }

    /**
     * 保存文档分段翻译信息（JSON格式）
     */
    @Transactional
    protected void saveDocumentTranslationSegments(Long documentId, Long userId, String targetLang,
            List<DocumentSegment> segments) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            // 构建分段数据
            Map<String, Object> segmentsData = new HashMap<>();
            segmentsData.put("version", "1.0");
            segmentsData.put("totalSegments", segments.size());
            segmentsData.put("targetLang", targetLang);

            // 按索引排序，确保保存时顺序一致
            List<DocumentSegment> sortedSegments = new ArrayList<>(segments);
            sortedSegments.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));

            List<Map<String, Object>> segmentList = new ArrayList<>();
            for (DocumentSegment segment : sortedSegments) {
                Map<String, Object> segData = new HashMap<>();
                segData.put("index", segment.getIndex());
                segData.put("start", segment.getStartIndex());
                segData.put("end", segment.getEndIndex());
                segData.put("text", segment.getText());
                segData.put("translated", segment.getTranslatedText());
                segmentList.add(segData);
            }
            segmentsData.put("segments", segmentList);

            // 转换为JSON字符串
            String jsonContent = objectMapper.writeValueAsString(segmentsData);

            // 保存到数据库
            saveDocumentTranslation(documentId, userId, targetLang, jsonContent);

        } catch (Exception e) {
            logger.error("保存分段翻译信息失败 - 文档ID: {}, 目标语言: {}", documentId, targetLang, e);
            throw new BusinessException("保存分段翻译信息失败", ErrorCode.DATABASE_ERROR, e);
        }
    }

    /**
     * 加载文档分段翻译信息
     */
    private List<DocumentSegment> loadDocumentTranslationSegments(Long documentId, String targetLang) {
        try {
            Optional<DocumentTranslation> optional = translationRepository.findByDocumentIdAndTargetLanguage(documentId,
                    targetLang);
            if (!optional.isPresent()) {
                return null;
            }

            String content = optional.get().getContent();
            if (content == null || content.trim().isEmpty()) {
                return null;
            }

            // 尝试解析JSON格式的分段数据
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> data = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });

            // 检查是否是分段格式
            if (!data.containsKey("segments") || !data.containsKey("version")) {
                // 旧格式，返回null，让调用者使用旧方法
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> segmentList = (List<Map<String, Object>>) data.get("segments");
            List<DocumentSegment> segments = new ArrayList<>();

            for (Map<String, Object> segData : segmentList) {
                DocumentSegment segment = new DocumentSegment();
                segment.setIndex(((Number) segData.get("index")).intValue());
                segment.setStartIndex(((Number) segData.get("start")).intValue());
                segment.setEndIndex(((Number) segData.get("end")).intValue());
                segment.setText((String) segData.get("text"));
                segment.setTranslatedText((String) segData.get("translated"));
                segments.add(segment);
            }

            // 按索引排序，确保段落顺序一致
            segments.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));

            return segments;

        } catch (Exception e) {
            logger.warn("加载分段翻译信息失败，可能使用旧格式 - 文档ID: {}, 目标语言: {}", documentId, targetLang, e);
            return null;
        }
    }

    /**
     * 文档分段信息（内部类）
     */
    private static class DocumentSegment {
        private int index;
        private int startIndex;
        private int endIndex;
        private String text;
        private String translatedText;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public void setStartIndex(int startIndex) {
            this.startIndex = startIndex;
        }

        public int getEndIndex() {
            return endIndex;
        }

        public void setEndIndex(int endIndex) {
            this.endIndex = endIndex;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getTranslatedText() {
            return translatedText;
        }

        public void setTranslatedText(String translatedText) {
            this.translatedText = translatedText;
        }
    }

    /**
     * 获取目标语言的显示名称
     */
    private String getTargetLanguageName(String targetLang) {
        if (targetLang == null) {
            return "简体中文";
        }

        switch (targetLang.toLowerCase()) {
            case "zh":
            case "zh-cn":
            case "zh_cn":
                return "简体中文";
            case "en":
            case "en-us":
            case "en_us":
                return "英文";
            case "ja":
            case "ja-jp":
            case "ja_jp":
                return "日文";
            case "ko":
            case "ko-kr":
            case "ko_kr":
                return "韩文";
            default:
                return "简体中文"; // 默认返回中文
        }
    }

    private String extractDocumentText(DocumentReader document) {
        return guideMindMapService.extractDocumentText(document);
    }

}
