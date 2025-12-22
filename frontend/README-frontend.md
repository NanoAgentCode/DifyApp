# DifyApp 前端项目

## 项目概述

DifyApp 前端是一个基于Vue 3构建的现代化单页应用（SPA），用于与后端API进行交互，提供用户认证、智能对话、知识库管理、AI应用管理、Text2SQL等功能。该项目采用响应式设计，支持多种设备访问，并集成了Tauri框架支持桌面应用开发。

## 技术栈

### 核心框架
- **前端框架**: Vue 3.3.4
- **构建工具**: Vite 5.0.8
- **状态管理**: Pinia 2.1.7
- **路由**: Vue Router 4.2.5

### UI框架
- **组件库**: Element Plus 2.4.2
- **图标**: @element-plus/icons-vue 2.3.1
- **主题**: 支持深色/浅色主题切换

### 功能库
- **HTTP客户端**: Axios 1.6.2
- **Markdown渲染**: 
  - marked 17.0.0 (Markdown解析)
  - marked-highlight 2.2.3 (代码高亮)
  - highlight.js 11.11.1 (语法高亮)
  - katex 0.16.9 (数学公式渲染)
  - mermaid 10.6.1 (图表渲染)

### 桌面应用
- **Tauri**: 2.9.6 (跨平台桌面应用框架)

### 开发工具
- **代码压缩**: Terser 5.44.1

## 项目结构

```
src/
├── api/                    # API接口定义
│   ├── aiApp.js            # AI应用API
│   ├── auth.js             # 认证API
│   ├── chat.js             # 聊天API
│   ├── dataSource.js       # 数据源API
│   ├── drawio.js           # 绘图API
│   ├── knowledgeBase.js    # 知识库API
│   ├── knowledgeBaseDocument.js  # 知识库文档API
│   ├── knowledgeBaseQA.js  # 知识库问答API
│   ├── model.js            # 模型API
│   ├── prompt.js           # Prompt API
│   ├── systemConfig.js     # 系统配置API
│   ├── text2sql.js         # Text2SQL API
│   ├── user.js             # 用户API
│   └── vectorDatabase.js   # 向量数据库API
├── components/             # 可复用组件
│   ├── AppIcon.vue         # 应用图标组件
│   ├── ChangePasswordDialog.vue  # 修改密码对话框
│   ├── HelpDialog.vue      # 帮助对话框
│   ├── HelpFloatingButton.vue    # 帮助浮动按钮
│   ├── ResetPasswordDialog.vue   # 重置密码对话框
│   ├── chat/               # 聊天相关组件
│   └── common/             # 公共组件
├── composables/            # 组合式函数
│   ├── useApiCache.js      # API缓存
│   ├── useChat.js          # 聊天功能
│   ├── useChatHistory.js   # 聊天历史
│   ├── useErrorHandler.js  # 错误处理
│   ├── useKnowledgeBaseQA.js  # 知识库问答
│   ├── useMarkdown.js      # Markdown处理
│   └── useModel.js         # 模型管理
├── config/                 # 配置文件
│   └── api.js              # API配置
├── layouts/                # 布局组件
│   ├── AdminLayout.vue     # 管理员布局
│   ├── AppLayout.vue       # 应用布局
│   └── UserLayout.vue      # 用户布局
├── router/                 # 路由配置
│   └── index.js            # 路由定义
├── stores/                 # Pinia状态管理
│   ├── app.js              # 应用状态
│   └── user.js             # 用户状态
├── styles/                 # 样式文件
│   └── vscode-dark.css     # VS Code深色主题
├── utils/                  # 工具函数
│   ├── apiHelper.js        # API辅助函数
│   ├── common.js           # 通用工具
│   ├── debounce.js         # 防抖函数
│   ├── globalTheme.js      # 全局主题
│   ├── icons.js            # 图标工具
│   ├── markdown.js         # Markdown工具
│   ├── modelColor.js       # 模型颜色
│   ├── request.js          # 请求封装
│   └── themes.js           # 主题工具
├── views/                  # 页面组件
│   ├── admin/              # 管理员页面
│   ├── app/                # 应用页面
│   ├── auth/               # 认证页面
│   └── user/               # 用户页面
├── App.vue                 # 根组件
└── main.js                 # 入口文件

src-tauri/                  # Tauri桌面应用配置
├── src/                    # Rust源代码
├── Cargo.toml              # Rust依赖配置
└── tauri.conf.json         # Tauri配置文件
```

## 模块说明

