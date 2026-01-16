-- ============================================
-- 数据库性能优化索引脚本
-- ============================================
-- 说明：此脚本添加组合索引以优化查询性能
-- 执行前请备份数据库
-- 建议在业务低峰期执行，因为索引创建会影响写入性能
-- 
-- 回滚脚本：见文件末尾

-- ============================================
-- 第一部分：chat_message 表索引优化
-- ============================================

-- 1.1 组合索引：conversation_id + role
-- 用途：查询会话中的特定角色消息（用户消息或助手消息）
-- 影响：getMessages(), countConversationRoundsByConversationId()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chat_message_conv_role 
ON chat_message(conversation_id, role);

-- 1.2 组合索引：conversation_id + sequence
-- 用途：查询会话消息按顺序排序
-- 影响：findByConversationId(), saveMessage()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chat_message_conv_seq 
ON chat_message(conversation_id, sequence);

-- 1.3 组合索引：conversation_id + create_time
-- 用途：查询会话消息按创建时间排序
-- 影响：getMessages() 按时间排序
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chat_message_conv_time 
ON chat_message(conversation_id, create_time);

-- 1.4 组合索引：role + create_time
-- 用途：统计热门问题（按角色和时间）
-- 影响：getStatistics() 中的热门问题统计
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chat_message_role_time 
ON chat_message(role, create_time);

-- 1.5 组合索引：conversation_id + role + create_time
-- 用途：查询会话中特定角色的消息并按时间排序
-- 影响：优化消息列表查询
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chat_message_conv_role_time 
ON chat_message(conversation_id, role, create_time);

-- ============================================
-- 第二部分：chat_conversation 表索引优化
-- ============================================

-- 2.1 组合索引：user_id + deleted + create_time
-- 用途：用户查询自己的会话列表（最常用查询）
-- 影响：getMyConversations(), findByUserId()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chat_conv_user_deleted_time 
ON chat_conversation(user_id, deleted, create_time);

-- 2.2 组合索引：user_id + type + deleted + create_time
-- 用途：按类型筛选用户会话
-- 影响：getMyConversations() 带类型筛选
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chat_conv_user_type_deleted_time 
ON chat_conversation(user_id, type, deleted, create_time);

-- 2.3 组合索引：deleted + type + create_time
-- 用途：管理员查询所有会话，按类型筛选
-- 影响：getAllConversations(), findAllNotDeleted()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chat_conv_deleted_type_time 
ON chat_conversation(deleted, type, create_time);

-- 2.4 组合索引：deleted + create_time
-- 用途：统计查询（按日期范围统计）
-- 影响：countByDateRange(), getStatistics()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chat_conv_deleted_time 
ON chat_conversation(deleted, create_time);

-- 2.5 组合索引：deleted + create_time（用于分页）
-- 用途：管理员查询会话列表分页
-- 影响：getAllConversations()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chat_conv_deleted_time_desc 
ON chat_conversation(deleted, create_time DESC);

-- ============================================
-- 第三部分：SYS_USER 表索引优化
-- ============================================

-- 3.1 组合索引：role + status
-- 用途：查询特定角色和状态的用户
-- 影响：管理员查询用户列表
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_role_status 
ON "SYS_USER"(role, status);

-- 3.2 组合索引：status + role
-- 用途：查询用户状态分布
-- 影响：用户统计
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_status_role 
ON "SYS_USER"(status, role);

-- ============================================
-- 第四部分：KNOWLEDGE_BASE 表索引优化
-- ============================================

-- 4.1 组合索引：creator_id + deleted + create_time
-- 用途：用户查询自己创建的知识库列表
-- 影响：getKnowledgeBasesByUserId()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kb_creator_deleted_time 
ON "KNOWLEDGE_BASE"(creator_id, deleted, create_time);

-- 4.2 组合索引：status + deleted + is_public
-- 用途：查询启用的公开知识库
-- 影响：getPublicKnowledgeBases()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kb_status_deleted_public 
ON "KNOWLEDGE_BASE"(status, deleted, is_public);

-- 4.3 组合索引：vector_store_type + deleted
-- 用途：按向量存储类型筛选知识库
-- 影响：向量数据库管理
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kb_vector_type_deleted 
ON "KNOWLEDGE_BASE"(vector_store_type, deleted);

-- ============================================
-- 第五部分：KNOWLEDGE_BASE_DOCUMENT 表索引优化
-- ============================================

-- 5.1 组合索引：knowledge_base_id + deleted + create_time
-- 用途：查询知识库的文档列表
-- 影响：getDocumentsByKnowledgeBaseId()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kb_doc_kb_deleted_time 
ON "KNOWLEDGE_BASE_DOCUMENT"(knowledge_base_id, deleted, create_time);

-- 5.2 组合索引：status + deleted
-- 用途：查询文档状态
-- 影响：文档管理
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kb_doc_status_deleted 
ON "KNOWLEDGE_BASE_DOCUMENT"(status, deleted);

