package com.github.app.dify.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
/**
 * MCP地理位置服务
 * 提供当前地理位置信息，帮助LLM了解用户的地理上下文
 */
@Service
public class McpLocationService {
    
    private static final Logger logger = LoggerFactory.getLogger(McpLocationService.class);
    
    @Autowired
    private McpConfig mcpConfig;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // WebClient实例（复用，避免每次创建）
    // 使用volatile确保多线程环境下的可见性
    private volatile WebClient webClient;
    
    // 缓存地理位置信息
    private CachedLocationInfo cachedLocationInfo;
    
    /**
     * 获取或创建WebClient实例
     * 使用双重检查锁定模式确保线程安全
     */
    private WebClient getWebClient() {
        // 第一次检查（无锁）
        WebClient result = webClient;
        if (result == null) {
            // 同步块，确保只有一个线程能创建实例
            synchronized (this) {
                // 第二次检查（有锁），防止其他线程已经创建了实例
                result = webClient;
                if (result == null) {
                    result = WebClient.builder()
                            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                            .build();
                    webClient = result; // 写入volatile变量，确保对其他线程可见
                    logger.info("McpLocationService WebClient已创建");
                }
            }
        }
        return result;
    }
    
    /**
     * 缓存的地理位置信息
     */
    private static class CachedLocationInfo {
        final LocationInfo locationInfo;
        final long cachedAt;
        
        CachedLocationInfo(LocationInfo locationInfo) {
            this.locationInfo = locationInfo;
            this.cachedAt = System.currentTimeMillis();
        }
        
        boolean isValid(int cacheSeconds) {
            return (System.currentTimeMillis() - cachedAt) < (cacheSeconds * 1000L);
        }
    }
    
