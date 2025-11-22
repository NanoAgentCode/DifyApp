# DifyApp

一个基于 Spring Boot 和 Vue 3 的 Dify AI 应用管理系统，提供完整的 AI 应用管理、知识库管理、Chat Flow 和 Workflow 调用功能。

## 项目简介

DifyApp 是一个集成了 Dify AI 平台的应用管理系统，支持：

- **用户认证与授权**：JWT 认证、用户注册、登录、角色管理（管理员/普通用户）
- **AI 应用管理**：创建、编辑、删除和查看 AI 应用
- **Chat Flow 调用**：支持流式和非流式聊天对话
- **Workflow 调用**：支持流式和非流式工作流执行
- **知识库管理**：创建、管理知识库，上传、删除文档
- **RAG 问答**：基于 LangChain4j 的检索增强生成，支持流式和非流式问答
- **文档向量化**：自动文档解析、分块、向量化存储
- **文件存储**：基于 MinIO 的对象存储
- **向量存储**：基于 Qdrant 的向量数据库
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
- **向量数据库**: Qdrant 1.7.0
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
├── src/
│   └── main/
│       ├── java/
│       │   └── com/github/app/dify/
│       │       ├── DifyAppApplication.java                    # 主应用类
│       │       ├── config/                                     # 配置类
│       │       │   ├── DatabaseConfig.java                    # 数据库配置
│       │       │   ├── DataInitializer.java                   # 数据初始化
│       │       │   ├── DifyConfig.java                        # Dify配置
│       │       │   ├── EmbeddingConfig.java                   # 向量化配置
│       │       │   ├── MinioConfig.java                       # MinIO配置
│       │       │   ├── QdrantConfig.java                      # Qdrant配置
│       │       │   ├── RagConfig.java                         # RAG配置
│       │       │   ├── SwaggerConfig.java                     # Swagger配置
│       │       │   └── WebMvcConfig.java                      # Web MVC配置
│       │       ├── controller/                                 # 控制器层
│       │       │   ├── AiAppController.java                   # AI应用控制器
│       │       │   ├── AuthController.java                    # 认证控制器
│       │       │   ├── GlobalExceptionHandler.java            # 全局异常处理
│       │       │   ├── KnowledgeBaseController.java          # 知识库控制器
│       │       │   ├── KnowledgeBaseDocumentController.java   # 知识库文档控制器
│       │       │   └── KnowledgeBaseQAController.java         # 知识库问答控制器
│       │       ├── domain/                                     # 实体类
│       │       │   ├── AiApp.java                             # AI应用实体
│       │       │   ├── AiAppUser.java                         # AI应用用户实体
│       │       │   ├── KnowledgeBase.java                     # 知识库实体
│       │       │   ├── KnowledgeBaseDocument.java             # 知识库文档实体
│       │       │   ├── User.java                              # 用户实体
│       │       │   ├── UserAppVisibility.java                # 用户应用可见性实体
│       │       │   └── UserKnowledgeBaseVisibility.java      # 用户知识库可见性实体
│       │       ├── interceptor/                                # 拦截器
│       │       │   └── JwtInterceptor.java                    # JWT拦截器
│       │       ├── langchain4j/                                # LangChain4j集成
│       │       │   ├── ConfigurableDocumentSplitter.java     # 可配置文档分割器
│       │       │   ├── CustomChatLanguageModel.java          # 自定义聊天模型
│       │       │   ├── CustomEmbeddingModel.java             # 自定义嵌入模型
│       │       │   ├── CustomStreamingChatLanguageModel.java # 自定义流式聊天模型
│       │       │   ├── LangChain4jConfig.java                # LangChain4j配置
│       │       │   ├── QdrantEmbeddingStore.java             # Qdrant嵌入存储
│       │       │   └── TikaDocumentLoader.java               # Tika文档加载器
│       │       ├── repository/                                 # 数据访问层
│       │       │   ├── AiAppRepository.java                   # AI应用仓库
│       │       │   ├── AiAppUserRepository.java              # AI应用用户仓库
│       │       │   ├── KnowledgeBaseRepository.java          # 知识库仓库
│       │       │   ├── KnowledgeBaseDocumentRepository.java  # 知识库文档仓库
│       │       │   ├── UserRepository.java                   # 用户仓库
│       │       │   ├── UserAppVisibilityRepository.java      # 用户应用可见性仓库
│       │       │   └── UserKnowledgeBaseVisibilityRepository.java # 用户知识库可见性仓库
│       │       ├── service/                                    # 服务层
│       │       │   ├── AiAppService.java                     # AI应用服务
│       │       │   ├── AuthService.java                       # 认证服务
│       │       │   ├── ChunkService.java                     # 文档分块服务
│       │       │   ├── DifyApiClient.java                    # Dify API客户端
│       │       │   ├── DocumentParserService.java            # 文档解析服务
│       │       │   ├── DocumentVectorizationService.java    # 文档向量化服务
│       │       │   ├── FileStorageService.java               # 文件存储服务接口
│       │       │   ├── KnowledgeBaseDocumentService.java     # 知识库文档服务
│       │       │   ├── KnowledgeBaseQAService.java           # 知识库问答服务
│       │       │   ├── KnowledgeBaseService.java             # 知识库服务
│       │       │   ├── MinioFileStorageService.java          # MinIO文件存储服务
│       │       │   ├── RagRetrievalService.java              # RAG检索服务
│       │       │   ├── UserAppVisibilityService.java        # 用户应用可见性服务
│       │       │   ├── UserKnowledgeBaseVisibilityService.java # 用户知识库可见性服务
│       │       │   └── VectorStoreService.java                # 向量存储服务
│       │       ├── req/                                       # 请求对象
│       │       │   ├── ChangePasswordRequest.java            # 修改密码请求
│       │       │   ├── ChatFlowRequest.java                   # Chat Flow请求
│       │       │   ├── CreateAiAppReq.java                    # 创建应用请求
│       │       │   ├── CreateKnowledgeBaseReq.java           # 创建知识库请求
│       │       │   ├── DifyChatRequest.java                  # Dify聊天请求
│       │       │   ├── DifyWorkflowRequest.java              # Dify工作流请求
│       │       │   ├── KnowledgeBaseQARequest.java           # 知识库问答请求
│       │       │   ├── LoginRequest.java                     # 登录请求
│       │       │   ├── RegisterRequest.java                 # 注册请求
│       │       │   ├── ResetPasswordRequest.java             # 重置密码请求
│       │       │   ├── UpdateAiAppReq.java                   # 更新应用请求
│       │       │   ├── UpdateKnowledgeBaseReq.java          # 更新知识库请求
│       │       │   └── WorkFlowRequest.java                   # Workflow请求
│       │       ├── resp/                                       # 响应对象
│       │       │   ├── AiAppResp.java                         # AI应用响应
│       │       │   ├── DifyResponse.java                      # Dify响应
│       │       │   ├── KnowledgeBaseDocumentResp.java        # 知识库文档响应
│       │       │   ├── KnowledgeBaseQAResponse.java           # 知识库问答响应
│       │       │   ├── KnowledgeBaseResp.java                 # 知识库响应
│       │       │   ├── LoginResponse.java                     # 登录响应
│       │       │   ├── RegisterResponse.java                 # 注册响应
│       │       │   ├── UserAppVisibilityResp.java           # 用户应用可见性响应
│       │       │   ├── UserKnowledgeBaseVisibilityResp.java  # 用户知识库可见性响应
│       │       │   └── UserResp.java                          # 用户响应
│       │       └── util/                                       # 工具类
│       │           └── JwtUtil.java                           # JWT工具类
│       └── resources/
│           ├── application.yml                                 # 应用配置文件
│           ├── sql/                                            # SQL脚本
│           │   ├── init_database.sql                          # 数据库初始化脚本
│           │   ├── drop_old_user_table.sql                    # 删除旧用户表脚本
│           │   └── README_INIT.md                             # 数据库初始化说明
│           └── static/                                         # 前端静态资源
│               ├── src/
│               │   ├── api/                                    # API接口
│               │   │   ├── aiApp.js                           # AI应用API
│               │   │   ├── auth.js                            # 认证API
│               │   │   ├── knowledgeBase.js                   # 知识库API
│               │   │   ├── knowledgeBaseDocument.js           # 知识库文档API
│               │   │   ├── knowledgeBaseQA.js                 # 知识库问答API
│               │   │   └── user.js                            # 用户API
│               │   ├── components/                             # 公共组件
│               │   │   ├── AppIcon.vue                        # 应用图标组件
│               │   │   ├── ChangePasswordDialog.vue          # 修改密码对话框
│               │   │   └── ResetPasswordDialog.vue            # 重置密码对话框
│               │   ├── layouts/                                # 布局组件
│               │   │   ├── AdminLayout.vue                    # 管理端布局
│               │   │   ├── AppLayout.vue                      # 应用端布局
│               │   │   └── UserLayout.vue                     # 用户端布局
│               │   ├── router/                                 # 路由配置
│               │   │   └── index.js                           # 路由定义
│               │   ├── utils/                                  # 工具函数
│               │   │   ├── icons.js                           # 图标工具
│               │   │   ├── request.js                         # HTTP请求工具
│               │   │   └── themes.js                           # 主题工具
│               │   ├── views/                                  # 页面组件
│               │   │   ├── admin/                              # 管理端页面
│               │   │   │   ├── AppDetail.vue                   # 应用详情
│               │   │   │   ├── AppForm.vue                     # 应用表单
│               │   │   │   ├── AppList.vue                    # 应用列表
│               │   │   │   ├── KnowledgeBaseManagement.vue    # 知识库管理
│               │   │   │   ├── KnowledgeBaseQA.vue           # 知识库问答
│               │   │   │   └── UserList.vue                   # 用户列表
│               │   │   ├── app/                                # 应用端页面
│               │   │   │   ├── ChatApp.vue                    # 聊天应用
│               │   │   │   └── WorkflowApp.vue               # 工作流应用
│               │   │   ├── auth/                               # 认证页面
│               │   │   │   ├── Login.vue                      # 登录页
│               │   │   │   └── Register.vue                   # 注册页
│               │   │   └── user/                              # 用户端页面
│               │   │       ├── AppList.vue                    # 应用列表
│               │   │       ├── KnowledgeBaseManagement.vue   # 知识库管理
│               │   │       └── KnowledgeBaseQA.vue            # 知识库问答
│               │   ├── App.vue                                # 根组件
│               │   └── main.js                                # 入口文件
│               ├── index.html                                 # HTML模板
│               ├── package.json                               # 前端依赖配置
│               └── vite.config.js                             # Vite配置
└── pom.xml                                                     # Maven配置文件
```

## 环境要求

### 必需环境
- JDK 1.8 或更高版本
- Maven 3.6+
- PostgreSQL 15
- MinIO (对象存储服务)
- Qdrant (向量数据库)

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

#### 启动 Qdrant

使用 Docker 启动 Qdrant：

```bash
docker run -d \
  --name qdrant \
  -p 6333:6333 \
  -p 6334:6334 \
  qdrant/qdrant
