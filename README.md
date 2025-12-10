# 智能应用工作台

一个基于 Spring Boot 和 Vue 3 的智能应用管理系统，提供完整的 AI 应用管理、知识库管理、Chat Flow 和 Workflow 调用功能。

## 项目简介

智能应用工作台是一个集成了 Dify AI 平台的应用管理系统，支持：

- **用户认证与授权**：JWT 认证、用户注册、登录、角色管理（管理员/普通用户）
- **AI 应用管理**：创建、编辑、删除和查看 AI 应用
- **Chat Flow 调用**：支持流式和非流式聊天对话
- **Workflow 调用**：支持流式和非流式工作流执行
- **知识库管理**：创建、管理知识库，上传、删除文档，支持选择向量化模型
- **RAG 问答**：基于 LangChain4j 的检索增强生成，支持流式和非流式问答，支持选择问答模型，支持上下文压缩（连续对话时压缩历史上下文）
- **文档向量化**：自动文档解析、分块、向量化存储
- **大模型管理**：动态管理问答模型和向量化模型，支持多种提供商（OpenAI、Ollama、VLLM 等），支持设置默认模型和启用/禁用状态
- **向量数据库管理**：动态管理向量数据库配置（Qdrant、Milvus、FAISS），支持多实例配置、默认设置、连接测试
- **对话历史管理**：完整的对话会话管理，支持会话记录、消息历史、继续对话、开启新对话
- **文件存储**：基于 MinIO 的对象存储
- **向量存储**：支持 Qdrant 向量数据库和 FAISS 本地文件存储，可在知识库级别选择
- **缓存系统**：基于 Redis 的缓存架构，提升查询性能
- **多租户支持**：支持多租户应用和知识库管理
- **权限控制**：用户对应用和知识库的可见性管理
- **数据源管理**：支持多种数据库类型（PostgreSQL、MySQL、Oracle、MongoDB），动态管理数据源配置
- **Text2SQL**：基于大语言模型的自然语言转SQL查询功能，支持智能SQL生成、类型转换修复、列验证和统计查询
- **MCP (Model Context Protocol)**：提供浏览器检索、时间信息、地理位置等上下文增强功能，支持实时信息检测
- **AI 绘图**：基于大语言模型的智能图表生成功能，支持流程图、架构图、思维导图、时序图、UML图、组织架构图、网络图等多种图表类型，支持图表修改和历史记录管理
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
- **向量数据库**: Qdrant 1.7.0（可选）、Milvus（可选）
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
├── front/                        # 前端项目（Vue 3）
│   ├── src/
│   │   ├── api/                  # API接口封装
│   │   │   ├── drawio.js         # AI绘图API
│   │   │   └── ...               # 其他API
│   │   ├── components/            # 公共组件
│   │   ├── layouts/               # 布局组件（管理端/用户端/应用端）
│   │   ├── views/                 # 页面组件
│   │   │   ├── admin/
│   │   │   │   ├── AIDrawIO.vue  # 管理端AI绘图页面
│   │   │   │   └── ...           # 其他管理端页面
│   │   │   ├── user/
│   │   │   │   ├── AIDrawIO.vue  # 用户端AI绘图页面
│   │   │   │   └── ...           # 其他用户端页面
│   │   │   └── ...               # 其他页面
│   │   ├── router/                # 路由配置
│   │   ├── utils/                 # 工具函数
│   │   ├── App.vue                # 根组件
│   │   └── main.js                # 入口文件
│   ├── public/                   # 静态资源
│   ├── index.html                # HTML模板
│   ├── package.json              # 前端依赖配置
│   ├── vite.config.js            # Vite配置
│   └── README.md                 # 前端说明文档
├── src/main/
│   ├── java/com/github/app/dify/
│   │   ├── config/               # 配置类（数据库、MinIO、Qdrant、Redis、Swagger等）
│   │   ├── controller/           # 控制器层（REST API）
│   │   │   ├── DrawIOController.java  # AI绘图控制器
│   │   │   └── ...               # 其他控制器
│   │   ├── domain/                # 实体类
│   │   │   ├── DrawIODiagram.java     # DrawIO图表实体
│   │   │   ├── DrawIOHistory.java     # DrawIO历史记录实体
│   │   │   └── ...               # 其他实体类
│   │   ├── repository/            # 数据访问层（JPA Repository）
│   │   │   ├── DrawIODiagramRepository.java
│   │   │   ├── DrawIOHistoryRepository.java
│   │   │   └── ...               # 其他Repository
│   │   ├── service/               # 服务层（业务逻辑）
│   │   │   ├── DrawIOService.java
│   │   │   ├── impl/
│   │   │   │   └── DrawIOServiceImpl.java
│   │   │   └── ...               # 其他服务
│   │   ├── langchain4j/           # LangChain4j集成（RAG相关）
│   │   ├── mcp/                   # MCP模块（Model Context Protocol）
│   │   ├── req/                   # 请求对象（DTO）
│   │   │   ├── DrawIOGenerateRequest.java
│   │   │   ├── DrawIOModifyRequest.java
│   │   │   ├── DrawIOHistoryRequest.java
│   │   │   └── ...               # 其他请求对象
│   │   ├── resp/                  # 响应对象（DTO）
│   │   │   ├── DrawIOGenerateResponse.java
│   │   │   ├── DrawIOHistoryResp.java
│   │   │   └── ...               # 其他响应对象
│   │   ├── interceptor/           # 拦截器（JWT认证等）
│   │   └── util/                  # 工具类
│   └── resources/
│       ├── application.yml        # 应用配置文件
│       ├── sql/                   # SQL脚本
│       │   └── init_database_complete.sql  # 完整的数据库初始化脚本
│       └── provider-template.yml  # 模型提供商配置模板
├── doc/                          # 项目文档
│   ├── 概要设计报告.md
│   ├── 用户端用户手册.md
│   ├── 管理端用户手册.md
│   └── 详细设计报告目录.md
└── pom.xml                       # Maven配置文件
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
- **Milvus** (向量数据库，支持大规模向量检索，适合生产环境)

