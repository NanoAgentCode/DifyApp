# DifyApp 后端项目

## 项目概述

DifyApp 后端是一个基于 Java 的企业级后端应用，提供用户认证、智能对话、知识库管理、AI 应用管理、AI 绘图等功能。该项目使用 Spring Boot 3.5.8 框架构建，集成了多种 AI 服务和向量数据库，实现了现代化的 RAG（检索增强生成）架构。

### 技术特点

- **现代化技术栈**：Spring Boot 3.5.8、Java 17、Spring Data JPA
- **RAG 框架**：基于 LangChain4j 0.34.0 实现检索增强生成
- **混合检索支持**：结合全文检索与向量检索提升准确率，支持 Elasticsearch 和 Weaviate
- **流式响应**：支持 Server-Sent Events (SSE) 流式输出
- **多协议支持**：支持 MCP 协议、Dify API、OCR 服务等
- **文档处理**：支持多种文档格式解析（PDF、Word、Excel、TXT、Markdown 等）
- **安全认证**：JWT 令牌认证，Spring Security 加密
- **备忘录意图识别**：智能识别用户提醒需求并在 AI 响应前自动创建备忘录
- **统一规范**：统一异常处理、统一 API 响应格式
- **日志与分析**：集成用户行为日志采集，支持 Elasticsearch 分析
- **数据分析**：Neo4j 图数据库集成，支持数据同步和关系可视化
- **上下文优化**：新增上下文压缩服务，提升长对话处理效率
- **实体架构优化**：统一的 BaseEntity 设计，移除中间层实体，简化继承结构
- **完善的填充逻辑**：所有实体类自动填充 BaseEntity 字段，确保数据完整性
- **记忆管理增强**：用户端和管理端记忆管理界面优化，清空功能细化
- **认证与 Web 配置**：JWT 拦截器在 WebMvcConfig 中注册，排除登录/注册/系统配置等公共接口；提供 `/api/system-config/value/{key}`、`/api/system-config/group/{group}` 等接口供前端按 key 或分组获取配置
- **缓存与一致性**：AI 应用删除时同步清除按 apikey 的缓存；知识库文档向量化/重索引/删除后触发 RAG 缓存失效；知识库向量库或类型变更时 VectorStoreFactory 缓存失效；各 VectorStore 策略使用 ConcurrentHashMap、Elasticsearch 客户端在切换时正确关闭

## 技术栈

### 核心框架

- **后端框架**: Spring Boot 3.5.8
- **编程语言**: Java 17
- **构建工具**: Maven 3.6+
- **ORM框架**: Spring Data JPA / Hibernate

### 数据库支持

- **关系型数据库**: PostgreSQL / MySQL / Oracle
- **NoSQL数据库**: MongoDB
- **图数据库**: Neo4j (用于数据分析和关系可视化)
- **搜索引擎**: Elasticsearch (用于用户行为日志存储和检索)
- **向量数据库**: 
  - Chroma
  - FAISS (本地文件存储)
  - Milvus
  - PgVector (PostgreSQL扩展)
  - Qdrant
  - Weaviate
  - Elasticsearch (向量搜索)

### 存储与缓存

- **对象存储**: RustFS (S3兼容，MinIO替代品)
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
- **监控**: Spring Boot Actuator，统一超时策略（30秒）

## 系统架构

