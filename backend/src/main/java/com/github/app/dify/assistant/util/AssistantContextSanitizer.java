package com.github.app.dify.assistant.util;

import com.github.app.dify.assistant.req.AssistantChatReq;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 对前端页面上下文做白名单清洗、HTML剥离和长度裁剪。
 */
@Component
public class AssistantContextSanitizer {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_SELECTION_LENGTH = 2000;
    private static final int MAX_SECTION_LENGTH = 3000;
    private static final int MAX_TOTAL_SECTION_LENGTH = 9000;
    private static final int MAX_HISTORY_ITEMS = 8;
    private static final int MAX_HISTORY_CONTENT_LENGTH = 1200;
    private static final int MAX_SUMMARY_SELECTION_LENGTH = 500;
    private static final int MAX_SUMMARY_SECTION_LENGTH = 600;
    private static final int MAX_SUMMARY_TOTAL_SECTION_LENGTH = 2400;
    private static final int MAX_SUMMARY_SECTIONS = 6;

    public String sanitizeMessage(String message) {
        return truncate(cleanText(message), MAX_MESSAGE_LENGTH);
    }

    public String sanitizeContextHash(String contextHash) {
        return truncate(cleanText(contextHash), 128);
    }

    public AssistantChatReq.AssistantPageContext sanitizePageContext(AssistantChatReq.AssistantPageContext context) {
        if (context == null) {
            return null;
        }

        AssistantChatReq.AssistantPageContext sanitized = new AssistantChatReq.AssistantPageContext();
        sanitized.setSource(truncate(cleanText(context.getSource()), 40));

        if (context.getPage() != null) {
            AssistantChatReq.PageInfo page = new AssistantChatReq.PageInfo();
            page.setRoute(truncate(cleanText(context.getPage().getRoute()), 300));
            page.setTitle(truncate(cleanText(context.getPage().getTitle()), 120));
            page.setType(truncate(cleanText(context.getPage().getType()), 80));
            sanitized.setPage(page);
        }

        if (context.getSelection() != null) {
            AssistantChatReq.SelectionInfo selection = new AssistantChatReq.SelectionInfo();
            selection.setText(truncate(cleanText(context.getSelection().getText()), MAX_SELECTION_LENGTH));
            sanitized.setSelection(selection);
        }

        List<AssistantChatReq.SectionInfo> sections = new ArrayList<>();
        int totalLength = 0;
        if (context.getSections() != null) {
            for (AssistantChatReq.SectionInfo section : context.getSections()) {
                if (section == null || totalLength >= MAX_TOTAL_SECTION_LENGTH) {
                    continue;
                }
                String content = truncate(cleanText(section.getContent()),
                        Math.min(MAX_SECTION_LENGTH, MAX_TOTAL_SECTION_LENGTH - totalLength));
                if (content == null || content.isBlank()) {
                    continue;
                }
                AssistantChatReq.SectionInfo sanitizedSection = new AssistantChatReq.SectionInfo();
                sanitizedSection.setType(truncate(cleanText(section.getType()), 40));
                sanitizedSection.setTitle(truncate(cleanText(section.getTitle()), 120));
                sanitizedSection.setContent(content);
                sections.add(sanitizedSection);
                totalLength += content.length();
            }
        }
        sanitized.setSections(sections);

        HashMap<String, Object> meta = new HashMap<>();
        meta.put("truncated", totalLength >= MAX_TOTAL_SECTION_LENGTH);
        sanitized.setMeta(meta);
        return sanitized;
    }

    public AssistantChatReq.AssistantPageContext summarizePageContext(AssistantChatReq.AssistantPageContext context) {
        if (context == null) {
            return null;
        }

        AssistantChatReq.AssistantPageContext summary = new AssistantChatReq.AssistantPageContext();
        summary.setSource(truncate(cleanText(context.getSource()), 40));

        if (context.getPage() != null) {
            AssistantChatReq.PageInfo page = new AssistantChatReq.PageInfo();
            page.setRoute(truncate(cleanText(context.getPage().getRoute()), 300));
            page.setTitle(truncate(cleanText(context.getPage().getTitle()), 120));
            page.setType(truncate(cleanText(context.getPage().getType()), 80));
            summary.setPage(page);
        }

        if (context.getSelection() != null) {
            AssistantChatReq.SelectionInfo selection = new AssistantChatReq.SelectionInfo();
            selection.setText(truncate(cleanText(context.getSelection().getText()), MAX_SUMMARY_SELECTION_LENGTH));
            summary.setSelection(selection);
        }

        List<AssistantChatReq.SectionInfo> sections = new ArrayList<>();
        int totalLength = 0;
        if (context.getSections() != null) {
            for (AssistantChatReq.SectionInfo section : context.getSections()) {
                if (section == null || sections.size() >= MAX_SUMMARY_SECTIONS || totalLength >= MAX_SUMMARY_TOTAL_SECTION_LENGTH) {
                    break;
                }
                String content = truncate(cleanText(section.getContent()),
                        Math.min(MAX_SUMMARY_SECTION_LENGTH, MAX_SUMMARY_TOTAL_SECTION_LENGTH - totalLength));
                if (content == null || content.isBlank()) {
                    continue;
                }

                AssistantChatReq.SectionInfo summarySection = new AssistantChatReq.SectionInfo();
                summarySection.setType(truncate(cleanText(section.getType()), 40));
                summarySection.setTitle(truncate(cleanText(section.getTitle()), 120));
                summarySection.setContent(content);
                sections.add(summarySection);
                totalLength += content.length();
            }
        }
        summary.setSections(sections);

        HashMap<String, Object> meta = new HashMap<>();
        meta.put("summary", true);
        meta.put("summarySections", sections.size());
        meta.put("summaryLength", totalLength);
        summary.setMeta(meta);
        return summary;
    }

    public List<AssistantChatReq.AssistantMessage> sanitizeHistory(List<AssistantChatReq.AssistantMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, history.size() - MAX_HISTORY_ITEMS);
        List<AssistantChatReq.AssistantMessage> result = new ArrayList<>();
        for (AssistantChatReq.AssistantMessage item : history.subList(from, history.size())) {
            if (item == null) {
                continue;
            }
            String role = cleanText(item.getRole());
            if (!"user".equalsIgnoreCase(role) && !"assistant".equalsIgnoreCase(role)) {
                continue;
            }
            String content = truncate(cleanText(item.getContent()), MAX_HISTORY_CONTENT_LENGTH);
            if (content == null || content.isBlank()) {
                continue;
            }
            AssistantChatReq.AssistantMessage msg = new AssistantChatReq.AssistantMessage();
            msg.setRole(role.toLowerCase());
            msg.setContent(content);
            result.add(msg);
        }
        return result;
    }

    private String cleanText(String text) {
        if (text == null) {
            return null;
        }
        return text
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("(?i)(api[_-]?key|token|password|secret)\\s*[:=]\\s*\\S+", "$1: [已脱敏]")
                .replaceAll("[\\u0000-\\u001F\\u007F]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 20)) + "...[已截断]";
    }
}
