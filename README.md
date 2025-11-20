# DifyApp

一个基于 Spring Boot 和 Vue 3 的 Dify AI 应用管理系统，提供完整的 AI 应用管理、Chat Flow 和 Workflow 调用功能。

## 项目简介

DifyApp 是一个集成了 Dify AI 平台的应用管理系统，支持：

- **AI 应用管理**：创建、编辑、删除和查看 AI 应用
- **Chat Flow 调用**：支持流式和非流式聊天对话
- **Workflow 调用**：支持流式和非流式工作流执行
- **文件上传**：支持向 Dify 平台上传文件
- **多租户支持**：支持多租户应用管理
- **前端界面**：提供管理端和应用端两个界面

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

### 前端
- **框架**: Vue 3.3+
- **路由**: Vue Router 4
- **状态管理**: Pinia
- **UI 组件库**: Element Plus 2.4+
- **HTTP 客户端**: Axios
- **构建工具**: Vite 5
- **Markdown 渲染**: marked
- **代码高亮**: highlight.js

## 项目结构

```
DifyApp/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/github/app/dify/
│       │       ├── DifyAppApplication.java          # 主应用类
│       │       ├── config/                           # 配置类
│       │       │   ├── DatabaseConfig.java          # 数据库配置
│       │       │   ├── DifyConfig.java              # Dify配置
│       │       │   └── SwaggerConfig.java           # Swagger配置
│       │       ├── controller/                       # 控制器层
│       │       │   ├── AiAppController.java         # AI应用控制器
│       │       │   └── GlobalExceptionHandler.java  # 全局异常处理
│       │       ├── domain/                           # 实体类
│       │       │   ├── AiApp.java                   # AI应用实体
│       │       │   └── AiAppUser.java               # AI应用用户实体
│       │       ├── repository/                      # 数据访问层
│       │       │   ├── AiAppRepository.java         # AI应用仓库
│       │       │   └── AiAppUserRepository.java     # AI应用用户仓库
│       │       ├── service/                         # 服务层
│       │       │   ├── AiAppService.java            # AI应用服务
│       │       │   └── DifyApiClient.java           # Dify API客户端
│       │       ├── req/                             # 请求对象
│       │       │   ├── ChatFlowRequest.java         # Chat Flow请求
│       │       │   ├── CreateAiAppReq.java         # 创建应用请求
│       │       │   ├── UpdateAiAppReq.java         # 更新应用请求
│       │       │   └── WorkFlowRequest.java        # Workflow请求
│       │       └── resp/                            # 响应对象
│       │           ├── AiAppResp.java               # AI应用响应
│       │           └── DifyResponse.java            # Dify响应
│       └── resources/
│           ├── application.yml                      # 应用配置文件
│           ├── sql/                                 # SQL脚本
│           └── static/                              # 前端静态资源
│               ├── src/
│               │   ├── api/                         # API接口
│               │   ├── components/                  # 公共组件
│               │   ├── layouts/                     # 布局组件
│               │   ├── router/                      # 路由配置
│               │   ├── utils/                       # 工具函数
│               │   └── views/                       # 页面组件
│               │       ├── admin/                    # 管理端页面
│               │       └── app/                      # 应用端页面
│               ├── index.html                       # HTML模板
│               ├── package.json                     # 前端依赖配置
│               └── vite.config.js                   # Vite配置
└── pom.xml                                          # Maven配置文件
```

## 环境要求

- JDK 1.8 或更高版本
- Maven 3.6+
- PostgreSQL 15
- Node.js 16+ (用于前端开发，可选)
- Yarn 或 npm (用于前端开发，可选)

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd DifyApp
```

### 2. 配置数据库

确保 PostgreSQL 15 已安装并运行，然后创建数据库：

```sql
CREATE DATABASE difyapp;
```

### 3. 配置应用

编辑 `src/main/resources/application.yml`，根据您的环境修改配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:15432/difyapp
    username: postgres
    password: 123456

dify:
  api:
    default-base-url: http://localhost:80
    timeout: 30000
    connect-timeout: 10000
    file-url-prefix: http://localhost:80
```

### 4. 构建后端

```bash
mvn clean install
```

### 5. 运行后端应用

```bash
mvn spring-boot:run
```

或者使用打包后的 JAR：

```bash
java -jar target/DifyApp-0.0.1-SNAPSHOT.jar
```

