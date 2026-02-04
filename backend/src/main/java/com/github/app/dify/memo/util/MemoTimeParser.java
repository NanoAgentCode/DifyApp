package com.github.app.dify.memo.util;

import java.time.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简单规则解析自然语言时间，用于备忘录提醒时间。
 * 支持：X分钟后、X小时后（含阿拉伯数字与中文数字如一、三、十、半小时）、明天X点、今天X点、后天X点 等。
 */
public final class MemoTimeParser {

    private MemoTimeParser() {
    }

    private static final ZoneId ZONE = ZoneId.systemDefault();

    // 数字：阿拉伯数字或中文数字（一 二 两 三 四 五 六 七 八 九 十 十一… 三十…）
    private static final String NUM = "(\\d+|[一二两三四五六七八九十百]+)";

    // 时间前缀：早上|上午|下午|晚上|凌晨...
    private static final String TIME_PREFIX = "(?:早上|上午|中午|下午|晚上|凌晨)?";

    // (\d+|[一二两三四五六七八九十百]+)\s*分钟\s*[以之]?后
    private static final Pattern MINUTES_LATER = Pattern.compile(NUM + "\\s*分钟\\s*[以之]?后");
    // (\d+|[一二两三四五六七八九十百]+)\s*小时\s*[以之]?后
    private static final Pattern HOURS_LATER = Pattern.compile(NUM + "\\s*小时\\s*[以之]?后");
    // 半小时后
    private static final Pattern HALF_HOUR_LATER = Pattern.compile("半\\s*小时\\s*[以之]?后");
    // (\d+|[一二两三四五六七八九十百]+)\s*天\s*[以之]?后
    private static final Pattern DAYS_LATER = Pattern.compile(NUM + "\\s*天\\s*[以之]?后");

    // (今天|明天|后天)? TIME_PREFIX (\d+)点 (半|(\d+)分)?
    private static final Pattern FULL_TIME_PATTERN = Pattern.compile(
            "(今天|明天|后天)?\\s*" + TIME_PREFIX + "\\s*" + NUM + "\\s*点\\s*(半|(?:" + NUM + "\\s*分?))?");

    // 每X分钟 / 每X小时（周期性）
    private static final Pattern EVERY_MINUTES = Pattern.compile("每\\s*" + NUM + "\\s*分钟");
    private static final Pattern EVERY_HOURS = Pattern.compile("每\\s*" + NUM + "\\s*小时");

    // 提醒|备忘|记一下|记着 等关键词后的内容
    private static final Pattern REMIND_PREFIX = Pattern.compile("^(?:提醒我?|备忘|记一下|记着|到时提醒)\\s*[:：]?\\s*",
            Pattern.CASE_INSENSITIVE);

    /**
     * 从自然语言中解析出提醒时间和内容。
     * 支持周期性：每40分钟提醒我喝水 -> remindAt=now+40min, intervalMinutes=40, content=喝水
     *
     * @param rawInput 用户输入，如 "30分钟后提醒我开会"、"每40分钟提醒我喝水"
     * @return 解析结果；若无法解析时间则 content 为整句，remindAt 为 null（调用方可拒绝创建或使用默认时间）
     */
    public static ParseResult parse(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return new ParseResult(null, rawInput != null ? rawInput.trim() : "", null);
        }
        String s = rawInput.trim();
        String contentOnly = REMIND_PREFIX.matcher(s).replaceFirst("").trim();
        if (contentOnly.isEmpty()) {
            contentOnly = s;
        }

        ZonedDateTime remindAt = null;
        Integer intervalMinutes = null;

        // 0) 每X分钟（周期，首次 X 分钟后）
        Matcher mEvery = EVERY_MINUTES.matcher(s);
        if (mEvery.find()) {
            Integer mins = parseNumber(mEvery.group(1));
            if (mins != null && mins > 0 && mins <= 60 * 24) {
                intervalMinutes = mins;
                remindAt = ZonedDateTime.now(ZONE).plusMinutes(mins);
                contentOnly = removeTimePhrase(s, mEvery.group(0), contentOnly);
            }
        }

        // 0b) 每X小时（周期，首次 X 小时后）
        if (remindAt == null) {
            mEvery = EVERY_HOURS.matcher(s);
            if (mEvery.find()) {
                Integer hours = parseNumber(mEvery.group(1));
                if (hours != null && hours > 0 && hours <= 24 * 7) {
                    intervalMinutes = hours * 60;
                    remindAt = ZonedDateTime.now(ZONE).plusHours(hours);
                    contentOnly = removeTimePhrase(s, mEvery.group(0), contentOnly);
                }
            }
        }

        // 1) 半小时后
        Matcher m = HALF_HOUR_LATER.matcher(s);
        if (remindAt == null && m.find()) {
            remindAt = ZonedDateTime.now(ZONE).plusMinutes(30);
            contentOnly = removeTimePhrase(s, m.group(0), contentOnly);
        }

        // 2) X分钟后
        if (remindAt == null) {
            m = MINUTES_LATER.matcher(s);
            if (m.find()) {
                Integer minutes = parseNumber(m.group(1));
                if (minutes != null && minutes > 0 && minutes <= 60 * 24 * 7) {
                    remindAt = ZonedDateTime.now(ZONE).plusMinutes(minutes);
                    contentOnly = removeTimePhrase(s, m.group(0), contentOnly);
                }
            }
        }