### 可选环境（MCP 功能）
- **SearX-NG** (搜索引擎聚合服务，用于浏览器检索功能，默认端口 10086)

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
  -v pgvector-data:/var/lib/postgresql/data \
  --name pgvector \
  --restart unless-stopped \
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

#### 启动 Milvus（可选，用于向量存储）

**方式一：使用 docker-compose（推荐）**

```bash
cd milvus
docker-compose up -d
```

这将启动完整的 Milvus 服务（包括 etcd 和 MinIO），服务地址：`http://localhost:19530`

**方式二：使用单个 Docker 命令**

```bash
docker run -d \
  --name milvus-standalone \
  -p 19530:19530 \
  -p 9091:9091 \
  -v ./volumes/milvus:/var/lib/milvus \
  milvusdb/milvus:v2.4.0 \
  milvus run standalone
```

> **注意**：
> - 如果遇到 Docker 启动错误，请参考 `milvus/README_DOCKER.md`
> - 详细说明请参考 `milvus/README_DOCKER.md` 文件

#### 启动 Elasticsearch（可选，用于向量存储）

**方式一：使用 docker-compose（推荐）**

```bash
cd docker/elasticsearch
docker-compose up -d
```

这将启动 Elasticsearch 服务，服务地址：`http://localhost:9200`

**方式二：使用单个 Docker 命令**

```bash
docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  -v elasticsearch_data:/usr/share/elasticsearch/data \
  docker.elastic.co/elasticsearch/elasticsearch:8.11.0
```

> **注意**：
> - 如果遇到 Docker 启动错误，请参考 `docker/elasticsearch/README.md`
> - 详细说明请参考 `docker/elasticsearch/README.md` 文件
> - 生产环境建议启用安全功能并设置密码

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

#### 启动 SearX-NG（可选，用于 MCP 浏览器检索功能）

使用 Docker 启动 SearX-NG：

```bash
docker run -d \
  --name searxng \
  -p 10086:8080 \
  -v searxng:/etc/searxng:rw \
  searxng/searxng:latest
```

访问 SearX-NG：http://localhost:10086

**重要：SearX-NG 配置修改**

为了支持 MCP 浏览器检索功能，需要修改 SearX-NG 的配置文件。配置文件通常位于容器内的 `/etc/searxng/settings.yml` 或通过环境变量配置。

**方式一：通过配置文件修改（推荐）**

如果使用 Docker 挂载卷，编辑配置文件：

```yaml
search:
  formats:
    - html
    - json
    - rss
    - atom

enable_api: true
```

**方式二：通过环境变量配置**

在启动 Docker 容器时添加环境变量：

