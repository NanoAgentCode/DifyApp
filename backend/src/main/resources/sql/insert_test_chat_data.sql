-- 插入会话趋势测试数据
-- 执行时间：2025-12-23
-- 数据库：PostgreSQL
-- 说明：为最近30天插入测试数据，用于测试会话趋势图表

-- 首先需要确保有用户数据（假设用户ID为1存在）
-- 如果没有用户，请先插入：
-- INSERT INTO sys_user (id, username, password, role, status, create_time) 
-- VALUES (1, 'test_user', 'password', 1, 1, NOW()) ON CONFLICT DO NOTHING;

-- 假设模型ID为1存在（如果没有，请先插入模型数据）

-- 生成最近30天的测试数据
-- 注意：请先确认用户ID和模型ID是否存在，如果不存在请修改下面的值

-- 设置变量（可以根据实际情况修改）
-- 获取第一个用户ID（如果用户表存在）
DO $$
DECLARE
    user_id_val BIGINT;
    model_id_val BIGINT;
BEGIN
    -- 尝试获取第一个用户ID
    SELECT id INTO user_id_val FROM sys_user WHERE deleted = 0 OR deleted IS NULL LIMIT 1;
    IF user_id_val IS NULL THEN
        user_id_val := 1; -- 如果查询不到，使用默认值1
    END IF;
    
    -- 尝试获取第一个模型ID
    SELECT id INTO model_id_val FROM qa_model WHERE (deleted = 0 OR deleted IS NULL) LIMIT 1;
    IF model_id_val IS NULL THEN
        model_id_val := 1; -- 如果查询不到，使用默认值1
    END IF;
    
    -- 存储到临时表中供后续使用
    CREATE TEMP TABLE IF NOT EXISTS temp_ids (user_id BIGINT, model_id BIGINT);
    DELETE FROM temp_ids;
    INSERT INTO temp_ids VALUES (user_id_val, model_id_val);
END $$;

-- 生成测试数据
DO $$
DECLARE
    i INTEGER;
    current_date DATE;
    conversation_id BIGINT;
    user_id_val BIGINT;
    model_id_val BIGINT;
    conversation_count INTEGER;
    message_count INTEGER;
    msg_sequence INTEGER;
    base_conversation_count INTEGER;
    rounds INTEGER;
    msg_time TIMESTAMP;
    conv_time TIMESTAMP;
    hour_val INTEGER;
    minute_val INTEGER;
    second_val INTEGER;
BEGIN
    -- 从临时表获取ID
    SELECT user_id, model_id INTO user_id_val, model_id_val FROM temp_ids LIMIT 1;
    
    -- 循环生成最近30天的数据
    FOR i IN 0..29 LOOP
        current_date := CURRENT_DATE - i;
        
        -- 每天的基础对话数（模拟真实场景，有波动）
        base_conversation_count := 5 + (i % 7) * 2 + FLOOR(RANDOM() * 10)::INTEGER;
        
        -- 插入当天的会话数据
        FOR conversation_count IN 1..base_conversation_count LOOP
            -- 生成随机时间（当天的0点到23:59之间）
            hour_val := FLOOR(RANDOM() * 24)::INTEGER;
            minute_val := FLOOR(RANDOM() * 60)::INTEGER;
            second_val := FLOOR(RANDOM() * 60)::INTEGER;
            conv_time := current_date + (hour_val || ' hours')::INTERVAL + (minute_val || ' minutes')::INTERVAL + (second_val || ' seconds')::INTERVAL;
            
            -- 插入会话
            INSERT INTO chat_conversation (
                user_id,
                app_id,
                knowledge_base_id,
                type,
                title,
                create_time,
                update_time,
                deleted,
                model_id
            ) VALUES (
                user_id_val,
                CASE WHEN (conversation_count % 2 = 0) THEN 1 ELSE NULL END,
                CASE WHEN (conversation_count % 3 = 0) THEN 1 ELSE NULL END,
                CASE WHEN (conversation_count % 3 = 0) THEN 2 ELSE 1 END,
                '测试会话 ' || conversation_count || ' - ' || TO_CHAR(current_date, 'YYYY-MM-DD'),
                conv_time,
                conv_time,
                0,
                model_id_val
            ) RETURNING id INTO conversation_id;
            
            -- 为每个会话插入消息（每轮对话包含用户消息和助手回复）
            rounds := 2 + FLOOR(RANDOM() * 4)::INTEGER; -- 2-5轮对话
            msg_sequence := 0;
            
            FOR message_count IN 1..rounds LOOP
                -- 用户消息
                msg_sequence := msg_sequence + 1;
                msg_time := conv_time + (FLOOR(RANDOM() * 3600)::INTEGER || ' seconds')::INTERVAL;
                
                INSERT INTO chat_message (
                    conversation_id,
                    role,
                    content,
                    sequence,
                    create_time,
                    model_id,
                    prompt_tokens,
                    completion_tokens,
                    total_tokens
                ) VALUES (
                    conversation_id,
                    'user',
                    '用户问题 ' || message_count || ' - ' || TO_CHAR(current_date, 'YYYY-MM-DD'),
                    msg_sequence,
                    msg_time,
                    model_id_val,
                    (50 + FLOOR(RANDOM() * 100)::INTEGER)::BIGINT,
                    NULL,
                    NULL
                );
                
                -- 助手回复
                msg_sequence := msg_sequence + 1;
                msg_time := msg_time + (FLOOR(RANDOM() * 60)::INTEGER || ' seconds')::INTERVAL;
                
                INSERT INTO chat_message (
                    conversation_id,
                    role,
                    content,
                    sequence,
                    create_time,
                    model_id,
                    prompt_tokens,
                    completion_tokens,
                    total_tokens
                ) VALUES (
                    conversation_id,
                    'assistant',
                    '助手回复 ' || message_count || ' - ' || TO_CHAR(current_date, 'YYYY-MM-DD'),
                    msg_sequence,
                    msg_time,
                    model_id_val,
                    (50 + FLOOR(RANDOM() * 100)::INTEGER)::BIGINT,
                    (100 + FLOOR(RANDOM() * 200)::INTEGER)::BIGINT,
                    (150 + FLOOR(RANDOM() * 300)::INTEGER)::BIGINT
                );
            END LOOP;
        END LOOP;
    END LOOP;
END $$;

-- 验证数据
SELECT 
    DATE(create_time) as date,
    COUNT(DISTINCT id) as conversation_count,
    (SELECT COUNT(*) FROM chat_message WHERE DATE(create_time) = DATE(cc.create_time)) as message_count
FROM chat_conversation cc
WHERE create_time >= CURRENT_DATE - INTERVAL '30 days'
    AND (deleted IS NULL OR deleted = 0)
GROUP BY DATE(create_time)
ORDER BY date DESC
LIMIT 30;