```mermaid
graph TB
    subgraph "前端层"
        Frontend[前端 Vue 3<br/>Port: 3000]
    end
    
    subgraph "后端服务层"
        Controller[Controller层<br/>REST API]
        Service[Service层<br/>业务逻辑]
        Repository[Repository层<br/>数据访问]
    end
    
    subgraph "数据存储层"
        DB[(关系型数据库<br/>PostgreSQL/MySQL)]
        Redis[(Redis<br/>缓存)]
        VectorDB[(向量数据库<br/>Chroma/FAISS/Milvus等)]
        Neo4j[(Neo4j<br/>图数据库)]
        ES[(Elasticsearch<br/>日志存储)]
        RustFS[RustFS<br/>对象存储<br/>S3兼容]
    end
    
    subgraph "外部服务"
        DifyAPI[Dify API<br/>LLM服务]
        EmbeddingAPI[Embedding API<br/>嵌入模型]
    end
    
    Frontend -->|HTTP/HTTPS| Controller
    Controller --> Service
    Service --> Repository
    Repository --> DB
    Service --> Redis
    Service --> VectorDB
    Service --> Neo4j
    Service --> ES
    Service --> RustFS
    Service --> DifyAPI
    Service --> EmbeddingAPI
    
    style Frontend fill:#42b983,stroke:#333,stroke-width:2px,color:#fff
    style Controller fill:#6db33f,stroke:#333,stroke-width:2px,color:#fff
    style Service fill:#6db33f,stroke:#333,stroke-width:2px,color:#fff
    style Repository fill:#6db33f,stroke:#333,stroke-width:2px,color:#fff
    style DB fill:#336791,stroke:#333,stroke-width:2px,color:#fff
    style Redis fill:#dc382d,stroke:#333,stroke-width:2px,color:#fff
    style VectorDB fill:#0d9488,stroke:#333,stroke-width:2px,color:#fff
    style RustFS fill:#ff9900,stroke:#333,stroke-width:2px,color:#fff
    style DifyAPI fill:#6366f1,stroke:#333,stroke-width:2px,color:#fff
    style EmbeddingAPI fill:#6366f1,stroke:#333,stroke-width:2px,color:#fff
```

## 项目结构

```mermaid
graph TD
    A[src/] --> B[main/]
    B --> C[java/com/github/app/dify/]
    B --> D[resources/]
    
    C --> E[DifyAppApplication.java<br/>主应用入口]
    C --> F[auth/<br/>用户认证模块]
    C --> G[chat/<br/>聊天对话模块]
    C --> H[knowledgebase/<br/>知识库模块]
    C --> I[system/<br/>系统配置模块]
    C --> J[common/<br/>公共组件]
    C --> K[documentreader/<br/>文档解读模块]
    C --> L[statistics/<br/>数据统计模块]
    C --> M[mcp/<br/>MCP协议服务]
    C --> N[model/<br/>模型管理模块]
    C --> O[datasource/<br/>数据源管理模块]
    C --> P[permission/<br/>权限管理模块]
    
    F --> F1[controller/]
    F --> F2[domain/]
    F --> F3[repository/]
    F --> F4[service/]
    F --> F5[interceptor/]
    F --> F6[req/ resp/ util/]
    
    G --> G1[controller/]
    G --> G2[domain/]
    G --> G3[repository/]
    G --> G4[service/]
    G --> G5[mcp/]
    G --> G6[req/ resp/]
    
    H --> H1[controller/]
    H --> H2[domain/]
    H --> H3[repository/]
    H --> H4[service/]
    H --> H5[langchain4j/]
    H --> H6[req/ resp/ util/]
    
    I --> I1[controller/]
    I --> I2[domain/]
    I --> I3[repository/]
    I --> I4[service/]
    I --> I5[config/]
    I --> I6[req/ resp/ util/]
    
    J --> J1[controller/]
    J --> J2[exception/]
    J --> J3[resp/]
    J --> J4[util/]
    
    D --> D1[application.yml]
    D --> D2[logback-spring.xml]
    D --> D3[sql/]
    
    style A fill:#e1f5ff
    style F fill:#fff3cd
    style G fill:#fff3cd
    style H fill:#fff3cd
    style I fill:#fff3cd
    style J fill:#d4edda
```

## 模块关系图

