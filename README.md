# DifyApp 系统文档

## 项目概述

DifyApp 是一个基于 Spring Boot 和 Vue 3 构建的企业级 AI 应用平台，提供完整的 AI 应用生命周期管理、知识库管理、智能问答、Text2SQL、AI 绘图等功能。系统采用前后端分离架构，支持多种向量数据库和 LLM 集成，实现了现代化的 RAG（检索增强生成）架构。

### 项目特点

- **企业级架构**：采用 Spring Boot 3.x 和 Vue 3，遵循最佳实践，代码结构清晰
- **多租户支持**：支持多用户、多租户场景，细粒度权限控制
- **RAG 技术**：基于 LangChain4j 实现检索增强生成，支持多种向量数据库
- **流式响应**：支持流式和非流式两种响应模式，提供更好的用户体验
- **多模态支持**：支持文本、图片等多种输入方式，集成 OCR 服务
- **可扩展性**：模块化设计，易于扩展新功能
- **跨平台**：支持 Web 应用和桌面应用（基于 Tauri）
- **丰富的集成**：支持 MCP 协议、多种向量数据库、多种 LLM 模型

### 核心功能

- **用户认证与授权**
  - JWT 令牌认证机制，安全可靠
  - 用户注册、登录、登出功能
  - 密码修改、重置、找回功能
  - 多租户支持，细粒度权限控制
  - 用户与应用/数据源/知识库的可见性管理
  - 管理员审核机制

- **智能对话**
  - 支持 Chat Flow 和 Workflow 两种应用模式
  - 集成多种大语言模型（通过 Dify API）
  - 流式和非流式响应模式
  - 实时对话交互，支持上下文理解
  - 对话历史完整记录和管理
  - 支持多轮对话和会话管理

- **知识库管理**
  - 知识库创建、编辑、删除、查询
  - 支持多种文档格式（PDF、Word、Excel、TXT、Markdown 等）
  - 自动文档解析和分块处理
  - 文档向量化和语义索引
  - 支持多种向量数据库（Chroma、FAISS、Milvus、Qdrant、Weaviate、PgVector、Elasticsearch）
  - 文档版本管理和更新机制
  - 批量文档上传和处理

- **RAG 问答（检索增强生成）**
  - 基于知识库的智能问答
  - 向量相似度搜索和语义检索
  - 上下文增强的 AI 回答生成
  - 支持引用来源和可追溯性
  - 可配置的检索参数（Top K、相似度阈值等）
  - 多知识库联合检索

- **AI 应用管理**
  - AI 应用的创建、编辑、删除、查看
  - 应用配置管理（模型选择、参数设置等）
  - 应用可见性控制（公开/私有）
  - 应用使用统计和分析
  - 应用模板和快速创建

- **Text2SQL 功能**
  - 自然语言转 SQL 查询
  - 支持多种数据库（PostgreSQL、MySQL、Oracle 等）
  - 数据源连接和管理
  - SQL 查询结果可视化
  - 查询历史记录

- **AI 绘图功能**
  - 基于 Mermaid 的图表生成
  - 支持流程图、时序图、类图等多种图表类型
  - 自然语言描述生成图表代码
  - 图表预览和导出

- **系统管理**
  - 系统配置管理（全局参数设置）
  - 数据源管理（数据库连接配置）
  - 模型管理（LLM 模型配置）
  - 向量数据库配置管理
  - Prompt 模板管理（可复用的提示词模板）
  - 用户管理（管理员功能，用户审核、禁用等）
  - 系统监控和日志管理

- **MCP 协议支持**
  - 浏览器搜索服务（实时网络搜索）
  - 地理位置服务（获取位置信息）
  - 时间服务（获取当前时间、时区等）
  - 实时信息检测和更新
  - 可扩展的 MCP 服务集成

- **OCR 服务集成**
  - 图片文字识别
  - 支持多种图片格式
  - 批量图片处理
  - 识别结果结构化输出

## 系统架构

