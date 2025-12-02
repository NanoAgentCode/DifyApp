# DifyApp

一个基于 Spring Boot 和 Vue 3 的 Dify AI 应用管理系统，提供完整的 AI 应用管理、知识库管理、Chat Flow 和 Workflow 调用功能。

## 项目简介

DifyApp 是一个集成了 Dify AI 平台的应用管理系统，支持：

- **用户认证与授权**：JWT 认证、用户注册、登录、角色管理（管理员/普通用户）
- **AI 应用管理**：创建、编辑、删除和查看 AI 应用
- **Chat Flow 调用**：支持流式和非流式聊天对话
- **Workflow 调用**：支持流式和非流式工作流执行
- **知识库管理**：创建、管理知识库，上传、删除文档，支持选择向量化模型
- **RAG 问答**：基于 LangChain4j 的检索增强生成，支持流式和非流式问答，支持选择问答模型
- **文档向量化**：自动文档解析、分块、向量化存储
- **大模型管理**：动态管理问答模型和向量化模型，支持多种提供商（OpenAI、Ollama、VLLM 等），支持设置默认模型和启用/禁用状态
- **对话历史管理**：完整的对话会话管理，支持会话记录、消息历史、继续对话、开启新对话
- **文件存储**：基于 MinIO 的对象存储
- **向量存储**：支持 Qdrant 向量数据库和 FAISS 本地文件存储，可在知识库级别选择
- **缓存系统**：基于 Redis 的缓存架构，提升查询性能
- **多租户支持**：支持多租户应用和知识库管理
- **权限控制**：用户对应用和知识库的可见性管理
- **前端界面**：提供管理端、用户端和应用端三个界面

## 技术栈

### 后端
- **Java**: JDK 1.8
- **框架**: Spring Boot 2.6.13
- **数据库**: PostgreSQL 15
- **ORM**: Spring Data JPA / Hibernate
- **构建工具**: Maven
- **API 文档**: Swagger 2.9.2
- **响应式编程**: Spring WebFlux (用于流式响应)
- **HTTP 客户端**: WebClient
- **认证**: JWT (JSON Web Token)
- **密码加密**: BCrypt
- **对象存储**: MinIO 8.5.2
- **向量数据库**: Qdrant 1.7.0（可选）
- **向量存储**: FAISS（本地文件存储，可选）
- **缓存中间件**: Redis (Spring Data Redis)
- **缓存框架**: Spring Cache
- **RAG 框架**: LangChain4j 0.34.0
- **文档解析**: Apache Tika 2.9.1

### 前端
- **框架**: Vue 3.3+
- **路由**: Vue Router 4
- **状态管理**: Pinia
- **UI 组件库**: Element Plus 2.4+
- **HTTP 客户端**: Axios
- **构建工具**: Vite 5
- **Markdown 渲染**: marked
- **代码高亮**: highlight.js
- **数学公式**: KaTeX

## 项目结构

```
DifyApp/
├── src/main/
│   ├── java/com/github/app/dify/
│   │   ├── config/              # 配置类（数据库、MinIO、Qdrant、Redis、Swagger等）
│   │   ├── controller/          # 控制器层（REST API）
│   │   ├── domain/               # 实体类（User、AiApp、KnowledgeBase、QAModel、EmbeddingModel等）
│   │   ├── repository/           # 数据访问层（JPA Repository）
│   │   ├── service/              # 服务层（业务逻辑）
│   │   ├── langchain4j/          # LangChain4j集成（RAG相关）
│   │   ├── req/                  # 请求对象（DTO）
│   │   ├── resp/                 # 响应对象（DTO）
│   │   └── util/                 # 工具类
│   └── resources/
│       ├── application.yml       # 应用配置文件
│       ├── sql/                  # SQL脚本
│       └── static/               # 前端静态资源（Vue 3项目）
│           └── src/
│               ├── api/          # API接口封装
│               ├── components/   # 公共组件
│               ├── layouts/      # 布局组件（管理端/用户端/应用端）
│               ├── views/        # 页面组件
│               ├── router/       # 路由配置
│               └── utils/        # 工具函数
└── pom.xml                      # Maven配置文件
```

## 环境要求

### 必需环境
- JDK 1.8 或更高版本
- Maven 3.6+
- PostgreSQL 15
- MinIO (对象存储服务)
- Redis (缓存服务，推荐 6.0+)

### 可选环境（向量存储）
- **Qdrant** (向量数据库，推荐用于生产环境)
- **FAISS** (本地文件存储，无需额外服务，适合开发测试环境)

### 可选环境（用于前端开发）
- Node.js 16+
- Yarn 或 npm

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd DifyApp
```

### 2. 启动依赖服务

#### 启动 PostgreSQL

**方式一：使用 Docker 启动 PostgreSQL with pgvector（推荐）**

```bash
docker run -d \
  -p 15432:5432 \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=postgres \
  --name pgvector \
  ankane/pgvector:latest
