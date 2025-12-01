-- ============================================
-- 为知识库表添加top_k字段
-- 执行时间：2025-12-01
-- ============================================

-- 添加top_k字段
ALTER TABLE "KNOWLEDGE_BASE" 
ADD COLUMN IF NOT EXISTS top_k INTEGER;

-- 添加字段注释
COMMENT ON COLUMN "KNOWLEDGE_BASE".top_k IS 'Top-K检索数量（每个知识库可单独配置，如果为NULL则使用全局配置）';

-- 注意：top_k字段默认为NULL，表示使用全局配置
-- 如果需要为现有知识库设置默认值，可以执行以下SQL（可选）：
-- UPDATE "KNOWLEDGE_BASE" SET top_k = 5 WHERE top_k IS NULL;

