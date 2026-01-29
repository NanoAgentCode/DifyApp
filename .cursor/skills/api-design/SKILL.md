---
name: api-design
description: REST API 设计与前后端对接规范。在定义或修改接口路径、请求/响应体、错误码、分页与鉴权时使用。适用于前后端联调、API 文档、axios/request 封装相关任务。
---

# REST API 设计

## 路径与版本

- 基础路径：`/api/`，与后端 `@RequestMapping("/api/...")` 一致
- 资源用名词复数：`/api/chat/history/conversations`、`/api/knowledge-bases`
- 子资源：`/api/knowledge-bases/{kbId}/documents`

## HTTP 方法

- GET - 查询（列表、详情），幂等
- POST - 创建、复杂查询（如 QA 提交）
- PUT - 全量更新
- PATCH - 部分更新（若项目统一用 PUT 也可）
- DELETE - 删除，幂等

## 请求与响应

- 请求体：JSON，字段命名小驼峰（与前端、后端 DTO 一致）
- 响应体：JSON，统一结构由后端 `ApiResponse` 或直接 DTO；列表分页使用 `PageResponse`（如 `total`、`list`、`page`、`size`）
- 错误：由 `GlobalExceptionHandler` 返回统一错误结构（如 `code`、`message`），前端用 `useErrorHandler` / `useResponseHandler` 处理

## 鉴权与上下文

- 需要登录的接口：后端从 Header 或 Session 取用户（如 `getUserId(httpRequest)`），前端在 `request` 封装中统一带 token
- 管理员接口：路径或逻辑上区分（如 `/api/admin/...`），后端根据角色判断

## 前后端对接检查

- [ ] 接口路径与前端 `src/api/*.js` 中 baseURL + path 一致
- [ ] 请求参数字段名、必填与后端 req DTO 一致
- [ ] 响应字段与前端使用的 resp 结构一致（含分页字段名）
- [ ] 错误码与前端错误提示映射一致
