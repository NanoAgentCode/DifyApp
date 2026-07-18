-- ============================================
-- DifyApp 数据库迁移脚本集合（统一版）
-- PostgreSQL 15
-- ============================================
-- 说明：此文件包含所有数据库迁移脚本，已合并以下独立迁移文件：
-- 1. add_token_statistics_fields.sql - Token统计字段（已合并）
-- 2. 知识库ID约束迁移（新增）
--
-- 适用于现有数据库的升级
--
-- 执行顺序：
-- 1. 主数据库迁移：执行主数据库相关迁移
-- 2. pgvector数据库迁移：如果使用pgvector，需要单独执行pgvector相关迁移
--    如果pgvector和主数据库是同一个，可以直接执行整个脚本
-- ============================================

-- ============================================
-- 主数据库迁移脚本
-- ============================================
-- 注意：以下脚本需要在主数据库（difyapp）中执行

-- 用户邮箱认证字段（注册及找回密码）
-- 兼容 Hibernate 创建的小写 sys_user 和初始化脚本创建的大写 "SYS_USER"。
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

    IF target_table IS NOT NULL THEN
        EXECUTE format('ALTER TABLE %I.%I ADD COLUMN IF NOT EXISTS email VARCHAR(254)', target_schema, target_table);
        EXECUTE format('ALTER TABLE %I.%I ADD COLUMN IF NOT EXISTS password_version INTEGER NOT NULL DEFAULT 0', target_schema, target_table);
        EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS idx_user_email ON %I.%I (LOWER(email)) WHERE email IS NOT NULL', target_schema, target_table);
        EXECUTE format('COMMENT ON COLUMN %I.%I.email IS %L', target_schema, target_table, '注册邮箱（新用户需验证码校验）');
        EXECUTE format('COMMENT ON COLUMN %I.%I.password_version IS %L', target_schema, target_table, '密码版本，用于密码变更后废止旧JWT');
    END IF;
END $$;

-- 1. 知识库摘要字段迁移（如果尚未执行）
-- 说明：为KNOWLEDGE_BASE表添加summary字段
-- 文件：add_summary_column.sql（如果存在）
-- 如果使用init_database_complete.sql初始化，此迁移已包含

-- 2. 多模态支持字段迁移（如果尚未执行）
-- 说明：为QA_MODEL表添加supports_multimodal和supports_vision字段
-- 文件：migration_add_multimodal_support.sql（如果存在）
-- 如果使用init_database_complete.sql初始化，此迁移已包含

-- 3. OCR服务配置初始化（如果尚未执行）
-- 说明：初始化OCR服务相关配置
-- 文件：init_ocr_config.sql（如果存在）
-- 如果使用init_database_complete.sql初始化，此迁移已包含

-- 4. Token统计字段迁移（如果尚未执行）
-- 说明：为chat_message和chat_conversation表添加token统计相关字段
-- 如果使用init_database_complete.sql初始化，此迁移已包含

-- 5. 知识库ID约束迁移（新增）
-- 说明：确保知识库ID不能为0（0保留给文档解读使用）
-- 执行位置：主数据库
-- ============================================

-- 添加约束：确保知识库ID不能为0（0保留给文档解读使用）
-- 注意：如果约束已存在，此语句会失败，这是正常的
DO $$
BEGIN
    -- 检查约束是否已存在
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'check_knowledge_base_id_not_zero'
    ) THEN
        ALTER TABLE "KNOWLEDGE_BASE" ADD CONSTRAINT check_knowledge_base_id_not_zero CHECK (id > 0);
        RAISE NOTICE '已添加知识库ID约束：check_knowledge_base_id_not_zero';
    ELSE
        RAISE NOTICE '知识库ID约束已存在，跳过';
    END IF;
EXCEPTION
    WHEN duplicate_object THEN
        RAISE NOTICE '知识库ID约束已存在，跳过';
    WHEN OTHERS THEN
        RAISE WARNING '添加知识库ID约束失败：%', SQLERRM;
END $$;