-- 5.3 组合索引：vectorized_status + deleted
-- 用途：查询向量化状态
-- 影响：文档向量化任务
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kb_doc_vectorized_deleted 
ON "KNOWLEDGE_BASE_DOCUMENT"(vectorized_status, deleted);

-- 5.4 组合索引：knowledge_base_id + vectorized_status + deleted
-- 用途：查询知识库中特定向量化状态的文档
-- 影响：文档向量化监控
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kb_doc_kb_vectorized_deleted 
ON "KNOWLEDGE_BASE_DOCUMENT"(knowledge_base_id, vectorized_status, deleted);

-- ============================================
-- 第六部分：AI_APP 表索引优化
-- ============================================

-- 6.1 组合索引：tenant_id + deleted + status
-- 用途：查询租户的应用列表
-- 影响：getAppsByTenantId()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ai_app_tenant_deleted_status 
ON "AI_APP"(tenant_id, deleted, status);

-- 6.2 组合索引：type + deleted
-- 用途：按应用类型筛选
-- 影响：应用管理
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ai_app_type_deleted 
ON "AI_APP"(type, deleted);

-- 6.3 组合索引：tenant_id + type + deleted
-- 用途：查询租户的特定类型应用
-- 影响：getAppsByTenantIdAndType()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ai_app_tenant_type_deleted 
ON "AI_APP"(tenant_id, type, deleted);

-- ============================================
-- 第七部分：USER_MEMORY 表索引优化
-- ============================================

-- 7.1 组合索引：user_id + deleted + scope_type + scope_id
-- 用途：查询用户的记忆（按作用域）
-- 影响：getUserMemories()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_memory_user_deleted_scope 
ON "USER_MEMORY"(user_id, deleted, scope_type, scope_id);

-- 7.2 组合索引：scope_type + scope_id + deleted
-- 用途：按作用域查询记忆
-- 影响：记忆管理
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_memory_scope_deleted 
ON "USER_MEMORY"(scope_type, scope_id, deleted);

-- 7.3 组合索引：memory_type + importance + update_time
-- 用途：按记忆类型和重要度查询
-- 影响：记忆检索
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_memory_type_importance_time 
ON "USER_MEMORY"(memory_type, importance, update_time DESC);

-- ============================================
-- 第八部分：DATA_SOURCE 表索引优化
-- ============================================

-- 8.1 组合索引：creator_id + deleted + status
-- 用途：用户查询自己创建的数据源
-- 影响：getDataSourcesByUserId()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_data_source_creator_deleted_status 
ON "DATA_SOURCE"(creator_id, deleted, status);

-- 8.2 组合索引：type + deleted
-- 用途：按数据源类型筛选
-- 影响：数据源管理
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_data_source_type_deleted 
ON "DATA_SOURCE"(type, deleted);

-- 8.3 组合索引：is_public + deleted + status
-- 用途：查询公开的数据源
-- 影响：getPublicDataSources()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_data_source_public_deleted_status 
ON "DATA_SOURCE"(is_public, deleted, status);

-- ============================================
-- 第九部分：DOCUMENT_READER 表索引优化
-- ============================================

-- 9.1 组合索引：user_id + deleted + create_time
-- 用途：用户查询自己的文档
-- 影响：getDocumentsByUserId()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_doc_reader_user_deleted_time 
ON "DOCUMENT_READER"(user_id, deleted, create_time);

-- 9.2 组合索引：status + deleted
-- 用途：查询文档状态
-- 影响：文档管理
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_doc_reader_status_deleted 
ON "DOCUMENT_READER"(status, deleted);

-- 9.3 组合索引：vectorized_status + deleted
-- 用途：查询向量化状态
-- 影响：文档向量化任务
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_doc_reader_vectorized_deleted 
ON "DOCUMENT_READER"(vectorized_status, deleted);

-- ============================================
-- 第十部分：DRAWIO_DIAGRAM 表索引优化
-- ============================================

-- 10.1 组合索引：user_id + deleted + create_time
-- 用途：用户查询自己的图表
-- 影响：getDiagramsByUserId()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_drawio_user_deleted_time 
ON "DRAWIO_DIAGRAM"(user_id, deleted, create_time);

-- 10.2 组合索引：user_id + diagram_type + deleted
-- 用途：按图表类型筛选
-- 影响：getDiagramsByType()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_drawio_user_type_deleted 
ON "DRAWIO_DIAGRAM"(user_id, diagram_type, deleted);

-- ============================================
-- 第十一部分：DRAWIO_HISTORY 表索引优化
-- ============================================

-- 11.1 组合索引：user_id + deleted + create_time
-- 用途：用户查询自己的历史记录
-- 影响：getHistoryByUserId()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_drawio_history_user_deleted_time 
ON "DRAWIO_HISTORY"(user_id, deleted, create_time);

-- 11.2 组合索引：user_id + diagram_type + deleted + create_time
-- 用途：按图表类型查询历史记录
-- 影响：getHistoryByType()
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_drawio_history_user_type_deleted_time 
ON "DRAWIO_HISTORY"(user_id, diagram_type, deleted, create_time);

