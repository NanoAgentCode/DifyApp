# DifyApp

一个基于 Spring Boot 的 RESTful API 应用，集成了 PostgreSQL 15 数据库。

## 技术栈

- **Java**: JDK 1.8
- **框架**: Spring Boot 2.6.13
- **数据库**: PostgreSQL 15
- **ORM**: Spring Data JPA / Hibernate
- **构建工具**: Maven

## 项目结构

```
DifyApp/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── github/
│       │           └── app/
│       │               └── dify/
│       │                   ├── DifyAppApplication.java    # 主应用类
│       │                   ├── entity/
│       │                   │   └── User.java              # 用户实体类
│       │                   ├── repository/
│       │                   │   └── UserRepository.java    # 用户数据访问接口
│       │                   ├── service/
│       │                   │   └── UserService.java       # 用户业务逻辑服务
│       │                   └── web/
│       │                       ├── HelloController.java   # 示例控制器
│       │                       └── UserController.java    # 用户 REST API 控制器
│       └── resources/
│           └── application.yml                            # 应用配置文件
└── pom.xml                                                # Maven 配置文件
```

## 环境要求

- JDK 1.8 或更高版本
- Maven 3.6+
- PostgreSQL 15

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd DifyApp
```

### 2. 配置数据库

确保 PostgreSQL 15 已安装并运行，然后创建数据库：

```sql
CREATE DATABASE difyapp;
```

### 3. 配置应用

编辑 `src/main/resources/application.yml`，根据您的环境修改数据库连接配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:15432/difyapp
    username: postgres
    password: 123456
```

### 4. 构建项目

```bash
mvn clean install
```

### 5. 运行应用

```bash
mvn spring-boot:run
```

或者使用打包后的 JAR：

```bash
java -jar target/DifyApp-0.0.1-SNAPSHOT.jar
```

应用启动后，访问 `http://localhost:8081`

## API 文档

### 基础接口

#### Hello World
```
GET /hello
```

**响应示例：**
```
Hello World
```

### 用户管理 API

#### 1. 创建用户

```
POST /api/users
Content-Type: application/json
```

**请求体：**
```json
{
  "username": "testuser",
  "email": "test@example.com"
}
```

**响应示例：**
```json
{
  "id": 1,
  "username": "testuser",
  "email": "test@example.com",
  "createdAt": "2024-01-01T12:00:00"
}
```

#### 2. 获取所有用户

```
GET /api/users
```

**响应示例：**
```json
[
  {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com",
    "createdAt": "2024-01-01T12:00:00"
  }
]
```

#### 3. 根据 ID 获取用户

```
GET /api/users/{id}
```

**响应示例：**
```json
{
  "id": 1,
  "username": "testuser",
  "email": "test@example.com",
  "createdAt": "2024-01-01T12:00:00"
}
```

#### 4. 根据用户名获取用户

```
GET /api/users/username/{username}
```

**响应示例：**
```json
{
  "id": 1,
  "username": "testuser",
  "email": "test@example.com",
  "createdAt": "2024-01-01T12:00:00"
}
```

#### 5. 更新用户

```
PUT /api/users/{id}
Content-Type: application/json
```

**请求体：**
```json
{
  "username": "newusername",
  "email": "newemail@example.com"
}
```

**响应示例：**
```json
{
  "id": 1,
  "username": "newusername",
  "email": "newemail@example.com",
  "createdAt": "2024-01-01T12:00:00"
}
```

#### 6. 删除用户

```
DELETE /api/users/{id}
```

**响应示例：**
```json
{
  "message": "用户删除成功"
}
```

## 错误处理

当发生错误时，API 会返回以下格式的响应：

```json
{
  "error": "错误信息描述"
}
```

常见错误：
- `400 Bad Request`: 请求参数错误或业务逻辑错误
- `404 Not Found`: 资源不存在

## 数据库配置说明

### 自动建表

应用使用 Hibernate 的 `ddl-auto: update` 模式，启动时会自动创建或更新数据库表结构。

### 生产环境建议

在生产环境中，建议：
1. 将 `ddl-auto` 设置为 `validate` 或 `none`
2. 使用 Flyway 或 Liquibase 进行数据库版本管理
3. 关闭 SQL 日志输出（设置 `show-sql: false`）

## 开发

### 运行测试

```bash
mvn test
```

### 打包应用

```bash
mvn clean package
```

打包后的 JAR 文件位于 `target/DifyApp-0.0.1-SNAPSHOT.jar`

## 许可证

本项目采用 MIT 许可证。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

如有问题或建议，请通过 Issue 联系。