```mermaid
graph TB
    subgraph "前端层"
        Frontend[前端 Vue 3<br/>Port: 3000]
    end
    
    subgraph "后端层"
        Backend[后端 Spring Boot<br/>Port: 9090]
    end
    
    subgraph "数据存储层"
        DB[(关系型数据库<br/>PostgreSQL/MySQL)]
        Redis[(Redis<br/>缓存)]
        VectorDB[(向量数据库<br/>Chroma/FAISS/Milvus<br/>Qdrant/Weaviate等)]
        MinIO[MinIO<br/>对象存储]
    end
    
    subgraph "外部服务"
        DifyAPI[Dify API<br/>LLM服务]
    end
    
    Frontend -->|HTTP/HTTPS| Backend
    Backend --> DB
    Backend --> Redis
    Backend --> VectorDB
    Backend --> MinIO
    Backend --> DifyAPI
    
    style Frontend fill:#42b983,stroke:#333,stroke-width:2px,color:#fff
    style Backend fill:#6db33f,stroke:#333,stroke-width:2px,color:#fff
    style DB fill:#336791,stroke:#333,stroke-width:2px,color:#fff
    style Redis fill:#dc382d,stroke:#333,stroke-width:2px,color:#fff
    style VectorDB fill:#0d9488,stroke:#333,stroke-width:2px,color:#fff
    style MinIO fill:#ff9900,stroke:#333,stroke-width:2px,color:#fff
    style DifyAPI fill:#6366f1,stroke:#333,stroke-width:2px,color:#fff
```

## 技术栈

### 后端技术栈

- **核心框架**: Spring Boot 3.5.8
- **编程语言**: Java 17
- **构建工具**: Maven 3.6+
- **ORM 框架**: Spring Data JPA / Hibernate
- **数据库**: PostgreSQL / MySQL / Oracle / MongoDB / Neo4j
- **向量数据库**: Chroma / FAISS / Milvus / PgVector / Qdrant / Weaviate / Elasticsearch
- **存储与缓存**: MinIO (对象存储) / Redis (缓存)
- **AI 框架**: LangChain4j 0.34.0
- **文档解析**: Apache Tika, Apache POI
- **安全**: JWT (JSON Web Token)
- **API 文档**: SpringDoc OpenAPI 2.3.0

### 前端技术栈

- **核心框架**: Vue 3.3.4
- **构建工具**: Vite 5.0.8
- **状态管理**: Pinia 2.1.7
- **路由**: Vue Router 4.2.5
- **UI 框架**: Element Plus 2.4.2
- **HTTP 客户端**: Axios 1.6.2
- **Markdown 渲染**: marked, highlight.js, katex, mermaid
- **桌面应用**: Tauri 2.9.6 (可选)

## 项目结构

```
DifyApp/
├── backend/                    # 后端项目
│   ├── src/                    # 源代码
│   │   ├── main/java/          # Java 源代码
│   │   └── main/resources/     # 配置文件
│   ├── doc/                    # 设计文档
│   ├── data/                   # 数据目录（向量数据库等）
│   ├── logs/                   # 日志文件
│   ├── pom.xml                 # Maven 配置
│   └── README-backend.md       # 后端详细文档
│
├── frontend/                   # 前端项目
│   ├── src/                    # 源代码
│   │   ├── api/                # API 接口定义
│   │   ├── components/         # 组件
│   │   ├── views/              # 页面
│   │   ├── stores/             # 状态管理
│   │   └── utils/              # 工具函数
│   ├── src-tauri/              # Tauri 桌面应用配置
│   ├── dist/                   # 构建产物
│   ├── package.json            # npm 配置
│   └── README-frontend.md      # 前端详细文档
│
├── easy_ocr/                   # OCR 服务（可选）
│   ├── app.py                  # Python 应用
│   ├── docker-compose.yml      # Docker 配置
│   └── requirements.txt        # Python 依赖
│
├── rustfs/                     # Rust 文件服务（可选）
│   ├── docker-compose.yml      # Docker 配置
│   └── README.md               # 服务文档
│
└── README.md                   # 本文件（系统总览）
```

## 快速开始

### 环境要求

#### 后端环境
- **JDK**: 17 或更高版本
- **Maven**: 3.6+
- **数据库**: PostgreSQL 12+ / MySQL 5.7+ / Oracle 12+
- **Redis**: 6.0+ (可选，用于缓存)
- **MinIO**: 最新版本 (用于对象存储)
- **向量数据库**: 根据需求选择安装（Chroma/Qdrant/Milvus 等）

#### 前端环境
- **Node.js**: 16 或更高版本
- **包管理器**: npm 8+ 或 yarn 1.22+
- **Git**: 最新版本

