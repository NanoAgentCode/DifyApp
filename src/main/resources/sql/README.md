# 数据库迁移说明

## 输入字段配置重构 - 数据库变更说明

### 变更内容
将输入组件的配置完全以 JSON 格式存储在数据库中，包括样式、类型、输入提示等。

### 数据库字段说明

#### AI_APP 表的 inputs 字段

**字段名**: `inputs`  
**数据类型**: `TEXT` (PostgreSQL)  
**说明**: 存储输入字段的完整配置信息，格式为 JSON 字符串

**JSON 格式示例**:
```json
{
  "fields": {
    "word": {
      "label": "关键词",
      "type": "text",
      "placeholder": "请输入关键词",
      "defaultValue": "",
      "helpText": "请输入要搜索的关键词",
      "required": true,
      "rows": 2,
      "options": [],
      "style": {
        "width": "100%",
        "labelWidth": "140px"
      },
      "validation": {
        "minLength": 1,
        "maxLength": 100,
        "pattern": ""
      }
    }
  },
  "defaults": {
    "word": ""
  }
}
```

### 兼容性说明

1. **向后兼容**: 系统会自动识别并转换旧格式的 inputs 数据
   - 旧格式: `{"word": "", "variable_name": []}`
   - 新格式: 包含 `fields` 和 `defaults` 的完整配置

2. **字段类型**: 
   - PostgreSQL: `TEXT` 类型（已足够，可存储最大 1GB）
   - MySQL: 建议使用 `LONGTEXT` 类型
   - 其他数据库: 请使用支持大文本的类型

### 迁移步骤

1. **检查字段类型**:
   ```sql
   SELECT column_name, data_type, character_maximum_length 
   FROM information_schema.columns 
   WHERE table_name = 'ai_app' AND column_name = 'inputs';
   ```

2. **如果需要修改字段类型** (通常不需要，因为 TEXT 已足够):
   ```sql
   -- PostgreSQL
   ALTER TABLE AI_APP ALTER COLUMN inputs TYPE TEXT;
   
   -- MySQL
   ALTER TABLE AI_APP MODIFY COLUMN inputs LONGTEXT;
   ```

3. **验证数据**:
   ```sql
   SELECT id, name, inputs FROM AI_APP WHERE inputs IS NOT NULL LIMIT 5;
   ```

### 注意事项

- 由于使用了 JPA 的 `ddl-auto: update`，Hibernate 会自动更新表结构
- 如果字段已存在且类型为 TEXT，则无需手动迁移
- 旧数据会自动兼容，无需手动转换
- 新创建的应用会自动使用新格式

### 测试建议

1. 创建新的 Workflow 类型应用
2. 在"输入字段配置"中添加字段并配置
3. 保存后检查数据库中的 `inputs` 字段内容
4. 验证前端是否正确渲染配置的输入组件

