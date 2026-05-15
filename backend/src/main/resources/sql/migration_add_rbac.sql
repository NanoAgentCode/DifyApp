-- ============================================
-- RBAC 菜单/模块权限迁移脚本
-- PostgreSQL 15
-- ============================================

CREATE TABLE IF NOT EXISTS "SYS_ROLE" (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    status INTEGER DEFAULT 1,
    sort_order INTEGER DEFAULT 100,
    system_role BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS "SYS_PERMISSION" (
    id BIGSERIAL PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL UNIQUE,
    permission_name VARCHAR(100) NOT NULL,
    client_type VARCHAR(20) NOT NULL,
    route_path VARCHAR(200),
    icon VARCHAR(80),
    sort_order INTEGER DEFAULT 100,
    status INTEGER DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS "SYS_USER_ROLE" (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_role UNIQUE (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS "SYS_ROLE_PERMISSION" (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_role_permission UNIQUE (role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_sys_role_code ON "SYS_ROLE"(role_code);
CREATE INDEX IF NOT EXISTS idx_sys_permission_code ON "SYS_PERMISSION"(permission_code);
CREATE INDEX IF NOT EXISTS idx_sys_user_role_user_id ON "SYS_USER_ROLE"(user_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_role_role_id ON "SYS_USER_ROLE"(role_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_permission_role_id ON "SYS_ROLE_PERMISSION"(role_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_permission_permission_id ON "SYS_ROLE_PERMISSION"(permission_id);

INSERT INTO "SYS_ROLE" (role_code, role_name, description, status, sort_order, system_role, create_time, update_time, deleted)
VALUES
('SUPER_ADMIN', '超级管理员', '系统内置超级管理员', 1, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('ADMIN', '管理员', '系统内置管理员', 1, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('USER', '普通用户', '系统内置普通用户', 1, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO "SYS_PERMISSION" (permission_code, permission_name, client_type, route_path, icon, sort_order, status, create_time, update_time, deleted)
VALUES
('admin.chat', '管理端-智能问答', 'admin', '/admin/chat', 'ChatLineRound', 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.apps', '管理端-应用管理', 'admin', '/admin/apps', 'Grid', 20, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.models', '管理端-组件管理', 'admin', '/admin/models', 'Setting', 30, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.users', '管理端-用户管理', 'admin', '/admin/users', 'User', 40, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.roles', '管理端-角色管理', 'admin', '/admin/roles', 'Lock', 45, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.system_config', '管理端-系统配置', 'admin', '/admin/system-config', 'Tools', 50, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.skills', '管理端-Skills管理', 'admin', '/admin/skills', 'Cpu', 60, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.statistics', '管理端-数据统计', 'admin', '/admin/statistics', 'DataAnalysis', 70, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.data_analysis', '管理端-数据分析', 'admin', '/admin/data-analysis', 'Share', 80, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.user_logs', '管理端-行为日志', 'admin', '/admin/user-action-logs', 'Document', 90, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.observability', '管理端-日志监控', 'admin', '/admin/observability', 'Monitor', 100, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.memos', '管理端-备忘录', 'admin', '/admin/memos', 'Bell', 110, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.knowledge_base', '管理端-知识管理', 'admin', '/admin/knowledge-base', 'Collection', 120, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.documents', '管理端-文档管理', 'admin', '/admin/knowledge-base/:kbId/documents', 'Files', 130, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.chat_history', '管理端-会话历史', 'admin', '/admin/chat-history', 'Clock', 140, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.document_reader', '管理端-文档解读', 'admin', '/admin/document-reader', 'Reading', 150, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('admin.ai_drawio', '管理端-智能框图', 'admin', '/admin/ai-drawio', 'EditPen', 160, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('user.chat', '用户端-智能问答', 'user', '/user/chat', 'ChatLineRound', 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('user.apps', '用户端-智能应用', 'user', '/user/apps', 'Grid', 20, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('user.kb_qa', '用户端-知识检索', 'user', '/user/kb-qa', 'Search', 30, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('user.knowledge_base', '用户端-知识管理', 'user', '/user/knowledge-base', 'Collection', 40, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('user.documents', '用户端-文档管理', 'user', '/user/knowledge-base/:kbId/documents', 'Files', 50, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('user.chat_history', '用户端-会话历史', 'user', '/user/chat-history', 'Clock', 60, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('user.ai_drawio', '用户端-智能框图', 'user', '/user/ai-drawio', 'EditPen', 70, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('user.document_reader', '用户端-文档解读', 'user', '/user/document-reader', 'Reading', 80, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('user.memos', '用户端-备忘录', 'user', '/user/memos', 'Bell', 90, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO "SYS_ROLE_PERMISSION" (role_id, permission_id, create_time)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM "SYS_ROLE" r CROSS JOIN "SYS_PERMISSION" p
WHERE r.role_code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO "SYS_ROLE_PERMISSION" (role_id, permission_id, create_time)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM "SYS_ROLE" r CROSS JOIN "SYS_PERMISSION" p
WHERE r.role_code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO "SYS_ROLE_PERMISSION" (role_id, permission_id, create_time)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM "SYS_ROLE" r CROSS JOIN "SYS_PERMISSION" p
WHERE r.role_code = 'USER' AND p.permission_code LIKE 'user.%'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO "SYS_USER_ROLE" (user_id, role_id, create_time)
SELECT u.id, r.id, CURRENT_TIMESTAMP
FROM "SYS_USER" u
JOIN "SYS_ROLE" r ON r.role_code = CASE
    WHEN u.id = 1 OR u.username = 'admin' THEN 'SUPER_ADMIN'
    WHEN u.role = 1 THEN 'ADMIN'
    ELSE 'USER'
END
WHERE COALESCE(u.deleted, 0) = 0
ON CONFLICT (user_id, role_id) DO NOTHING;