```mermaid
graph LR
    subgraph "核心模块"
        Auth[用户认证模块<br/>auth]
        Chat[聊天对话模块<br/>chat]
        KB[知识库模块<br/>knowledgebase]
        System[系统配置模块<br/>system]
        DocReader[文档解读模块<br/>documentreader]
        Statistics[数据统计模块<br/>statistics]
        DataSource[数据源管理模块<br/>datasource]
        Model[模型管理模块<br/>model]
        Permission[权限管理模块<br/>permission]
        Common[公共组件<br/>common]
    end
    
    subgraph "外部集成"
        Dify[Dify API]
        MCP[MCP协议服务]
        OCR[OCR服务]
        LangChain[LangChain4j]
    end
    
    Auth --> Chat
    Auth --> KB
    Auth --> System
    Auth --> DocReader
    Chat --> KB
    Chat --> Dify
    Chat --> MCP
    KB --> OCR
    DocReader --> OCR
    KB --> LangChain
    KB --> System
    DocReader --> KB
    DocReader --> System
    System --> Dify
    System --> DataSource
    System --> Model
    System --> Permission
    Statistics --> Chat
    Statistics --> KB
    Common --> Auth
    Common --> Chat
    Common --> KB
    Common --> System
    Common --> DocReader
    Common --> Statistics
    
    style Auth fill:#e74c3c,stroke:#333,stroke-width:2px,color:#fff
    style Chat fill:#3498db,stroke:#333,stroke-width:2px,color:#fff
    style KB fill:#9b59b6,stroke:#333,stroke-width:2px,color:#fff
    style System fill:#1abc9c,stroke:#333,stroke-width:2px,color:#fff
    style DocReader fill:#8e44ad,stroke:#333,stroke-width:2px,color:#fff
    style Statistics fill:#27ae60,stroke:#333,stroke-width:2px,color:#fff
    style DataSource fill:#2980b9,stroke:#333,stroke-width:2px,color:#fff
    style Model fill:#c0392b,stroke:#333,stroke-width:2px,color:#fff
    style Permission fill:#d35400,stroke:#333,stroke-width:2px,color:#fff
    style Common fill:#95a5a6,stroke:#333,stroke-width:2px,color:#fff
    style Dify fill:#6366f1,stroke:#333,stroke-width:2px,color:#fff
    style MCP fill:#e67e22,stroke:#333,stroke-width:2px,color:#fff
    style OCR fill:#f39c12,stroke:#333,stroke-width:2px,color:#fff
    style LangChain fill:#16a085,stroke:#333,stroke-width:2px,color:#fff
```

## 模块说明

系统采用模块化设计，主要包含以下14个核心模块（按主应用类中的模块化结构顺序）：

1. **auth** - 认证模块（登录、注册、JWT）
2. **permission** - 权限管理模块（可见性控制）
3. **chat** - AI应用与对话模块
4. **knowledgebase** - 知识库模块
5. **documentreader** - 文档解读模块
6. **system** - 系统配置模块
7. **statistics** - 数据统计模块
8. **analysis** - 数据分析模块（Neo4j 图数据库集成）
9. **userlog** - 用户行为日志模块（Elasticsearch 日志存储）
10. **memory** - 记忆管理模块（用户长期记忆和实体记忆）
11. **mcp** - MCP服务集成模块（浏览器搜索、时间服务等）
12. **model** - 模型配置模块（问答模型、向量化模型配置管理）
13. **datasource** - 数据源管理模块（数据源配置、连接管理、表结构管理）
14. **common** - 公共组件模块（工具类、异常、响应格式）

### 1. 用户认证模块 (auth)

**核心功能：**

- 用户注册：支持邮箱注册，管理员审核机制
- 用户登录：JWT 令牌认证，支持记住登录状态
- 密码管理：密码修改、重置、找回功能
- JWT 令牌管理：令牌生成、验证、刷新机制
- 用户权限控制：基于角色的访问控制（RBAC）
- 可见性管理：用户与应用/数据源/知识库的关联关系管理
- 用户状态管理：待审核、已激活、已禁用状态流转

**技术实现：**

- 使用 Spring Security Crypto 进行密码加密
- JWT 拦截器进行请求认证
- 统一异常处理机制
- 用户实体与业务实体的多对多关联

