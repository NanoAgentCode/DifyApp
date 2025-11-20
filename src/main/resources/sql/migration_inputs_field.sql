-- 数据库迁移脚本：确保 inputs 字段可以存储 JSON 配置
-- 执行时间：重构输入组件配置为 JSON 格式后

-- 检查并更新 AI_APP 表的 inputs 字段
-- PostgreSQL 中 TEXT 类型可以存储无限长度的字符串，足够存储 JSON 配置

-- 如果 inputs 字段不存在，创建它（通常不会发生，因为字段已存在）
-- ALTER TABLE AI_APP ADD COLUMN IF NOT EXISTS inputs TEXT;

-- 如果 inputs 字段类型不是 TEXT，修改为 TEXT（PostgreSQL 中 TEXT 类型可以存储无限长度）
-- 注意：PostgreSQL 的 TEXT 类型已经足够，但如果之前是 VARCHAR，需要修改
-- ALTER TABLE AI_APP ALTER COLUMN inputs TYPE TEXT;

-- 验证字段类型
-- SELECT column_name, data_type, character_maximum_length 
-- FROM information_schema.columns 
-- WHERE table_name = 'ai_app' AND column_name = 'inputs';

-- 示例：查看现有数据的格式
-- SELECT id, name, inputs FROM AI_APP WHERE inputs IS NOT NULL LIMIT 5;

-- 注意：
-- 1. PostgreSQL 的 TEXT 类型可以存储最大 1GB 的数据，完全足够存储 JSON 配置
-- 2. 如果使用 MySQL，可能需要使用 LONGTEXT 类型
-- 3. 如果使用其他数据库，请根据数据库类型调整字段类型

