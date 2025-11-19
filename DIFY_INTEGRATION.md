# Dify集成说明

## 功能概述

本项目实现了Java后端微应用的管理和使用，以及与Dify的对接，包括：

1. **微应用管理**：创建、更新、删除、查询AI应用
2. **Dify集成**：支持Chat Flow和Workflow两种智能体的集成
3. **流式响应**：自动适配Dify应用的流式响应能力

## 主要功能

### 1. 微应用管理

- **创建应用**：`POST /api/ai-apps`
- **更新应用**：`PUT /api/ai-apps/{id}`
- **查询应用**：`GET /api/ai-apps/{id}`
- **删除应用**：`DELETE /api/ai-apps/{id}`
- **应用列表**：`GET /api/ai-apps?tenantId=1&type=1&status=1`

### 2. Dify集成

#### Chat Flow
- **非流式调用**：`POST /api/ai-apps/{id}/chat`
- **流式调用**：`POST /api/ai-apps/{id}/chat/stream`

#### Workflow
- **非流式调用**：`POST /api/ai-apps/{id}/workflow`
- **流式调用**：`POST /api/ai-apps/{id}/workflow/stream`

## 配置说明

### application.yml配置

```yaml
dify:
  api:
    default-base-url: http://localhost:5001  # 默认Dify API Base URL
    timeout: 30000                            # 超时时间（毫秒）
    connect-timeout: 10000                    # 连接超时时间（毫秒）
```

### 应用类型

- `1`: Chat Flow
- `2`: Workflow

### 应用状态

- `0`: 禁用
- `1`: 启用

## 使用示例

### 1. 创建Chat Flow应用

```json
POST /api/ai-apps
{
  "name": "智能客服",
  "description": "智能客服助手",
  "type": 1,
  "appId": "app-xxxxxxxxxxxx",
  "apiBaseUrl": "http://localhost:5001",
  "streamEnabled": true,
  "tenantId": 1
}
```

### 2. 调用Chat Flow（非流式）

```json
POST /api/ai-apps/1/chat
{
  "query": "你好",
  "userId": "user123",
  "conversationId": "conv123",
  "stream": false
}
```

### 3. 调用Chat Flow（流式）

```json
POST /api/ai-apps/1/chat/stream
{
  "query": "你好",
  "userId": "user123",
  "conversationId": "conv123"
}
```

响应格式为Server-Sent Events (SSE)：

```
data: {"event":"message","answer":"你好！","finished":false}
data: {"event":"message","answer":"有什么可以帮助你的吗？","finished":true}
```

### 4. 调用Workflow（非流式）

```json
POST /api/ai-apps/2/workflow
{
  "userId": "user123",
  "inputs": {
    "input1": "value1",
    "input2": "value2"
  },
  "stream": false
}
```

### 5. 调用Workflow（流式）

```json
POST /api/ai-apps/2/workflow/stream
{
  "userId": "user123",
  "inputs": {
    "input1": "value1",
    "input2": "value2"
  }
}
```

## 流式响应自动适配

系统会自动检测应用是否支持流式响应：

1. 如果应用配置了`streamEnabled=true`，且请求中`stream=true`，则使用流式响应
2. 如果应用不支持流式响应，系统会自动降级为非流式响应
3. 流式响应接口（`/stream`）要求应用必须支持流式响应，否则会返回错误

## API文档

启动应用后，访问Swagger文档：

```
http://localhost:8081/swagger-ui.html
```

## 注意事项

1. 确保Dify服务正常运行并可访问
2. 确保API Key有效且有相应权限
3. 流式响应需要客户端支持SSE（Server-Sent Events）
4. 应用类型必须与Dify中的实际应用类型匹配

