# DifyApp 后端项目

## 项目概述

DifyApp 是一个基于Java的企业级后端应用，提供用户认证、智能对话、知识库管理、AI应用管理、Text2SQL等功能。该项目使用Spring Boot 3.x框架构建，集成了多种AI服务和向量数据库，实现了现代化的RAG（检索增强生成）架构。

## 技术栈

### 核心框架
- **后端框架**: Spring Boot 3.5.8
- **编程语言**: Java 17
- **构建工具**: Maven 3.6+
- **ORM框架**: Spring Data JPA / Hibernate

### 数据库支持
- **关系型数据库**: PostgreSQL / MySQL / Oracle
- **NoSQL数据库**: MongoDB / Neo4j
- **搜索引擎**: Elasticsearch
- **向量数据库**: 
  - Chroma
  - FAISS (本地文件存储)
  - Milvus
  - PgVector (PostgreSQL扩展)
  - Qdrant
  - Weaviate
  - Elasticsearch (向量搜索)

### 存储与缓存
- **对象存储**: MinIO
- **缓存**: Redis (Spring Data Redis)

### AI与LLM集成
- **LangChain4j**: 0.34.0 (RAG框架)
- **文档解析**: Apache Tika, Apache POI
- **嵌入模型**: 支持多种嵌入模型
- **LLM集成**: 通过Dify API集成多种大语言模型

### 安全与认证
- **认证方式**: JWT (JSON Web Token)
- **密码加密**: Spring Security Crypto

### 其他技术
- **API文档**: SpringDoc OpenAPI 2.3.0
- **HTTP客户端**: Spring WebFlux (Reactive)
- **日志框架**: Logback
- **监控**: Spring Boot Actuator

## 项目结构

```
src/
├── main/
│   ├── java/com/github/app/dify/
│   │   ├── DifyAppApplication.java    # 主应用入口
│   │   ├── auth/                      # 用户认证模块
│   │   │   ├── controller/            # 认证控制器
│   │   │   ├── domain/                # 实体类
│   │   │   ├── repository/            # 数据访问层
│   │   │   ├── service/               # 业务逻辑层
│   │   │   ├── interceptor/           # JWT拦截器
│   │   │   ├── req/                   # 请求DTO
│   │   │   ├── resp/                  # 响应DTO
│   │   │   └── util/                  # 工具类
│   │   ├── chat/                      # 聊天对话模块
│   │   │   ├── controller/            # 聊天控制器
│   │   │   ├── domain/                # 实体类
│   │   │   ├── repository/            # 数据访问层
│   │   │   ├── service/               # 业务逻辑层
│   │   │   ├── mcp/                   # MCP协议服务
│   │   │   ├── req/                   # 请求DTO
│   │   │   └── resp/                  # 响应DTO
│   │   ├── knowledgebase/             # 知识库模块
│   │   │   ├── controller/            # 知识库控制器
│   │   │   ├── domain/                # 实体类
│   │   │   ├── repository/            # 数据访问层
│   │   │   ├── service/               # 业务逻辑层
│   │   │   ├── langchain4j/          # LangChain4j集成
│   │   │   ├── req/                   # 请求DTO
│   │   │   ├── resp/                  # 响应DTO
│   │   │   └── util/                  # 工具类
│   │   ├── system/                     # 系统配置模块
│   │   │   ├── controller/            # 系统控制器
│   │   │   ├── domain/                # 实体类
│   │   │   ├── repository/            # 数据访问层
│   │   │   ├── service/               # 业务逻辑层
│   │   │   ├── config/                # 配置类
│   │   │   ├── req/                   # 请求DTO
│   │   │   ├── resp/                  # 响应DTO
│   │   │   └── util/                  # 工具类
│   │   └── common/                     # 公共组件
│   │       ├── controller/            # 基础控制器
│   │       ├── exception/             # 异常定义
│   │       ├── resp/                  # 统一响应格式
│   │       └── util/                  # 工具类
│   └── resources/
│       ├── application.yml            # 应用配置文件
│       ├── logback-spring.xml         # 日志配置
│       └── sql/                       # 数据库初始化脚本
└── test/                              # 测试代码
```

## 模块说明

### 1. 用户认证模块 (auth)
- 用户注册、登录、密码修改、密码重置
- JWT令牌生成与验证
- 用户权限控制
- 用户与应用/数据源/知识库的可见性管理