### 2. 权限管理模块 (permission)

**核心功能：**

- 可见性管理：
  - 用户与应用关联
  - 用户与数据源关联
  - 用户与知识库关联
- 权限控制：
  - 基于关联关系的权限验证
  - 权限查询接口
- 关联关系管理：
  - 关联关系创建和删除
  - 批量关联操作

**技术实现：**

- 使用 JPA 多对多关系映射
- 中间表存储关联关系
- 权限验证拦截器

### 3. 聊天对话模块 (chat)

**核心功能：**

- AI 应用管理：创建、编辑、删除、查询 AI 应用
- 聊天对话：支持 Chat Flow 和 Workflow 两种应用模式
- 流式响应：支持 Server-Sent Events (SSE) 流式输出
- 非流式响应：传统 HTTP 请求-响应模式
- 对话历史管理：
  - 会话（Conversation）管理：创建、查询、删除会话
  - 消息（Message）管理：保存、查询对话消息
  - 支持按时间、应用等条件查询
- Dify API 集成：
  - 应用调用接口封装
  - 流式和非流式响应处理
  - 错误处理和重试机制
- MCP (Model Context Protocol) 协议服务：
  - 浏览器搜索服务：实时网络搜索，获取最新信息
  - 地理位置服务：获取用户位置信息
  - 时间服务：获取当前时间、时区等信息
  - 实时信息检测：检测和更新实时数据
- 视觉模型支持：
  - 支持多模态输入（文本+图片）
  - 图片理解、文字识别、图表分析
  - 自动检测模型视觉能力
- **备忘录意图识别与 AI 融合**：
  - 自动解析用户提醒内容与时间
  - 预先创建备忘录并将成功信息注入 AI 上下文
  - 实现自然、准确的 AI 操作确认

**技术实现：**

- Spring WebFlux 实现响应式编程
- 流式响应使用 SSE 技术
- MCP 服务通过 HTTP 客户端调用
- 对话历史使用 JPA 持久化

### 4. 知识库模块 (knowledgebase)

**核心功能：**

- 知识库管理：创建、编辑、删除、查询知识库
- 文档管理：
  - 文档上传（支持 PDF、Word、Excel、TXT、Markdown 等格式）
  - 文档解析（使用 Apache Tika、Apache POI）
  - 智能文档分块处理（根据文件类型和内容特征自动选择分块策略）
    - 支持多种分块策略：固定大小、段落、句子、标题、代码、表格、递归分块
    - 自动识别文件类型和内容特征（表格、代码块、标题等）
    - 混合内容支持（表格+文本+代码）
  - 文档删除、重新处理
- 文档向量化：
  - 使用嵌入模型将文档转换为向量
  - 支持多种嵌入模型（OpenAI、本地模型等）
  - 向量存储到向量数据库
- 向量数据库管理：
  - 支持多种向量数据库（Chroma、FAISS、Milvus、Qdrant、Weaviate、PgVector、Elasticsearch）
  - 向量数据库连接配置
  - 向量数据的增删改查
- 知识库问答（RAG）：
  - 向量相似度搜索
  - 混合检索（向量 + 全文，支持 Elasticsearch 和 Weaviate）
  - 检索结果排序和过滤归一化
  - 上下文增强生成
  - 支持引用来源
  - 支持视觉模型（多模态输入）
- OCR 服务集成：
  - 图片和PDF文字识别
  - Word文档图片识别
  - 自动回退机制
- 嵌入模型管理：配置、测试嵌入模型
- QA 模型管理：配置、测试问答模型

**技术实现：**

- LangChain4j 框架实现 RAG 功能
- 文档解析使用 Apache Tika 和 Apache POI
- 向量数据库适配器模式，支持多种向量数据库
- RustFS 存储原始文档文件（S3兼容）

### 5. 文档解读模块 (documentreader)

**核心功能：**

- 文档管理：
  - 文档上传和存储
  - 文档解析（支持多种格式）
  - 文档向量化处理