-- 6. 用户记忆作用域字段迁移（新增）
-- 说明：为USER_MEMORY表添加scope_type/scope_id，并升级唯一约束与索引
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'user_memory' OR table_name = 'USER_MEMORY'
    ) THEN
        BEGIN
            ALTER TABLE "USER_MEMORY" ADD COLUMN IF NOT EXISTS scope_type VARCHAR(32) NOT NULL DEFAULT 'chat';
        EXCEPTION WHEN OTHERS THEN
            ALTER TABLE user_memory ADD COLUMN IF NOT EXISTS scope_type VARCHAR(32) NOT NULL DEFAULT 'chat';
        END;

        BEGIN
            ALTER TABLE "USER_MEMORY" ADD COLUMN IF NOT EXISTS scope_id BIGINT;
        EXCEPTION WHEN OTHERS THEN
            ALTER TABLE user_memory ADD COLUMN IF NOT EXISTS scope_id BIGINT;
        END;

        BEGIN
            ALTER TABLE "USER_MEMORY" ADD COLUMN IF NOT EXISTS first_seen_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
            ALTER TABLE "USER_MEMORY" ADD COLUMN IF NOT EXISTS last_mentioned_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
            ALTER TABLE "USER_MEMORY" ADD COLUMN IF NOT EXISTS last_accessed_time TIMESTAMP;
            ALTER TABLE "USER_MEMORY" ADD COLUMN IF NOT EXISTS access_count INTEGER DEFAULT 0;
            ALTER TABLE "USER_MEMORY" ADD COLUMN IF NOT EXISTS source_conversation_id BIGINT;
        EXCEPTION WHEN OTHERS THEN
            ALTER TABLE user_memory ADD COLUMN IF NOT EXISTS first_seen_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
            ALTER TABLE user_memory ADD COLUMN IF NOT EXISTS last_mentioned_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
            ALTER TABLE user_memory ADD COLUMN IF NOT EXISTS last_accessed_time TIMESTAMP;
            ALTER TABLE user_memory ADD COLUMN IF NOT EXISTS access_count INTEGER DEFAULT 0;
            ALTER TABLE user_memory ADD COLUMN IF NOT EXISTS source_conversation_id BIGINT;
        END;

        BEGIN
            UPDATE "USER_MEMORY"
            SET first_seen_time = COALESCE(first_seen_time, create_time, CURRENT_TIMESTAMP),
                last_mentioned_time = COALESCE(last_mentioned_time, update_time, create_time, CURRENT_TIMESTAMP),
                access_count = COALESCE(access_count, 0);
        EXCEPTION WHEN OTHERS THEN
            UPDATE user_memory
            SET first_seen_time = COALESCE(first_seen_time, create_time, CURRENT_TIMESTAMP),
                last_mentioned_time = COALESCE(last_mentioned_time, update_time, create_time, CURRENT_TIMESTAMP),
                access_count = COALESCE(access_count, 0);
        END;

        BEGIN
            ALTER TABLE "USER_MEMORY" DROP CONSTRAINT IF EXISTS uk_user_memory;
        EXCEPTION WHEN OTHERS THEN
            BEGIN
                ALTER TABLE user_memory DROP CONSTRAINT IF EXISTS uk_user_memory;
            EXCEPTION WHEN OTHERS THEN
            END;
        END;

        BEGIN
            ALTER TABLE "USER_MEMORY" ADD CONSTRAINT uk_user_memory UNIQUE (user_id, scope_type, scope_id, memory_type, memory_key);
        EXCEPTION WHEN OTHERS THEN
            ALTER TABLE user_memory ADD CONSTRAINT uk_user_memory UNIQUE (user_id, scope_type, scope_id, memory_type, memory_key);
        END;

        BEGIN
            CREATE INDEX IF NOT EXISTS idx_user_memory_scope ON "USER_MEMORY"(scope_type, scope_id);
            CREATE INDEX IF NOT EXISTS idx_user_memory_user_scope ON "USER_MEMORY"(user_id, scope_type, scope_id);
            CREATE INDEX IF NOT EXISTS idx_user_memory_user_scope_type ON "USER_MEMORY"(user_id, scope_type, scope_id, memory_type);
            CREATE INDEX IF NOT EXISTS idx_user_memory_last_mentioned ON "USER_MEMORY"(last_mentioned_time DESC);
            CREATE INDEX IF NOT EXISTS idx_user_memory_last_accessed ON "USER_MEMORY"(last_accessed_time DESC);
        EXCEPTION WHEN OTHERS THEN
            CREATE INDEX IF NOT EXISTS idx_user_memory_scope ON user_memory(scope_type, scope_id);
            CREATE INDEX IF NOT EXISTS idx_user_memory_user_scope ON user_memory(user_id, scope_type, scope_id);
            CREATE INDEX IF NOT EXISTS idx_user_memory_user_scope_type ON user_memory(user_id, scope_type, scope_id, memory_type);
            CREATE INDEX IF NOT EXISTS idx_user_memory_last_mentioned ON user_memory(last_mentioned_time DESC);
            CREATE INDEX IF NOT EXISTS idx_user_memory_last_accessed ON user_memory(last_accessed_time DESC);
        END;

        RAISE NOTICE '已完成USER_MEMORY作用域与时间元数据字段迁移';
    ELSE
        RAISE NOTICE 'USER_MEMORY表不存在，跳过作用域字段迁移';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'USER_MEMORY作用域与时间元数据字段迁移失败：%', SQLERRM;
