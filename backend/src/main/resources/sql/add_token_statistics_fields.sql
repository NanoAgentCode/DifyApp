-- 为chat_message表添加token统计相关字段
-- 执行时间：2025-12-23
-- 数据库：PostgreSQL

-- 为chat_message表添加模型ID和token字段
ALTER TABLE chat_message 
ADD COLUMN IF NOT EXISTS model_id BIGINT NULL,
ADD COLUMN IF NOT EXISTS prompt_tokens BIGINT NULL,
ADD COLUMN IF NOT EXISTS completion_tokens BIGINT NULL,
ADD COLUMN IF NOT EXISTS total_tokens BIGINT NULL;

-- 添加字段注释（PostgreSQL语法）
COMMENT ON COLUMN chat_message.model_id IS '模型ID（关联使用的模型）';
COMMENT ON COLUMN chat_message.prompt_tokens IS 'Prompt Tokens数量';
COMMENT ON COLUMN chat_message.completion_tokens IS 'Completion Tokens数量';
COMMENT ON COLUMN chat_message.total_tokens IS '总Tokens数量';

-- 为chat_conversation表添加模型ID字段
ALTER TABLE chat_conversation 
ADD COLUMN IF NOT EXISTS model_id BIGINT NULL;

-- 添加字段注释
COMMENT ON COLUMN chat_conversation.model_id IS '模型ID（会话使用的模型，可选）';

-- 添加索引以提高查询性能（如果不存在则创建）
CREATE INDEX IF NOT EXISTS idx_chat_message_model_id ON chat_message(model_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_total_tokens ON chat_message(total_tokens);
CREATE INDEX IF NOT EXISTS idx_chat_message_create_time ON chat_message(create_time);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_model_id ON chat_conversation(model_id);

