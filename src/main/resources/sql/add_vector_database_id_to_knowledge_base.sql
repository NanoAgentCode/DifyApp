-- 为知识库表添加向量库实例ID字段
-- 用于精确关联向量库实例，支持同一类型多个实例的区分

ALTER TABLE KNOWLEDGE_BASE 
ADD COLUMN vector_database_id BIGINT NULL COMMENT '向量库实例ID（关联VECTOR_DATABASE表的id）';

-- 添加索引以提高查询性能
CREATE INDEX idx_knowledge_base_vector_database_id ON KNOWLEDGE_BASE(vector_database_id);

-- 添加外键约束（可选，如果不需要外键约束可以注释掉）
-- ALTER TABLE KNOWLEDGE_BASE 
-- ADD CONSTRAINT fk_knowledge_base_vector_database 
-- FOREIGN KEY (vector_database_id) REFERENCES VECTOR_DATABASE(id);