#### Tauri 开发（可选）
- **Rust**: 1.70+ (仅用于桌面应用开发)

### 1. 克隆项目

```bash
git clone https://github.com/Yarao-Liu/DifyApp.git
cd DifyApp
```

### 2. 后端启动

#### 2.1 配置数据库

编辑 `backend/src/main/resources/application.yml`，配置数据库连接信息：

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:15432/difyapp
    username: postgres
    password: your_password
```

#### 2.2 配置其他服务

**Redis 配置（可选）**
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: # 可选
```

**MinIO 配置**
```yaml
minio:
  endpoint: http://localhost:9000
  access-key: your_access_key
  secret-key: your_secret_key
  bucket-name: knowledge-base
```

**向量数据库配置**
根据使用的向量数据库，配置相应的连接信息（Qdrant、Milvus、Chroma 等）。

#### 2.3 初始化数据库

执行 `backend/src/main/resources/sql/init_database_complete.sql` 脚本创建数据库表。

#### 2.4 构建并运行

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

后端服务默认运行在 `http://localhost:9090`

### 3. 前端启动

#### 3.1 安装依赖

```bash
cd frontend
npm install
# 或者
yarn install
```

#### 3.2 配置 API 地址

默认 API 地址为 `http://localhost:9090`，如需修改，请编辑 `frontend/src/config/api.js` 文件。

#### 3.3 启动开发服务器

```bash
npm run dev
# 或者
yarn dev
```

前端开发服务器默认运行在 `http://localhost:3000`

### 4. 访问系统

- **前端应用**: http://localhost:3000
- **后端 API**: http://localhost:9090
- **API 文档**: http://localhost:9090/swagger-ui.html

## 系统数据流

```mermaid
sequenceDiagram
    participant User as 用户
    participant Frontend as 前端 (Vue 3)
    participant Backend as 后端 (Spring Boot)
    participant Auth as 认证服务
    participant Chat as 对话服务
    participant KB as 知识库服务
    participant VectorDB as 向量数据库
    participant Dify as Dify API
    participant DB as 关系数据库
    
    User->>Frontend: 访问系统
    Frontend->>Auth: 登录请求
    Auth->>DB: 验证用户
    DB-->>Auth: 用户信息
    Auth-->>Frontend: JWT Token
    
    User->>Frontend: 创建知识库
    Frontend->>KB: 上传文档
    KB->>DB: 保存文档元数据
    KB->>VectorDB: 向量化并存储
    
    User->>Frontend: 发起对话
    Frontend->>Chat: 发送消息
    Chat->>KB: 检索相关知识
    KB->>VectorDB: 向量相似度搜索
    VectorDB-->>KB: 相关文档片段
    KB-->>Chat: 上下文信息
    Chat->>Dify: 调用LLM
    Dify-->>Chat: AI回复
    Chat->>DB: 保存对话历史
    Chat-->>Frontend: 返回回复
    Frontend-->>User: 显示结果
```

## 功能模块关系

```mermaid
graph LR
    subgraph "用户层"
        Auth[用户认证模块]
    end
    
    subgraph "应用层"
        Chat[智能对话模块]
        KB[知识库模块]
        App[AI应用管理模块]
        System[系统管理模块]
    end
    
    subgraph "服务层"
        Text2SQL[Text2SQL]
        MCP[MCP协议服务]
        OCR[OCR服务]
    end
    
    Auth --> Chat
    Auth --> KB
    Auth --> App
    Auth --> System
    
    Chat --> KB
    Chat --> App
    Chat --> MCP
    Chat --> OCR
    
    KB --> System
    App --> System
    System --> Text2SQL
    
    style Auth fill:#e74c3c,stroke:#333,stroke-width:2px,color:#fff
    style Chat fill:#3498db,stroke:#333,stroke-width:2px,color:#fff
    style KB fill:#9b59b6,stroke:#333,stroke-width:2px,color:#fff
    style App fill:#f39c12,stroke:#333,stroke-width:2px,color:#fff
    style System fill:#1abc9c,stroke:#333,stroke-width:2px,color:#fff
    style Text2SQL fill:#16a085,stroke:#333,stroke-width:2px,color:#fff
    style MCP fill:#e67e22,stroke:#333,stroke-width:2px,color:#fff
    style OCR fill:#95a5a6,stroke:#333,stroke-width:2px,color:#fff
```

