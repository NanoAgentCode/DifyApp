-- ============================================
-- DifyApp 数据库初始化脚本
-- PostgreSQL 15
-- ============================================

-- 创建数据库（如果不存在）
-- 注意：需要在PostgreSQL中手动执行，或者使用psql命令行工具
-- CREATE DATABASE difyapp;

-- 连接到difyapp数据库后执行以下SQL

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
-- 2. 创建AI应用表 (AI_APP)
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
-- 3. 插入默认管理员账户
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
-- 4. 验证数据
-- ============================================
-- ============================================
-- 3. 创建用户应用可见性表 (USER_APP_VISIBILITY)
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

-- 外键约束（可选，如果需要）
-- ALTER TABLE "USER_APP_VISIBILITY" ADD CONSTRAINT fk_user_app_visibility_user 
--     FOREIGN KEY (user_id) REFERENCES "SYS_USER"(id) ON DELETE CASCADE;
-- ALTER TABLE "USER_APP_VISIBILITY" ADD CONSTRAINT fk_user_app_visibility_app 
--     FOREIGN KEY (app_id) REFERENCES "AI_APP"(id) ON DELETE CASCADE;

-- ============================================
-- 4. 创建知识库表 (KNOWLEDGE_BASE)
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

-- ============================================
-- 创建用户知识库可见性表 (USER_KNOWLEDGE_BASE_VISIBILITY)
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
-- 5. 创建知识库文档表 (KNOWLEDGE_BASE_DOCUMENT)
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

-- 外键约束（可选，如果需要）
-- ALTER TABLE "KNOWLEDGE_BASE_DOCUMENT" ADD CONSTRAINT fk_kb_doc_kb 
--     FOREIGN KEY (knowledge_base_id) REFERENCES "KNOWLEDGE_BASE"(id) ON DELETE CASCADE;

-- ============================================
-- 6. 插入默认管理员账户
-- ============================================