应用启动后，后端服务运行在 `http://localhost:8081`

### 6. 前端开发（可选）

如果需要单独开发前端：

```bash
cd src/main/resources/static
yarn install
yarn dev
```

前端开发服务器运行在 `http://localhost:3000`

### 7. 访问应用

- **后端 API**: http://localhost:8081
- **Swagger API 文档**: http://localhost:8081/swagger-ui.html
- **前端应用**: http://localhost:8081 (生产环境) 或 http://localhost:3000 (开发环境)

## API 文档

### AI 应用管理 API

#### 1. 创建 AI 应用

```
POST /api/ai-apps
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
  "tenantId": 1,
  "streamEnabled": true,
  "fileUploadEnabled": false,
  "inputEnabled": true,
  "themeColor": "#409EFF",
  "inputs": "{\"key\":\"value\"}"
}
```

**响应示例：**
```json
{
  "id": 1,
  "name": "我的AI应用",
  "description": "应用描述",
  "type": 1,
  "status": 1,
  "appId": "app-xxx",
  "apiBaseUrl": "http://localhost:80",
  "streamEnabled": true,
  "fileUploadEnabled": false,
  "inputEnabled": true,
  "themeColor": "#409EFF"
}
```

#### 2. 更新 AI 应用

```
PUT /api/ai-apps/{id}
Content-Type: application/json
```

#### 3. 获取 AI 应用详情

```
GET /api/ai-apps/{id}
```

#### 4. 删除 AI 应用

```
DELETE /api/ai-apps/{id}
```

#### 5. 获取应用列表

```
GET /api/ai-apps?tenantId=1&type=1&status=1
```

**查询参数：**
- `tenantId` (可选): 租户ID
- `type` (可选): 应用类型 (1-ChatFlow, 2-Workflow)
- `status` (可选): 应用状态

#### 6. 获取 Dify 配置信息

```
GET /api/ai-apps/config
```

### Chat Flow API

#### 1. 调用 Chat Flow（非流式）

```
POST /api/ai-apps/{id}/chat
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
Content-Type: application/json
Accept: text/event-stream
```

**响应格式：** Server-Sent Events (SSE)

### Workflow API

#### 1. 调用 Workflow（非流式）

```
POST /api/ai-apps/{id}/workflow
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
Content-Type: application/json
Accept: text/event-stream
X-Trace-Id: trace-123 (可选)
```

**响应格式：** Server-Sent Events (SSE)

### 文件上传 API

#### 上传文件到 Dify

```
POST /api/ai-apps/{id}/files/upload
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

## 功能特性

### 后端功能

- ✅ AI 应用的完整 CRUD 操作
- ✅ 支持 Chat Flow 和 Workflow 两种应用类型
- ✅ 流式和非流式响应支持
- ✅ 文件上传功能
- ✅ 多租户支持
- ✅ 全局异常处理
- ✅ Swagger API 文档
- ✅ 异步请求支持（最长 10 分钟）
- ✅ 文件上传大小限制（单个文件 10MB，总请求 20MB）

### 前端功能

- ✅ 管理端界面：应用列表、创建、编辑、删除
- ✅ 应用端界面：Chat Flow 和 Workflow 交互界面
- ✅ 流式响应实时显示
- ✅ Markdown 渲染和代码高亮
- ✅ 文件上传支持
- ✅ 响应式设计

## 配置说明

### 数据库配置

应用使用 Hibernate 的 `ddl-auto: update` 模式，启动时会自动创建或更新数据库表结构。

### 生产环境建议

在生产环境中，建议：

1. 将 `ddl-auto` 设置为 `validate` 或 `none`
2. 使用 Flyway 或 Liquibase 进行数据库版本管理
3. 关闭 SQL 日志输出（设置 `show-sql: false`）
4. 配置合适的连接池参数
5. 设置正确的 Dify API Base URL 和文件 URL 前缀

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
- `404 Not Found`: 资源不存在
- `500 Internal Server Error`: 服务器内部错误

## 数据库表结构

主要数据表：

- **AI_APP**: AI 应用表
  - 存储应用基本信息、Dify API Key、配置等
- **AI_APP_USER**: AI 应用用户表（如需要）

## 许可证

本项目采用 MIT 许可证。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

如有问题或建议，请通过 Issue 联系。
