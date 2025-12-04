-- ============================================
-- 创建向量数据库配置表
-- ============================================
-- 说明：用于存储向量数据库（Qdrant、Milvus、FAISS、Chroma、Weaviate）的配置信息
-- 支持在管理端动态配置，不再依赖application.yml

-- 创建向量数据库配置表
CREATE TABLE IF NOT EXISTS "VECTOR_DATABASE" (
    "id" BIGSERIAL PRIMARY KEY,
    "name" VARCHAR(100) NOT NULL,
    "type" VARCHAR(20) NOT NULL,
    "url" VARCHAR(500) NOT NULL,
    "api_key" VARCHAR(500),
    "timeout" INTEGER DEFAULT 30000,
    "extra_config" TEXT,
    "enabled" BOOLEAN DEFAULT true,
    "is_default" BOOLEAN DEFAULT false,
    "description" VARCHAR(500),
    "create_time" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "update_time" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "deleted" INTEGER DEFAULT 0
);

-- 添加字段注释
COMMENT ON TABLE "VECTOR_DATABASE" IS '向量数据库配置表';
COMMENT ON COLUMN "VECTOR_DATABASE".id IS '配置编号';
COMMENT ON COLUMN "VECTOR_DATABASE".name IS '配置名称';
COMMENT ON COLUMN "VECTOR_DATABASE".type IS '数据库类型：qdrant-Qdrant向量数据库，milvus-Milvus向量数据库，faiss-FAISS本地文件存储，chroma-Chroma向量数据库，weaviate-Weaviate向量数据库';
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
CREATE INDEX IF NOT EXISTS idx_vector_database_type ON "VECTOR_DATABASE"("type");
CREATE INDEX IF NOT EXISTS idx_vector_database_enabled ON "VECTOR_DATABASE"("enabled");
CREATE INDEX IF NOT EXISTS idx_vector_database_deleted ON "VECTOR_DATABASE"("deleted");
CREATE INDEX IF NOT EXISTS idx_vector_database_type_default ON "VECTOR_DATABASE"("type", "is_default");

-- 插入默认配置（从application.yml迁移）
-- Qdrant默认配置
INSERT INTO "VECTOR_DATABASE" ("name", "type", "url", "api_key", "timeout", "enabled", "is_default", "description", "create_time", "update_time", "deleted")
VALUES ('默认Qdrant配置', 'qdrant', 'http://localhost:6333', NULL, 30000, true, true, '从application.yml迁移的默认Qdrant配置', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT DO NOTHING;

-- Milvus默认配置
INSERT INTO "VECTOR_DATABASE" ("name", "type", "url", "api_key", "timeout", "enabled", "is_default", "description", "create_time", "update_time", "deleted")
VALUES ('默认Milvus配置', 'milvus', 'http://localhost:19530', NULL, 30000, true, true, '从application.yml迁移的默认Milvus配置', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT DO NOTHING;

-- FAISS默认配置
INSERT INTO "VECTOR_DATABASE" ("name", "type", "url", "api_key", "timeout", "enabled", "is_default", "description", "create_time", "update_time", "deleted")
VALUES ('默认FAISS配置', 'faiss', './data/faiss', NULL, NULL, true, true, '从application.yml迁移的默认FAISS配置', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT DO NOTHING;

