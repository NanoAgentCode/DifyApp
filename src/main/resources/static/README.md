# Dify应用管理前端

基于Vue3 + Element Plus的前端项目，包含管理端和应用端。

## 技术栈

- Vue 3.3+
- Vue Router 4
- Pinia (状态管理)
- Element Plus 2.4+
- Axios
- Vite 5

## 项目结构

```
static/
├── src/
│   ├── api/              # API接口
│   ├── components/       # 公共组件
│   ├── layouts/          # 布局组件
│   ├── router/           # 路由配置
│   ├── utils/            # 工具函数
│   ├── views/            # 页面组件
│   │   ├── admin/        # 管理端页面
│   │   └── app/          # 应用端页面
│   ├── App.vue           # 根组件
│   └── main.js           # 入口文件
├── index.html            # HTML模板
├── package.json          # 项目配置
├── vite.config.js        # Vite配置
└── README.md             # 说明文档
```

## 安装依赖

使用 yarn 管理依赖（推荐）：

```bash
cd src/main/resources/static
yarn install
```

或使用 npm：

```bash
npm install
```

## 开发

```bash
yarn dev
# 或
npm run dev
```

访问 http://localhost:3000

## 构建

```bash
yarn build
# 或
npm run build
```

构建产物在 `dist` 目录。

## 功能模块

### 管理端 (/admin)

- **应用列表** (`/admin/apps`)
  - 查看所有应用
  - 创建、编辑、删除应用
  - 查看应用详情
  - 使用应用

- **创建/编辑应用** (`/admin/apps/create`, `/admin/apps/edit/:id`)
  - 配置应用基本信息
  - 设置Dify API Key
  - 配置流式响应

- **应用详情** (`/admin/apps/detail/:id`)
  - 查看应用完整信息

### 应用端 (/app)

- **聊天应用** (`/app/chat/:id`)
  - Chat Flow交互界面
  - 支持流式和非流式响应
  - 消息历史记录

- **工作流应用** (`/app/workflow/:id`)
  - Workflow输入输出界面
  - 支持流式和非流式响应
  - 结果展示

## API配置

API请求默认代理到 `http://localhost:8081`，可在 `vite.config.js` 中修改。

## 注意事项

1. 确保后端服务运行在 8081 端口
2. 流式响应需要浏览器支持 EventSource 或 Fetch Stream API
3. 生产环境需要配置正确的API地址