-- ============================================
-- 索引创建完成
-- ============================================

-- 查看索引创建结果
SELECT 
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = 'public'
    AND tablename IN (
        'chat_message', 
        'chat_conversation',
        'SYS_USER',
        'KNOWLEDGE_BASE',
        'KNOWLEDGE_BASE_DOCUMENT',
        'AI_APP',
        'USER_MEMORY',
        'DATA_SOURCE',
        'DOCUMENT_READER',
        'DRAWIO_DIAGRAM',
        'DRAWIO_HISTORY'
    )
ORDER BY tablename, indexname;

-- 查看索引大小
SELECT 
    schemaname,
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(indexrelid)) as index_size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
    AND tablename IN (
        'chat_message', 
        'chat_conversation',
        'SYS_USER',
        'KNOWLEDGE_BASE',
        'KNOWLEDGE_BASE_DOCUMENT',
        'AI_APP',
        'USER_MEMORY',
        'DATA_SOURCE',
        'DOCUMENT_READER',
        'DRAWIO_DIAGRAM',
        'DRAWIO_HISTORY'
    )
ORDER BY pg_relation_size(indexrelid) DESC;

-- ============================================
-- 回滚脚本（如果需要删除索引）
-- ============================================
-- 注意：删除索引前请确认不会影响业务

/*
-- 回滚 chat_message 表索引
DROP INDEX IF EXISTS idx_chat_message_conv_role;
DROP INDEX IF EXISTS idx_chat_message_conv_seq;
DROP INDEX IF EXISTS idx_chat_message_conv_time;
DROP INDEX IF EXISTS idx_chat_message_role_time;
DROP INDEX IF EXISTS idx_chat_message_conv_role_time;

-- 回滚 chat_conversation 表索引
DROP INDEX IF EXISTS idx_chat_conv_user_deleted_time;
DROP INDEX IF EXISTS idx_chat_conv_user_type_deleted_time;
DROP INDEX IF EXISTS idx_chat_conv_deleted_type_time;
DROP INDEX IF EXISTS idx_chat_conv_deleted_time;
DROP INDEX IF EXISTS idx_chat_conv_deleted_time_desc;

-- 回滚 SYS_USER 表索引
DROP INDEX IF EXISTS idx_user_role_status;
DROP INDEX IF EXISTS idx_user_status_role;

-- 回滚 KNOWLEDGE_BASE 表索引
DROP INDEX IF EXISTS idx_kb_creator_deleted_time;
DROP INDEX IF EXISTS idx_kb_status_deleted_public;
DROP INDEX IF EXISTS idx_kb_vector_type_deleted;

-- 回滚 KNOWLEDGE_BASE_DOCUMENT 表索引
DROP INDEX IF EXISTS idx_kb_doc_kb_deleted_time;
DROP INDEX IF EXISTS idx_kb_doc_status_deleted;
DROP INDEX IF EXISTS idx_kb_doc_vectorized_deleted;
DROP INDEX IF EXISTS idx_kb_doc_kb_vectorized_deleted;

-- 回滚 AI_APP 表索引
DROP INDEX IF EXISTS idx_ai_app_tenant_deleted_status;
DROP INDEX IF EXISTS idx_ai_app_type_deleted;
DROP INDEX IF EXISTS idx_ai_app_tenant_type_deleted;

-- 回滚 USER_MEMORY 表索引
DROP INDEX IF EXISTS idx_user_memory_user_deleted_scope;
DROP INDEX IF EXISTS idx_user_memory_scope_deleted;
DROP INDEX IF EXISTS idx_user_memory_type_importance_time;

-- 回滚 DATA_SOURCE 表索引
DROP INDEX IF EXISTS idx_data_source_creator_deleted_status;
DROP INDEX IF EXISTS idx_data_source_type_deleted;
DROP INDEX IF EXISTS idx_data_source_public_deleted_status;

-- 回滚 DOCUMENT_READER 表索引
DROP INDEX IF EXISTS idx_doc_reader_user_deleted_time;
DROP INDEX IF EXISTS idx_doc_reader_status_deleted;
DROP INDEX IF EXISTS idx_doc_reader_vectorized_deleted;

-- 回滚 DRAWIO_DIAGRAM 表索引
DROP INDEX IF EXISTS idx_drawio_user_deleted_time;
DROP INDEX IF EXISTS idx_drawio_user_type_deleted;

-- 回滚 DRAWIO_HISTORY 表索引
DROP INDEX IF EXISTS idx_drawio_history_user_deleted_time;
DROP INDEX IF EXISTS idx_drawio_history_user_type_deleted_time;
*/

-- ============================================
-- 脚本执行完成
-- ============================================
-- 所有索引已创建完成
-- 建议执行以下验证查询：
-- 1. 检查慢查询日志，确认索引是否被使用
-- 2. 执行 EXPLAIN ANALYZE 验证查询计划是否优化
-- 3. 监控数据库性能指标
-- 4. 在测试环境验证业务功能正常
