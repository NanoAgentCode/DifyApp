-- ============================================
-- DifyApp 数据库完整初始化脚本（融合版）
-- PostgreSQL 15
-- ============================================
-- 说明：此脚本包含所有数据库表的创建语句，按执行顺序排列
-- 适用于全新安装或完整重建数据库
-- 
-- 本脚本已融合以下迁移脚本的内容：
-- 1. add_summary_column.sql - 知识库摘要字段（已包含在KNOWLEDGE_BASE表定义中）
-- 2. migration_add_multimodal_support.sql - 多模态支持字段（已包含在QA_MODEL表定义中）
-- 3. init_ocr_config.sql - OCR服务配置初始化（已包含在初始化数据部分）
-- 4. 知识库ID约束 - 确保知识库ID不能为0（0保留给文档解读使用）
--
-- 对于现有数据库的升级，请使用对应的迁移脚本
-- 
-- 注意：文档解读向量表的user_id字段迁移脚本需要在pgvector数据库中执行
-- 详见：migration_add_user_id_to_document_reader_vectors.sql

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
-- 2.1 创建Agent技能配置表 (AGENT_SKILL_CONFIG)
-- ============================================
DROP TABLE IF EXISTS "AGENT_SKILL_CONFIG" CASCADE;

CREATE TABLE "AGENT_SKILL_CONFIG" (
    id BIGSERIAL PRIMARY KEY,
    skill_key VARCHAR(100) NOT NULL UNIQUE,
    skill_name VARCHAR(200),
    skill_path VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    visible_to_user BOOLEAN DEFAULT FALSE,
    description VARCHAR(1000),
    source_type VARCHAR(30),
    ext_json TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    creator VARCHAR(64),
    creator_id BIGINT,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE "AGENT_SKILL_CONFIG" IS 'Agent技能配置表';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".id IS '主键ID';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".skill_key IS 'Skill唯一键（目录名）';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".skill_name IS 'Skill名称';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".skill_path IS 'Skill路径（相对项目根）';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".enabled IS '是否启用';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".visible_to_user IS '普通用户是否可见';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".description IS '技能描述';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".source_type IS '来源类型（system/custom）';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".ext_json IS '扩展JSON';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".create_time IS '创建时间';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".update_time IS '更新时间';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".creator IS '创建者';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".creator_id IS '创建者ID';
COMMENT ON COLUMN "AGENT_SKILL_CONFIG".deleted IS '是否删除（0-未删除，1-已删除）';

CREATE INDEX idx_agent_skill_key ON "AGENT_SKILL_CONFIG"(skill_key);
CREATE INDEX idx_agent_skill_deleted ON "AGENT_SKILL_CONFIG"(deleted);
CREATE INDEX idx_agent_skill_enabled ON "AGENT_SKILL_CONFIG"(enabled);
CREATE INDEX idx_agent_skill_visible_user ON "AGENT_SKILL_CONFIG"(visible_to_user);

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
COMMENT ON COLUMN "AI_APP".input_enabled IS '已废弃，表单由输入字段配置控制，保留列兼容旧数据';
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
    supports_multimodal BOOLEAN DEFAULT FALSE,
    supports_vision BOOLEAN DEFAULT FALSE,
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
COMMENT ON COLUMN "QA_MODEL".supports_multimodal IS '是否支持多模态：true-支持, false-不支持';
COMMENT ON COLUMN "QA_MODEL".supports_vision IS '是否支持视觉输入：true-支持, false-不支持';
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
    allow_create_knowledge_base BOOLEAN DEFAULT true,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE "VECTOR_DATABASE" IS '向量数据库配置表';
COMMENT ON COLUMN "VECTOR_DATABASE".id IS '配置编号';
COMMENT ON COLUMN "VECTOR_DATABASE".name IS '配置名称';
COMMENT ON COLUMN "VECTOR_DATABASE".type IS '数据库类型：qdrant-Qdrant向量数据库，milvus-Milvus向量数据库，faiss-FAISS本地文件存储，chroma-Chroma向量数据库，weaviate-Weaviate向量数据库，elasticsearch-Elasticsearch向量数据库，pgvector-PgVector向量数据库（PostgreSQL扩展）';
COMMENT ON COLUMN "VECTOR_DATABASE".url IS '连接地址（URL或路径）';
COMMENT ON COLUMN "VECTOR_DATABASE".api_key IS 'API Key（可选）';
COMMENT ON COLUMN "VECTOR_DATABASE".timeout IS '超时时间（毫秒）';
COMMENT ON COLUMN "VECTOR_DATABASE".extra_config IS '额外配置（JSON格式）';
COMMENT ON COLUMN "VECTOR_DATABASE".enabled IS '是否启用：true-启用, false-禁用';
COMMENT ON COLUMN "VECTOR_DATABASE".is_default IS '是否默认：true-默认, false-非默认';
COMMENT ON COLUMN "VECTOR_DATABASE".description IS '描述';
COMMENT ON COLUMN "VECTOR_DATABASE".allow_create_knowledge_base IS '是否允许新建知识库：true-允许, false-不允许，默认为true';
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
    tenant_id INTEGER DEFAULT 1,
    summary TEXT
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

-- 添加约束：确保知识库ID不能为0（0保留给文档解读使用）
ALTER TABLE "KNOWLEDGE_BASE" ADD CONSTRAINT check_knowledge_base_id_not_zero CHECK (id > 0);

-- 确保知识库ID序列从1开始
-- 注意：如果序列已经存在且当前值小于1，将其重置为1
DO $$
DECLARE
    seq_name TEXT;
    current_val BIGINT;
BEGIN
    -- 查找知识库表的序列名
    SELECT pg_get_serial_sequence('"KNOWLEDGE_BASE"', 'id') INTO seq_name;
    
    IF seq_name IS NOT NULL THEN
        -- 获取当前序列值
        EXECUTE 'SELECT last_value FROM ' || seq_name INTO current_val;
        
        -- 如果当前值小于1，重置为1
        IF current_val < 1 THEN
            EXECUTE 'ALTER SEQUENCE ' || seq_name || ' RESTART WITH 1';
            RAISE NOTICE '知识库ID序列已重置为1';
        END IF;
    END IF;
END $$;
COMMENT ON COLUMN "KNOWLEDGE_BASE".create_time IS '创建时间';
COMMENT ON COLUMN "KNOWLEDGE_BASE".updater IS '更新者';
COMMENT ON COLUMN "KNOWLEDGE_BASE".update_time IS '更新时间';
COMMENT ON COLUMN "KNOWLEDGE_BASE".deleted IS '是否删除：0-未删除，1-已删除';
COMMENT ON COLUMN "KNOWLEDGE_BASE".tenant_id IS '租户编号';
COMMENT ON COLUMN "KNOWLEDGE_BASE".summary IS '知识库智能摘要';

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
    vectorized_status INTEGER,
    vectorized_time TIMESTAMP,
    vectorized_error VARCHAR(500),
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
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".vectorized_status IS '向量化状态：0-未向量化，1-向量化中，2-已向量化，3-向量化失败';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".vectorized_time IS '向量化完成时间';
COMMENT ON COLUMN "KNOWLEDGE_BASE_DOCUMENT".vectorized_error IS '向量化错误信息';
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
-- 13. 创建文档解读表 (DOCUMENT_READER)
-- ============================================
DROP TABLE IF EXISTS "DOCUMENT_READER" CASCADE;

CREATE TABLE "DOCUMENT_READER" (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255),
    original_file_name VARCHAR(255),
    file_path VARCHAR(500),
    file_url VARCHAR(500),
    file_size BIGINT,
    file_type VARCHAR(50),
    mime_type VARCHAR(100),
    storage_type VARCHAR(20),
    status INTEGER,
    user_id BIGINT,
    total_pages INTEGER,
    vectorized_status INTEGER,
    vectorized_error VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE "DOCUMENT_READER" IS '文档解读主表';
COMMENT ON COLUMN "DOCUMENT_READER".id IS '文档编号';
COMMENT ON COLUMN "DOCUMENT_READER".file_name IS '文件名（存储后的文件名）';
COMMENT ON COLUMN "DOCUMENT_READER".original_file_name IS '原始文件名';
COMMENT ON COLUMN "DOCUMENT_READER".file_path IS '文件路径（在MinIO中的路径）';
COMMENT ON COLUMN "DOCUMENT_READER".file_url IS '文件访问URL';
COMMENT ON COLUMN "DOCUMENT_READER".file_size IS '文件大小（字节）';
COMMENT ON COLUMN "DOCUMENT_READER".file_type IS '文件类型（扩展名）';
COMMENT ON COLUMN "DOCUMENT_READER".mime_type IS 'MIME类型';
COMMENT ON COLUMN "DOCUMENT_READER".storage_type IS '存储类型（minio）';
COMMENT ON COLUMN "DOCUMENT_READER".status IS '文档状态：1-正常，0-已删除';
COMMENT ON COLUMN "DOCUMENT_READER".user_id IS '上传用户ID';
COMMENT ON COLUMN "DOCUMENT_READER".total_pages IS '总页数（用于PDF等分页文档）';
COMMENT ON COLUMN "DOCUMENT_READER".vectorized_status IS '向量化状态：0-未向量化，1-向量化中，2-已向量化，3-向量化失败';
COMMENT ON COLUMN "DOCUMENT_READER".vectorized_error IS '向量化错误信息';
COMMENT ON COLUMN "DOCUMENT_READER".create_time IS '创建时间';
COMMENT ON COLUMN "DOCUMENT_READER".update_time IS '更新时间';
COMMENT ON COLUMN "DOCUMENT_READER".deleted IS '是否删除：0-未删除，1-已删除';

CREATE INDEX idx_document_reader_user_id ON "DOCUMENT_READER"(user_id);
CREATE INDEX idx_document_reader_status ON "DOCUMENT_READER"(status);
CREATE INDEX idx_document_reader_deleted ON "DOCUMENT_READER"(deleted);
CREATE INDEX idx_document_reader_vectorized_status ON "DOCUMENT_READER"(vectorized_status);

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
    diagram_json TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE "DRAWIO_HISTORY" IS 'DrawIO 历史记录表';
COMMENT ON COLUMN "DRAWIO_HISTORY".id IS '历史记录ID';
COMMENT ON COLUMN "DRAWIO_HISTORY".user_id IS '用户ID';
COMMENT ON COLUMN "DRAWIO_HISTORY".prompt IS '提示词内容';
COMMENT ON COLUMN "DRAWIO_HISTORY".diagram_type IS '图表类型：flowchart-流程图, architecture-架构图, mindmap-思维导图, sequence-时序图, uml-UML图, org-组织架构, network-网络图, custom-自定义';
COMMENT ON COLUMN "DRAWIO_HISTORY".diagram_json IS '图表JSON内容（AntV Infographic DSL）';
COMMENT ON COLUMN "DRAWIO_HISTORY".create_time IS '创建时间';
COMMENT ON COLUMN "DRAWIO_HISTORY".deleted IS '是否删除：0-未删除，1-已删除';

-- 创建索引
CREATE INDEX idx_drawio_history_user_id ON "DRAWIO_HISTORY"(user_id);
CREATE INDEX idx_drawio_history_deleted ON "DRAWIO_HISTORY"(deleted);
CREATE INDEX idx_drawio_history_user_deleted ON "DRAWIO_HISTORY"(user_id, deleted);
CREATE INDEX idx_drawio_history_create_time ON "DRAWIO_HISTORY"(create_time);

-- ============================================
-- 16. 创建用户记忆表 (USER_MEMORY)
-- ============================================
DROP TABLE IF EXISTS "USER_MEMORY" CASCADE;

CREATE TABLE "USER_MEMORY" (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'chat',
    scope_id BIGINT,
    memory_type VARCHAR(32) NOT NULL,
    memory_key VARCHAR(200) NOT NULL,
    content TEXT,
    importance INTEGER,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    CONSTRAINT uk_user_memory UNIQUE (user_id, scope_type, scope_id, memory_type, memory_key)
);

COMMENT ON TABLE "USER_MEMORY" IS '用户记忆表（长期记忆/实体记忆）';
COMMENT ON COLUMN "USER_MEMORY".id IS '主键';
COMMENT ON COLUMN "USER_MEMORY".user_id IS '用户ID';
COMMENT ON COLUMN "USER_MEMORY".scope_type IS '作用域类型：chat/knowledge_base/app';
COMMENT ON COLUMN "USER_MEMORY".scope_id IS '作用域ID（知识库/应用ID，chat为空）';
COMMENT ON COLUMN "USER_MEMORY".memory_type IS '记忆类型：long_term/entity';
COMMENT ON COLUMN "USER_MEMORY".memory_key IS '记忆键（用于去重更新）';
COMMENT ON COLUMN "USER_MEMORY".content IS '记忆内容';
COMMENT ON COLUMN "USER_MEMORY".importance IS '重要度（0-5）';
COMMENT ON COLUMN "USER_MEMORY".create_time IS '创建时间';
COMMENT ON COLUMN "USER_MEMORY".update_time IS '更新时间';
COMMENT ON COLUMN "USER_MEMORY".deleted IS '是否删除：0-未删除，1-已删除';

CREATE INDEX idx_user_memory_user_id ON "USER_MEMORY"(user_id);
CREATE INDEX idx_user_memory_scope ON "USER_MEMORY"(scope_type, scope_id);
CREATE INDEX idx_user_memory_user_scope ON "USER_MEMORY"(user_id, scope_type, scope_id);
CREATE INDEX idx_user_memory_type ON "USER_MEMORY"(memory_type);
CREATE INDEX idx_user_memory_user_type ON "USER_MEMORY"(user_id, memory_type);
CREATE INDEX idx_user_memory_user_scope_type ON "USER_MEMORY"(user_id, scope_type, scope_id, memory_type);
CREATE INDEX idx_user_memory_deleted ON "USER_MEMORY"(deleted);

-- ============================================
-- 17. 创建会话表 (chat_conversation)
-- ============================================
DROP TABLE IF EXISTS chat_conversation CASCADE;

CREATE TABLE chat_conversation (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    app_id BIGINT,
    knowledge_base_id BIGINT,
    type INTEGER,
    title VARCHAR(500),
    model_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE chat_conversation IS '会话表（一个会话包含多轮对话）';
COMMENT ON COLUMN chat_conversation.id IS '主键';
COMMENT ON COLUMN chat_conversation.user_id IS '用户ID（外键关联SYS_USER）';
COMMENT ON COLUMN chat_conversation.app_id IS '应用ID（可选，关联AI应用）';
COMMENT ON COLUMN chat_conversation.knowledge_base_id IS '知识库ID（对于文档问答类型，此字段存储文档ID）';
COMMENT ON COLUMN chat_conversation.type IS '会话类型：1-普通聊天，2-知识库问答，3-文档问答';
COMMENT ON COLUMN chat_conversation.title IS '会话标题（自动生成或用户自定义）';
COMMENT ON COLUMN chat_conversation.model_id IS '模型ID（会话使用的模型，可选）';
COMMENT ON COLUMN chat_conversation.create_time IS '创建时间';
COMMENT ON COLUMN chat_conversation.update_time IS '更新时间';
COMMENT ON COLUMN chat_conversation.deleted IS '是否删除：0-未删除，1-已删除';

CREATE INDEX idx_chat_conversation_user_id ON chat_conversation(user_id);
CREATE INDEX idx_chat_conversation_app_id ON chat_conversation(app_id);
CREATE INDEX idx_chat_conversation_kb_id ON chat_conversation(knowledge_base_id);
CREATE INDEX idx_chat_conversation_type ON chat_conversation(type);
CREATE INDEX idx_chat_conversation_deleted ON chat_conversation(deleted);
CREATE INDEX idx_chat_conversation_create_time ON chat_conversation(create_time);

-- ============================================
-- 18. 创建会话消息表 (chat_message)
-- ============================================
DROP TABLE IF EXISTS chat_message CASCADE;

CREATE TABLE chat_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT,
    role VARCHAR(20),
    content TEXT,
    sequence INTEGER,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    model_id BIGINT,
    prompt_tokens BIGINT,
    completion_tokens BIGINT,
    total_tokens BIGINT
);

COMMENT ON TABLE chat_message IS '会话消息表（会话中的单条消息，一问一答为一轮对话）';
COMMENT ON COLUMN chat_message.id IS '主键';
COMMENT ON COLUMN chat_message.conversation_id IS '会话ID（外键关联chat_conversation）';
COMMENT ON COLUMN chat_message.role IS '角色（user/assistant）';
COMMENT ON COLUMN chat_message.content IS '消息内容';
COMMENT ON COLUMN chat_message.sequence IS '消息顺序';
COMMENT ON COLUMN chat_message.create_time IS '创建时间';
COMMENT ON COLUMN chat_message.model_id IS '模型ID（关联使用的模型）';
COMMENT ON COLUMN chat_message.prompt_tokens IS 'Prompt Tokens数量';
COMMENT ON COLUMN chat_message.completion_tokens IS 'Completion Tokens数量';
COMMENT ON COLUMN chat_message.total_tokens IS '总Tokens数量';

CREATE INDEX idx_chat_message_conversation_id ON chat_message(conversation_id);
CREATE INDEX idx_chat_message_role ON chat_message(role);
CREATE INDEX idx_chat_message_create_time ON chat_message(create_time);
CREATE INDEX idx_chat_message_model_id ON chat_message(model_id);
CREATE INDEX idx_chat_message_total_tokens ON chat_message(total_tokens);

-- ============================================
-- 19. 创建 AI 应用与用户关联表 (AI_APP_USER)
-- ============================================
DROP TABLE IF EXISTS "AI_APP_USER" CASCADE;

CREATE TABLE "AI_APP_USER" (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    user_name VARCHAR(100),
    role_type INTEGER,
    status INTEGER,
    permissions VARCHAR(1000),
    creator VARCHAR(64),
    updater VARCHAR(64),
    tenant_id INTEGER NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE "AI_APP_USER" IS 'AI 应用与用户关联表';
COMMENT ON COLUMN "AI_APP_USER".id IS '关联编号';
COMMENT ON COLUMN "AI_APP_USER".app_id IS '应用编号';
COMMENT ON COLUMN "AI_APP_USER".user_id IS '用户编号';
COMMENT ON COLUMN "AI_APP_USER".user_name IS '用户名称';
COMMENT ON COLUMN "AI_APP_USER".role_type IS '角色类型：1-普通用户，2-管理员，3-超级管理员';
COMMENT ON COLUMN "AI_APP_USER".status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN "AI_APP_USER".permissions IS '权限配置JSON';
COMMENT ON COLUMN "AI_APP_USER".creator IS '创建者';
COMMENT ON COLUMN "AI_APP_USER".updater IS '更新者';
COMMENT ON COLUMN "AI_APP_USER".tenant_id IS '租户编号';
COMMENT ON COLUMN "AI_APP_USER".create_time IS '创建时间';
COMMENT ON COLUMN "AI_APP_USER".update_time IS '更新时间';
COMMENT ON COLUMN "AI_APP_USER".deleted IS '是否删除：0-未删除，1-已删除';

CREATE UNIQUE INDEX idx_ai_app_user_app_user ON "AI_APP_USER"(app_id, user_id);
CREATE INDEX idx_ai_app_user_tenant_id ON "AI_APP_USER"(tenant_id);
CREATE INDEX idx_ai_app_user_status ON "AI_APP_USER"(status);
CREATE INDEX idx_ai_app_user_deleted ON "AI_APP_USER"(deleted);

-- ============================================
-- 21. 创建备忘录表 (MEMO)
-- ============================================
DROP TABLE IF EXISTS "MEMO" CASCADE;

CREATE TABLE "MEMO" (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    remind_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    interval_minutes INTEGER NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

COMMENT ON TABLE "MEMO" IS '备忘录表';
COMMENT ON COLUMN "MEMO".id IS '备忘录编号';
COMMENT ON COLUMN "MEMO".user_id IS '用户ID';
COMMENT ON COLUMN "MEMO".content IS '提醒内容';
COMMENT ON COLUMN "MEMO".remind_at IS '提醒时间';
COMMENT ON COLUMN "MEMO".status IS '状态：pending-待提醒，done-已提醒，cancelled-已取消';
COMMENT ON COLUMN "MEMO".interval_minutes IS '周期提醒间隔（分钟），NULL 表示一次性';
COMMENT ON COLUMN "MEMO".create_time IS '创建时间';
COMMENT ON COLUMN "MEMO".update_time IS '更新时间';
COMMENT ON COLUMN "MEMO".deleted IS '是否删除：0-未删除，1-已删除';

CREATE INDEX idx_memo_user_deleted ON "MEMO"(user_id, deleted);
CREATE INDEX idx_memo_remind_at_status ON "MEMO"(remind_at, status);

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
-- 17. 初始化OCR服务配置
-- ============================================
-- 插入OCR服务配置（如果不存在）
INSERT INTO "SYSTEM_CONFIG" (config_key, config_value, description, config_group, config_type, create_time, update_time, deleted, creator, creator_id)
VALUES 
    (
        'ocr.service.url',
        'http://localhost:8000',
        'EasyOCR服务地址（如：http://localhost:8000）',
        'ocr',
        'string',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0,
        'system',
        NULL
    ),
    (
        'ocr.service.timeout',
        '30000',
        'EasyOCR服务请求超时时间（毫秒，默认：30000）',
        'ocr',
        'number',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0,
        'system',
        NULL
    )
ON CONFLICT (config_key) DO NOTHING;

-- ============================================
-- 脚本执行完成
-- ============================================
-- 所有表已创建完成，可以开始使用系统
-- 注意：如果表已存在，DROP TABLE语句会删除旧表及其数据，请谨慎使用
-- 
-- 本脚本已包含以下迁移内容：
-- 1. 知识库摘要字段（summary）- 已包含在KNOWLEDGE_BASE表定义中
-- 2. 多模态支持字段（supports_multimodal, supports_vision）- 已包含在QA_MODEL表定义中
-- 3. OCR服务配置初始化 - 已包含在初始化数据部分
-- 4. 知识库ID约束（check_knowledge_base_id_not_zero）- 确保知识库ID不能为0
-- 5. 知识库ID序列初始化 - 确保序列从1开始
--
-- 对于现有数据库的升级，请使用 migration_all.sql 迁移脚本
-- 该脚本包含：
-- - 主数据库迁移：知识库ID约束、序列重置等
-- - pgvector数据库迁移：文档解读向量表user_id字段添加（如果使用pgvector）
