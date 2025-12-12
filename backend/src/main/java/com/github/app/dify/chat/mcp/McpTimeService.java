package com.github.app.dify.chat.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * MCP时间服务
 * 提供当前时间信息，帮助LLM了解时间上下文
 */
@Service
public class McpTimeService {
    
    private static final Logger logger = LoggerFactory.getLogger(McpTimeService.class);
    
    @Autowired
    private McpConfig mcpConfig;
    
    // 缓存时间信息（key: 时区ID, value: 缓存的时间信息和过期时间）
    private final Map<String, CachedTimeInfo> timeInfoCache = new ConcurrentHashMap<>();
    
    /**
     * 获取当前时间信息（使用配置的默认时区）
     */
    public TimeInfo getCurrentTime() {
        return getCurrentTime(mcpConfig.getTime().getDefaultTimeZone());
    }
    
    /**
     * 获取指定时区的当前时间信息（带缓存）
     * @param timeZoneId 时区ID，如 "Asia/Shanghai", "America/New_York", "UTC" 等
     */
    public TimeInfo getCurrentTime(String timeZoneId) {
        try {
            // 检查缓存
            int cacheSeconds = mcpConfig.getTime().getCacheSeconds();
            CachedTimeInfo cached = timeInfoCache.get(timeZoneId);
            if (cached != null && cached.isValid(cacheSeconds)) {
                logger.debug("使用缓存的时间信息 - 时区: {}", timeZoneId);
                return cached.timeInfo;
            }
            
            // 获取新的时间信息
            ZoneId zoneId = ZoneId.of(timeZoneId);
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            
            TimeInfo timeInfo = new TimeInfo();
            timeInfo.setDateTime(now);
            timeInfo.setYear(now.getYear());
            timeInfo.setMonth(now.getMonthValue());
            timeInfo.setDay(now.getDayOfMonth());
            timeInfo.setHour(now.getHour());
            timeInfo.setMinute(now.getMinute());
            timeInfo.setSecond(now.getSecond());
            timeInfo.setDayOfWeek(now.getDayOfWeek().toString());
            timeInfo.setTimeZone(timeZoneId);
            timeInfo.setTimestamp(now.toEpochSecond());
            timeInfo.setFormattedDateTime(formatDateTime(now));
            timeInfo.setFormattedDate(formatDate(now));
            timeInfo.setFormattedTime(formatTime(now));
            
            // 更新缓存
            timeInfoCache.put(timeZoneId, new CachedTimeInfo(timeInfo));
            
            logger.debug("获取当前时间信息 - 时区: {}, 时间: {}", timeZoneId, timeInfo.getFormattedDateTime());
            
            return timeInfo;
            
        } catch (Exception e) {
            logger.error("获取时间信息失败 - 时区: {}", timeZoneId, e);
            // 返回UTC时间作为备用
            if (!"UTC".equals(timeZoneId)) {
                return getCurrentTime("UTC");
            }
            // 如果UTC也失败，返回一个基本的时间信息
            return createDefaultTimeInfo();
        }
    }
    
