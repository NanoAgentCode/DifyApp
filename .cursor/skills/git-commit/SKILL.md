---
name: git-commit
description: 根据 git diff 生成规范的提交信息。在用户请求写 commit message、总结本次修改、或进行提交前说明时使用。采用约定式提交（Conventional Commits）格式。
---

# Git 提交规范

## 格式

```
<type>(<scope>): <subject>

<body>
```

- **type**：提交类型（见下表）
- **scope**：影响范围，可选，如 `frontend`、`backend`、`chat`、`knowledge-base`
- **subject**：简短说明，建议 50 字以内，结尾不加句号
- **body**：可选，说明动机或变更细节

## Type 类型

| type     | 说明         |
|----------|--------------|
| feat     | 新功能       |
| fix      | 修复 bug     |
| docs     | 文档         |
| style    | 格式、样式   |
| refactor | 重构         |
| perf     | 性能         |
| test     | 测试         |
| chore    | 构建/脚本等  |

## 示例

**示例 1：**  
变更：新增会话历史导出  
提交信息：
```
feat(chat): 支持会话历史导出为报告

增加导出接口与前端导出入口，支持选择时间范围与格式
```

**示例 2：**  
变更：修复知识库问答空结果时前端报错  
提交信息：
```
fix(knowledge-base): 空检索结果时返回空列表避免前端报错
```

**示例 3：**  
变更：仅改文档与注释  
提交信息：
```
docs: 更新 API 与前端样式规范说明
```

## 注意

- 根据实际 diff 归纳 type 与 scope，subject 要能概括本次改动
- 多模块同时改动可写多个 scope 或用 `*`，如 `fix(backend,frontend): 统一分页参数名`
