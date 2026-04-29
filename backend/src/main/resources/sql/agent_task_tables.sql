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
