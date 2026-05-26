# Skills 提示词模板

`skills/` 用于集中存放后端大模型相关功能的提示词模板。Java 代码通过 `SkillLoader` 读取这些文件，避免在业务 Service 中硬编码大段提示词。

## 加载规则

- 模板路径相对于当前目录，调用时不需要写 `.md` 后缀。
- 示例：`SkillLoader.loadSkill("chat/system_prompt")` 会读取 `skills/chat/system_prompt.md`。
- `SkillLoader.loadSkillWithTemplate(path, variables)` 会将 `{question}` 这类占位符替换为 `variables` 中的值。
- 如果占位符没有对应变量，会保留原文，方便模板中写 JSON 示例，例如 `{}`。

## 目录说明

- `chat/`：通用智能问答、浏览器检索、备忘录提示、视觉能力说明和默认兜底文案。
- `knowledge_base/`：知识库问答、检索上下文、知识库摘要相关提示词。
- `document_reader/`：文档问答、文档导读、思维导图、文档翻译相关提示词。
- `assistant/`：用户端全局页面助手提示词。
- `analytics/`：数据分析和 GraphRAG 相关提示词。
- `memory/`：长期记忆抽取相关提示词。
- `dialog/`：上下文压缩时使用的对话总结提示词。
- `drawio/`：AI 图表生成和修改相关提示词。
- `common/`：通用提示词片段，例如 Markdown 输出格式要求。
- `mcp/`：MCP 工具上下文模板，例如浏览器检索结果格式化。

## 维护建议

- 新增或修改提示词时，优先维护这里的 `.md` 文件，不建议在 Java 代码中硬编码提示词。
- 如果某段提示词会被单独加载，建议保持独立文件，避免多个功能互相影响。
- 占位符命名要清晰，例如 `{question}`、`{documentText}`、`{retrievalTime}`。
- 新增模板后，尽量保持路径稳定，因为 Java 代码会直接引用模板路径。