- 文档问答：
  - 基于 RAG 技术的文档问答
  - 文档内容检索
  - 上下文增强生成
- 文档翻译：
  - 多语言翻译支持
  - 翻译历史记录
- 文档思维导图：
  - 自动生成文档思维导图
  - 思维导图数据存储
- 文档笔记：
  - 笔记创建和管理
  - 笔记与文档关联
- 文档导读：
  - 自动生成文档导读
  - 导读内容管理

**技术实现：**

- 复用知识库的向量化能力
- 使用 LangChain4j 实现文档检索
- 思维导图数据使用 JSON 格式存储

### 6. 系统配置模块 (system)

**核心功能：**

- 系统配置管理：
  - 全局参数设置（RAG 参数、文件上传限制等）
  - 配置的增删改查
- 数据源管理：
  - 数据库连接配置（PostgreSQL、MySQL、Oracle 等）
  - 连接测试功能
  - 数据源增删改查
- 模型管理：
  - LLM 模型配置（模型名称、API 地址、密钥等）
  - 模型测试功能
  - 模型增删改查
- 向量数据库配置管理：
  - 向量数据库连接配置
  - 配置测试功能
  - 配置增删改查
- Prompt 模板管理：
  - 提示词模板创建、编辑、删除
  - 模板变量支持
  - 模板使用统计
- 用户管理（管理员功能）：
  - 用户列表查询（支持分页、搜索）
  - 用户审核（激活、禁用用户）
  - 用户信息编辑
  - 用户权限管理

**技术实现：**

- 配置信息使用 JPA 持久化
- 数据源连接使用 JDBC

### 7. 数据统计模块 (statistics)

**核心功能：**

- 对话历史统计：
  - 按时间维度统计
  - 按应用维度统计
  - 按用户维度统计
- 应用使用统计：
  - 应用调用次数
  - 应用使用趋势
- 知识库使用统计：
  - 知识库访问统计
  - 文档处理统计
- 用户活跃度统计：
  - 用户登录统计
  - 用户操作统计

**技术实现：**

- 基于 JPA 查询聚合统计
- 支持多维度数据统计
- 统计数据缓存机制

### 8. 数据分析模块 (analysis)

**核心功能：**
- Neo4j 图数据库集成
- 数据同步到 Neo4j（用户、应用、知识库、文档、对话、消息等实体和关系）
- 定时同步任务（可配置同步间隔，默认60分钟）
- 立即同步功能
- 同步状态监控（最近同步时间、状态、指标等）
- 图数据查询和可视化（节点和关系展示）
- 数据源配置（通过数据源管理配置 Neo4j 连接）

**技术实现：**
- Neo4j Java Driver
- Spring Scheduled 定时任务
- 系统配置管理（存储同步设置）
- 数据源管理集成

### 9. 用户行为日志模块 (userlog)

**核心功能：**
- 用户行为日志采集（基于 AOP 切面）
- Elasticsearch 日志存储
- 日志查询和检索（多维度查询：用户、模块、操作类型、时间范围等）
- 日志聚合分析（操作类型统计、模块统计等）
- 数据源配置（通过数据源管理配置 Elasticsearch 连接）
- 动态索引管理（自动创建索引和映射）

**技术实现：**
- AOP 切面编程（@UserAction 注解）
- Elasticsearch Java Client
- 异步日志保存（@Async）
- 动态索引管理
- 数据源管理集成

**详细文档：** `doc/14.用户行为日志功能设计文档.md`

### 10. 记忆管理模块 (memory)

**核心功能：**

- **自动记忆抽取**：
  - 从用户问题和助手回答中自动抽取长期可复用的信息
  - 使用LLM模型进行记忆抽取，输出严格JSON格式
  - 支持长期记忆（long_term）和实体记忆（entity）两种类型
- **记忆上下文构建**：
  - 读取近期记忆（各类型最多30条）
  - 按当前问题做轻量token匹配挑选（各类型最多8条）
  - 单条记忆截断到400字符以内，组装成可注入提示词的文本块
