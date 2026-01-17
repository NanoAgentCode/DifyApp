# 多阶段构建：构建阶段
FROM node:20-alpine AS build

# 设置工作目录
WORKDIR /app

ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

# 复制package文件（用于依赖缓存）
COPY package*.json ./
COPY yarn.lock* ./

# 安装依赖
RUN npm ci --only=production=false || npm install

# 复制源代码
COPY . .

# 构建应用
RUN npm run build

# 运行阶段：使用Nginx服务静态文件
FROM nginx:alpine

# 设置维护者信息
LABEL maintainer="DifyApp"
LABEL description="DifyApp Frontend Service"

# 复制构建产物到nginx目录
COPY --from=build /app/dist /usr/share/nginx/html

# 复制nginx配置文件
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1

# 启动nginx
CMD ["nginx", "-g", "daemon off;"]