        // 2b) X小时后
        if (remindAt == null) {
            m = HOURS_LATER.matcher(s);
            if (m.find()) {
                Integer hours = parseNumber(m.group(1));
                if (hours != null && hours > 0 && hours <= 24 * 7) {
                    remindAt = ZonedDateTime.now(ZONE).plusHours(hours);
                    contentOnly = removeTimePhrase(s, m.group(0), contentOnly);
                }
            }
        }

        // 3) X天后
        if (remindAt == null) {
            m = DAYS_LATER.matcher(s);
            if (m.find()) {
                Integer days = parseNumber(m.group(1));
                if (days != null && days > 0 && days <= 30) {
                    remindAt = ZonedDateTime.now(ZONE).plusDays(days);
                    contentOnly = removeTimePhrase(s, m.group(0), contentOnly);
                }
            }
        }

        // 4) 综合时间解析 (今天|明天|后天)? (早上|上午|下午|晚上)? X点 (半|Y分)?
        if (remindAt == null) {
            m = FULL_TIME_PATTERN.matcher(s);
            if (m.find()) {
                String dayPart = m.group(1);
                Integer hour = parseNumber(m.group(2));
                String minutePart = m.group(3);

                if (hour != null) {
                    // 处理上下午带来的 24 小时转换
                    if (s.contains("下午") || s.contains("晚上")) {
                        if (hour < 12)
                            hour += 12;
                    } else if (s.contains("凌晨") || s.contains("早上") || s.contains("上午")) {
                        if (hour == 12)
                            hour = 0;
                    }

                    int minute = 0;
                    if (minutePart != null) {
                        if (minutePart.equals("半")) {
                            minute = 30;
                        } else {
                            // 去掉可能的“分”后缀再解析
                            String minStr = minutePart.replace("分", "").trim();
                            Integer parsedMin = parseNumber(minStr);
                            if (parsedMin != null)
                                minute = parsedMin;
                        }
                    }

                    if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                        LocalDate baseDate = LocalDate.now(ZONE);
                        if ("明天".equals(dayPart)) {
                            baseDate = baseDate.plusDays(1);
                        } else if ("后天".equals(dayPart)) {
                            baseDate = baseDate.plusDays(2);
                        }

                        remindAt = baseDate.atTime(hour, minute).atZone(ZONE);

                        // 如果没有指定日期且时间已过，则默认为明天
                        if (dayPart == null && remindAt.isBefore(ZonedDateTime.now(ZONE))) {
                            remindAt = remindAt.plusDays(1);
                        }

                        contentOnly = removeTimePhrase(s, m.group(0), contentOnly);
                    }
                }
            }
        }

        String finalContent = contentOnly != null && !contentOnly.isEmpty() ? contentOnly : s;
        Date remindAtDate = remindAt != null ? Date.from(remindAt.toInstant()) : null;
        return new ParseResult(remindAtDate, finalContent, intervalMinutes);
    }

    private static String removeTimePhrase(String full, String timePhrase, String contentFallback) {
        String without = full.replace(timePhrase, "").trim();
        without = REMIND_PREFIX.matcher(without).replaceFirst("").trim();
        return without.isEmpty() ? contentFallback : without;
    }

    /**
     * 解析数字：纯阿拉伯数字直接解析；否则按中文数字解析（一 二 两 三… 十 十一 二十 三十 等）。
     */
    private static Integer parseNumber(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        String t = s.trim();
        if (t.matches("\\d+")) {
            return Integer.parseInt(t);
        }
        return parseChineseNumber(t);
    }

    private static Integer parseChineseNumber(String s) {
        if (s == null || s.isEmpty())
            return null;
        int n = 0;
        int curr = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '零':
                    break;
                case '一':
                    curr = 1;
                    break;
                case '二':
                case '两':
                    curr = 2;
                    break;
                case '三':
                    curr = 3;
                    break;
                case '四':
                    curr = 4;
                    break;
                case '五':
                    curr = 5;
                    break;
                case '六':
                    curr = 6;
                    break;
                case '七':
                    curr = 7;
                    break;
                case '八':
                    curr = 8;
                    break;
                case '九':
                    curr = 9;
                    break;
                case '十':
                    if (curr == 0)
                        curr = 1;
                    n += curr * 10;
                    curr = 0;
                    break;
                case '百':
                    if (curr == 0)
                        curr = 1;
                    n += curr * 100;
                    curr = 0;
                    break;
                default:
                    return null;
            }
        }
        n += curr;
        return n > 0 ? n : null;
    }

    public static class ParseResult {
        private final Date remindAt;
        private final String content;
        private final Integer intervalMinutes;

        public ParseResult(Date remindAt, String content, Integer intervalMinutes) {
            this.remindAt = remindAt;
            this.content = content != null ? content.trim() : "";
            this.intervalMinutes = intervalMinutes;
        }

        public Date getRemindAt() {
            return remindAt;
        }

        public String getContent() {
            return content;
        }

        public Integer getIntervalMinutes() {
            return intervalMinutes;
        }
    }
}
