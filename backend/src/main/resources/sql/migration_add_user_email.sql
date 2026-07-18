-- 用户邮箱认证升级脚本（PostgreSQL 15）
-- 生产环境启用了 ddl-auto=validate，部署新版本前需先执行本脚本。
-- 同时兼容 Hibernate 创建的小写 sys_user 和初始化脚本创建的大写 "SYS_USER"。

DO $$
DECLARE
    target_schema TEXT;
    target_table TEXT;
BEGIN
    SELECT t.table_schema, t.table_name
      INTO target_schema, target_table
      FROM information_schema.tables t
     WHERE LOWER(t.table_name) = 'sys_user'
       AND t.table_schema = ANY (current_schemas(false))
     ORDER BY CASE WHEN t.table_name = 'sys_user' THEN 0 ELSE 1 END
     LIMIT 1;

    IF target_table IS NULL THEN
        RAISE EXCEPTION '未找到 SYS_USER/sys_user 表，请确认连接的是应用实际使用的数据库和 schema';
    END IF;

    EXECUTE format(
        'ALTER TABLE %I.%I ADD COLUMN IF NOT EXISTS email VARCHAR(254)',
        target_schema, target_table
    );
    EXECUTE format(
        'ALTER TABLE %I.%I ADD COLUMN IF NOT EXISTS password_version INTEGER NOT NULL DEFAULT 0',
        target_schema, target_table
    );
    EXECUTE format(
        'CREATE UNIQUE INDEX IF NOT EXISTS idx_user_email ON %I.%I (LOWER(email)) WHERE email IS NOT NULL',
        target_schema, target_table
    );
    EXECUTE format(
        'COMMENT ON COLUMN %I.%I.email IS %L',
        target_schema, target_table, '注册邮箱（新用户需验证码校验）'
    );
    EXECUTE format(
        'COMMENT ON COLUMN %I.%I.password_version IS %L',
        target_schema, target_table, '密码版本，用于密码变更后废止旧JWT'
    );

    RAISE NOTICE '已升级 %.%', target_schema, target_table;
END $$;