    /**
     * 创建默认时间信息（当获取失败时使用）
     */
    private TimeInfo createDefaultTimeInfo() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setDateTime(now);
        timeInfo.setYear(now.getYear());
        timeInfo.setMonth(now.getMonthValue());
        timeInfo.setDay(now.getDayOfMonth());
        timeInfo.setHour(now.getHour());
        timeInfo.setMinute(now.getMinute());
        timeInfo.setSecond(now.getSecond());
        timeInfo.setDayOfWeek(now.getDayOfWeek().toString());
        timeInfo.setTimeZone("UTC");
        timeInfo.setTimestamp(now.toEpochSecond());
        timeInfo.setFormattedDateTime(formatDateTime(now));
        timeInfo.setFormattedDate(formatDate(now));
        timeInfo.setFormattedTime(formatTime(now));
        return timeInfo;
    }
    
    /**
     * 缓存的时间信息
     */
    private static class CachedTimeInfo {
        final TimeInfo timeInfo;
        final long cachedAt;
        
        CachedTimeInfo(TimeInfo timeInfo) {
            this.timeInfo = timeInfo;
            this.cachedAt = System.currentTimeMillis();
        }
        
        boolean isValid(int cacheSeconds) {
            return (System.currentTimeMillis() - cachedAt) < (cacheSeconds * 1000L);
        }
    }
    
    /**
     * 格式化日期时间
     */
    private String formatDateTime(ZonedDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return dateTime.format(formatter);
    }
    
    /**
     * 格式化日期
     */
    private String formatDate(ZonedDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return dateTime.format(formatter);
    }
    
    /**
     * 格式化时间
     */
    private String formatTime(ZonedDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        return dateTime.format(formatter);
    }
    
    /**
     * 获取格式化的时间信息字符串（用于LLM上下文，使用配置的默认时区）
     */
    public String getFormattedTimeInfo() {
        return getFormattedTimeInfo(mcpConfig.getTime().getDefaultTimeZone());
    }
    
    /**
     * 获取格式化的时间信息字符串（用于LLM上下文）
     */
    public String getFormattedTimeInfo(String timeZoneId) {
        TimeInfo timeInfo = getCurrentTime(timeZoneId);
        
        StringBuilder sb = new StringBuilder();
        sb.append("【当前时间信息（MCP时间模块）】\n");
        sb.append(String.format("当前日期时间：%s %s（%s）\n", 
                timeInfo.getFormattedDate(), 
                timeInfo.getFormattedTime(),
                getDayOfWeekChinese(timeInfo.getDayOfWeek())));
        sb.append(String.format("当前年份：%d年\n", timeInfo.getYear()));
        sb.append(String.format("当前月份：%d月\n", timeInfo.getMonth()));
        sb.append(String.format("当前日期：%d日\n", timeInfo.getDay()));
        sb.append(String.format("时区：%s\n", timeInfo.getTimeZone()));
        sb.append(String.format("Unix时间戳：%d\n", timeInfo.getTimestamp()));
        sb.append("\n【重要提示】\n");
        sb.append("1. 请始终使用上述当前时间信息来判断任何信息的时效性\n");
        sb.append("2. 当回答涉及时间、日期相关的问题时，必须基于当前时间进行回答\n");
        sb.append("3. 当判断信息是否过期时，请使用当前年份（").append(timeInfo.getYear()).append("年）作为参考\n");
        sb.append("4. 如果信息中的日期是2023年或更早，且当前年份是").append(timeInfo.getYear()).append("年，则该信息可能已过期\n");
        
        return sb.toString();
    }
    
    /**
     * 将英文星期转换为中文
     */
    private String getDayOfWeekChinese(String dayOfWeek) {
        Map<String, String> dayMap = new HashMap<>();
        dayMap.put("MONDAY", "星期一");
        dayMap.put("TUESDAY", "星期二");
        dayMap.put("WEDNESDAY", "星期三");
        dayMap.put("THURSDAY", "星期四");
        dayMap.put("FRIDAY", "星期五");
        dayMap.put("SATURDAY", "星期六");
        dayMap.put("SUNDAY", "星期日");
        
        return dayMap.getOrDefault(dayOfWeek, dayOfWeek);
    }
    
    /**
     * 时间信息数据类
     */
    public static class TimeInfo {
        private ZonedDateTime dateTime;
        private int year;
        private int month;
        private int day;
        private int hour;
        private int minute;
        private int second;
        private String dayOfWeek;
        private String timeZone;
        private long timestamp;
        private String formattedDateTime;
        private String formattedDate;
        private String formattedTime;
        
        // Getters and Setters
        public ZonedDateTime getDateTime() {
            return dateTime;
        }
        
        public void setDateTime(ZonedDateTime dateTime) {
            this.dateTime = dateTime;
        }
        
        public int getYear() {
            return year;
        }
        
        public void setYear(int year) {
            this.year = year;
        }
        
        public int getMonth() {
            return month;
        }
        
        public void setMonth(int month) {
            this.month = month;
        }
        
        public int getDay() {
            return day;
        }
        
        public void setDay(int day) {
            this.day = day;
        }
        
        public int getHour() {
            return hour;
        }
        
        public void setHour(int hour) {
            this.hour = hour;
        }
        
        public int getMinute() {
            return minute;
        }
        
        public void setMinute(int minute) {
            this.minute = minute;
        }
        
        public int getSecond() {
            return second;
        }
        
        public void setSecond(int second) {
            this.second = second;
        }
        
        public String getDayOfWeek() {
            return dayOfWeek;
        }
        
        public void setDayOfWeek(String dayOfWeek) {
            this.dayOfWeek = dayOfWeek;
        }
        
        public String getTimeZone() {
            return timeZone;
        }
        
        public void setTimeZone(String timeZone) {
            this.timeZone = timeZone;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
        
        public String getFormattedDateTime() {
            return formattedDateTime;
        }
        
        public void setFormattedDateTime(String formattedDateTime) {
            this.formattedDateTime = formattedDateTime;
        }
        
        public String getFormattedDate() {
            return formattedDate;
        }
        
        public void setFormattedDate(String formattedDate) {
            this.formattedDate = formattedDate;
        }
        
        public String getFormattedTime() {
            return formattedTime;
        }
        
        public void setFormattedTime(String formattedTime) {
            this.formattedTime = formattedTime;
        }
    }
}