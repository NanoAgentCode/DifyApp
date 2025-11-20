-- 验证 inputs 字段配置的 SQL 脚本
-- 用于确认数据库字段类型是否正确配置

-- 1. 检查 inputs 字段的类型和属性
SELECT 
    column_name,
    data_type,
    character_maximum_length,
    is_nullable,
    column_default
FROM information_schema.columns 
WHERE table_name = 'ai_app' 
  AND column_name = 'inputs';

-- 2. 检查现有数据的 inputs 字段内容（显示前5条）
SELECT 
    id,
    name,
    type,
    CASE 
        WHEN inputs IS NULL THEN 'NULL'
        WHEN LENGTH(inputs) = 0 THEN '空字符串'
        ELSE '有内容(' || LENGTH(inputs) || '字符)'
    END AS inputs_status,
    LEFT(inputs, 100) AS inputs_preview  -- 显示前100个字符
FROM ai_app
ORDER BY id DESC
LIMIT 5;

-- 3. 统计 inputs 字段的使用情况
SELECT 
    COUNT(*) AS total_apps,
    COUNT(inputs) AS apps_with_inputs,
    COUNT(*) - COUNT(inputs) AS apps_without_inputs,
    AVG(CASE WHEN inputs IS NOT NULL THEN LENGTH(inputs) ELSE NULL END) AS avg_inputs_length,
    MAX(CASE WHEN inputs IS NOT NULL THEN LENGTH(inputs) ELSE NULL END) AS max_inputs_length
FROM ai_app;

-- 4. 检查是否有新格式的 inputs 数据（包含 "fields" 和 "defaults"）
SELECT 
    id,
    name,
    CASE 
        WHEN inputs LIKE '%"fields"%' AND inputs LIKE '%"defaults"%' THEN '新格式'
        WHEN inputs IS NOT NULL AND inputs != '' THEN '旧格式或其他格式'
        ELSE '无配置'
    END AS format_type,
    LENGTH(inputs) AS inputs_length
FROM ai_app
WHERE inputs IS NOT NULL
ORDER BY id DESC;

-- 5. 验证字段是否可以存储大文本（测试）
-- 注意：这只是验证，不会实际插入数据
-- SELECT 
--     LENGTH(REPEAT('A', 1000000)) AS test_length;  -- PostgreSQL
--     -- 或
--     -- LENGTH(REPEAT('A', 1000000)) AS test_length;  -- MySQL

-- 预期结果：
-- 1. data_type 应该是 'text' (PostgreSQL) 或 'longtext' (MySQL)
-- 2. character_maximum_length 应该是 NULL（TEXT 类型没有长度限制）
-- 3. is_nullable 应该是 'YES'（允许为空）