- **记忆存储与管理**：
  - 基于唯一键（user_id, scope_type, scope_id, memory_type, memory_key）自动去重和更新
  - 容量控制：每类记忆最多保留200条，超出自动软删除最旧记录
  - 作用域隔离：支持按智能问答/知识库/应用进行记忆隔离
- **管理端接口**：
  - 管理员可查看指定用户的记忆列表
  - 管理员可清空指定用户的记忆（软删除）
- **用户端接口**：
  - 用户可查看自己的记忆列表
  - 用户可清空自己的记忆（支持按作用域筛选）

**技术实现：**

- 使用LLM模型进行记忆抽取（优先使用当前问答模型，否则使用默认模型）
- 异步处理记忆更新，不影响主流程延迟
- 基于JPA进行数据持久化
- 支持软删除机制，便于审计和恢复

**详细文档：** `doc/15.记忆模块功能设计文档.md`

### 11. MCP 协议服务模块 (mcp)

**核心功能：**

- MCP 服务配置管理
- 浏览器搜索服务：
  - 实时网络搜索
  - 搜索结果处理
- 地理位置服务：
  - 获取位置信息
  - 位置数据缓存
- 时间服务：
  - 获取当前时间
  - 时区信息处理

**技术实现：**

- HTTP 客户端调用外部服务
- 服务结果缓存机制
- 统一的服务接口封装

### 12. 模型配置模块 (model)

**核心功能：**

- 问答模型管理：
  - 模型配置（名称、API 地址、密钥等）
  - 模型测试功能
  - 模型增删改查
- 嵌入模型管理：
  - 嵌入模型配置
  - 模型测试功能
  - 模型切换支持

**技术实现：**

- 模型配置使用 JPA 持久化
- 模型测试通过 API 调用验证
- 支持多种模型提供商

### 13. 数据源管理模块 (datasource)

**核心功能：**

- 数据源配置：
  - 数据库连接配置（PostgreSQL、MySQL、Oracle 等）
  - 连接参数管理
  - 数据源可见性控制
- 连接管理：
  - 连接测试功能
  - 连接池管理
  - 连接状态监控
- 表结构管理：
  - 自动发现表结构
  - 表结构缓存
  - 表结构更新机制

**技术实现：**

- 使用 JDBC 进行数据库连接
- 动态加载数据库驱动
- 表结构信息缓存到数据库

### 14. 实体架构优化

**核心改进：**

- **BaseEntity 统一设计**：
  - 统一的实体基类，包含所有通用字段（id, createTime, updateTime, createBy, updateBy, deleted）
  - 支持软删除模式，通过 deleted 字段实现
  - 自动填充创建人、更新人、时间戳等信息
- **移除中间层实体**：
  - 移除了 BaseSoftDeleteEntity 等中间层实体
  - 所有实体直接继承 BaseEntity，简化继承结构
  - 减少代码复杂度，提高可维护性
- **完善的填充逻辑**：
  - EntityLifecycleUtil 提供统一的实体生命周期管理
  - 自动填充所有 BaseEntity 字段
  - 支持模块特定的 DateTimeUtil 类进行定制化填充
  - 确保所有实体数据的完整性和一致性
- **实体类验证**：
  - 验证所有15个BaseEntity继承实体的填充逻辑
  - 确保所有字段（创建时间、更新时间、创建人、更新人、删除标记）都被正确填充
  - 修复AiAppUser等实体的填充逻辑，补充缺失的DateTimeUtil方法

**技术实现：**

- 使用 JPA @MappedSuperclass 注解实现实体继承
- 通过 @PrePersist 和 @PreUpdate 注解实现自动填充
- 反射机制实现动态字段填充
- 模块化的 DateTimeUtil 设计，支持不同业务场景的定制化需求
- 11个模块特定的DateTimeUtil类（如PermissionDateTimeUtil、ChatDateTimeUtil等）
- 特殊实体处理：UserActionLog使用UserLogDateTimeUtil，DrawIOHistory使用SystemDateTimeUtil