END $$;

-- 7. 会话滚动摘要字段迁移（新增）
-- 说明：为chat_conversation表添加summary/summary_updated_sequence/summary_update_time字段
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_conversation') THEN
        ALTER TABLE chat_conversation
            ADD COLUMN IF NOT EXISTS summary TEXT,
            ADD COLUMN IF NOT EXISTS summary_updated_sequence INTEGER,
            ADD COLUMN IF NOT EXISTS summary_update_time TIMESTAMP;

        COMMENT ON COLUMN chat_conversation.summary IS '会话滚动摘要';
        COMMENT ON COLUMN chat_conversation.summary_updated_sequence IS '摘要已覆盖到的消息序号';
        COMMENT ON COLUMN chat_conversation.summary_update_time IS '摘要更新时间';

        CREATE INDEX IF NOT EXISTS idx_chat_conversation_summary_update_time
            ON chat_conversation(summary_update_time);

        RAISE NOTICE '已为chat_conversation表添加会话滚动摘要字段';
    ELSE
        RAISE NOTICE 'chat_conversation表不存在，跳过会话滚动摘要字段迁移';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '为chat_conversation表添加会话滚动摘要字段失败：%', SQLERRM;
END $$;

-- 8. DrawIO 历史记录返回数据迁移（新增）
-- 说明：为DRAWIO_HISTORY表添加diagram_json字段
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'drawio_history' OR table_name = 'DRAWIO_HISTORY'
    ) THEN
        BEGIN
            ALTER TABLE "DRAWIO_HISTORY" ADD COLUMN IF NOT EXISTS diagram_json TEXT;
        EXCEPTION WHEN OTHERS THEN
            ALTER TABLE drawio_history ADD COLUMN IF NOT EXISTS diagram_json TEXT;
        END;

        BEGIN
            COMMENT ON COLUMN "DRAWIO_HISTORY".diagram_json IS '图表JSON内容（AntV Infographic DSL）';
        EXCEPTION WHEN OTHERS THEN
            BEGIN
                COMMENT ON COLUMN drawio_history.diagram_json IS '图表JSON内容（AntV Infographic DSL）';
            EXCEPTION WHEN OTHERS THEN
            END;
        END;

        RAISE NOTICE '已完成DRAWIO_HISTORY返回数据字段迁移';
    ELSE
        RAISE NOTICE 'DRAWIO_HISTORY表不存在，跳过返回数据字段迁移';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'DRAWIO_HISTORY返回数据字段迁移失败：%', SQLERRM;
END $$;

-- 确保知识库ID序列从1开始
-- 注意：如果序列已经存在且当前值小于1，将其重置为1
DO $$
DECLARE
    seq_name TEXT;
    current_val BIGINT;
BEGIN
    -- 查找知识库表的序列名
    SELECT pg_get_serial_sequence('"KNOWLEDGE_BASE"', 'id') INTO seq_name;
    
    IF seq_name IS NOT NULL THEN
        -- 获取当前序列值
        EXECUTE 'SELECT last_value FROM ' || seq_name INTO current_val;
        
        -- 如果当前值小于1，重置为1
        IF current_val < 1 THEN
            EXECUTE 'ALTER SEQUENCE ' || seq_name || ' RESTART WITH 1';
            RAISE NOTICE '知识库ID序列已重置为1（原值：%）', current_val;
        ELSE
            RAISE NOTICE '知识库ID序列当前值：%，无需重置', current_val;
        END IF;
    ELSE
        RAISE NOTICE '未找到知识库ID序列，可能表不存在';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '重置知识库ID序列失败：%', SQLERRM;
END $$;

-- ============================================
-- Token统计字段迁移
-- ============================================
-- 说明：为chat_message和chat_conversation表添加token统计相关字段
-- 执行位置：主数据库
-- ============================================

