# RustFS Docker 部署指南

RustFS 是一个高性能的分布式对象存储系统，100% 兼容 S3 协议，可以作为 MinIO 的替代品。

## 特性

- ✅ 100% S3 协议兼容
- ✅ 高性能（基于 Rust 开发）
- ✅ 内存安全
- ✅ 商业友好（Apache 2.0 许可证）
- ✅ 跨平台支持
- ✅ 云原生部署

## 快速开始

### 1. 创建数据目录

```bash
mkdir -p rustfs/data
# 设置目录权限（RustFS容器以uid 10001运行）
chown -R 10001:10001 rustfs/data
```

### 2. 启动服务

```bash
cd rustfs
docker-compose up -d
```

### 3. 验证部署

访问 RustFS 控制台（如果启用了控制台）：
- URL: http://localhost:9001
- 用户名: `rustfsadmin`
- 密码: `rustfsadmin`

或者使用 S3 客户端测试：

```bash
# 使用 MinIO Client (mc)
mc alias set rustfs http://localhost:9000 rustfsadmin rustfsadmin
mc mb rustfs/mybucket
mc ls rustfs
```

## 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `RUSTFS_ACCESS_KEY` | 访问密钥 | `rustfsadmin` |
| `RUSTFS_SECRET_KEY` | 密钥 | `rustfsadmin` |
| `RUSTFS_CONSOLE_ENABLE` | 启用控制台 | `true` |
| `RUSTFS_ADDRESS` | 监听地址 | `:9000` |
| `RUSTFS_SERVER_DOMAINS` | 服务器域名（虚拟主机模式） | - |

### 端口说明

- **9000**: S3 API 端口（必需）
- **9001**: 控制台端口（可选，需要启用 `RUSTFS_CONSOLE_ENABLE=true`）

### 数据持久化

数据存储在 `./data` 目录中，请确保：
1. 目录有足够的磁盘空间
2. 目录权限正确（uid:gid = 10001:10001）
3. 定期备份数据目录

## 与 DifyApp 集成

### 1. 修改后端配置

编辑 `backend/src/main/resources/application.yml`：

```yaml
# MinIO配置（RustFS兼容MinIO配置）
minio:
  endpoint: http://localhost:9000
  access-key: rustfsadmin
  secret-key: rustfsadmin
  bucket-name: knowledge-base
```

### 2. 创建 Bucket

使用控制台或命令行创建 bucket：

```bash
# 使用 mc 客户端
mc mb rustfs/knowledge-base

# 或使用 curl
curl -X PUT http://localhost:9000/knowledge-base \
  -H "Authorization: AWS rustfsadmin:$(echo -n 'rustfsadmin' | base64)"
```

## 生产环境建议

### 1. 修改默认密钥

```yaml
environment:
  - RUSTFS_ACCESS_KEY=your-secure-access-key
  - RUSTFS_SECRET_KEY=your-secure-secret-key
```

### 2. 启用 TLS

```yaml
volumes:
  - ./data:/data
  - ./certs:/certs
environment:
  - RUSTFS_TLS_PATH=/certs
```

### 3. 多节点部署

对于生产环境，建议使用多节点多磁盘（MNMD）模式，参考 [RustFS 官方文档](https://docs.rustfs.com/installation/linux/multiple-node-multiple-disk.html)。

### 4. 监控和日志

- 查看容器日志：`docker logs rustfs-server`
- 查看健康状态：`docker ps`
- 监控指标：RustFS 支持 Prometheus 指标导出

## 常用命令

```bash
# 启动服务
docker-compose up -d

# 停止服务
docker-compose down

# 查看日志
docker-compose logs -f rustfs

# 重启服务
docker-compose restart rustfs

# 查看容器状态
docker ps | grep rustfs
```

## 故障排查

### 1. 权限问题

如果遇到权限错误，确保数据目录的所有者是 uid 10001：

```bash
chown -R 10001:10001 ./data
```

### 2. 端口冲突

如果 9000 或 9001 端口被占用，修改 `docker-compose.yml` 中的端口映射：

```yaml
ports:
  - "9002:9000"  # 将主机端口改为 9002
  - "9003:9001"  # 将主机端口改为 9003
```

### 3. 连接失败

检查防火墙设置，确保端口已开放：

```bash
# Linux
sudo ufw allow 9000/tcp
sudo ufw allow 9001/tcp
```

## 更多信息

- [RustFS 官方文档](https://docs.rustfs.com/)
- [RustFS GitHub](https://github.com/rustfs/rustfs)
- [Docker 安装指南](https://docs.rustfs.com/installation/docker/)
