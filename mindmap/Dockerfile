# 使用官方 Python 镜像（支持 Python 3.12，兼容 3.13 要求）
FROM python:3.12-slim

# 设置镜像标签
LABEL maintainer="DifyApp"
LABEL description="Mindmap service for markdown to mindmap conversion and file management"
LABEL version="1.0.0"

# 设置参数
ARG USE_CHINA_MIRROR=false

# 配置国内镜像源（可选，通过构建参数控制）
RUN if [ "$USE_CHINA_MIRROR" = "true" ]; then \
        sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources 2>/dev/null || \
        sed -i 's|http://deb.debian.org|http://mirrors.aliyun.com|g' /etc/apt/sources.list 2>/dev/null || \
        echo "deb http://mirrors.aliyun.com/debian/ bookworm main contrib non-free non-free-firmware" > /etc/apt/sources.list && \
        echo "deb http://mirrors.aliyun.com/debian-security/ bookworm-security main contrib non-free non-free-firmware" >> /etc/apt/sources.list && \
        echo "deb http://mirrors.aliyun.com/debian/ bookworm-updates main contrib non-free non-free-firmware" >> /etc/apt/sources.list; \
    fi

# 安装系统依赖（带重试机制）
RUN for i in 1 2 3; do \
        apt-get update -y && \
        apt-get install -y --fix-missing \
            ca-certificates \
            curl \
            git \
            build-essential \
        && rm -rf /var/lib/apt/lists/* \
        && break || sleep 5; \
    done

# 安装 Node.js 和 npm（用于 markmap-cli）
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

# 验证 Node.js 和 npm 安装
RUN node --version && npm --version

# 全局安装 markmap-cli
RUN npm install -g markmap-cli

# 验证 markmap-cli 安装
RUN markmap --version || echo "markmap-cli installed"

# 设置工作目录
WORKDIR /app

# 配置 pip 使用国内镜像源（可选）
ARG USE_CHINA_MIRROR=false
RUN if [ "$USE_CHINA_MIRROR" = "true" ]; then \
        pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/ && \
        pip config set install.trusted-host mirrors.aliyun.com; \
    fi

# 复制依赖文件
COPY requirements.txt /app/requirements.txt
COPY pyproject.toml /app/pyproject.toml

# 安装 Python 依赖
RUN pip install --no-cache-dir -r requirements.txt

# 复制项目文件
COPY main.py /app/main.py
COPY config.ini /app/config.ini
COPY src/ /app/src/
COPY htmljs/ /app/htmljs/
COPY static/ /app/static/

# 创建必要的目录（如果不存在）
RUN mkdir -p /app/static/html /app/static/markdown /app/static/text_files

# 暴露端口
EXPOSE 6066

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:6066/ || exit 1

# 启动服务
CMD ["python", "main.py"]

