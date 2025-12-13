-- ============================================
-- OCR服务配置初始化脚本
-- 执行时间：2025-12-13
-- ============================================

-- 插入OCR服务配置（如果不存在）
INSERT INTO "SYSTEM_CONFIG" (config_key, config_value, description, config_group, config_type, create_time, update_time, deleted, creator, creator_id)
VALUES 
    (
        'ocr.service.url',
        'http://localhost:8000',
        'EasyOCR服务地址（如：http://localhost:8000）',
        'ocr',
        'string',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0,
        'system',
        NULL
    ),
    (
        'ocr.service.timeout',
        '30000',
        'EasyOCR服务请求超时时间（毫秒，默认：30000）',
        'ocr',
        'number',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0,
        'system',
        NULL
    )
ON CONFLICT (config_key) DO NOTHING;

-- 添加注释
COMMENT ON COLUMN "SYSTEM_CONFIG".config_key IS '配置键（唯一，如：ocr.service.url, ocr.service.timeout）';
