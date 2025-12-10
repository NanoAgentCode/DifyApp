-- ============================================
-- DifyApp 数据库完整初始化脚本
-- PostgreSQL 15
-- ============================================
-- 说明：此脚本包含所有数据库表的创建语句，按执行顺序排列
-- 适用于全新安装或完整重建数据库

-- 创建数据库（如果不存在）
-- 注意：需要在PostgreSQL中手动执行，或者使用psql命令行工具
-- CREATE DATABASE difyapp;

-- 连接到difyapp数据库后执行以下SQL

-- ============================================
-- 第一部分：清理旧表（可选，谨慎使用）
-- ============================================
-- 删除旧的user表（如果存在）
DROP TABLE IF EXISTS "user" CASCADE;
DROP TABLE IF EXISTS user CASCADE;
DROP TABLE IF EXISTS "USER" CASCADE;

-- ============================================
-- 第二部分：基础表
-- ============================================

-- ============================================
-- 1. 创建用户表 (SYS_USER)
-- ============================================
DROP TABLE IF EXISTS "SYS_USER" CASCADE;

CREATE TABLE "SYS_USER" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role INTEGER DEFAULT 2,
    status INTEGER DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE "SYS_USER" IS '用户表';
COMMENT ON COLUMN "SYS_USER".id IS '用户编号';
COMMENT ON COLUMN "SYS_USER".username IS '用户名';
COMMENT ON COLUMN "SYS_USER".password IS '密码（BCrypt加密）';
COMMENT ON COLUMN "SYS_USER".role IS '角色：1-管理员，2-普通用户';
COMMENT ON COLUMN "SYS_USER".status IS '状态：0-待审核，1-已激活，2-已禁用';
COMMENT ON COLUMN "SYS_USER".create_time IS '创建时间';
COMMENT ON COLUMN "SYS_USER".update_time IS '更新时间';
COMMENT ON COLUMN "SYS_USER".deleted IS '是否删除：0-未删除，1-已删除';

-- 创建索引
CREATE INDEX idx_user_username ON "SYS_USER"(username);
CREATE INDEX idx_user_status ON "SYS_USER"(status);
CREATE INDEX idx_user_role ON "SYS_USER"(role);

-- ============================================
-- 2. 创建系统配置表 (SYSTEM_CONFIG)
-- ============================================
DROP TABLE IF EXISTS "SYSTEM_CONFIG" CASCADE;

