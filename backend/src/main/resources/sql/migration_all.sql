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
        EXCEPTION WHEN OTHERS THEN
            CREATE INDEX IF NOT EXISTS idx_user_memory_scope ON user_memory(scope_type, scope_id);
            CREATE INDEX IF NOT EXISTS idx_user_memory_user_scope ON user_memory(user_id, scope_type, scope_id);
            CREATE INDEX IF NOT EXISTS idx_user_memory_user_scope_type ON user_memory(user_id, scope_type, scope_id, memory_type);
        END;

        RAISE NOTICE '已完成USER_MEMORY作用域字段迁移';
    ELSE
        RAISE NOTICE 'USER_MEMORY表不存在，跳过作用域字段迁移';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'USER_MEMORY作用域字段迁移失败：%', SQLERRM;
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