    /**
     * 获取当前地理位置信息（带缓存）
     * @return 地理位置信息
     */
    public LocationInfo getCurrentLocation() {
        // 检查服务是否启用
        if (!mcpConfig.getLocation().isEnabled()) {
            logger.debug("地理位置服务已禁用，返回默认位置信息");
            return createDefaultLocationInfo();
        }
        
        // 检查缓存
        int cacheSeconds = mcpConfig.getLocation().getCacheSeconds();
        if (cachedLocationInfo != null && cachedLocationInfo.isValid(cacheSeconds)) {
            logger.debug("使用缓存的地理位置信息");
            return cachedLocationInfo.locationInfo;
        }
        
        try {
            logger.info("开始获取地理位置信息");
            
            // 使用ip-api.com免费API（支持中文，无需API key）
            // 注意：免费版本有请求频率限制（每分钟45次）
            String apiUrl = "http://ip-api.com/json/?lang=zh-CN&fields=status,message,country,countryCode,region,regionName,city,lat,lon,timezone,isp,org,as,query";
            
            int timeout = mcpConfig.getLocation().getTimeout();
            String response = getWebClient()
                    .get()
                    .uri(apiUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeout))
                    .block();
            
            if (response == null || response.isEmpty()) {
                logger.warn("地理位置API返回空响应");
                return createDefaultLocationInfo();
            }
            
            logger.debug("地理位置API响应: {}", response);
            
            // 解析JSON响应
            JsonNode jsonNode = objectMapper.readTree(response);
            
            // 检查状态
            String status = jsonNode.has("status") ? jsonNode.get("status").asText() : "fail";
            if (!"success".equals(status)) {
                String message = jsonNode.has("message") ? jsonNode.get("message").asText() : "未知错误";
                logger.warn("地理位置API返回失败状态: {}", message);
                return createDefaultLocationInfo();
            }
            
            // 构建位置信息
            LocationInfo locationInfo = new LocationInfo();
            locationInfo.setCountry(jsonNode.has("country") ? jsonNode.get("country").asText() : "");
            locationInfo.setCountryCode(jsonNode.has("countryCode") ? jsonNode.get("countryCode").asText() : "");
            locationInfo.setRegion(jsonNode.has("regionName") ? jsonNode.get("regionName").asText() : "");
            locationInfo.setRegionCode(jsonNode.has("region") ? jsonNode.get("region").asText() : "");
            locationInfo.setCity(jsonNode.has("city") ? jsonNode.get("city").asText() : "");
            locationInfo.setLatitude(jsonNode.has("lat") ? jsonNode.get("lat").asDouble() : 0.0);
            locationInfo.setLongitude(jsonNode.has("lon") ? jsonNode.get("lon").asDouble() : 0.0);
            locationInfo.setTimezone(jsonNode.has("timezone") ? jsonNode.get("timezone").asText() : "");
            locationInfo.setIsp(jsonNode.has("isp") ? jsonNode.get("isp").asText() : "");
            locationInfo.setOrg(jsonNode.has("org") ? jsonNode.get("org").asText() : "");
            locationInfo.setIpAddress(jsonNode.has("query") ? jsonNode.get("query").asText() : "");
            
            // 更新缓存
            cachedLocationInfo = new CachedLocationInfo(locationInfo);
            
            logger.info("获取地理位置信息成功 - 国家: {}, 城市: {}", locationInfo.getCountry(), locationInfo.getCity());
            
            return locationInfo;
            
        } catch (Exception e) {
            logger.error("获取地理位置信息失败", e);
            return createDefaultLocationInfo();
        }
    }
    
    /**
     * 创建默认位置信息（当API失败时使用）
     */
    private LocationInfo createDefaultLocationInfo() {
        LocationInfo locationInfo = new LocationInfo();
        locationInfo.setCountry("未知");
        locationInfo.setCountryCode("");
        locationInfo.setRegion("未知");
        locationInfo.setCity("未知");
        locationInfo.setTimezone("UTC");
        return locationInfo;
    }
    
    /**
     * 获取格式化的地理位置信息字符串（用于LLM上下文）
     */
    public String getFormattedLocationInfo() {
        LocationInfo locationInfo = getCurrentLocation();
        
        StringBuilder sb = new StringBuilder();
        sb.append("【当前地理位置信息（MCP地理位置模块）】\n");
        
        if (locationInfo.getCountry() != null && !locationInfo.getCountry().isEmpty() && !"未知".equals(locationInfo.getCountry())) {
            sb.append(String.format("国家：%s", locationInfo.getCountry()));
            if (locationInfo.getCountryCode() != null && !locationInfo.getCountryCode().isEmpty()) {
                sb.append(String.format("（%s）", locationInfo.getCountryCode()));
            }
            sb.append("\n");
        }
        
        if (locationInfo.getRegion() != null && !locationInfo.getRegion().isEmpty() && !"未知".equals(locationInfo.getRegion())) {
            sb.append(String.format("地区/省份：%s", locationInfo.getRegion()));
            if (locationInfo.getRegionCode() != null && !locationInfo.getRegionCode().isEmpty()) {
                sb.append(String.format("（%s）", locationInfo.getRegionCode()));
            }
            sb.append("\n");
        }
        
        if (locationInfo.getCity() != null && !locationInfo.getCity().isEmpty() && !"未知".equals(locationInfo.getCity())) {
            sb.append(String.format("城市：%s\n", locationInfo.getCity()));
        }
        
        if (locationInfo.getLatitude() != 0.0 && locationInfo.getLongitude() != 0.0) {
            sb.append(String.format("经纬度：%.4f, %.4f\n", locationInfo.getLatitude(), locationInfo.getLongitude()));
        }
        
        if (locationInfo.getTimezone() != null && !locationInfo.getTimezone().isEmpty()) {
            sb.append(String.format("时区：%s\n", locationInfo.getTimezone()));
        }
        
        if (locationInfo.getIsp() != null && !locationInfo.getIsp().isEmpty()) {
            sb.append(String.format("网络服务商：%s\n", locationInfo.getIsp()));
        }
        
        sb.append("\n【重要提示】\n");
        sb.append("1. 上述地理位置信息基于IP地址获取，可能不完全准确\n");
        sb.append("2. 当回答涉及地理位置相关的问题时，可以参考上述信息\n");
        sb.append("3. 如果问题涉及特定地区的信息，请优先使用上述地理位置信息\n");
        
        return sb.toString();
    }
    
    /**
     * 地理位置信息数据类
     */
    public static class LocationInfo {
        private String country;          // 国家
        private String countryCode;      // 国家代码
        private String region;           // 地区/省份
        private String regionCode;       // 地区代码
        private String city;             // 城市
        private double latitude;         // 纬度
        private double longitude;        // 经度
        private String timezone;         // 时区
        private String isp;              // 网络服务商
        private String org;              // 组织
        private String ipAddress;        // IP地址
        
        // Getters and Setters
        public String getCountry() {
            return country;
        }
        
        public void setCountry(String country) {
            this.country = country;
        }
        
        public String getCountryCode() {
            return countryCode;
        }
        
        public void setCountryCode(String countryCode) {
            this.countryCode = countryCode;
        }
        
        public String getRegion() {
            return region;
        }
        
        public void setRegion(String region) {
            this.region = region;
        }
        
        public String getRegionCode() {
            return regionCode;
        }
        
        public void setRegionCode(String regionCode) {
            this.regionCode = regionCode;
        }
        
        public String getCity() {
            return city;
        }
        
        public void setCity(String city) {
            this.city = city;
        }
        
        public double getLatitude() {
            return latitude;
        }
        
        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }
        
        public double getLongitude() {
            return longitude;
        }
        
        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }
        
        public String getTimezone() {
            return timezone;
        }
        
        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }
        
        public String getIsp() {
            return isp;
        }
        
        public void setIsp(String isp) {
            this.isp = isp;
        }
        
        public String getOrg() {
            return org;
        }
        
        public void setOrg(String org) {
            this.org = org;
        }
        
        public String getIpAddress() {
            return ipAddress;
        }
        
        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }
    }
}