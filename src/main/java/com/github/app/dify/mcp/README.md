# MCP (Model Context Protocol) 模块

本包包含所有 MCP 协议相关的功能实现，为 LLM 提供外部数据源和上下文信息。

## 模块说明

### McpBrowserSearchService
浏览器检索服务，提供网络搜索功能。

**功能：**
- 使用 SearX-NG 本地部署实现网络搜索
- 自动优化搜索查询，添加时间限制以获取最新信息
- 解析搜索结果并格式化为 LLM 可用的上下文
- 支持时效性检查，帮助判断信息是否过期
- WebClient 复用，提高性能
- 支持配置化（SearX-NG 地址、超时时间等）

**配置项（application.yml）：**
```yaml
mcp:
  browser-search:
    searxng-base-url: http://localhost:10086  # SearX-NG服务地址
    timeout: 10                               # 请求超时时间（秒）
    default-max-results: 5                    # 默认最大搜索结果数
    enable-query-optimization: true           # 是否启用查询优化
```

**使用方式：**
```java
@Autowired
private McpBrowserSearchService mcpBrowserSearchService;

List<McpBrowserSearchService.SearchResult> results = 
    mcpBrowserSearchService.search("查询内容", 5);
String formattedResults = 
    mcpBrowserSearchService.formatSearchResultsForContext(results);
```

### McpTimeService
时间服务，提供当前时间信息。

**功能：**
- 获取当前时间信息（日期、时间、年份、星期等）
- 支持自定义时区（默认：Asia/Shanghai）
- 提供格式化的时间信息字符串，便于 LLM 使用
- 包含中文星期显示
- 时间信息缓存（减少频繁获取）

**配置项（application.yml）：**
```yaml
mcp:
  time:
    default-time-zone: Asia/Shanghai  # 默认时区
    cache-seconds: 1                  # 时间信息缓存时间（秒）
```

**使用方式：**
```java
@Autowired
private McpTimeService mcpTimeService;

McpTimeService.TimeInfo timeInfo = mcpTimeService.getCurrentTime();
String formattedTimeInfo = mcpTimeService.getFormattedTimeInfo();
```

### McpLocationService
地理位置服务，提供当前地理位置信息。

**功能：**
- 基于IP地址获取地理位置信息
- 获取国家、地区、城市、经纬度等信息
- 获取时区和网络服务商信息
- 提供格式化的地理位置信息字符串，便于 LLM 使用
- 地理位置信息缓存（减少API调用）
- WebClient 复用，提高性能

**配置项（application.yml）：**
```yaml
mcp:
  location:
    enabled: true        # 是否启用地理位置服务
    cache-seconds: 3600  # 地理位置信息缓存时间（秒，默认1小时）
    timeout: 10         # 请求超时时间（秒）
```

**使用方式：**
```java
@Autowired
private McpLocationService mcpLocationService;

McpLocationService.LocationInfo locationInfo = mcpLocationService.getCurrentLocation();
String formattedLocationInfo = mcpLocationService.getFormattedLocationInfo();
```

**注意：**
- 使用 ip-api.com 免费API（支持中文，无需API key）
- 免费版本有请求频率限制（每分钟45次）
- 地理位置信息基于IP地址，可能不完全准确
- 地理位置信息会缓存1小时，减少API调用

### McpRealtimeInfoDetector
实时信息检测器，智能检测问题是否涉及实时信息。

**功能：**
- 智能检测问题是否涉及实时信息（不局限于关键词）
- 提供置信度评分（0.0-1.0）
- 支持多种检测方法（时间关键词、实时信息类型、模式匹配等）
- 可配置的置信度阈值
- 优化的关键词集合（使用HashSet提高查找效率）

**配置项（application.yml）：**
```yaml
mcp:
  realtime-info-detector:
    enabled: true              # 是否启用实时信息检测
    confidence-threshold: 0.5  # 检测置信度阈值（0.0-1.0）
```

**使用方式：**
```java
@Autowired
private McpRealtimeInfoDetector mcpRealtimeInfoDetector;

boolean isRealtime = mcpRealtimeInfoDetector.isRealtimeInfoQuestion("问题内容");
double confidence = mcpRealtimeInfoDetector.getRealtimeInfoConfidence("问题内容");
```

## 配置说明

所有 MCP 服务的配置都通过 `McpConfig` 类统一管理，支持通过 `application.yml` 进行配置。

**完整配置示例：**
```yaml
mcp:
  browser-search:
    searxng-base-url: http://localhost:10086
    timeout: 10
    default-max-results: 5
    enable-query-optimization: true
  location:
    enabled: true
    cache-seconds: 3600
    timeout: 10
  time:
    default-time-zone: Asia/Shanghai
    cache-seconds: 1
  realtime-info-detector:
    enabled: true
    confidence-threshold: 0.5
```

## 性能优化

1. **WebClient 复用**：`McpBrowserSearchService` 和 `McpLocationService` 都使用单例 WebClient，避免重复创建
2. **缓存机制**：
   - 时间信息缓存1秒（避免频繁获取）
   - 地理位置信息缓存1小时（减少API调用）
3. **优化的数据结构**：`McpRealtimeInfoDetector` 使用 HashSet 存储关键词，提高查找效率

## 扩展说明

如需添加新的 MCP 功能模块，请遵循以下规范：

1. **命名规范**：使用 `Mcp` 前缀，如 `McpXxxService`
2. **包位置**：所有 MCP 相关服务都应放在 `com.github.app.dify.mcp` 包下
3. **注解**：使用 `@Service` 注解标记为 Spring 服务
4. **配置**：在 `McpConfig` 类中添加相应的配置类
5. **日志**：使用 SLF4J 记录日志
6. **异常处理**：合理处理异常，避免影响主流程
7. **性能优化**：考虑使用缓存和 WebClient 复用

## 未来扩展

可以添加的 MCP 模块：
- McpWeatherService：天气查询服务
- McpCalculatorService：计算器服务
- McpFileSystemService：文件系统操作服务
- McpDatabaseService：数据库查询服务
- McpTranslationService：翻译服务
- 等等...

## 更新日志

### v2.0 (最新)
- ✅ 添加统一的配置类 `McpConfig`
- ✅ 优化 `McpBrowserSearchService`：WebClient 复用、配置化
- ✅ 优化 `McpTimeService`：添加缓存机制
- ✅ 优化 `McpLocationService`：添加缓存、WebClient 复用、配置化
- ✅ 优化 `McpRealtimeInfoDetector`：使用配置的置信度阈值
- ✅ 改进所有服务的错误处理和日志记录
- ✅ 更新文档，反映所有优化

### v1.0
- 初始版本，包含基本的 MCP 功能

