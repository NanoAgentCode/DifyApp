-- ============================================
-- 删除旧的USER表（如果存在）
-- 执行此脚本以清理旧的表结构
-- ============================================

-- 删除旧的user表（小写，如果存在）
DROP TABLE IF EXISTS "user" CASCADE;
DROP TABLE IF EXISTS user CASCADE;

-- 删除旧的USER表（大写，如果存在）
DROP TABLE IF EXISTS "USER" CASCADE;

-- 验证表是否已删除
SELECT 
    table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
    AND table_name IN ('user', 'USER', 'SYS_USER')
ORDER BY table_name;

