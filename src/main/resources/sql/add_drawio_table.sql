-- ============================================
-- 创建 DrawIO 图表表
-- ============================================
-- 说明：用于存储用户通过 AI 生成的图表（使用 AntV X6 格式）

-- 创建 DrawIO 图表表
CREATE TABLE IF NOT EXISTS "DRAWIO_DIAGRAM" (
    "id" BIGSERIAL PRIMARY KEY,
    "name" VARCHAR(255) NOT NULL,
    "diagram_type" VARCHAR(50),
    "diagram_json" TEXT NOT NULL,
    "user_id" BIGINT NOT NULL,
    "create_time" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "update_time" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "deleted" INTEGER DEFAULT 0
);

-- 添加字段注释
COMMENT ON TABLE "DRAWIO_DIAGRAM" IS 'DrawIO 图表表';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".id IS '图表ID';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".name IS '图表名称';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".diagram_type IS '图表类型：flowchart-流程图, architecture-架构图, mindmap-思维导图, sequence-时序图, uml-UML图, org-组织架构, network-网络图, custom-自定义';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".diagram_json IS '图表JSON内容（AntV X6格式）';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".user_id IS '用户ID';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".create_time IS '创建时间';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".update_time IS '更新时间';
COMMENT ON COLUMN "DRAWIO_DIAGRAM".deleted IS '是否删除：0-未删除，1-已删除';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_drawio_diagram_user_id ON "DRAWIO_DIAGRAM"("user_id");
CREATE INDEX IF NOT EXISTS idx_drawio_diagram_type ON "DRAWIO_DIAGRAM"("diagram_type");
CREATE INDEX IF NOT EXISTS idx_drawio_diagram_deleted ON "DRAWIO_DIAGRAM"("deleted");
CREATE INDEX IF NOT EXISTS idx_drawio_diagram_user_deleted ON "DRAWIO_DIAGRAM"("user_id", "deleted");
CREATE INDEX IF NOT EXISTS idx_drawio_diagram_create_time ON "DRAWIO_DIAGRAM"("create_time");

