-- ============================================
-- 修复配置项不匹配问题的 SQL 脚本
-- 问题：documentReader.defaultEmbeddingModelId 的描述和分组错误
-- ============================================

-- 修复 documentReader.defaultEmbeddingModelId 配置项
-- 错误：描述为"智能框图默认使用的问答模型ID"，分组为"system"
-- 正确：描述应为"文档解读默认使用的向量化模型ID"，分组应为"documentReader"

UPDATE SYSTEM_CONFIG 
SET 
    config_group = 'documentReader',
    description = '文档解读默认使用的向量化模型ID'
WHERE 
    config_key = 'documentReader.defaultEmbeddingModelId'
    AND (
        description != '文档解读默认使用的向量化模型ID' 
        OR config_group != 'documentReader'
    );

-- 验证修复结果
SELECT 
    config_key,
    config_group,
    config_type,
    description,
    config_value
FROM SYSTEM_CONFIG
WHERE config_key = 'documentReader.defaultEmbeddingModelId';

-- ============================================
-- 说明：
-- 1. 此脚本会修复 documentReader.defaultEmbeddingModelId 配置项的分组和描述
-- 2. 修复后，配置项将正确匹配：
--    - 配置键：documentReader.defaultEmbeddingModelId
--    - 分组：documentReader
--    - 类型：number
--    - 描述：文档解读默认使用的向量化模型ID
-- ============================================

