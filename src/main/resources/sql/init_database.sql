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
-- 查看用户表数据
SELECT id, username, role, status, create_time FROM "SYS_USER";

-- 查看表结构
SELECT 
    table_name,
    column_name,
    data_type,
    is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
    AND table_name IN ('SYS_USER', 'AI_APP')
ORDER BY table_name, ordinal_position;