```

然后创建应用数据库：

```sql
CREATE DATABASE difyapp;
```

**方式二：本地安装 PostgreSQL**

确保 PostgreSQL 15 已安装并运行，然后创建数据库：

```sql
CREATE DATABASE difyapp;
```

#### 启动 MinIO

使用 Docker 启动 MinIO：

```bash
docker run -d \
  --name minio \
  -p 9000:9000 \
  -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data --console-address ":9001"
```

访问 MinIO 控制台：http://localhost:9001

#### 启动 Qdrant（可选，用于向量存储）

使用 Docker 启动 Qdrant：

```bash
docker run -d \
  --name qdrant \
  -p 6333:6333 \
  -p 6334:6334 \
  qdrant/qdrant
```

访问 Qdrant 控制台：http://localhost:6333/dashboard

> **注意**：如果使用 FAISS 作为向量存储，则不需要启动 Qdrant 服务。FAISS 使用本地文件存储，无需额外服务。

#### 启动 Redis

使用 Docker 启动 Redis：

```bash
docker run -d \
  --name redis \
  -p 6379:6379 \
  redis:latest
```

如果需要设置密码：

```bash
docker run -d  --name redis -p 6379:6379 redis:latest   redis-server
```

### 3. 配置应用

编辑 `src/main/resources/application.yml`，根据您的环境修改配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:15432/difyapp
    username: postgres
    password: 123456

# Dify配置
dify:
  api:
    default-base-url: http://localhost:80
    timeout: 30000
    connect-timeout: 10000
    file-url-prefix: http://localhost:80

# JWT配置
jwt:
  secret: dify-app-secret-key-2024  # 生产环境请修改为更安全的密钥
  expiration: 604800000  # 7天

# MinIO配置
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket-name: knowledge-base

# Qdrant配置（可选，如果使用Qdrant作为向量存储）
qdrant:
  url: http://localhost:6333
  api-key: # 可选，Docker部署默认不需要
  timeout: 30000

# FAISS配置（可选，如果使用FAISS作为向量存储）
faiss:
  base-path: ./data/faiss  # FAISS索引文件的基础存储路径

# Redis配置
spring:
  redis:
    host: localhost
    port: 6379
    password: # 可选，如果Redis设置了密码，请在此配置
    database: 0
    timeout: 3000
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        max-wait: -1ms

# 向量化配置
embedding:
  api-url: https://api.siliconflow.cn/v1/embeddings
  api-key: your-api-key
  model: Qwen/Qwen3-Embedding-8B
  timeout: 300000
  batch-size: 100

# RAG配置
rag:
  chunk-size: 500
  chunk-overlap: 50
  top-k: 10
  similarity-threshold: 0.3
  llm-api-url: https://api.siliconflow.cn
  llm-api-key: your-api-key
  llm-model: Qwen/Qwen2.5-72B-Instruct
```

### 4. 初始化数据库

#### 方式一：使用 SQL 脚本（推荐用于生产环境）

```bash
# 初始化基础表结构
psql -U postgres -h localhost -p 15432 -d difyapp -f src/main/resources/sql/init_database.sql

# 添加向量存储类型字段（如果使用新版本）
psql -U postgres -h localhost -p 15432 -d difyapp -f src/main/resources/sql/add_vector_store_type_to_knowledge_base.sql
```

#### 方式二：使用 Hibernate 自动创建（推荐用于开发环境）

确保 `application.yml` 中配置了 `ddl-auto: update`，启动应用时会自动创建表结构。

**默认管理员账户：**
- 用户名：`admin`
- 密码：`admin123`
- 角色：管理员（role=1）

⚠️ **重要提示：生产环境请务必修改默认管理员密码！**

### 5. 构建后端

```bash
mvn clean install
```

### 6. 运行后端应用

```bash
mvn spring-boot:run
```

或者使用打包后的 JAR：

```bash
java -jar target/DifyApp-0.0.1-SNAPSHOT.jar
```

应用启动后，后端服务运行在 `http://localhost:8081`

### 7. 前端开发（可选）

如果需要单独开发前端：

```bash
cd src/main/resources/static
yarn install
yarn dev
```

前端开发服务器运行在 `http://localhost:3000`

### 8. 访问应用

- **后端 API**: http://localhost:8081
- **Swagger API 文档**: http://localhost:8081/swagger-ui.html
- **前端应用**: http://localhost:8081 (生产环境) 或 http://localhost:3000 (开发环境)

## API 文档

### 认证 API

#### 1. 用户注册

```
POST /api/auth/register
Content-Type: application/json
```

**请求体：**
```json
{
  "username": "testuser",
  "password": "password123",
  "email": "test@example.com"
}
```

#### 2. 用户登录

```
POST /api/auth/login
Content-Type: application/json
```

**请求体：**
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**响应示例：**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": 1,
    "username": "admin",
    "role": 1,
    "status": 1
  }
}
```

#### 3. 修改密码

```
POST /api/auth/change-password
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体：**
```json
{
  "oldPassword": "oldpassword",
  "newPassword": "newpassword"
}
```

#### 4. 管理员重置用户密码