CREATE TABLE "SYSTEM_CONFIG" (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    description VARCHAR(500),
    config_group VARCHAR(50),
    config_type VARCHAR(20),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    creator VARCHAR(64),
    creator_id BIGINT,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE "SYSTEM_CONFIG" IS '系统配置表（通用配置存储）';
COMMENT ON COLUMN "SYSTEM_CONFIG".id IS '配置编号';
COMMENT ON COLUMN "SYSTEM_CONFIG".config_key IS '配置键（唯一，如：help.knowledgeBaseId, help.modelId）';
COMMENT ON COLUMN "SYSTEM_CONFIG".config_value IS '配置值（JSON格式，支持复杂类型）';
COMMENT ON COLUMN "SYSTEM_CONFIG".description IS '配置描述';
COMMENT ON COLUMN "SYSTEM_CONFIG".config_group IS '配置分组（如：help-帮助配置，system-系统配置）';
COMMENT ON COLUMN "SYSTEM_CONFIG".config_type IS '配置类型（如：number, string, boolean, json）';
COMMENT ON COLUMN "SYSTEM_CONFIG".create_time IS '创建时间';
COMMENT ON COLUMN "SYSTEM_CONFIG".update_time IS '更新时间';
COMMENT ON COLUMN "SYSTEM_CONFIG".creator IS '创建者';
COMMENT ON COLUMN "SYSTEM_CONFIG".creator_id IS '创建者ID';
COMMENT ON COLUMN "SYSTEM_CONFIG".deleted IS '是否删除（0-未删除，1-已删除）';

-- 创建索引
CREATE INDEX idx_system_config_key ON "SYSTEM_CONFIG"(config_key);
CREATE INDEX idx_system_config_group ON "SYSTEM_CONFIG"(config_group);
CREATE INDEX idx_system_config_deleted ON "SYSTEM_CONFIG"(deleted);

-- ============================================
-- 第三部分：核心业务表
-- ============================================

-- ============================================
-- 3. 创建AI应用表 (AI_APP)
-- ============================================
DROP TABLE IF EXISTS "AI_APP" CASCADE;

CREATE TABLE "AI_APP" (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    type INTEGER NOT NULL,
    status INTEGER,
    inputs TEXT,
    icon VARCHAR(255),
    sort INTEGER,
    creator VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    tenant_id INTEGER NOT NULL,
    app_id VARCHAR(255) UNIQUE,
    api_base_url VARCHAR(500),
    stream_enabled BOOLEAN DEFAULT FALSE,
    file_upload_enabled BOOLEAN DEFAULT FALSE,
    input_enabled BOOLEAN DEFAULT TRUE,
    theme_color VARCHAR(64)
);

COMMENT ON TABLE "AI_APP" IS 'AI应用表';
COMMENT ON COLUMN "AI_APP".id IS '应用编号';
COMMENT ON COLUMN "AI_APP".name IS '应用名称';
COMMENT ON COLUMN "AI_APP".description IS '应用描述';
COMMENT ON COLUMN "AI_APP".type IS '应用类型：1-chatFlow，2-workflow';
COMMENT ON COLUMN "AI_APP".status IS '应用状态';
COMMENT ON COLUMN "AI_APP".inputs IS '应用配置JSON';
COMMENT ON COLUMN "AI_APP".icon IS '应用图标';
COMMENT ON COLUMN "AI_APP".sort IS '排序';
COMMENT ON COLUMN "AI_APP".creator IS '创建者';
COMMENT ON COLUMN "AI_APP".create_time IS '创建时间';
COMMENT ON COLUMN "AI_APP".updater IS '更新者';
COMMENT ON COLUMN "AI_APP".update_time IS '更新时间';
COMMENT ON COLUMN "AI_APP".deleted IS '是否删除：0-未删除，1-已删除';
COMMENT ON COLUMN "AI_APP".tenant_id IS '租户编号';
COMMENT ON COLUMN "AI_APP".app_id IS 'Dify API_KEY，需要唯一';
COMMENT ON COLUMN "AI_APP".api_base_url IS 'Dify API Base URL';
COMMENT ON COLUMN "AI_APP".stream_enabled IS '是否支持流式响应';
COMMENT ON COLUMN "AI_APP".file_upload_enabled IS '是否需要上传文件';
COMMENT ON COLUMN "AI_APP".input_enabled IS '是否显示文本输入框';
COMMENT ON COLUMN "AI_APP".theme_color IS '主题色';

-- 创建索引
CREATE INDEX idx_ai_app_tenant_id ON "AI_APP"(tenant_id);
CREATE INDEX idx_ai_app_type ON "AI_APP"(type);
CREATE INDEX idx_ai_app_status ON "AI_APP"(status);
CREATE INDEX idx_ai_app_app_id ON "AI_APP"(app_id);
CREATE INDEX idx_ai_app_deleted ON "AI_APP"(deleted);

-- ============================================
-- 4. 创建用户应用可见性表 (USER_APP_VISIBILITY)
-- ============================================
DROP TABLE IF EXISTS "USER_APP_VISIBILITY" CASCADE;

CREATE TABLE "USER_APP_VISIBILITY" (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    app_id BIGINT NOT NULL,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, app_id)
);

COMMENT ON TABLE "USER_APP_VISIBILITY" IS '用户应用可见性表';
COMMENT ON COLUMN "USER_APP_VISIBILITY".id IS '主键ID';
COMMENT ON COLUMN "USER_APP_VISIBILITY".user_id IS '用户ID';
COMMENT ON COLUMN "USER_APP_VISIBILITY".app_id IS '应用ID';
COMMENT ON COLUMN "USER_APP_VISIBILITY".visible IS '是否可见：true-可见，false-不可见';
COMMENT ON COLUMN "USER_APP_VISIBILITY".create_time IS '创建时间';
COMMENT ON COLUMN "USER_APP_VISIBILITY".update_time IS '更新时间';

-- 创建索引
CREATE INDEX idx_user_app_visibility_user_id ON "USER_APP_VISIBILITY"(user_id);
CREATE INDEX idx_user_app_visibility_app_id ON "USER_APP_VISIBILITY"(app_id);
CREATE INDEX idx_user_app_visibility_visible ON "USER_APP_VISIBILITY"(visible);