### 15. 记忆管理模块增强

**核心改进：**

- **UI 界面优化**：
  - 用户端记忆管理：新增"重置查询条件"和"清空记忆"双按钮设计
  - 管理端记忆管理：同样的双按钮设计，避免功能混淆
  - 按钮颜色区分：清空查询条件（橙色警告）、清空记忆（红色危险）
- **功能细化**：
  - 清空查询条件：重置搜索关键词、范围选择、类型选择、分页等
  - 清空记忆：根据当前选择的范围清空对应用户的记忆数据
  - 保持确认对话框，确保操作安全性
- **用户体验提升**：
  - 清晰的功能区分，避免误操作
  - 操作成功提示，提升用户反馈
  - 保持原有的数据安全性设计

**技术实现：**

- 前端 Vue 组件中添加新的按钮和对应方法
- 保持现有的 clearUserMemory API 不变
- 新增 handleClearFilters 方法处理查询条件重置
- 使用 Element Plus 的按钮组件实现视觉区分

### 16. 公共组件模块 (common)

**核心功能：**

- 统一异常处理：
  - 全局异常处理器（GlobalExceptionHandler）
  - 自定义异常类型
  - 统一错误响应格式
- 统一 API 响应格式：
  - ApiResponse 统一响应封装
  - 成功和失败响应格式
  - 分页响应格式
- 基础控制器：
  - BaseController 提供通用方法
  - 统一参数验证
- 工具类：
  - 日期时间工具
  - 字符串工具
  - 文件工具
  - 加密工具等
- SSE 响应工具：
  - 流式响应封装
  - SSE 格式处理

**技术实现：**

- 使用 Spring AOP 实现统一异常处理
- 使用 Bean Validation 进行参数验证
- 工具类使用静态方法设计

## 开发环境要求

- **JDK**: 17 或更高版本
- **Maven**: 3.6+
- **数据库**: PostgreSQL 12+ / MySQL 5.7+ / Oracle 12+
- **Redis**: 6.0+ (可选，用于缓存)
- **对象存储**: RustFS (S3兼容，用于文档存储)
- **向量数据库**: 根据需求选择安装（Qdrant/Milvus/FAISS/Chroma/Weaviate/PgVector/Elasticsearch）
- **OCR服务**: EasyOCR (可选，用于图片和PDF文字识别)

## Docker 部署

### Elasticsearch 8.11.0

#### 方式一：使用 Docker 命令

使用 Docker 快速启动 Elasticsearch（单节点模式，禁用安全认证）：

```bash
docker run -d -p 9200:9200 -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "xpack.security.http.ssl.enabled=false" \
  elasticsearch:8.11.0
```

#### 方式二：使用 Docker Compose

项目提供了 `docker-compose.yml` 文件，可以使用以下命令启动：

```bash
# 启动服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f elasticsearch

# 停止服务
docker-compose down

# 停止服务并删除数据卷
docker-compose down -v
```

**参数说明：**

- `-p 9200:9200`: HTTP REST API 端口
- `-p 9300:9300`: 节点通信端口
- `discovery.type=single-node`: 单节点模式
- `xpack.security.enabled=false`: 禁用 X-Pack 安全功能
- `xpack.security.http.ssl.enabled=false`: 禁用 HTTPS
- `ES_JAVA_OPTS=-Xms512m -Xmx512m`: JVM 内存设置

**注意**: 此配置仅用于开发环境，生产环境建议启用安全认证。

验证 Elasticsearch 是否启动成功：

```bash
curl http://localhost:9200
```

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

#### RustFS配置（RustFS 100% 兼容 MinIO 配置）

```yaml
minio:
  endpoint: http://localhost:9000
  access-key: rustfsadmin
  secret-key: rustfsadmin
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
- **智能分块策略**: 根据文件类型自动选择分块方式，无需手动配置
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
