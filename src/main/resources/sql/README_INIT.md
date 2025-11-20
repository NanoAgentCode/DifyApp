# 数据库初始化说明

## 方式一：使用SQL脚本初始化（推荐用于生产环境）

### 步骤：

1. **创建数据库**
   ```bash
   # 连接到PostgreSQL
   psql -U postgres -h localhost -p 15432
   
   # 创建数据库
   CREATE DATABASE difyapp;
   
   # 退出
   \q
   ```

2. **执行初始化脚本**
   ```bash
   # 执行SQL脚本
   psql -U postgres -h localhost -p 15432 -d difyapp -f src/main/resources/sql/init_database.sql
   ```

   或者在psql中：
   ```sql
   \c difyapp
   \i src/main/resources/sql/init_database.sql
   ```

3. **验证初始化结果**
   ```sql
   -- 查看用户表
   SELECT * FROM "SYS_USER";
   
   -- 查看表结构
   \d "SYS_USER"
   \d "AI_APP"
   ```

## 方式二：使用Hibernate自动创建（推荐用于开发环境）

### 步骤：

1. **确保application.yml配置正确**
   ```yaml
   spring:
     jpa:
       hibernate:
         ddl-auto: update  # 自动创建/更新表结构
   ```

2. **启动应用**
   ```bash
   mvn spring-boot:run
   ```

3. **应用启动后会自动：**
   - 创建/更新数据库表结构
   - 创建默认管理员账户（如果不存在）
     - 用户名：`admin`
     - 密码：`admin123`

## 默认管理员账户

- **用户名**: `admin`
- **密码**: `admin123`
- **角色**: 管理员（role=1）
- **状态**: 已激活（status=1）

**⚠️ 重要提示：生产环境请务必修改默认管理员密码！**

## 数据库表说明

### SYS_USER表
- 存储用户信息
- 包含用户名、密码（BCrypt加密）、角色、状态等字段
- 注意：表名使用SYS_USER而不是USER，因为USER是PostgreSQL的保留关键字

### AI_APP表
- 存储AI应用信息
- 包含应用名称、类型、配置、API Key等字段

## 注意事项

1. **密码加密**
   - 密码使用BCrypt算法加密存储
   - BCrypt每次加密结果不同，这是正常现象
   - SQL脚本中的密码哈希是示例值，实际使用时应用会自动生成

2. **表名大小写**
   - PostgreSQL中表名使用双引号包裹以保持大小写敏感
   - 查询时也需要使用双引号：`SELECT * FROM "SYS_USER"`
   - 注意：表名使用SYS_USER而不是USER，因为USER是PostgreSQL的保留关键字

3. **索引**
   - 脚本已创建必要的索引以提高查询性能
   - 包括用户名、状态、角色等常用查询字段的索引

4. **数据初始化**
   - 如果使用SQL脚本，会插入默认管理员账户
   - 如果使用Hibernate自动创建，`DataInitializer`会在启动时创建管理员账户
   - 两种方式都会检查账户是否已存在，避免重复创建

## 故障排查

### 问题1：表已存在错误
**解决**：脚本使用`DROP TABLE IF EXISTS`，会先删除表再创建。如果表中有重要数据，请先备份。

### 问题2：密码无法登录
**解决**：
- 如果使用SQL脚本，密码哈希可能不正确，建议使用应用启动时的自动创建
- 或者重置密码：删除admin用户，重启应用让DataInitializer重新创建

### 问题3：权限不足
**解决**：确保PostgreSQL用户有创建数据库和表的权限
```sql
-- 授予权限
GRANT ALL PRIVILEGES ON DATABASE difyapp TO postgres;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
```