-- ============================================
-- 5. 创建问答模型表 (QA_MODEL)
-- ============================================
DROP TABLE IF EXISTS "QA_MODEL" CASCADE;

CREATE TABLE "QA_MODEL" (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_type VARCHAR(20),
    api_url VARCHAR(500) NOT NULL,
    api_key VARCHAR(500),
    model VARCHAR(200) NOT NULL,
    use_for VARCHAR(20) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    is_default BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE "QA_MODEL" IS '问答模型表';
COMMENT ON COLUMN "QA_MODEL".id IS '模型编号';
COMMENT ON COLUMN "QA_MODEL".name IS '模型名称';
COMMENT ON COLUMN "QA_MODEL".provider IS '提供商类型：openai, vllm, ollama';
COMMENT ON COLUMN "QA_MODEL".provider_type IS '提供商类型（原始值，用于前端显示）';
COMMENT ON COLUMN "QA_MODEL".api_url IS 'API 地址';
COMMENT ON COLUMN "QA_MODEL".api_key IS 'API Key';
COMMENT ON COLUMN "QA_MODEL".model IS '模型标识';
COMMENT ON COLUMN "QA_MODEL".use_for IS '使用场景：chat-仅智能问答, rag-仅知识库问答, both-两者都使用';
COMMENT ON COLUMN "QA_MODEL".enabled IS '是否启用：true-启用, false-禁用';
COMMENT ON COLUMN "QA_MODEL".is_default IS '是否默认：true-默认, false-非默认';
COMMENT ON COLUMN "QA_MODEL".create_time IS '创建时间';
COMMENT ON COLUMN "QA_MODEL".update_time IS '更新时间';
COMMENT ON COLUMN "QA_MODEL".deleted IS '是否删除：0-未删除，1-已删除';

-- 创建索引
CREATE INDEX idx_qa_model_provider ON "QA_MODEL"(provider);
CREATE INDEX idx_qa_model_use_for ON "QA_MODEL"(use_for);
CREATE INDEX idx_qa_model_enabled ON "QA_MODEL"(enabled);
CREATE INDEX idx_qa_model_is_default ON "QA_MODEL"(is_default);
CREATE INDEX idx_qa_model_deleted ON "QA_MODEL"(deleted);

-- ============================================
-- 6. 创建向量化模型表 (EMBEDDING_MODEL)
-- ============================================
DROP TABLE IF EXISTS "EMBEDDING_MODEL" CASCADE;

CREATE TABLE "EMBEDDING_MODEL" (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_type VARCHAR(20),
    api_url VARCHAR(500) NOT NULL,
    api_key VARCHAR(500),
    model VARCHAR(200) NOT NULL,
    timeout INTEGER DEFAULT 300000,
    batch_size INTEGER DEFAULT 100,
    enabled BOOLEAN DEFAULT TRUE,
    is_default BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE "EMBEDDING_MODEL" IS '向量化模型表';
COMMENT ON COLUMN "EMBEDDING_MODEL".id IS '模型编号';
COMMENT ON COLUMN "EMBEDDING_MODEL".name IS '模型名称';
COMMENT ON COLUMN "EMBEDDING_MODEL".provider IS '提供商类型：openai, vllm, ollama';
COMMENT ON COLUMN "EMBEDDING_MODEL".provider_type IS '提供商类型（原始值，用于前端显示）';
COMMENT ON COLUMN "EMBEDDING_MODEL".api_url IS 'API 地址';
COMMENT ON COLUMN "EMBEDDING_MODEL".api_key IS 'API Key';
COMMENT ON COLUMN "EMBEDDING_MODEL".model IS '模型标识';
COMMENT ON COLUMN "EMBEDDING_MODEL".timeout IS '超时时间（毫秒）';
COMMENT ON COLUMN "EMBEDDING_MODEL".batch_size IS '批处理大小';
COMMENT ON COLUMN "EMBEDDING_MODEL".enabled IS '是否启用：true-启用, false-禁用';
COMMENT ON COLUMN "EMBEDDING_MODEL".is_default IS '是否默认：true-默认, false-非默认';
COMMENT ON COLUMN "EMBEDDING_MODEL".create_time IS '创建时间';
COMMENT ON COLUMN "EMBEDDING_MODEL".update_time IS '更新时间';
COMMENT ON COLUMN "EMBEDDING_MODEL".deleted IS '是否删除：0-未删除，1-已删除';

-- 创建索引
CREATE INDEX idx_embedding_model_provider ON "EMBEDDING_MODEL"(provider);
CREATE INDEX idx_embedding_model_enabled ON "EMBEDDING_MODEL"(enabled);
CREATE INDEX idx_embedding_model_is_default ON "EMBEDDING_MODEL"(is_default);
CREATE INDEX idx_embedding_model_deleted ON "EMBEDDING_MODEL"(deleted);

-- ============================================
-- 7. 创建向量数据库配置表 (VECTOR_DATABASE)
-- ============================================
DROP TABLE IF EXISTS "VECTOR_DATABASE" CASCADE;

CREATE TABLE "VECTOR_DATABASE" (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    url VARCHAR(500) NOT NULL,
    api_key VARCHAR(500),
    timeout INTEGER DEFAULT 30000,
    extra_config TEXT,
    enabled BOOLEAN DEFAULT true,
    is_default BOOLEAN DEFAULT false,
    description VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE "VECTOR_DATABASE" IS '向量数据库配置表';
COMMENT ON COLUMN "VECTOR_DATABASE".id IS '配置编号';
COMMENT ON COLUMN "VECTOR_DATABASE".name IS '配置名称';
COMMENT ON COLUMN "VECTOR_DATABASE".type IS '数据库类型：qdrant-Qdrant向量数据库，milvus-Milvus向量数据库，faiss-FAISS本地文件存储，chroma-Chroma向量数据库，weaviate-Weaviate向量数据库，elasticsearch-Elasticsearch向量数据库';
COMMENT ON COLUMN "VECTOR_DATABASE".url IS '连接地址（URL或路径）';
COMMENT ON COLUMN "VECTOR_DATABASE".api_key IS 'API Key（可选）';
COMMENT ON COLUMN "VECTOR_DATABASE".timeout IS '超时时间（毫秒）';
COMMENT ON COLUMN "VECTOR_DATABASE".extra_config IS '额外配置（JSON格式）';
COMMENT ON COLUMN "VECTOR_DATABASE".enabled IS '是否启用：true-启用, false-禁用';
COMMENT ON COLUMN "VECTOR_DATABASE".is_default IS '是否默认：true-默认, false-非默认';
COMMENT ON COLUMN "VECTOR_DATABASE".description IS '描述';
COMMENT ON COLUMN "VECTOR_DATABASE".create_time IS '创建时间';
COMMENT ON COLUMN "VECTOR_DATABASE".update_time IS '更新时间';
COMMENT ON COLUMN "VECTOR_DATABASE".deleted IS '是否删除：0-未删除，1-已删除';

-- 创建索引
CREATE INDEX idx_vector_database_type ON "VECTOR_DATABASE"(type);
CREATE INDEX idx_vector_database_enabled ON "VECTOR_DATABASE"(enabled);
CREATE INDEX idx_vector_database_deleted ON "VECTOR_DATABASE"(deleted);
CREATE INDEX idx_vector_database_type_default ON "VECTOR_DATABASE"(type, is_default);

-- 插入默认配置
INSERT INTO "VECTOR_DATABASE" (name, type, url, api_key, timeout, enabled, is_default, description, create_time, update_time, deleted)
VALUES ('默认Qdrant配置', 'qdrant', 'http://localhost:6333', NULL, 30000, true, true, '从application.yml迁移的默认Qdrant配置', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT DO NOTHING;

INSERT INTO "VECTOR_DATABASE" (name, type, url, api_key, timeout, enabled, is_default, description, create_time, update_time, deleted)
VALUES ('默认Milvus配置', 'milvus', 'http://localhost:19530', NULL, 30000, true, true, '从application.yml迁移的默认Milvus配置', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT DO NOTHING;

INSERT INTO "VECTOR_DATABASE" (name, type, url, api_key, timeout, enabled, is_default, description, create_time, update_time, deleted)
VALUES ('默认FAISS配置', 'faiss', './data/faiss', NULL, NULL, true, true, '从application.yml迁移的默认FAISS配置', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT DO NOTHING;

-- ============================================
-- 8. 创建知识库表 (KNOWLEDGE_BASE)
-- ============================================
DROP TABLE IF EXISTS "KNOWLEDGE_BASE" CASCADE;

CREATE TABLE "KNOWLEDGE_BASE" (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    status INTEGER DEFAULT 1,
    creator VARCHAR(64),
    creator_id BIGINT,
    is_public BOOLEAN DEFAULT FALSE,
    embedding_model_id BIGINT,
    top_k INTEGER,
    vector_store_type VARCHAR(20) DEFAULT 'qdrant',
    vector_database_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    tenant_id INTEGER DEFAULT 1
);

COMMENT ON TABLE "KNOWLEDGE_BASE" IS '知识库表';
COMMENT ON COLUMN "KNOWLEDGE_BASE".id IS '知识库编号';
COMMENT ON COLUMN "KNOWLEDGE_BASE".name IS '知识库名称';
COMMENT ON COLUMN "KNOWLEDGE_BASE".description IS '知识库描述';
COMMENT ON COLUMN "KNOWLEDGE_BASE".status IS '知识库状态：1-启用，0-禁用';
COMMENT ON COLUMN "KNOWLEDGE_BASE".creator IS '创建者';
COMMENT ON COLUMN "KNOWLEDGE_BASE".creator_id IS '创建者ID';
COMMENT ON COLUMN "KNOWLEDGE_BASE".is_public IS '是否公开：true-公开，false-私有';
COMMENT ON COLUMN "KNOWLEDGE_BASE".embedding_model_id IS '向量化模型ID（关联EMBEDDING_MODEL表）';
COMMENT ON COLUMN "KNOWLEDGE_BASE".top_k IS 'Top-K检索数量（每个知识库可单独配置，如果为NULL则使用全局配置）';
COMMENT ON COLUMN "KNOWLEDGE_BASE".vector_store_type IS '向量存储类型：qdrant-Qdrant向量数据库，faiss-FAISS本地文件存储，milvus-Milvus向量数据库，chroma-Chroma向量数据库，weaviate-Weaviate向量数据库';
COMMENT ON COLUMN "KNOWLEDGE_BASE".vector_database_id IS '向量库实例ID（关联VECTOR_DATABASE表的id）';
COMMENT ON COLUMN "KNOWLEDGE_BASE".create_time IS '创建时间';
COMMENT ON COLUMN "KNOWLEDGE_BASE".updater IS '更新者';
COMMENT ON COLUMN "KNOWLEDGE_BASE".update_time IS '更新时间';
COMMENT ON COLUMN "KNOWLEDGE_BASE".deleted IS '是否删除：0-未删除，1-已删除';
COMMENT ON COLUMN "KNOWLEDGE_BASE".tenant_id IS '租户编号';

-- 创建索引
CREATE INDEX idx_knowledge_base_status ON "KNOWLEDGE_BASE"(status);
CREATE INDEX idx_knowledge_base_tenant_id ON "KNOWLEDGE_BASE"(tenant_id);
CREATE INDEX idx_knowledge_base_deleted ON "KNOWLEDGE_BASE"(deleted);
CREATE INDEX idx_knowledge_base_name ON "KNOWLEDGE_BASE"(name);
CREATE INDEX idx_knowledge_base_creator_id ON "KNOWLEDGE_BASE"(creator_id);
CREATE INDEX idx_knowledge_base_is_public ON "KNOWLEDGE_BASE"(is_public);
CREATE INDEX idx_knowledge_base_vector_store_type ON "KNOWLEDGE_BASE"(vector_store_type);
CREATE INDEX idx_knowledge_base_vector_database_id ON "KNOWLEDGE_BASE"(vector_database_id);

-- ============================================
-- 9. 创建用户知识库可见性表 (USER_KNOWLEDGE_BASE_VISIBILITY)
-- ============================================
DROP TABLE IF EXISTS "USER_KNOWLEDGE_BASE_VISIBILITY" CASCADE;

CREATE TABLE "USER_KNOWLEDGE_BASE_VISIBILITY" (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, knowledge_base_id)
);

COMMENT ON TABLE "USER_KNOWLEDGE_BASE_VISIBILITY" IS '用户知识库可见性表';
COMMENT ON COLUMN "USER_KNOWLEDGE_BASE_VISIBILITY".id IS '主键ID';
COMMENT ON COLUMN "USER_KNOWLEDGE_BASE_VISIBILITY".user_id IS '用户ID';
COMMENT ON COLUMN "USER_KNOWLEDGE_BASE_VISIBILITY".knowledge_base_id IS '知识库ID';
COMMENT ON COLUMN "USER_KNOWLEDGE_BASE_VISIBILITY".visible IS '是否可见：true-可见，false-不可见';
COMMENT ON COLUMN "USER_KNOWLEDGE_BASE_VISIBILITY".create_time IS '创建时间';
COMMENT ON COLUMN "USER_KNOWLEDGE_BASE_VISIBILITY".update_time IS '更新时间';

-- 创建索引
CREATE INDEX idx_user_kb_visibility_user_id ON "USER_KNOWLEDGE_BASE_VISIBILITY"(user_id);
CREATE INDEX idx_user_kb_visibility_kb_id ON "USER_KNOWLEDGE_BASE_VISIBILITY"(knowledge_base_id);
CREATE INDEX idx_user_kb_visibility_visible ON "USER_KNOWLEDGE_BASE_VISIBILITY"(visible);

-- ============================================
-- 10. 创建知识库文档表 (KNOWLEDGE_BASE_DOCUMENT)
-- ============================================
DROP TABLE IF EXISTS "KNOWLEDGE_BASE_DOCUMENT" CASCADE;

CREATE TABLE "KNOWLEDGE_BASE_DOCUMENT" (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_url VARCHAR(500),
    file_size BIGINT,
    file_type VARCHAR(50),
    mime_type VARCHAR(100),
    storage_type VARCHAR(20) DEFAULT 'minio',
    status INTEGER DEFAULT 1,
    upload_user VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    tenant_id INTEGER DEFAULT 1
);

COMMENT ON TABLE "KNOWLEDGE_BASE_DOCUMENT" IS '知识库文档表';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".id IS '文档编号';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".knowledge_base_id IS '知识库编号';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".file_name IS '文件名（存储后的文件名）';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".original_file_name IS '原始文件名';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".file_path IS '文件路径（在MinIO中的路径）';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".file_url IS '文件访问URL';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".file_size IS '文件大小（字节）';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".file_type IS '文件类型（扩展名）';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".mime_type IS 'MIME类型';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".storage_type IS '存储类型（minio）';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".status IS '文档状态：1-正常，0-已删除';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".upload_user IS '上传用户';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".create_time IS '创建时间';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".update_time IS '更新时间';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".deleted IS '是否删除：0-未删除，1-已删除';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".tenant_id IS '租户编号';

-- 创建索引
CREATE INDEX idx_kb_doc_kb_id ON "KNOWLEDGE_BASE_DOCUMENT"(knowledge_base_id);
CREATE INDEX idx_kb_doc_status ON "KNOWLEDGE_BASE_DOCUMENT"(status);
CREATE INDEX idx_kb_doc_deleted ON "KNOWLEDGE_BASE_DOCUMENT"(deleted);
CREATE INDEX idx_kb_doc_tenant_id ON "KNOWLEDGE_BASE_DOCUMENT"(tenant_id);

-- ============================================
-- 第四部分：扩展功能表
-- ============================================

-- ============================================
-- 11. 创建数据源表 (DATA_SOURCE)
-- ============================================
DROP TABLE IF EXISTS "DATA_SOURCE" CASCADE;

CREATE TABLE "DATA_SOURCE" (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    type VARCHAR(20) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER,
    database VARCHAR(100),
    username VARCHAR(100),
    password VARCHAR(500),
    status INTEGER DEFAULT 1,
    creator VARCHAR(64),
    creator_id BIGINT,
    is_public BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    tenant_id INTEGER DEFAULT 1
);

COMMENT ON TABLE "DATA_SOURCE" IS '数据源表';
COMMENT ON COLUMN "DATA_SOURCE".id IS '数据源编号';
COMMENT ON COLUMN "DATA_SOURCE".name IS '数据源名称';
COMMENT ON COLUMN "DATA_SOURCE".description IS '数据源描述';
COMMENT ON COLUMN "DATA_SOURCE".type IS '数据库类型：postgresql, mysql, oracle, mongodb';
COMMENT ON COLUMN "DATA_SOURCE".host IS '主机地址';
COMMENT ON COLUMN "DATA_SOURCE".port IS '端口号';
COMMENT ON COLUMN "DATA_SOURCE".database IS '数据库名称';
COMMENT ON COLUMN "DATA_SOURCE".username IS '用户名';
COMMENT ON COLUMN "DATA_SOURCE".password IS '密码（加密存储）';
COMMENT ON COLUMN "DATA_SOURCE".status IS '数据源状态：1-启用，0-禁用';
COMMENT ON COLUMN "DATA_SOURCE".creator IS '创建者';
COMMENT ON COLUMN "DATA_SOURCE".creator_id IS '创建者ID';
COMMENT ON COLUMN "DATA_SOURCE".is_public IS '是否公开：true-公开，false-私有';
COMMENT ON COLUMN "DATA_SOURCE".create_time IS '创建时间';
COMMENT ON COLUMN "DATA_SOURCE".updater IS '更新者';
COMMENT ON COLUMN "DATA_SOURCE".update_time IS '更新时间';
COMMENT ON COLUMN "DATA_SOURCE".deleted IS '是否删除：0-未删除，1-已删除';
COMMENT ON COLUMN "DATA_SOURCE".tenant_id IS '租户编号';

-- 创建索引
CREATE INDEX idx_data_source_tenant_id ON "DATA_SOURCE"(tenant_id);
CREATE INDEX idx_data_source_type ON "DATA_SOURCE"(type);
CREATE INDEX idx_data_source_status ON "DATA_SOURCE"(status);
CREATE INDEX idx_data_source_deleted ON "DATA_SOURCE"(deleted);
CREATE INDEX idx_data_source_creator_id ON "DATA_SOURCE"(creator_id);

-- ============================================
-- 12. 创建用户数据源可见性表 (USER_DATA_SOURCE_VISIBILITY)
-- ============================================
DROP TABLE IF EXISTS "USER_DATA_SOURCE_VISIBILITY" CASCADE;

CREATE TABLE "USER_DATA_SOURCE_VISIBILITY" (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_data_source UNIQUE (user_id, data_source_id)
);

COMMENT ON TABLE "USER_DATA_SOURCE_VISIBILITY" IS '用户数据源可见性表';
COMMENT ON COLUMN "USER_DATA_SOURCE_VISIBILITY".id IS '主键ID';
COMMENT ON COLUMN "USER_DATA_SOURCE_VISIBILITY".user_id IS '用户ID';
COMMENT ON COLUMN "USER_DATA_SOURCE_VISIBILITY".data_source_id IS '数据源ID';
COMMENT ON COLUMN "USER_DATA_SOURCE_VISIBILITY".visible IS '是否可见：true-可见，false-不可见';
COMMENT ON COLUMN "USER_DATA_SOURCE_VISIBILITY".create_time IS '创建时间';
COMMENT ON COLUMN "USER_DATA_SOURCE_VISIBILITY".update_time IS '更新时间';

-- 创建索引
CREATE INDEX idx_user_data_source_visibility_user_id ON "USER_DATA_SOURCE_VISIBILITY"(user_id);
CREATE INDEX idx_user_data_source_visibility_data_source_id ON "USER_DATA_SOURCE_VISIBILITY"(data_source_id);

-- ============================================
-- 13. 创建表结构缓存表 (TABLE_SCHEMA_CACHE)
-- ============================================
DROP TABLE IF EXISTS "TABLE_SCHEMA_CACHE" CASCADE;

CREATE TABLE "TABLE_SCHEMA_CACHE" (
    id BIGSERIAL PRIMARY KEY,
    data_source_id BIGINT NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    schema_info TEXT,
    last_refresh_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_data_source_table UNIQUE (data_source_id, table_name)
);

COMMENT ON TABLE "TABLE_SCHEMA_CACHE" IS '表结构缓存表';
COMMENT ON COLUMN "TABLE_SCHEMA_CACHE".id IS '主键ID';
COMMENT ON COLUMN "TABLE_SCHEMA_CACHE".data_source_id IS '数据源ID';
COMMENT ON COLUMN "TABLE_SCHEMA_CACHE".table_name IS '表名';
COMMENT ON COLUMN "TABLE_SCHEMA_CACHE".schema_info IS '表结构信息（JSON格式）';
COMMENT ON COLUMN "TABLE_SCHEMA_CACHE".last_refresh_time IS '最后刷新时间';
COMMENT ON COLUMN "TABLE_SCHEMA_CACHE".create_time IS '创建时间';
COMMENT ON COLUMN "TABLE_SCHEMA_CACHE".update_time IS '更新时间';

-- 创建索引
CREATE INDEX idx_table_schema_cache_data_source_id ON "TABLE_SCHEMA_CACHE"(data_source_id);
CREATE INDEX idx_table_schema_cache_table_name ON "TABLE_SCHEMA_CACHE"(table_name);

-- ============================================
-- 第五部分：DrawIO相关表
-- ============================================

-- ============================================
-- 14. 创建 DrawIO 图表表 (DRAWIO_DIAGRAM)
-- ============================================
DROP TABLE IF EXISTS "DRAWIO_DIAGRAM" CASCADE;

CREATE TABLE "DRAWIO_DIAGRAM" (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    diagram_type VARCHAR(50),
    diagram_json TEXT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE "DRAWIO_DIAGRAM" IS 'DrawIO 图表表';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".id IS '图表ID';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".name IS '图表名称';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".diagram_type IS '图表类型：flowchart-流程图, architecture-架构图, mindmap-思维导图, sequence-时序图, uml-UML图, org-组织架构, network-网络图, custom-自定义';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".diagram_json IS '图表JSON内容（AntV X6格式）';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".user_id IS '用户ID';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".create_time IS '创建时间';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".update_time IS '更新时间';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".deleted IS '是否删除：0-未删除，1-已删除';

-- 创建索引
CREATE INDEX idx_drawio_diagram_user_id ON "DRAWIO_DIAGRAM"(user_id);
CREATE INDEX idx_drawio_diagram_type ON "DRAWIO_DIAGRAM"(diagram_type);
CREATE INDEX idx_drawio_diagram_deleted ON "DRAWIO_DIAGRAM"(deleted);
CREATE INDEX idx_drawio_diagram_user_deleted ON "DRAWIO_DIAGRAM"(user_id, deleted);
CREATE INDEX idx_drawio_diagram_create_time ON "DRAWIO_DIAGRAM"(create_time);

-- ============================================
-- 15. 创建 DrawIO 历史记录表 (DRAWIO_HISTORY)
-- ============================================
DROP TABLE IF EXISTS "DRAWIO_HISTORY" CASCADE;

CREATE TABLE "DRAWIO_HISTORY" (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    prompt VARCHAR(500) NOT NULL,
    diagram_type VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE "DRAWIO_HISTORY" IS 'DrawIO 历史记录表';
COMMENT ON COLUMN "DRAWIO_HISTORY".id IS '历史记录ID';
COMMENT ON COLUMN "DRAWIO_HISTORY".user_id IS '用户ID';
COMMENT ON COLUMN "DRAWIO_HISTORY".prompt IS '提示词内容';
COMMENT ON COLUMN "DRAWIO_HISTORY".diagram_type IS '图表类型：flowchart-流程图, architecture-架构图, mindmap-思维导图, sequence-时序图, uml-UML图, org-组织架构, network-网络图, custom-自定义';
COMMENT ON COLUMN "DRAWIO_HISTORY".create_time IS '创建时间';
COMMENT ON COLUMN "DRAWIO_HISTORY".deleted IS '是否删除：0-未删除，1-已删除';

-- 创建索引
CREATE INDEX idx_drawio_history_user_id ON "DRAWIO_HISTORY"(user_id);
CREATE INDEX idx_drawio_history_deleted ON "DRAWIO_HISTORY"(deleted);
CREATE INDEX idx_drawio_history_user_deleted ON "DRAWIO_HISTORY"(user_id, deleted);
CREATE INDEX idx_drawio_history_create_time ON "DRAWIO_HISTORY"(create_time);

-- ============================================
-- 第六部分：初始化数据
-- ============================================

-- ============================================
-- 16. 插入默认管理员账户
-- ============================================
-- 注意：密码是 "admin123" 的BCrypt加密结果
-- BCrypt每次加密结果不同，这里提供一个示例哈希值
-- 如果使用应用启动时的DataInitializer，会自动创建管理员账户
-- 如果需要手动插入，可以使用以下SQL（密码：admin123）

INSERT INTO "SYS_USER" (username, password, role, status, create_time, update_time, deleted)
VALUES (
    'admin',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ/2', -- admin123的BCrypt哈希
    1, -- 管理员
    1, -- 已激活
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0  -- 未删除
) ON CONFLICT (username) DO NOTHING;

-- ============================================
-- 脚本执行完成
-- ============================================
-- 所有表已创建完成，可以开始使用系统
-- 注意：如果表已存在，DROP TABLE语句会删除旧表及其数据，请谨慎使用