### 2. 聊天对话模块 (chat)
- AI应用创建与管理
- 聊天对话功能（支持Chat和Workflow两种模式）
- 对话历史管理（会话、消息）
- Dify API集成
- MCP (Model Context Protocol) 协议支持：
  - 浏览器搜索服务
  - 地理位置服务
  - 时间服务
  - 实时信息检测
- OCR服务集成

### 3. 知识库模块 (knowledgebase)
- 知识库创建与管理
- 文档上传、解析与处理
- 文档向量化与存储
- 向量数据库管理（支持多种向量数据库）
- 知识库问答（RAG）
- 嵌入模型管理
- QA模型管理

### 4. 系统配置模块 (system)
- 系统配置管理
- 数据源管理
- 模型管理
- Prompt模板管理
- Text2SQL功能
- 用户管理（管理员功能）

### 5. 公共组件 (common)
- 统一异常处理
- 统一API响应格式
- 基础控制器
- 工具类

## 开发环境要求

- **JDK**: 17 或更高版本
- **Maven**: 3.6+
- **数据库**: PostgreSQL 12+ / MySQL 5.7+ / Oracle 12+
- **Redis**: 6.0+ (可选，用于缓存)
- **MinIO**: 最新版本 (用于对象存储)
- **向量数据库**: 根据需求选择安装（Chroma/Qdrant/Milvus等）

## 快速开始

### 1. 克隆项目
```bash
git clone https://github.com/Yarao-Liu/DifyApp.git
cd DifyApp/backend
```

### 2. 配置数据库
编辑 `src/main/resources/application.yml`，配置数据库连接信息：

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:15432/difyapp
    username: postgres
    password: your_password
```

### 3. 配置其他服务

#### Redis配置（可选）
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: # 可选
```

#### MinIO配置
```yaml
minio:
  endpoint: http://localhost:9000
  access-key: your_access_key
  secret-key: your_secret_key
  bucket-name: knowledge-base
```

#### 向量数据库配置
根据使用的向量数据库，配置相应的连接信息（Qdrant、Milvus、Chroma等）。

### 4. 初始化数据库
执行 `src/main/resources/sql/init_database_complete.sql` 脚本创建数据库表。

### 5. 构建项目
```bash
mvn clean install
```

### 6. 运行应用
```bash
mvn spring-boot:run
```

或者使用打包后的JAR文件：
```bash
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

应用启动后，默认运行在 `http://localhost:9090`

### 7. 访问API文档
启动应用后，访问 `http://localhost:9090/swagger-ui.html` 查看API文档。

## 配置说明

### 主要配置项

- **服务器端口**: 默认 9090
- **JWT配置**: 密钥和过期时间
- **文件上传**: 最大文件大小 100MB，最大请求大小 200MB
- **RAG配置**: 文档分块大小、重叠大小、相似度阈值等
- **MCP配置**: 浏览器搜索、地理位置、时间服务等

详细配置请参考 `src/main/resources/application.yml` 文件。

## API 文档

项目使用 SpringDoc OpenAPI 自动生成API文档：
- **Swagger UI**: `http://localhost:9090/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:9090/v3/api-docs`

更多详细的设计文档请参考 `doc/` 目录。

## 日志配置

日志文件位置：
- **应用日志**: `logs/dify-app.log`
- **错误日志**: `logs/dify-app-error.log`

日志按天滚动，保留历史日志文件。

## 开发规范

- 遵循Spring Boot最佳实践
- 使用MVC架构模式（Controller-Service-Repository）
- 统一异常处理机制（GlobalExceptionHandler）
- 统一API响应格式（ApiResponse）
- 详细的日志记录
- 使用JPA进行数据持久化
- 使用DTO进行数据传输

## 部署说明

### 打包应用
```bash
mvn clean package
```

### 运行JAR文件
```bash
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

### 生产环境建议
- 修改JWT密钥为更安全的随机字符串
- 配置HTTPS
- 配置数据库连接池参数
- 配置日志级别和输出
- 配置监控和告警

## 贡献指南

欢迎提交Issue和Pull Request来帮助我们改进项目。请确保你的代码符合项目规范。

提交代码前请确保：
- 代码通过编译
- 遵循代码风格规范
- 添加必要的注释
- 更新相关文档

## 许可证

本项目采用MIT许可证，详情请见 [LICENSE](LICENSE) 文件。

## 联系方式

如有问题，请通过GitHub Issues与我们联系。