```bash
docker run -d \
  --name searxng \
  -p 10086:8080 \
  -e SEARXNG_SETTINGS_PATH=/etc/searxng/settings.yml \
  -v searxng:/etc/searxng:rw \
  searxng/searxng:latest
```

然后在配置文件中添加上述配置。

**配置说明：**
- `search.formats`: 必须包含 `json` 格式，以便 API 调用返回 JSON 格式的搜索结果
- `enable_api: true`: 启用 API 功能，允许通过 API 调用搜索服务

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

# MCP (Model Context Protocol) 配置
mcp:
  # 浏览器搜索服务配置
  browser-search:
    searxng-base-url: http://localhost:10086  # SearX-NG服务地址
    timeout: 10                               # 请求超时时间（秒）
    default-max-results: 5                    # 默认最大搜索结果数
    enable-query-optimization: true           # 是否启用查询优化（自动添加时间限制等）
    default-engines: "duckduckgo,brave"      # 默认搜索引擎（多个用逗号分隔）
  # 地理位置服务配置
  location:
    enabled: true                              # 是否启用地理位置服务
    cache-seconds: 3600                       # 地理位置信息缓存时间（秒，默认1小时）
    timeout: 10                               # 请求超时时间（秒）
  # 时间服务配置
  time:
    default-time-zone: Asia/Shanghai          # 默认时区
    cache-seconds: 1                          # 时间信息缓存时间（秒，避免频繁获取）
  # 实时信息检测配置
  realtime-info-detector:
    enabled: true                              # 是否启用实时信息检测
    confidence-threshold: 0.3                 # 检测置信度阈值（0.0-1.0），超过此值才认为是实时信息问题（降低阈值使检测更宽松）
```

### 4. 初始化数据库

#### 方式一：使用 SQL 脚本（推荐用于生产环境）

```bash
# 执行完整的数据库初始化脚本（包含所有表结构）
psql -U postgres -h localhost -p 15432 -d difyapp -f src/main/resources/sql/init_database_complete.sql
```

**说明：**
- `init_database_complete.sql` 包含了所有数据库表的创建语句，按依赖关系排序
- 包括基础表、核心业务表、扩展功能表和 DrawIO 相关表
- 包含所有字段注释和索引
- 包含默认初始化数据（管理员账户、向量数据库配置等）

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
cd front
yarn install
yarn dev
```

前端开发服务器运行在 `http://localhost:3000`

### 8. 访问应用

- **后端 API**: http://localhost:8081
- **Swagger API 文档**: http://localhost:8081/swagger-ui.html
- **前端应用**: 
  - 开发环境：http://localhost:3000（需要先启动前端开发服务器，见步骤7）
  - 生产环境：需要将前端构建产物集成到后端静态资源中，然后访问 http://localhost:8081

> **API 文档**: 详细的 API 文档请访问 Swagger UI：http://localhost:8081/swagger-ui.html

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
  - 向量存储：支持 Qdrant、FAISS、Milvus 三种存储方式，可在知识库级别选择
    - **Qdrant**：分布式向量数据库，适合生产环境，需要独立服务
    - **FAISS**：本地文件存储，无需额外服务，适合开发测试环境
    - **Milvus**：分布式向量数据库，适合生产环境，需要独立服务，使用 gRPC 协议
  - 支持为知识库选择向量化模型

- ✅ **RAG 问答**
  - 基于 LangChain4j 的检索增强生成
  - 流式和非流式问答
  - 相似度检索
  - 可配置的检索参数
  - 会话ID管理，支持连续对话
  - 支持选择问答模型
  - 向量化模型状态检查和警告提示
  - 上下文压缩功能（支持滑动窗口、总结、混合三种策略，自动压缩历史对话上下文）

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

- ✅ **向量数据库管理**
  - 动态管理向量数据库配置（Qdrant、Milvus、FAISS）
  - 支持多实例配置
  - 支持设置默认向量数据库
  - 支持启用/禁用向量数据库
  - 支持连接测试功能
  - 数据库驱动的动态配置，无需重启应用

- ✅ **数据源管理**
  - 支持多种数据库类型（PostgreSQL、MySQL、Oracle、MongoDB）
  - 数据源CRUD操作（创建、编辑、删除、查看）
  - 数据源连接测试
  - 表结构自动获取和缓存
  - 表结构刷新功能
  - 数据源权限管理（公开/私有、用户可见性控制）
  - 多租户支持

