-- ============================================
-- 用户行为日志表
-- ============================================
-- 创建用户行为日志表
DROP TABLE IF EXISTS "USER_ACTION_LOG" CASCADE;

CREATE TABLE "USER_ACTION_LOG" (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64),
    module VARCHAR(50),
    action_type VARCHAR(50),
    description VARCHAR(500),
    method VARCHAR(20),
    request_path VARCHAR(500),
    request_params TEXT,
    result VARCHAR(20),
    error_msg TEXT,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    execution_time BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE "USER_ACTION_LOG" IS '用户行为日志表';
COMMENT ON COLUMN "USER_ACTION_LOG".id IS '日志ID';
COMMENT ON COLUMN "USER_ACTION_LOG".user_id IS '用户ID';
COMMENT ON COLUMN "USER_ACTION_LOG".username IS '用户名';
COMMENT ON COLUMN "USER_ACTION_LOG".module IS '操作模块';
COMMENT ON COLUMN "USER_ACTION_LOG".action_type IS '操作类型';
COMMENT ON COLUMN "USER_ACTION_LOG".description IS '操作描述';
COMMENT ON COLUMN "USER_ACTION_LOG".method IS '请求方法（GET/POST/PUT/DELETE）';
COMMENT ON COLUMN "USER_ACTION_LOG".request_path IS '请求路径';
COMMENT ON COLUMN "USER_ACTION_LOG".request_params IS '请求参数（JSON格式）';
COMMENT ON COLUMN "USER_ACTION_LOG".result IS '操作结果（SUCCESS/FAILURE）';
COMMENT ON COLUMN "USER_ACTION_LOG".error_msg IS '错误信息';
COMMENT ON COLUMN "USER_ACTION_LOG".ip_address IS 'IP地址';
COMMENT ON COLUMN "USER_ACTION_LOG".user_agent IS '用户代理';
COMMENT ON COLUMN "USER_ACTION_LOG".execution_time IS '执行时长（毫秒）';
COMMENT ON COLUMN "USER_ACTION_LOG".create_time IS '创建时间';

-- 创建索引
CREATE INDEX idx_user_action_log_user_id ON "USER_ACTION_LOG"(user_id);
CREATE INDEX idx_user_action_log_username ON "USER_ACTION_LOG"(username);
CREATE INDEX idx_user_action_log_module ON "USER_ACTION_LOG"(module);
CREATE INDEX idx_user_action_log_action_type ON "USER_ACTION_LOG"(action_type);
CREATE INDEX idx_user_action_log_result ON "USER_ACTION_LOG"(result);
CREATE INDEX idx_user_action_log_create_time ON "USER_ACTION_LOG"(create_time);
CREATE INDEX idx_user_action_log_user_time ON "USER_ACTION_LOG"(user_id, create_time);

-- 说明
-- 1. 该表用于记录用户在系统中的关键操作行为
-- 2. 通过Spring AOP切面自动记录，无需手动调用
-- 3. 支持按用户、模块、操作类型、时间范围等多维度查询
-- 4. 可用于审计、安全分析、用户行为分析等场景