## 功能模块

### 1. 用户认证模块

**后端功能：**
- 用户注册、登录、登出
- JWT 令牌生成、验证、刷新
- 密码加密存储（Spring Security Crypto）
- 密码修改、重置、找回
- 用户状态管理（待审核、已激活、已禁用）
- 用户权限控制（普通用户、管理员）
- 用户与应用/数据源/知识库的可见性关联管理
- 管理员审核用户注册

**前端功能：**
- 登录、注册页面
- 密码修改对话框
- 密码重置对话框
- 用户信息展示和管理
- JWT 令牌自动管理（存储、刷新）

### 2. 智能对话模块

**后端功能：**
- AI 应用创建、编辑、删除、查询
- 聊天对话接口（支持 Chat Flow 和 Workflow 模式）
- 流式和非流式响应处理
- 对话历史管理（会话、消息的增删改查）
- Dify API 集成和调用
- MCP (Model Context Protocol) 协议服务：
  - 浏览器搜索服务（实时网络搜索）
  - 地理位置服务（获取位置信息）
  - 时间服务（获取当前时间、时区）
  - 实时信息检测和更新
- OCR 服务集成（图片文字识别）
- 对话上下文管理

**前端功能：**
- 实时聊天界面（支持流式显示）
- AI 应用选择和管理界面
- 对话历史列表和查看
- 支持 Chat 和 Workflow 两种模式切换
- Markdown 渲染（支持代码高亮、数学公式、Mermaid 图表）
- 消息发送、接收、重试功能
- 对话会话管理（新建、切换、删除）

### 3. 知识库模块

**后端功能：**
- 知识库创建、编辑、删除、查询
- 文档上传接口（支持多种格式）
- 文档解析（Apache Tika、Apache POI）
- 文档分块处理（可配置分块大小、重叠大小）
- 文档向量化（使用嵌入模型）
- 向量数据库管理（支持多种向量数据库）
- 知识库问答（RAG）：
  - 向量相似度搜索
  - 上下文检索
  - 检索结果排序和过滤
- 嵌入模型管理（配置、测试）
- QA 模型管理（配置、测试）
- 文档版本管理
- 批量文档处理

**前端功能：**
- 知识库创建和编辑界面
- 文档上传界面（拖拽上传、批量上传）
- 文档列表和管理（查看、删除、重新处理）
- 向量数据库配置界面
- 知识库问答界面（输入问题、显示答案、引用来源）
- 文档处理状态显示（处理中、已完成、失败）
- 知识库统计信息展示

### 4. AI 应用管理模块

**后端功能：**
- AI 应用创建、编辑、删除、查询
- 应用配置管理（模型选择、参数设置、提示词配置等）
- 应用可见性管理（公开/私有）
- 应用使用统计
- 应用模板管理

**前端功能：**
- AI 应用列表和搜索
- 应用创建和编辑表单
- 应用配置界面
- 应用可见性设置
- 应用详情查看
- 应用使用统计图表

### 5. 系统管理模块

**后端功能：**
- 系统配置管理（全局参数设置）
- 数据源管理（数据库连接配置、测试连接）
- 模型管理（LLM 模型配置、测试）
- 向量数据库配置管理
- Prompt 模板管理（创建、编辑、删除、使用）
- Text2SQL 功能（自然语言转 SQL）
- 用户管理（管理员功能）：
  - 用户列表查询
  - 用户审核（激活、禁用）
  - 用户信息编辑
  - 用户权限管理

**前端功能：**
- 系统配置管理界面（管理员）
- 数据源管理界面（添加、编辑、删除、测试）
- 模型管理界面（配置、测试）
- 向量数据库配置界面
- Prompt 模板管理界面
- Text2SQL 界面（输入自然语言、显示 SQL、执行查询、查看结果）
- 用户管理界面（管理员，用户列表、审核、编辑）

### 6. 其他功能

**前端功能：**
- **主题切换**：支持深色/浅色主题切换，VS Code 风格深色主题
- **Markdown 渲染**：
  - 标准 Markdown 语法支持
  - 代码高亮（highlight.js，支持多种编程语言）
  - 数学公式渲染（KaTeX）
  - 流程图和图表（Mermaid）
  - 自定义样式主题