- ✅ **Text2SQL（自然语言转SQL）**
  - 基于大语言模型的智能SQL生成
  - 支持多种数据库类型（PostgreSQL、MySQL、Oracle）
  - 自动表结构解析和列类型识别
  - PostgreSQL类型转换自动修复（bigint、integer等数字类型与字符串比较时自动添加CAST）
  - SQL列验证（执行前验证列是否存在，避免运行时错误）
  - 系统列自动过滤（tenant_id、deleted、status、create_time等系统列不暴露给用户）
  - 统计查询支持（COUNT、SUM、AVG、MAX、MIN等聚合函数）
  - 支持分组统计（GROUP BY）和分组过滤（HAVING）
  - SQL安全检查（只允许SELECT查询，禁止危险操作）
  - 支持指定表查询和全表查询
  - 支持选择不同的问答模型进行SQL生成

- ✅ **MCP (Model Context Protocol) 功能**
  - 浏览器检索服务：基于 SearX-NG 的网络搜索功能，支持查询优化和时效性检查
  - 时间服务：获取当前时间信息，支持自定义时区和格式化输出
  - 地理位置服务：基于 IP 地址获取地理位置信息，支持缓存机制
  - 实时信息检测：智能检测问题是否涉及实时信息，提供置信度评分

- ✅ **系统配置管理**
  - 通用系统配置存储（SYSTEM_CONFIG表）
  - 支持配置分组（help、system等）
  - 支持多种配置类型（string、number、boolean、json）
  - 支持配置的增删改查
  - 用于存储系统级别的配置项（如AI绘图默认模型ID等）
  - **全局主题色配置**：支持从预设主题中选择全局主题色，包括饿了么蓝、京东红等多种主题，主题色会应用到整个系统（导航栏、登录页面、Element Plus组件等）

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
  - 向量数据库管理（Qdrant、Milvus、FAISS 的配置、默认设置、状态管理、连接测试）
  - 数据源管理（数据源CRUD、连接测试、表结构管理）
  - 系统配置管理（系统级别配置的增删改查，如AI绘图默认模型配置等）
  - 智能问答
  - 对话历史管理
  - 知识库管理（支持选择向量化模型和向量存储类型）
  - 知识库问答（支持选择问答模型，显示向量化模型状态）
  - Text2SQL（自然语言转SQL查询，支持统计查询）
  - AI绘图（支持多种图表类型生成、修改、保存、历史记录管理）
  - 用户权限管理（应用和知识库可见性）

- ✅ **用户端界面**
  - 应用列表（仅显示有权限的应用）
  - 智能问答
  - 对话历史管理
  - 知识库管理（仅显示有权限的知识库，支持创建和编辑知识库，支持选择向量化模型和向量存储类型）
  - 知识库问答（支持选择问答模型，显示向量化模型状态）
  - Text2SQL（自然语言转SQL查询，支持统计查询）
  - AI绘图（支持多种图表类型生成、修改、保存、历史记录管理）

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
  - **全局主题色配置**：支持从预设主题中选择，包括饿了么蓝、京东红、经典工业风、现代工业风等多种主题
  - **主题色自动应用**：主题色自动应用到导航栏、登录页面、Element Plus组件等所有UI元素
  - **导航栏滚动支持**：当菜单项过多时，导航栏支持滚动查看
  - **表格表头固定**：会话历史管理等表格支持表头固定，提升用户体验

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

> ⚠️ **重要提示**：系统现已支持通过数据库动态管理向量数据库配置，无需修改配置文件。以下配置仅作为兼容性保留，建议通过管理端界面配置向量数据库。

在 `application.yml` 中配置 Qdrant（如果使用 Qdrant 作为向量存储，仅作为兼容性保留）：

```yaml
qdrant:
  url: http://localhost:6333
  api-key: # 可选，仅在启用认证时需要
  timeout: 30000
```

**通过管理端配置（推荐）：**

1. 登录管理端
2. 进入"向量数据库管理"页面
3. 在"Qdrant"标签页中添加、编辑配置
4. 支持配置：
   - 配置名称、URL、API Key
   - 启用状态
   - 默认配置设置
   - 连接测试功能

### FAISS 配置（可选）

> ⚠️ **重要提示**：系统现已支持通过数据库动态管理向量数据库配置，无需修改配置文件。以下配置仅作为兼容性保留，建议通过管理端界面配置向量数据库。