-- 为chat_message表添加模型ID和token字段
DO $$
BEGIN
    -- 检查表是否存在
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_message') THEN
        -- 为chat_message表添加字段
        ALTER TABLE chat_message 
        ADD COLUMN IF NOT EXISTS model_id BIGINT NULL,
        ADD COLUMN IF NOT EXISTS prompt_tokens BIGINT NULL,
        ADD COLUMN IF NOT EXISTS completion_tokens BIGINT NULL,
        ADD COLUMN IF NOT EXISTS total_tokens BIGINT NULL;
        
        -- 添加字段注释
        COMMENT ON COLUMN chat_message.model_id IS '模型ID（关联使用的模型）';
        COMMENT ON COLUMN chat_message.prompt_tokens IS 'Prompt Tokens数量';
        COMMENT ON COLUMN chat_message.completion_tokens IS 'Completion Tokens数量';
        COMMENT ON COLUMN chat_message.total_tokens IS '总Tokens数量';
        
        -- 添加索引以提高查询性能
        CREATE INDEX IF NOT EXISTS idx_chat_message_model_id ON chat_message(model_id);
        CREATE INDEX IF NOT EXISTS idx_chat_message_total_tokens ON chat_message(total_tokens);
        CREATE INDEX IF NOT EXISTS idx_chat_message_create_time ON chat_message(create_time);
        
        RAISE NOTICE '已为chat_message表添加token统计字段和索引';
    ELSE
        RAISE NOTICE 'chat_message表不存在，跳过token统计字段迁移';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '为chat_message表添加token统计字段失败：%', SQLERRM;
END $$;

-- 为chat_conversation表添加模型ID字段
DO $$
BEGIN
    -- 检查表是否存在
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_conversation') THEN
        -- 为chat_conversation表添加字段
        ALTER TABLE chat_conversation 
        ADD COLUMN IF NOT EXISTS model_id BIGINT NULL;
        
        -- 添加字段注释
        COMMENT ON COLUMN chat_conversation.model_id IS '模型ID（会话使用的模型，可选）';
        
        -- 添加索引
        CREATE INDEX IF NOT EXISTS idx_chat_conversation_model_id ON chat_conversation(model_id);
        
        RAISE NOTICE '已为chat_conversation表添加model_id字段和索引';
    ELSE
        RAISE NOTICE 'chat_conversation表不存在，跳过model_id字段迁移';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '为chat_conversation表添加model_id字段失败：%', SQLERRM;
END $$;

-- ============================================
-- pgvector数据库迁移脚本
-- ============================================
-- 注意：以下脚本需要在pgvector数据库中执行（不是主数据库）
-- 如果pgvector和主数据库是同一个，可以直接执行

-- 为文档解读向量表添加user_id字段
-- 说明：为kb_vectors_0表添加user_id字段，用于支持用户隔离
-- 执行位置：pgvector数据库
-- ============================================

-- 1. 为kb_vectors_0表添加user_id字段（如果不存在）
DO $$
BEGIN
    -- 检查表是否存在
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'kb_vectors_0') THEN
        -- 检查user_id字段是否已存在
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'kb_vectors_0' AND column_name = 'user_id'
        ) THEN
            -- 添加user_id字段
            ALTER TABLE kb_vectors_0 ADD COLUMN user_id BIGINT;
            
            -- 添加注释
            COMMENT ON COLUMN kb_vectors_0.user_id IS '用户ID（文档解读专用，用于用户隔离）';
            
            RAISE NOTICE '已为kb_vectors_0表添加user_id字段';
        ELSE
            RAISE NOTICE 'kb_vectors_0表的user_id字段已存在，跳过';
        END IF;
    ELSE
        RAISE NOTICE 'kb_vectors_0表不存在，将在首次向量化时自动创建（包含user_id字段）';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '添加user_id字段失败：%', SQLERRM;
END $$;

-- 2. 为user_id字段创建索引（如果字段存在）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'kb_vectors_0' AND column_name = 'user_id'
    ) THEN
        -- 检查索引是否已存在
        IF NOT EXISTS (
            SELECT 1 FROM pg_indexes 
            WHERE tablename = 'kb_vectors_0' AND indexname = 'kb_vectors_0_user_id_idx'
        ) THEN
            CREATE INDEX IF NOT EXISTS kb_vectors_0_user_id_idx ON kb_vectors_0 (user_id);
            RAISE NOTICE '已为kb_vectors_0表的user_id字段创建索引';
        ELSE
            RAISE NOTICE 'kb_vectors_0_user_id_idx索引已存在，跳过';
        END IF;
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '创建user_id索引失败：%', SQLERRM;
END $$;

-- 3. 可选：如果DOCUMENT_READER表在同一个数据库中，可以批量更新现有数据的user_id
-- 注意：如果DOCUMENT_READER表在主数据库中，此步骤会失败，这是正常的
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'DOCUMENT_READER' OR table_name = 'document_reader'
    ) THEN
        -- 尝试更新现有数据的user_id
        UPDATE kb_vectors_0 v
        SET user_id = d.user_id
        FROM "DOCUMENT_READER" d
        WHERE v.document_id = d.id 
          AND v.user_id IS NULL
          AND d.deleted = 0;
        
        RAISE NOTICE '已更新现有向量数据的user_id';
    ELSE
        RAISE NOTICE 'DOCUMENT_READER表不在当前数据库中，无法批量更新user_id（这是正常的，如果DOCUMENT_READER在主数据库中）';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE '更新user_id时出错（可能是DOCUMENT_READER表不在当前数据库）：%', SQLERRM;
