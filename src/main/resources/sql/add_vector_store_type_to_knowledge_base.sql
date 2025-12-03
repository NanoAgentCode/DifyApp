-- ============================================
-- 添加向量存储类型字段到知识库表
-- ============================================
-- 说明：为知识库表添加vector_store_type字段，支持选择Qdrant、FAISS或Milvus作为向量存储
-- 默认值为'qdrant'，保持向后兼容

-- 添加vector_store_type字段
ALTER TABLE "KNOWLEDGE_BASE" 
ADD COLUMN IF NOT EXISTS vector_store_type VARCHAR(20) DEFAULT 'qdrant';

-- 添加字段注释
COMMENT ON COLUMN "KNOWLEDGE_BASE".vector_store_type IS '向量存储类型：qdrant-Qdrant向量数据库，faiss-FAISS本地文件存储，milvus-Milvus向量数据库';

-- 创建索引（可选，如果需要按向量存储类型查询）
CREATE INDEX IF NOT EXISTS idx_knowledge_base_vector_store_type ON "KNOWLEDGE_BASE"(vector_store_type);

-- 更新现有记录的默认值（确保所有现有知识库都使用qdrant）
UPDATE "KNOWLEDGE_BASE" 
SET vector_store_type = 'qdrant' 
WHERE vector_store_type IS NULL;

