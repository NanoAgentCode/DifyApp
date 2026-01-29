---
name: frontend-vue
description: Vue 3 前端开发规范与常用模式。在编写或修改 Vue 组件、API 调用、Composables、路由与状态管理时使用。适用于 .vue、前端 api、composables、stores、router 相关任务。
---

# Vue 前端开发

## 项目结构

- `src/views/` - 页面级组件（admin / user / auth / app 等）
- `src/components/` - 可复用组件（common、业务子目录如 chat、documentReader）
- `src/api/` - 后端 API 封装（按模块分文件，如 chat.js、knowledgeBase.js）
- `src/composables/` - 组合式逻辑（useChat、useErrorHandler、useSSEStream 等）
- `src/stores/` - Pinia 状态（app、user）
- `src/utils/` - 工具（request、apiHelper、logger 等）
- `src/styles/` - 设计令牌与主题（design-tokens.css、enterprise-base.css）

## 样式规范

优先使用设计令牌，勿硬编码颜色与间距。详见 `backend/doc/前端样式规范.md`。

- 颜色：`var(--color-primary)`、`var(--color-text-primary)` 等
- 间距：`var(--spacing-sm)`、`var(--spacing-md)` 等（8px 基准）
- 字体：`var(--font-family-base)`、`var(--font-size-base)` 等

## 组件约定

- 单文件组件：`<script setup>` + `<template>` + `<style scoped>`
- 命名：组件 PascalCase，文件与组件名一致（如 `DataTable.vue`）
- API 调用：在 `src/api/` 中封装，在组件或 composable 中通过 `request` 使用
- 错误与加载：使用项目已有的 `useErrorHandler`、`useResponseHandler` 等

## 新增功能检查清单

- [ ] API 是否在 `src/api/` 对应模块中封装
- [ ] 是否使用设计令牌而非硬编码样式
- [ ] 路由是否在 `src/router/index.js` 中注册
- [ ] 是否需要新 composable 或复用已有（useApiCache、useSSEStream 等）
