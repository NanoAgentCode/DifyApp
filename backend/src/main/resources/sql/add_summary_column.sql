-- 为知识库表添加智能摘要字段
-- 执行时间：2025-12-10

-- 添加summary字段
ALTER TABLE "KNOWLEDGE_BASE" 
ADD COLUMN IF NOT EXISTS summary TEXT;

-- 添加字段注释
COMMENT ON COLUMN "KNOWLEDGE_BASE".summary IS '知识库智能摘要';

