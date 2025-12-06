-- ============================================
-- 数据源管理相关表
-- ============================================

-- ============================================
-- 1. 创建数据源表 (DATA_SOURCE)
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
-- 2. 创建用户数据源可见性表 (USER_DATA_SOURCE_VISIBILITY)
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
-- 3. 创建表结构缓存表 (TABLE_SCHEMA_CACHE)
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