```
POST /api/auth/reset-password/{userId}
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体：**
```json
{
  "newPassword": "newpassword"
}
```

#### 5. 获取所有用户列表（管理员）

```
GET /api/auth/users
Authorization: Bearer {token}
```

#### 6. 管理员审核用户

```
POST /api/auth/approve/{userId}
Authorization: Bearer {token}
```

#### 7. 管理员禁用用户

```
POST /api/auth/disable/{userId}
Authorization: Bearer {token}
```

#### 8. 更新用户角色

```
PUT /api/auth/users/{userId}/role?role=1
Authorization: Bearer {token}
```

### AI 应用管理 API

#### 1. 创建 AI 应用

```
POST /api/ai-apps
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体：**
```json
{
  "name": "我的AI应用",
  "description": "应用描述",
  "type": 1,
  "appId": "app-xxx",
  "apiBaseUrl": "http://localhost:80",
  "apiKey": "app-xxx-key",
  "tenantId": 1,
  "streamEnabled": true,
  "fileUploadEnabled": false,
  "inputEnabled": true,
  "themeColor": "#409EFF",
  "inputs": "{\"key\":\"value\"}"
}
```

#### 2. 更新 AI 应用

```
PUT /api/ai-apps/{id}
Authorization: Bearer {token}
Content-Type: application/json
```

#### 3. 获取 AI 应用详情

```
GET /api/ai-apps/{id}
Authorization: Bearer {token}
```

#### 4. 删除 AI 应用

```
DELETE /api/ai-apps/{id}
Authorization: Bearer {token}
```

#### 5. 获取应用列表

```
GET /api/ai-apps?tenantId=1&type=1&status=1
Authorization: Bearer {token}
```

**查询参数：**
- `tenantId` (可选): 租户ID
- `type` (可选): 应用类型 (1-ChatFlow, 2-Workflow)
- `status` (可选): 应用状态

#### 6. 获取 Dify 配置信息

```
GET /api/ai-apps/config
Authorization: Bearer {token}
```

### Chat Flow API

#### 1. 调用 Chat Flow（非流式）

```
POST /api/ai-apps/{id}/chat
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体：**
```json
{
  "query": "你好",
  "user": "user-123",
  "inputs": {},
  "conversationId": 123,
  "response_mode": "blocking"
}
```

**响应示例：**
```json
{
  "answer": "你好！我是AI助手...",
  "finished": true,
  "conversationId": 123
}
```

#### 2. 调用 Chat Flow（流式）

```
POST /api/ai-apps/{id}/chat/stream
Authorization: Bearer {token}
Content-Type: application/json
Accept: text/event-stream
```

**响应格式：** Server-Sent Events (SSE)

**响应数据格式：**
```
data: {"answer":"你好","finished":false,"conversationId":123}
data: {"answer":"！","finished":false,"conversationId":123}
data: {"answer":"","finished":true,"conversationId":123}
```

### Workflow API

#### 1. 调用 Workflow（非流式）

```
POST /api/ai-apps/{id}/workflow
Authorization: Bearer {token}
Content-Type: application/json
X-Trace-Id: trace-123 (可选)
```

**请求体：**
```json
{
  "inputs": {
    "key": "value"
  },
  "user": "user-123",
  "response_mode": "blocking"
}
```

#### 2. 调用 Workflow（流式）

```
POST /api/ai-apps/{id}/workflow/stream
Authorization: Bearer {token}
Content-Type: application/json
Accept: text/event-stream
X-Trace-Id: trace-123 (可选)
```

**响应格式：** Server-Sent Events (SSE)

### 文件上传 API

#### 上传文件到 Dify

```
POST /api/ai-apps/{id}/files/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**表单数据：**
- `file`: 文件（必填）
- `user`: 用户ID（可选）

**响应示例：**
```json
{
  "id": "file-123",
  "name": "example.pdf",
  "size": 1024,
  "url": "http://localhost:80/files/file-123"
}
```

### 知识库管理 API

#### 1. 创建知识库

```
POST /api/knowledge-bases
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体：**
```json
{
  "name": "我的知识库",
  "description": "知识库描述",
  "tenantId": 1,
  "embeddingModelId": 1
}
```

**请求体：**
```json
{
  "name": "我的知识库",
  "description": "知识库描述",
  "tenantId": 1,
  "embeddingModelId": 1,
  "vectorStoreType": "qdrant"
}
```

**请求参数说明：**
- `name` (必填): 知识库名称
- `description` (可选): 知识库描述
- `tenantId` (可选): 租户ID
- `embeddingModelId` (可选): 向量化模型ID，不指定则使用默认向量化模型
- `vectorStoreType` (可选): 向量存储类型，可选值：`qdrant`（默认）、`faiss`。已有文档的知识库无法修改此参数

#### 2. 更新知识库

```
PUT /api/knowledge-bases/{id}
Authorization: Bearer {token}
Content-Type: application/json
```

#### 3. 获取知识库详情

```
GET /api/knowledge-bases/{id}
Authorization: Bearer {token}
```

#### 4. 删除知识库

```
DELETE /api/knowledge-bases/{id}
Authorization: Bearer {token}
```

#### 5. 获取知识库列表

```
GET /api/knowledge-bases?tenantId=1&status=1&keyword=搜索关键词&userId=1
Authorization: Bearer {token}
```

### 知识库文档管理 API

#### 1. 上传文档

```
POST /api/knowledge-bases/{kbId}/documents/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**表单数据：**
- `file`: 文件（必填，支持 PDF、Word、TXT、Markdown 等格式）
- `uploadUser`: 上传用户（可选）
- `tenantId`: 租户ID（可选）