```

访问 Qdrant 控制台：http://localhost:6333/dashboard

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

# Qdrant配置
qdrant:
  url: http://localhost:6333
  api-key: # 可选，Docker部署默认不需要
  timeout: 30000

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
psql -U postgres -h localhost -p 15432 -d difyapp -f src/main/resources/sql/init_database.sql
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
  "conversation_id": "conversation-123",
  "response_mode": "blocking"
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
  "tenantId": 1
}
```

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
  "conversationId": "conversation-123"
}
```

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

- ✅ **知识库管理**
  - 知识库 CRUD 操作
  - 文档上传、删除、下载
  - 支持多种文档格式（PDF、Word、TXT、Markdown 等）
  - 文档自动解析（Apache Tika）
  - 文档分块和向量化
  - 向量存储（Qdrant）

- ✅ **RAG 问答**
  - 基于 LangChain4j 的检索增强生成
  - 流式和非流式问答
  - 相似度检索
  - 可配置的检索参数

- ✅ **文件存储**
  - MinIO 对象存储
  - 文件上传、下载
  - 大文件支持（单个文件最大 100MB）

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
  - 知识库管理
  - 知识库问答
  - 用户权限管理（应用和知识库可见性）

- ✅ **用户端界面**
  - 应用列表（仅显示有权限的应用）
  - 知识库管理（仅显示有权限的知识库）
  - 知识库问答

- ✅ **应用端界面**
  - Chat Flow 交互界面
  - Workflow 交互界面
  - 流式响应实时显示
  - Markdown 渲染和代码高亮
  - 文件上传支持
  - 响应式设计

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

### Qdrant 配置

在 `application.yml` 中配置 Qdrant：

```yaml
qdrant:
  url: http://localhost:6333
  api-key: # 可选，仅在启用认证时需要
  timeout: 30000
```

### 向量化配置

> 💡 **提示**：详细的配置模板请参考 `src/main/resources/application-provider-template.yml` 文件，其中包含了所有支持的 provider 类型的完整配置示例。

在 `application.yml` 中配置向量化服务：

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

在 `application.yml` 中配置 RAG：

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
6. 配置 MinIO 和 Qdrant 的认证
7. 设置正确的 Dify API Base URL 和文件 URL 前缀
8. 配置合适的文件上传大小限制

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

- **KNOWLEDGE_BASE_DOCUMENT**: 知识库文档表
  - 存储文档信息、向量化状态等

- **USER_APP_VISIBILITY**: 用户应用可见性表
  - 存储用户对应用的可见性权限

- **USER_KNOWLEDGE_BASE_VISIBILITY**: 用户知识库可见性表
  - 存储用户对知识库的可见性权限

## 许可证

本项目采用 MIT 许可证。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

如有问题或建议，请通过 Issue 联系。