- **帮助文档**：内置帮助对话框，提供使用指南
- **响应式布局**：适配不同屏幕尺寸，支持移动端访问
- **桌面应用支持**：基于 Tauri 的跨平台桌面应用（Windows、macOS、Linux）
- **API 缓存**：智能缓存机制，提升性能
- **错误处理**：统一的错误提示和处理机制
- **国际化支持**：预留多语言支持接口

## 部署说明

### 后端部署

#### 打包应用

```bash
cd backend
mvn clean package
```

#### 运行 JAR 文件

```bash
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

#### 生产环境建议

- 修改 JWT 密钥为更安全的随机字符串
- 配置 HTTPS
- 配置数据库连接池参数
- 配置日志级别和输出
- 配置监控和告警

### 前端部署

#### 构建生产版本

```bash
cd frontend
npm run build
# 或者
yarn build
```

构建产物将输出到 `frontend/dist/` 目录。

#### 部署到 Web 服务器

将 `dist/` 目录的内容部署到 Nginx、Apache 或其他 Web 服务器。

#### Tauri 桌面应用构建（可选）

```bash
npm run tauri:build
# 或者
yarn tauri:build
```

构建产物将输出到 `src-tauri/target/release/` 目录。

### Docker 部署（可选）

项目包含一些可选服务的 Docker 配置：

- **OCR 服务**: `easy_ocr/docker-compose.yml`
- **文件服务**: `rustfs/docker-compose.yml`

## 开发规范

### 后端开发规范

- 遵循 Spring Boot 最佳实践
- 使用 MVC 架构模式（Controller-Service-Repository）
- 统一异常处理机制（GlobalExceptionHandler）
- 统一 API 响应格式（ApiResponse）
- 详细的日志记录
- 使用 JPA 进行数据持久化
- 使用 DTO 进行数据传输

### 前端开发规范

- 遵循 Vue 3 Composition API 最佳实践
- 使用组合式函数（Composables）封装可复用逻辑
- 组件化开发，保持组件单一职责
- 统一的代码风格和规范
- 使用 Element Plus 组件库保持 UI 一致性
- API 调用统一通过 `src/api/` 目录下的文件
- 工具函数统一放在 `src/utils/` 目录

## 文档说明

### 设计文档

详细的设计文档位于 `backend/doc/` 目录：

- 系统概要设计报告
- 详细设计报告目录
- 用户端用户手册
- 管理端用户手册
- 各功能模块设计文档

### API 文档

项目使用 SpringDoc OpenAPI 自动生成 API 文档：

- **Swagger UI**: http://localhost:9090/swagger-ui.html
- **OpenAPI JSON**: http://localhost:9090/v3/api-docs

### 日志文件

- **后端应用日志**: `backend/logs/dify-app.log`
- **后端错误日志**: `backend/logs/dify-app-error.log`
- 日志按天滚动，保留历史日志文件

## 常见问题

### 1. 后端启动失败

- 检查数据库连接配置是否正确
- 检查数据库是否已初始化
- 检查端口 9090 是否被占用
- 查看日志文件获取详细错误信息

### 2. 前端 API 请求失败

- 检查后端服务是否正常运行
- 检查 API 地址配置是否正确（`frontend/src/config/api.js`）
- 检查跨域配置是否正确

### 3. 向量数据库连接失败

- 检查向量数据库服务是否正常运行
- 检查连接配置是否正确
- 查看后端日志获取详细错误信息

### 4. 文件上传失败

- 检查 MinIO 服务是否正常运行
- 检查 MinIO 配置是否正确
- 检查文件大小是否超过限制（默认 100MB）

## 贡献指南

欢迎提交 Issue 和 Pull Request 来帮助我们改进项目。请确保你的代码符合项目规范。

提交代码前请确保：

- 代码通过编译和测试
- 遵循代码风格规范
- 添加必要的注释
- 更新相关文档

## 许可证

本项目采用 MIT 许可证，详情请见 [LICENSE](LICENSE) 文件。

## 联系方式

如有问题，请通过 GitHub Issues 与我们联系。

## 相关链接

- [后端详细文档](backend/README-backend.md)
- [前端详细文档](frontend/README-frontend.md)
- [GitHub 仓库](https://github.com/Yarao-Liu/DifyApp)

