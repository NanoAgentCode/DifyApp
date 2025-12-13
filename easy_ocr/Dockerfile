# 使用官方 Python 镜像（国内可正常拉取）
FROM python:3.10-slim

# 设置镜像标签
LABEL maintainer="DifyApp"
LABEL description="EasyOCR service for document image recognition"
LABEL version="1.0.0"

# 设置参数
ARG service_home="/home/EasyOCR"
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
            git \
            libglib2.0-0 \
            libsm6 \
            libxext6 \
            libxrender-dev \
            libgl1-mesa-dev \
            build-essential \
            curl \
        && rm -rf /var/lib/apt/lists/* \
        && break || sleep 5; \
    done

# 设置工作目录
WORKDIR /app

# 配置 pip 使用国内镜像源（可选）
ARG USE_CHINA_MIRROR=false
RUN if [ "$USE_CHINA_MIRROR" = "true" ]; then \
        pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/ && \
        pip config set install.trusted-host mirrors.aliyun.com; \
    fi

# 安装 PyTorch（CPU 版，EasyOCR 默认使用 CPU）
# 如果你未来需要 GPU，再考虑 CUDA 版本
RUN pip install --no-cache-dir torch torchvision torchaudio --extra-index-url https://download.pytorch.org/whl/cpu || \
    pip install --no-cache-dir torch torchvision torchaudio

# 克隆 EasyOCR 仓库
RUN mkdir -p "$service_home" \
    && git clone "https://github.com/JaidedAI/EasyOCR.git" "$service_home" \
    && cd "$service_home" \
    && pip install --no-cache-dir -e .

# 安装 API 服务依赖
RUN pip install --no-cache-dir \
    flask \
    flask-cors \
    pillow \
    requests

# 复制 API 服务文件
COPY app.py /app/app.py
COPY requirements.txt /app/requirements.txt

# 安装额外的 Python 依赖
RUN pip install --no-cache-dir -r requirements.txt

# 暴露端口
EXPOSE 8000

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

# 启动服务
CMD ["python", "app.py"]