在 `application.yml` 中配置 FAISS（如果使用 FAISS 作为向量存储，仅作为兼容性保留）：

```yaml
faiss:
  base-path: ./data/faiss  # FAISS索引文件的基础存储路径
```

**通过管理端配置（推荐）：**

1. 登录管理端
2. 进入"向量数据库管理"页面
3. 在"FAISS"标签页中添加、编辑配置
4. 支持配置：
   - 配置名称、存储路径
   - 启用状态
   - 默认配置设置

**FAISS 文件存储说明：**
- 每个知识库的向量数据存储在独立目录中：`{basePath}/kb_{knowledgeBaseId}/`
- 元数据文件：`metadata.json`，包含向量数据、文本内容和元信息
- 默认路径：`./data/faiss`（相对于应用运行目录）
- 支持自定义路径（可使用绝对路径）

**向量存储类型选择：**
- 创建知识库时可以选择使用 Qdrant、FAISS、Milvus
- 已有文档的知识库无法修改向量存储类型
- 默认使用 Qdrant（向后兼容）

### Milvus 配置（可选）

> ⚠️ **重要提示**：系统现已支持通过数据库动态管理向量数据库配置，无需修改配置文件。以下配置仅作为兼容性保留，建议通过管理端界面配置向量数据库。

在 `application.yml` 中配置 Milvus（如果使用 Milvus 作为向量存储，仅作为兼容性保留）：

```yaml
milvus:
  url: http://localhost:19530  # Milvus HTTP API地址
  api-key: # 可选，如果Milvus启用了认证，请配置API Key
  timeout: 30000
```

**通过管理端配置（推荐）：**

1. 登录管理端
2. 进入"向量数据库管理"页面
3. 在"Milvus"标签页中添加、编辑配置
4. 支持配置：
   - 配置名称、URL、API Key
   - 启用状态
   - 默认配置设置
   - 连接测试功能

**Milvus 说明：**
- **Milvus**：分布式向量数据库，适合生产环境，需要独立服务
- 使用 gRPC 协议进行通信

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

> ⚠️ **重要提示**：模型配置（LLM 和 Embedding）现已通过数据库动态管理。以下配置仅作为兼容性保留，建议通过管理端界面配置模型。

在 `application.yml` 中配置 RAG：

**基础配置：**

```yaml
rag:
  chunk-size: 500              # 文档分块大小
  chunk-overlap: 50             # 分块重叠大小
  top-k: 10                     # 检索数量
  similarity-threshold: 0.3     # 相似度阈值
  # 上下文压缩配置（用于连续对话时压缩历史上下文）
  enable-context-compression: true  # 是否启用上下文压缩
  compression-strategy: sliding_window  # 压缩策略：sliding_window（滑动窗口）、summary（总结）、hybrid（混合）
  max-history-rounds: 10  # 最大历史对话轮数（滑动窗口策略，每轮包含一问一答）
  max-history-tokens: 2000  # 最大历史对话token数（用于判断是否需要压缩）
  enable-summary: false  # 是否启用总结压缩（需要额外调用LLM，会增加延迟和成本）
```

**上下文压缩说明：**

- **滑动窗口策略（sliding_window）**：只保留最近的 N 轮对话，适合大多数场景，性能开销小
- **总结策略（summary）**：使用 LLM 总结历史对话，需要额外调用 LLM，会增加延迟和成本
- **混合策略（hybrid）**：结合滑动窗口和总结，先使用滑动窗口，当超过 token 限制时使用总结
- **启用条件**：当历史对话的 token 数超过 `max-history-tokens` 时，会自动触发压缩

**模型配置（已迁移到数据库，以下仅作为兼容性保留）：**

**默认配置（OpenAI 兼容格式，包括 SiliconFlow、VLLM 等）：**

```yaml
rag:
  llm-api-url: https://api.siliconflow.cn  # 或只配置基础URL
  llm-api-key: your-api-key
  llm-model: Qwen/Qwen2.5-72B-Instruct
  # provider: openai  # 默认值，可省略
```

**使用 Ollama：**

```yaml
rag:
  llm-api-url: http://localhost:11434  # Ollama 服务地址（不包含路径）
  llm-api-key: # Ollama 不需要 API Key，可留空
  llm-model: qwen2.5:72b  # Ollama 模型名称
  provider: ollama  # 指定使用 Ollama
```