END $$;

-- 7. 备忘录表 (MEMO) 迁移
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'MEMO'
    ) THEN
        CREATE TABLE "MEMO" (
            id BIGSERIAL PRIMARY KEY,
            user_id BIGINT NOT NULL,
            content TEXT NOT NULL,
            remind_at TIMESTAMP NOT NULL,
            status VARCHAR(20) NOT NULL DEFAULT 'pending',
            interval_minutes INTEGER NULL,
            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            deleted INTEGER DEFAULT 0
        );
        COMMENT ON TABLE "MEMO" IS '备忘录表';
        CREATE INDEX idx_memo_user_deleted ON "MEMO"(user_id, deleted);
        CREATE INDEX idx_memo_remind_at_status ON "MEMO"(remind_at, status);
        RAISE NOTICE '已创建备忘录表 MEMO';
    ELSE
        RAISE NOTICE '表 MEMO 已存在，跳过';
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'MEMO' AND column_name = 'interval_minutes'
        ) THEN
            ALTER TABLE "MEMO" ADD COLUMN interval_minutes INTEGER NULL;
            COMMENT ON COLUMN "MEMO".interval_minutes IS '周期提醒间隔（分钟），NULL 表示一次性';
            RAISE NOTICE '已为 MEMO 表添加 interval_minutes 列';
        END IF;
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '备忘录表迁移失败：%', SQLERRM;
END $$;

-- ============================================
-- 迁移完成
-- ============================================
-- 所有迁移脚本已执行完成
-- 
-- 注意事项：
-- 1. 如果pgvector和主数据库是同一个，上述所有脚本可以在同一个数据库中执行
-- 2. 如果pgvector和主数据库是分开的，需要分别在两个数据库中执行对应的部分
-- 3. 如果某些表或字段已存在，相关语句会跳过，这是正常的
-- ============================================
-- Agent task mode run and step persistence
CREATE TABLE IF NOT EXISTS agent_task_run (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL UNIQUE,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    question TEXT,
    status VARCHAR(40),
    model_id BIGINT,
    enable_browser_search BOOLEAN,
    is_admin BOOLEAN,
    final_answer TEXT,
    pending_confirmation_id VARCHAR(64),
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agent_task_run_conversation_id ON agent_task_run(conversation_id);
CREATE INDEX IF NOT EXISTS idx_agent_task_run_user_id ON agent_task_run(user_id);
CREATE INDEX IF NOT EXISTS idx_agent_task_run_status ON agent_task_run(status);

CREATE TABLE IF NOT EXISTS agent_task_step (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    task_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    step_index INTEGER,
    step_number INTEGER NOT NULL,
    event_type VARCHAR(60),
    status VARCHAR(40),
    content TEXT,
    tool_name VARCHAR(120),
    tool_input_summary TEXT,
    tool_output_summary TEXT,
    requires_confirmation BOOLEAN,
    confirmation_id VARCHAR(64),
    risk_level VARCHAR(40),
    error TEXT,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agent_task_step_run_id ON agent_task_step(run_id);
CREATE INDEX IF NOT EXISTS idx_agent_task_step_task_id ON agent_task_step(task_id);
CREATE INDEX IF NOT EXISTS idx_agent_task_step_conversation_id ON agent_task_step(conversation_id);
CREATE INDEX IF NOT EXISTS idx_agent_task_step_event_type ON agent_task_step(event_type);

ALTER TABLE agent_task_step ADD COLUMN IF NOT EXISTS task_id BIGINT;
UPDATE agent_task_step s
SET task_id = r.id
FROM agent_task_run r
WHERE s.task_id IS NULL AND s.run_id = r.run_id;
UPDATE agent_task_step SET task_id = 0 WHERE task_id IS NULL;
ALTER TABLE agent_task_step ALTER COLUMN task_id SET NOT NULL;

ALTER TABLE agent_task_step ADD COLUMN IF NOT EXISTS step_number INTEGER;
UPDATE agent_task_step SET step_number = COALESCE(step_number, step_index, 0) WHERE step_number IS NULL;
ALTER TABLE agent_task_step ALTER COLUMN step_number SET NOT NULL;
