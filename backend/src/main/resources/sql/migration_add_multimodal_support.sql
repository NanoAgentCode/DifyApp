-- ============================================
-- 数据库迁移脚本：添加多模态支持字段
-- 执行时间：2025-12-13
-- ============================================

-- 为 QA_MODEL 表添加多模态支持字段
ALTER TABLE "QA_MODEL" 
ADD COLUMN IF NOT EXISTS supports_multimodal BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS supports_vision BOOLEAN DEFAULT FALSE;

-- 添加字段注释
COMMENT ON COLUMN "QA_MODEL".supports_multimodal IS '是否支持多模态：true-支持, false-不支持';
COMMENT ON COLUMN "QA_MODEL".supports_vision IS '是否支持视觉输入：true-支持, false-不支持';

-- 更新现有模型：如果模型名称包含 qwen-vl 或 qwen-vl-max，自动设置为支持多模态和视觉
UPDATE "QA_MODEL" 
SET supports_multimodal = TRUE, 
    supports_vision = TRUE 
WHERE LOWER(model) LIKE '%qwen-vl%' 
   OR LOWER(model) LIKE '%qwen-vl-max%'
   OR LOWER(model) LIKE '%qwen2-vl%'
   OR LOWER(model) LIKE '%qwen3-vl%'
   OR LOWER(name) LIKE '%qwen-vl%'
   OR LOWER(name) LIKE '%qwen3-vl%'
   OR LOWER(name) LIKE '%视觉%'
   OR LOWER(name) LIKE '%vision%';

-- 更新其他已知的多模态模型
UPDATE "QA_MODEL" 
SET supports_multimodal = TRUE, 
    supports_vision = TRUE 
WHERE LOWER(model) LIKE '%gpt-4-vision%'
   OR LOWER(model) LIKE '%gpt-4o%'
   OR LOWER(model) LIKE '%claude-3%'
   OR LOWER(model) LIKE '%claude-3.5%'
   OR LOWER(model) LIKE '%gemini-pro-vision%'
   OR LOWER(model) LIKE '%gemini-1.5%';