**响应示例：**
```json
{
  "id": 1,
  "originalFileName": "example.pdf",
  "fileSize": 1024000,
  "mimeType": "application/pdf",
  "vectorizedStatus": "processing",
  "uploadTime": "2024-01-01T00:00:00"
}
```

#### 2. 删除文档

```
DELETE /api/knowledge-bases/{kbId}/documents/{docId}
Authorization: Bearer {token}
```

#### 3. 获取文档列表

```
GET /api/knowledge-bases/{kbId}/documents
Authorization: Bearer {token}
```

#### 4. 获取文档详情

```
GET /api/knowledge-bases/{kbId}/documents/{docId}
Authorization: Bearer {token}
```

#### 5. 下载文档

```
GET /api/knowledge-bases/{kbId}/documents/{docId}/download
Authorization: Bearer {token}
```

#### 6. 重新向量化文档

```
POST /api/knowledge-bases/{kbId}/documents/{docId}/reindex
Authorization: Bearer {token}
```

### 知识库问答 API

#### 1. 知识库问答（非流式）

```
POST /api/knowledge-bases/{kbId}/qa
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体：**
```json
{
  "question": "什么是人工智能？",
  "conversationId": "conversation-123",
  "modelId": 1
}
```

**请求参数说明：**
- `question` (必填): 问题内容
- `conversationId` (可选): 会话ID，用于连续对话
- `modelId` (可选): 问答模型ID，不指定则使用默认模型

**响应示例：**
```json
{
  "answer": "人工智能是...",
  "sources": [
    {
      "content": "相关文档内容...",
      "metadata": {
        "documentId": 1,
        "chunkIndex": 0
      }
    }
  ],
  "finished": true
}
```

#### 2. 知识库问答（流式）

```
POST /api/knowledge-bases/{kbId}/qa/stream
Authorization: Bearer {token}
Content-Type: application/json
Accept: text/event-stream
```

**响应格式：** Server-Sent Events (SSE)

**响应数据格式：**
```
data: {"answer":"人工智能是...","finished":false,"conversationId":"123"}
data: {"answer":"一种技术...","finished":false,"conversationId":"123"}
data: {"answer":"","finished":true,"conversationId":"123"}
```

### 对话历史管理 API

#### 1. 获取我的会话列表（用户端）

```
GET /api/chat/history/conversations?page=1&size=20&keyword=搜索关键词&type=1
Authorization: Bearer {token}
```

**查询参数：**
- `page` (可选): 页码，默认 1
- `size` (可选): 每页数量，默认 20
- `keyword` (可选): 搜索关键词
- `type` (可选): 会话类型 (1-普通聊天, 2-知识库问答)

**响应示例：**
```json
{
  "content": [
    {
      "id": 1,
      "appId": 1,
      "knowledgeBaseId": null,
      "type": 1,
      "title": "会话标题",
      "messageCount": 5,
      "lastMessageTime": "2024-01-01T12:00:00",
      "createdTime": "2024-01-01T10:00:00"
    }
  ],
  "totalElements": 10,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

#### 2. 获取会话消息列表

```
GET /api/chat/history/conversations/{conversationId}/messages
Authorization: Bearer {token}
```

**响应示例：**
```json
[
  {
    "id": 1,
    "conversationId": 1,
    "role": "user",
    "content": "你好",
    "createdTime": "2024-01-01T10:00:00"
  },
  {
    "id": 2,
    "conversationId": 1,
    "role": "assistant",
    "content": "你好！我是AI助手...",
    "createdTime": "2024-01-01T10:00:01"
  }
]
```

#### 3. 删除会话

```
DELETE /api/chat/history/conversations/{conversationId}
Authorization: Bearer {token}
```

#### 4. 获取所有会话列表（管理员）

```
GET /api/admin/chat/history/conversations?page=1&size=20&keyword=搜索关键词&userId=1&type=1
Authorization: Bearer {token}
```

**查询参数：**
- `page` (可选): 页码，默认 1
- `size` (可选): 每页数量，默认 20
- `keyword` (可选): 搜索关键词
- `userId` (可选): 用户ID
- `type` (可选): 会话类型 (1-普通聊天, 2-知识库问答)

#### 5. 获取对话历史统计（管理员）

```
GET /api/admin/chat/history/statistics
Authorization: Bearer {token}
```

**响应示例：**
```json
{
  "totalConversations": 100,
  "totalMessages": 500,
  "todayConversations": 10,
  "todayMessages": 50
}
```

### 用户权限管理 API

#### 1. 获取用户的应用可见性列表

```
GET /api/auth/users/{userId}/app-visibilities
Authorization: Bearer {token}
```

#### 2. 更新用户对应用的可见性

```
PUT /api/auth/users/{userId}/app-visibilities/{appId}?visible=true
Authorization: Bearer {token}
```

#### 3. 获取用户的知识库可见性列表

```
GET /api/auth/users/{userId}/knowledge-base-visibilities
Authorization: Bearer {token}
```

#### 4. 更新用户对知识库的可见性

```
PUT /api/auth/users/{userId}/knowledge-base-visibilities/{knowledgeBaseId}?visible=true
Authorization: Bearer {token}
```

### 大模型管理 API

#### 1. 获取问答模型列表

```
GET /api/models/qa
Authorization: Bearer {token}
```

#### 2. 获取向量化模型列表

```
GET /api/models/embedding
Authorization: Bearer {token}
```

#### 3. 获取可用的 RAG 问答模型

```
GET /api/models/qa/available/rag
Authorization: Bearer {token}
```

#### 4. 更新模型配置（添加/更新/删除/设置默认/切换启用状态）

```
POST /api/models/config
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体示例（添加模型）：**
```json
{
  "action": "add",
  "type": "qa",
  "model": {
    "name": "Qwen2.5-72B",
    "provider": "openai",
    "apiUrl": "https://api.siliconflow.cn",
    "apiKey": "your-api-key",
    "model": "Qwen/Qwen2.5-72B-Instruct",
    "useFor": "both",
    "enabled": true
  }
}
```

**请求体示例（设置默认模型）：**
```json
{
  "action": "setDefault",
  "type": "qa",
  "modelId": 1,
  "useFor": "rag"
}
```

**请求体示例（切换启用状态）：**
```json
{
  "action": "toggleEnabled",
  "type": "qa",
  "modelId": 1,
  "enabled": true
}
```

**操作类型说明：**
- `action`: 操作类型，可选值：`add`（添加）、`update`（更新）、`delete`（删除）、`setDefault`（设置默认）、`toggleEnabled`（切换启用状态）
- `type`: 模型类型，可选值：`qa`（问答模型）、`embedding`（向量化模型）
- `useFor`: 使用场景（仅问答模型），可选值：`chat`（智能问答）、`rag`（知识库问答）、`both`（两者都支持）

#### 5. 测试模型连接

```
POST /api/models/test
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体：**
```json
{
  "type": "qa",
  "provider": "openai",
  "apiUrl": "https://api.siliconflow.cn",
  "apiKey": "your-api-key",
  "model": "Qwen/Qwen2.5-72B-Instruct"
}
```

## 功能特性

### 后端功能

- ✅ **用户认证与授权**
  - JWT 认证
  - 用户注册、登录
  - 密码加密（BCrypt）
  - 角色管理（管理员/普通用户）
  - 用户审核机制
  - 密码修改和重置

- ✅ **AI 应用管理**
  - 完整的 CRUD 操作
  - 支持 Chat Flow 和 Workflow 两种应用类型
  - 流式和非流式响应支持
  - 文件上传功能
  - 多租户支持

- ✅ **大模型管理**
  - 问答模型和向量化模型的动态管理
  - 支持多种提供商（OpenAI、Ollama、VLLM 等）
  - 问答模型支持使用场景配置（智能问答、知识库问答、两者都支持）
  - 支持设置默认模型（按使用场景区分）
  - 支持启用/禁用模型状态
  - 自动处理默认模型切换逻辑
  - 模型连接测试功能
  - 数据库驱动的动态配置，无需重启应用

- ✅ **知识库管理**
  - 知识库 CRUD 操作
  - 文档上传、删除、下载
  - 支持多种文档格式（PDF、Word、TXT、Markdown 等）
  - 文档自动解析（Apache Tika）
  - 文档分块和向量化
  - 向量存储：支持 Qdrant 和 FAISS 两种存储方式，可在知识库级别选择
    - **Qdrant**：分布式向量数据库，适合生产环境，需要独立服务
    - **FAISS**：本地文件存储，无需额外服务，适合开发测试环境
  - 支持为知识库选择向量化模型

- ✅ **RAG 问答**
  - 基于 LangChain4j 的检索增强生成
  - 流式和非流式问答
  - 相似度检索
  - 可配置的检索参数
  - 会话ID管理，支持连续对话
  - 支持选择问答模型
  - 向量化模型状态检查和警告提示

- ✅ **对话历史管理**
  - 完整的会话和消息记录
  - 会话列表查询（分页、搜索、筛选）
  - 消息历史查询
  - 继续对话功能
  - 开启新对话功能
  - 会话删除功能
  - 管理员查看所有用户对话历史
  - 对话历史统计

- ✅ **文件存储**
  - MinIO 对象存储
  - 文件上传、下载
  - 大文件支持（单个文件最大 100MB）

- ✅ **缓存系统**
  - 基于 Redis 的缓存架构
  - 使用 Spring Cache 注解简化缓存操作
  - 自动缓存用户信息、模型配置、AI应用信息
  - 数据更新时自动清除相关缓存
  - 支持缓存过期策略（默认1小时）
  - 提供统一的缓存服务工具类

- ✅ **其他功能**
  - 全局异常处理
  - Swagger API 文档
  - 异步请求支持（最长 10 分钟）
  - 用户权限管理（应用和知识库可见性）

### 前端功能

- ✅ **认证界面**
  - 用户登录
  - 用户注册
  - 密码修改

- ✅ **管理端界面**
  - 应用列表、创建、编辑、删除
  - 用户管理（审核、禁用、重置密码、角色管理）
  - 大模型管理（问答模型和向量化模型的配置、默认设置、状态管理）
  - 智能问答
  - 对话历史管理
  - 知识库管理（支持选择向量化模型和向量存储类型）
  - 知识库问答（支持选择问答模型，显示向量化模型状态）
  - 用户权限管理（应用和知识库可见性）

- ✅ **用户端界面**
  - 应用列表（仅显示有权限的应用）
  - 智能问答
  - 对话历史管理
  - 知识库管理（仅显示有权限的知识库，支持创建和编辑知识库，支持选择向量化模型和向量存储类型）
  - 知识库问答（支持选择问答模型，显示向量化模型状态）

- ✅ **应用端界面**
  - Chat Flow 交互界面
  - Workflow 交互界面
  - 流式响应实时显示
  - Markdown 渲染和代码高亮
  - 文件上传支持
  - 响应式设计

- ✅ **对话历史界面**
  - 用户端：卡片式布局展示会话历史
  - 管理端：表格形式展示所有用户会话
  - 支持 Markdown 渲染、代码高亮、数学公式渲染
  - 支持继续对话、开启新对话
  - 支持会话搜索、筛选、排序
  - 响应式设计，自适应布局

- ✅ **UI/UX 增强**
  - 模型标识动态颜色显示，便于区分不同模型
  - 单选按钮选择默认模型，直观易用
  - 开关按钮控制模型启用状态
  - 模型状态警告提示（向量化模型禁用时提示）
  - 知识库列表布局优化

## 配置说明

### 数据库配置

应用使用 Hibernate 的 `ddl-auto: update` 模式，启动时会自动创建或更新数据库表结构。

### JWT 配置

在 `application.yml` 中配置 JWT：

```yaml
jwt:
  secret: your-secret-key  # 生产环境请使用强密钥
  expiration: 604800000    # Token过期时间（毫秒），默认7天
```

### MinIO 配置

在 `application.yml` 中配置 MinIO：

```yaml
minio:
  endpoint: http://localhost:9000
  access-key: your-access-key
  secret-key: your-secret-key
  bucket-name: knowledge-base  # 存储桶名称
```

### Qdrant 配置（可选）

在 `application.yml` 中配置 Qdrant（如果使用 Qdrant 作为向量存储）：

```yaml
qdrant:
  url: http://localhost:6333
  api-key: # 可选，仅在启用认证时需要
  timeout: 30000
```

### FAISS 配置（可选）

在 `application.yml` 中配置 FAISS（如果使用 FAISS 作为向量存储）：

```yaml
faiss:
  base-path: ./data/faiss  # FAISS索引文件的基础存储路径
```

**FAISS 文件存储说明：**
- 每个知识库的向量数据存储在独立目录中：`{basePath}/kb_{knowledgeBaseId}/`
- 元数据文件：`metadata.json`，包含向量数据、文本内容和元信息
- 默认路径：`./data/faiss`（相对于应用运行目录）
- 支持自定义路径（可使用绝对路径）

**向量存储类型选择：**
- 创建知识库时可以选择使用 Qdrant 或 FAISS
- 已有文档的知识库无法修改向量存储类型
- 默认使用 Qdrant（向后兼容）

### Redis 配置

在 `application.yml` 中配置 Redis：

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: # 可选，如果Redis设置了密码，请在此配置
    database: 0
    timeout: 3000
    lettuce:
      pool:
        max-active: 8      # 最大连接数
        max-idle: 8        # 最大空闲连接数
        min-idle: 0        # 最小空闲连接数
        max-wait: -1ms     # 最大等待时间（-1表示无限等待）
```

**缓存说明**：
- 系统会自动缓存以下数据：
  - 用户信息（根据用户ID和用户名）
  - 模型配置（问答模型、向量化模型）
  - AI应用信息（根据应用ID和API Key）
- 缓存默认过期时间为 1 小时
- 数据更新时会自动清除相关缓存，保证数据一致性

**容错机制**：
- ✅ **Redis 不可用时系统仍能正常工作**
  - 如果 Redis 连接失败，系统会自动降级到 `NoOpCacheManager`（不缓存）
  - 所有查询将直接访问数据库，功能完全正常
  - 缓存操作失败时不会抛出异常，只记录警告日志
  - 应用启动时如果 Redis 不可用，会记录错误日志但不会阻止应用启动
- ⚠️ **注意事项**：
  - Redis 不可用时，系统性能会下降（无法利用缓存加速）
  - 建议在生产环境中确保 Redis 正常运行以获得最佳性能
  - 可以通过日志查看 Redis 连接状态和缓存操作情况

### 大模型配置

> ⚠️ **重要提示**：系统现已支持通过数据库动态管理大模型配置，无需修改配置文件。以下配置仅作为兼容性保留，建议通过管理端界面配置模型。

#### 通过管理端配置（推荐）

1. 登录管理端
2. 进入"大模型管理"页面
3. 在"问答模型"或"向量化模型"标签页中添加、编辑模型
4. 支持配置：
   - 模型名称、提供商类型（OpenAI、Ollama、VLLM 等）
   - API 地址、API Key
   - 模型标识
   - 使用场景（仅问答模型：智能问答、知识库问答、两者都支持）
   - 启用状态
   - 默认模型设置

#### 配置文件方式（兼容性保留）

> 💡 **提示**：详细的配置模板请参考 `src/main/resources/application-provider-template.yml` 文件，其中包含了所有支持的 provider 类型的完整配置示例。

在 `application.yml` 中配置向量化服务（仅作为兼容性保留）：

**默认配置（OpenAI 兼容格式，包括 SiliconFlow、VLLM 等）：**

```yaml
embedding:
  api-url: https://api.siliconflow.cn/v1/embeddings  # 或只配置基础URL: https://api.siliconflow.cn
  api-key: your-api-key
  model: Qwen/Qwen3-Embedding-8B
  timeout: 300000  # 5分钟超时，支持大文档向量化
  batch-size: 100
  # provider: openai  # 默认值，可省略
```

**使用 Ollama：**

```yaml
embedding:
  api-url: http://localhost:11434  # Ollama 服务地址（不包含路径）
  api-key: # Ollama 不需要 API Key，可留空
  model: nomic-embed-text  # Ollama 模型名称
  timeout: 300000
  batch-size: 100
  provider: ollama  # 指定使用 Ollama
```

**使用 VLLM（兼容 OpenAI）：**

```yaml
embedding:
  api-url: http://localhost:8000  # VLLM 服务地址（不包含路径）
  api-key: # VLLM 通常不需要 API Key，除非启用了认证
  model: Qwen/Qwen3-Embedding-8B
  timeout: 300000
  batch-size: 100
  provider: openai  # 或省略，VLLM 兼容 OpenAI API
```

### RAG 配置

> ⚠️ **重要提示**：RAG 配置现已通过数据库动态管理。以下配置仅作为兼容性保留，建议通过管理端界面配置模型。

在 `application.yml` 中配置 RAG（仅作为兼容性保留）：

**默认配置（OpenAI 兼容格式，包括 SiliconFlow、VLLM 等）：**

```yaml
rag:
  chunk-size: 500              # 文档分块大小
  chunk-overlap: 50             # 分块重叠大小
  top-k: 10                     # 检索数量
  similarity-threshold: 0.3     # 相似度阈值
  llm-api-url: https://api.siliconflow.cn  # 或只配置基础URL
  llm-api-key: your-api-key
  llm-model: Qwen/Qwen2.5-72B-Instruct
  # provider: openai  # 默认值，可省略
```

**使用 Ollama：**

```yaml
rag:
  chunk-size: 500
  chunk-overlap: 50
  top-k: 10
  similarity-threshold: 0.3
  llm-api-url: http://localhost:11434  # Ollama 服务地址（不包含路径）
  llm-api-key: # Ollama 不需要 API Key，可留空
  llm-model: qwen2.5:72b  # Ollama 模型名称
  provider: ollama  # 指定使用 Ollama
```

**使用 VLLM（兼容 OpenAI）：**

```yaml
rag:
  chunk-size: 500
  chunk-overlap: 50
  top-k: 10
  similarity-threshold: 0.3
  llm-api-url: http://localhost:8000  # VLLM 服务地址（不包含路径）
  llm-api-key: # VLLM 通常不需要 API Key，除非启用了认证
  llm-model: Qwen/Qwen2.5-72B-Instruct
  provider: openai  # 或省略，VLLM 兼容 OpenAI API
```

### Dify 配置

在 `application.yml` 中配置 Dify 相关参数：

```yaml
dify:
  api:
    default-base-url: http://your-dify-server:80
    timeout: 30000
    connect-timeout: 10000
    file-url-prefix: http://your-dify-server:80
```

### 生产环境建议

在生产环境中，建议：

1. 将 `ddl-auto` 设置为 `validate` 或 `none`
2. 使用 Flyway 或 Liquibase 进行数据库版本管理
3. 关闭 SQL 日志输出（设置 `show-sql: false`）
4. 配置合适的连接池参数
5. 设置强 JWT 密钥
6. 配置 MinIO 和 Qdrant 的认证（如果使用 Qdrant）
7. 设置正确的 Dify API Base URL 和文件 URL 前缀
8. 配置合适的文件上传大小限制
9. **向量存储选择**：
   - 生产环境推荐使用 Qdrant（分布式、高性能）
   - 开发测试环境可使用 FAISS（无需额外服务）
   - 如果使用 FAISS，确保存储目录有足够的磁盘空间和写入权限
   - 定期备份 FAISS 存储目录（`data/faiss`）
9. **Redis 配置**：
   - 设置 Redis 密码以提高安全性
   - 配置 Redis 持久化（RDB 或 AOF）
   - 考虑使用 Redis 集群或哨兵模式以提高可用性
   - 根据实际负载调整连接池参数
   - 监控 Redis 内存使用情况
   - ⚠️ **重要**：即使 Redis 配置错误或不可用，系统仍能正常工作，但性能会下降

## 开发

### 运行测试

```bash
mvn test
```

### 打包应用

```bash
mvn clean package
```

打包后的 JAR 文件位于 `target/DifyApp-0.0.1-SNAPSHOT.jar`

### 前端构建

```bash
cd src/main/resources/static
yarn build
```

构建产物在 `dist` 目录，会自动集成到后端静态资源中。

## 错误处理

当发生错误时，API 会返回以下格式的响应：

```json
{
  "error": "错误信息描述"
}
```

常见错误：
- `400 Bad Request`: 请求参数错误或业务逻辑错误
- `401 Unauthorized`: 未授权，需要登录或 Token 无效
- `403 Forbidden`: 权限不足
- `404 Not Found`: 资源不存在
- `500 Internal Server Error`: 服务器内部错误

## 数据库表结构

主要数据表：

- **SYS_USER**: 用户表
  - 存储用户信息（用户名、密码、角色、状态等）
  - 注意：表名使用 SYS_USER 而不是 USER，因为 USER 是 PostgreSQL 的保留关键字

- **AI_APP**: AI 应用表
  - 存储应用基本信息、Dify API Key、配置等

- **AI_APP_USER**: AI 应用用户表（如需要）

- **KNOWLEDGE_BASE**: 知识库表
  - 存储知识库基本信息
  - 包含向量化模型ID（embedding_model_id），关联到 EMBEDDING_MODEL 表
  - 包含向量存储类型（vector_store_type），可选值：`qdrant`、`faiss`，默认为 `qdrant`

- **KNOWLEDGE_BASE_DOCUMENT**: 知识库文档表
  - 存储文档信息、向量化状态等

- **QA_MODEL**: 问答模型表
  - 存储问答模型配置信息（模型名称、提供商、API地址、API Key、模型标识等）
  - 支持使用场景配置（chat-仅智能问答、rag-仅知识库问答、both-两者都使用）
  - 支持启用/禁用状态和默认模型设置
  - 用于智能问答和知识库问答功能

- **EMBEDDING_MODEL**: 向量化模型表
  - 存储向量化模型配置信息（模型名称、提供商、API地址、API Key、模型标识等）
  - 包含超时时间和批处理大小配置
  - 支持启用/禁用状态和默认模型设置
  - 用于文档向量化和知识库检索功能

- **CHAT_CONVERSATION**: 对话会话表
  - 存储会话信息（用户ID、应用ID、知识库ID、会话类型、标题等）
  - 一个会话包含多轮对话消息

- **CHAT_MESSAGE**: 对话消息表
  - 存储单条消息信息（会话ID、角色、内容、创建时间等）
  - 支持用户消息和助手消息

- **USER_APP_VISIBILITY**: 用户应用可见性表
  - 存储用户对应用的可见性权限

- **USER_KNOWLEDGE_BASE_VISIBILITY**: 用户知识库可见性表
  - 存储用户对知识库的可见性权限

## 缓存架构

### 缓存策略

系统采用 **Cache-Aside 模式**（旁路缓存模式）：

1. **读取数据**：
   - 先查询 Redis 缓存，如果缓存命中，直接返回
   - 如果缓存未命中，查询数据库
   - 将查询结果写入缓存

2. **更新数据**：
   - 更新数据库
   - 删除相关缓存（Cache Evict）

### 缓存范围

系统自动缓存以下数据：

- **用户信息**：根据用户ID和用户名缓存
- **模型配置**：问答模型、向量化模型配置
- **AI应用信息**：根据应用ID和API Key缓存

### 缓存管理

- **默认过期时间**：1 小时
- **自动清除**：数据更新时自动清除相关缓存
- **容错机制**：
  - Redis 连接失败时自动降级到无缓存模式（NoOpCacheManager）
  - 缓存操作失败时记录日志但不抛出异常
  - 确保系统在 Redis 不可用时仍能正常工作
  - 所有业务功能不受影响，只是无法利用缓存加速

### 缓存工具类

系统提供了 `CacheService` 工具类，支持：
- 设置缓存（带过期时间）
- 获取缓存
- 删除缓存（支持按前缀批量删除）
- 判断缓存是否存在
- 设置过期时间

## 许可证

本项目采用 MIT 许可证。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

如有问题或建议，请通过 Issue 联系。