### 1. 用户认证模块 (auth)
- 用户登录、注册页面
- 密码修改、重置功能
- JWT令牌管理
- 用户信息管理

### 2. 聊天对话模块 (chat)
- 实时聊天界面
- AI应用选择与管理
- 对话历史管理
- 支持Chat和Workflow两种模式
- Markdown渲染（支持代码高亮、数学公式、图表）

### 3. 知识库模块 (knowledgebase)
- 知识库创建与编辑
- 文档上传与管理
- 向量数据库配置
- 知识库问答（RAG）

### 4. AI应用管理模块
- AI应用创建与管理
- 应用配置
- 应用可见性管理

### 5. 系统管理模块 (admin)
- 系统配置管理
- 数据源管理
- 模型管理
- Prompt模板管理
- Text2SQL功能
- 用户管理

### 6. 其他功能
- 主题切换（深色/浅色）
- 帮助文档
- 响应式布局

## 开发环境要求

- **Node.js**: 16 或更高版本
- **包管理器**: npm 8+ 或 yarn 1.22+
- **Git**: 最新版本

### Tauri开发（可选）
- **Rust**: 1.70+ (仅用于桌面应用开发)
- **系统依赖**: 根据平台不同需要不同的系统依赖

## 快速开始

### 1. 克隆项目
```bash
git clone https://github.com/Yarao-Liu/DifyApp.git
cd DifyApp/frontend
```

### 2. 安装依赖
```bash
npm install
# 或者
yarn install
```

### 3. 配置API地址
默认API地址为 `http://localhost:9090`，如需修改，请编辑 `src/config/api.js` 文件。

### 4. 启动开发服务器
```bash
npm run dev
# 或者
yarn dev
```

开发服务器默认运行在 `http://localhost:3000`

### 5. 构建生产版本
```bash
npm run build
# 或者
yarn build
```

构建产物将输出到 `dist/` 目录。

### 6. 预览生产构建
```bash
npm run preview
# 或者
yarn preview
```

## Tauri桌面应用（可选）

### 开发模式
```bash
npm run tauri:dev
# 或者
yarn tauri:dev
```

### 构建桌面应用
```bash
npm run tauri:build
# 或者
yarn tauri:build
```

构建产物将输出到 `src-tauri/target/release/` 目录。

## 配置说明

### Vite配置
主要配置项在 `vite.config.js` 中：
- **开发服务器端口**: 3000
- **API代理**: `/api` 代理到 `http://localhost:9090`
- **代码分割**: 自动分割Vue、Element Plus、Markdown等库
- **构建优化**: 生产环境自动移除console和debugger

### API配置
API基础配置在 `src/config/api.js` 中，可以修改：
- API基础URL
- 请求超时时间
- 其他请求配置

## 功能特性

### Markdown渲染
- 支持标准Markdown语法
- 代码高亮（highlight.js）
- 数学公式渲染（KaTeX）
- 流程图和图表（Mermaid）
- 自定义样式主题

### 主题系统
- 支持深色/浅色主题切换
- VS Code风格深色主题
- 响应式设计

### 状态管理
- 使用Pinia进行状态管理
- 用户状态持久化
- 应用配置管理

## 开发规范

- 遵循Vue 3 Composition API最佳实践
- 使用组合式函数（Composables）封装可复用逻辑
- 组件化开发，保持组件单一职责
- 统一的代码风格和规范
- 使用Element Plus组件库保持UI一致性
- API调用统一通过 `src/api/` 目录下的文件
- 工具函数统一放在 `src/utils/` 目录

## 项目构建优化

### 代码分割
- Vue相关库单独打包
- Element Plus单独打包
- Markdown相关库单独打包
- 工具库单独打包

### 性能优化
- 依赖预构建
- 代码压缩（Terser）
- CSS代码分割
- 资源文件优化

## 常见问题

### 1. API请求失败
检查后端服务是否正常运行，以及API地址配置是否正确。

### 2. 主题切换不生效
清除浏览器缓存或检查主题配置文件。

### 3. Markdown渲染异常
检查相关依赖是否正确安装。

## 贡献指南

欢迎提交Issue和Pull Request来帮助我们改进项目。请确保你的代码符合项目规范。

提交代码前请确保：
- 代码通过ESLint检查
- 遵循Vue 3最佳实践
- 添加必要的注释
- 更新相关文档

## 许可证

本项目采用MIT许可证，详情请见 [LICENSE](LICENSE) 文件。

## 联系方式

如有问题，请通过GitHub Issues与我们联系。