**使用 VLLM（兼容 OpenAI）：**

```yaml
rag:
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

### AI 绘图配置

AI 绘图功能支持通过系统配置表（SYSTEM_CONFIG）设置默认使用的问答模型。

**配置方式：**

1. **通过管理端界面配置（推荐）**：
   - 登录管理端
   - 进入"系统配置管理"页面
   - 添加或编辑配置项：
     - 配置键：`drawio.defaultModelId`
     - 配置值：问答模型的ID（数字，如：1）
     - 配置分组：`system`
     - 配置类型：`number`
     - 描述：`AI绘图默认使用的问答模型ID`

2. **通过SQL直接配置**：
   ```sql
   INSERT INTO "SYSTEM_CONFIG" (config_key, config_value, config_group, config_type, description, create_time, update_time, deleted)
   VALUES ('drawio.defaultModelId', '1', 'system', 'number', 'AI绘图默认使用的问答模型ID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
   ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, update_time = CURRENT_TIMESTAMP;
   ```

**模型选择优先级：**
1. 请求中指定的 `modelId`（如果提供）
2. 系统配置中的 `drawio.defaultModelId`（如果已配置）
3. 默认的RAG问答模型（如果前两者都未配置）

**注意事项：**
- 如果配置的模型ID不存在或已禁用，系统会自动降级使用默认RAG模型
- 用户端AI绘图功能只能使用系统配置的默认模型，无法选择模型
- 管理端AI绘图功能可以显示当前使用的模型，但无法修改（使用系统配置的默认模型）

### 全局主题色配置

系统支持全局主题色配置，可以从预设主题中选择，主题色会自动应用到整个系统。

**预设主题：**
- **饿了么蓝**（默认）：Element Plus 默认蓝色主题
- **京东红**：京东品牌红色主题
- **经典工业风**：深灰背景 + 柔和橙色
- **现代工业风**：深蓝背景 + 柔和青色
- **金属工业风**：银灰背景 + 柔和蓝
- **暗黑工业风**：黑色背景 + 柔和红色
- **蒸汽朋克**：棕色背景 + 柔和金色
- **赛博工业风**：深紫背景 + 柔和青绿
- **温暖橙**：温暖的橙色主题

**配置方式：**

1. **通过管理端界面配置（推荐）**：
   - 登录管理端
   - 进入"系统配置管理"页面
   - 添加新配置或编辑现有配置：
     - 配置键：`system.globalTheme`
     - 配置值：系统会自动生成（格式：`themeId:primaryColor`）
     - 配置分组：`system`（自动设置）
     - 配置类型：`string`
     - 描述：`全局主题色配置，从预设主题中选择`
   - 在主题选择器中选择想要的主题
   - 保存后主题立即生效

2. **通过SQL直接配置**：
   ```sql
   INSERT INTO "SYSTEM_CONFIG" (config_key, config_value, config_group, config_type, description, create_time, update_time, deleted)
   VALUES ('system.globalTheme', 'element:#409EFF', 'system', 'string', '全局主题色配置，从预设主题中选择', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
   ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, update_time = CURRENT_TIMESTAMP;
   ```

**主题应用范围：**
- 导航栏背景色
- 登录页面所有主题色元素
- Element Plus 组件主色及其变体
- 整个应用中使用主题色的地方

**注意事项：**
- 主题配置保存后立即生效，无需刷新页面
- 如果配置的主题ID无效，系统会自动回退到默认的"饿了么蓝"主题
- 系统启动时会自动加载并应用全局主题色配置

### MCP (Model Context Protocol) 配置

MCP 模块提供了浏览器检索、时间信息、地理位置等上下文增强功能。

**浏览器搜索服务配置：**

```yaml
mcp:
  browser-search:
    searxng-base-url: http://localhost:10086  # SearX-NG服务地址
    timeout: 10                               # 请求超时时间（秒）
    default-max-results: 5                    # 默认最大搜索结果数
    enable-query-optimization: true           # 是否启用查询优化
    default-engines: "duckduckgo,brave"      # 默认搜索引擎（多个用逗号分隔）
```

**SearX-NG 配置要求：**

为了支持 MCP 浏览器检索功能，SearX-NG 需要修改以下配置。配置文件通常位于容器内的 `/etc/searxng/settings.yml`：

```yaml
search:
  formats:
    - html
    - json
    - rss
    - atom

enable_api: true
```

**配置说明：**
- `search.formats`: 必须包含 `json` 格式，以便 API 调用返回 JSON 格式的搜索结果
- `enable_api: true`: 启用 API 功能，允许通过 API 调用搜索服务

**地理位置服务配置：**

```yaml
mcp:
  location:
    enabled: true                              # 是否启用地理位置服务
    cache-seconds: 3600                       # 地理位置信息缓存时间（秒）
    timeout: 10                               # 请求超时时间（秒）
```

**时间服务配置：**

```yaml
mcp:
  time:
    default-time-zone: Asia/Shanghai          # 默认时区
    cache-seconds: 1                          # 时间信息缓存时间（秒）
```

**完整配置示例：**

```yaml
mcp:
  browser-search:
    searxng-base-url: http://localhost:10086
    timeout: 10
    default-max-results: 5
    enable-query-optimization: true
    default-engines: "duckduckgo,brave"
  location:
    enabled: true
    cache-seconds: 3600
    timeout: 10
  time:
    default-time-zone: Asia/Shanghai
    cache-seconds: 1
  realtime-info-detector:
    enabled: true
    confidence-threshold: 0.3
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
   - 生产环境推荐使用 Qdrant 或 Milvus（分布式、高性能）
   - 开发测试环境可使用 FAISS（无需额外服务）
   - 如果使用 FAISS，确保存储目录有足够的磁盘空间和写入权限
   - 如果使用 Milvus，确保服务正常运行并确保存储目录有足够的磁盘空间和写入权限
   - 定期备份向量存储目录（FAISS: `data/faiss`，Milvus: 根据服务配置）
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
cd front
yarn build
```

构建产物在 `front/dist` 目录。如需集成到后端，需要将构建产物复制到后端的静态资源目录。

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

- **SYSTEM_CONFIG**: 系统配置表
  - 存储系统级别的配置项（通用配置存储）
  - 支持配置键值对存储（config_key、config_value）
  - 支持配置分组（config_group，如：help、system等）
  - 支持配置类型（config_type，如：string、number、boolean、json）
  - 用于存储各种系统配置，如AI绘图默认模型ID（drawio.defaultModelId）等
  - 支持软删除机制

- **AI_APP**: AI 应用表
  - 存储应用基本信息、Dify API Key、配置等

- **AI_APP_USER**: AI 应用用户表（如需要）

- **KNOWLEDGE_BASE**: 知识库表
  - 存储知识库基本信息
  - 包含向量化模型ID（embedding_model_id），关联到 EMBEDDING_MODEL 表
  - 包含向量存储类型（vector_store_type），可选值：`qdrant`、`faiss`、`milvus`，默认为 `qdrant`

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

- **VECTOR_DATABASE**: 向量数据库配置表
  - 存储向量数据库配置信息（类型、名称、URL、API Key、存储路径等）
  - 支持 Qdrant、Milvus、FAISS 三种类型
  - 支持启用/禁用状态和默认配置设置
  - 用于知识库向量存储功能

- **DATA_SOURCE**: 数据源表
  - 存储数据源配置信息（名称、类型、主机、端口、数据库、用户名、密码等）
  - 支持 PostgreSQL、MySQL、Oracle、MongoDB 四种类型
  - 支持启用/禁用状态
  - 支持公开/私有设置
  - 包含创建者ID和租户ID

- **TABLE_SCHEMA_CACHE**: 表结构缓存表
  - 缓存数据源的表结构信息（JSON格式）
  - 提升表结构查询性能
  - 支持强制刷新

- **USER_DATA_SOURCE_VISIBILITY**: 用户数据源可见性表
  - 存储用户对数据源的可见性权限
  - 支持管理员为用户设置数据源访问权限

- **DRAWIO_DIAGRAM**: DrawIO 图表表
  - 存储用户通过 AI 生成的图表（使用 AntV X6 格式）
  - 包含图表名称、类型、JSON内容、用户ID等信息
  - 支持图表保存、查询、删除功能
  - 用户只能查看和管理自己的图表

- **DRAWIO_HISTORY**: DrawIO 历史记录表
  - 存储用户AI绘图的历史记录（提示词）
  - 包含用户ID、提示词、图表类型、创建时间等信息
  - 每个用户最多保存10条历史记录
  - 支持历史记录查询、删除功能
  - 用户只能查看和管理自己的历史记录

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
