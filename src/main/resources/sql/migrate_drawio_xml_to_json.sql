-- ============================================
-- DrawIO 图表表迁移脚本：XML -> JSON
-- ============================================
-- 说明：将 diagram_xml 字段迁移为 diagram_json 字段
-- 注意：此脚本会删除 diagram_xml 列，请确保已备份数据

-- 1. 如果表已存在且包含 diagram_xml 列，添加新列
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'DRAWIO_DIAGRAM' 
        AND column_name = 'diagram_xml'
        AND column_name != 'diagram_json'
    ) THEN
        -- 添加新列
        ALTER TABLE "DRAWIO_DIAGRAM" 
        ADD COLUMN IF NOT EXISTS "diagram_json" TEXT;
        
        -- 注意：由于 XML 和 JSON 格式完全不同，无法直接转换
        -- 这里只是添加新列，旧数据需要重新生成或手动处理
        -- 如果需要保留旧数据，可以暂时保留 diagram_xml 列
        
        -- 更新注释
        COMMENT ON COLUMN "DRAWIO_DIAGRAM".diagram_json IS '图表JSON内容（AntV X6格式）';
        
        RAISE NOTICE '已添加 diagram_json 列';
    END IF;
END $$;

-- 2. 如果表已存在且包含 diagram_xml 列，可以选择删除旧列（谨慎操作）
-- 注意：取消下面的注释以删除 diagram_xml 列（仅在确认不需要旧数据后执行）
-- ALTER TABLE "DRAWIO_DIAGRAM" DROP COLUMN IF EXISTS "diagram_xml";

-- 3. 如果表不存在，直接创建新表（使用新结构）
-- 此部分已在 add_drawio_table.sql 中处理

