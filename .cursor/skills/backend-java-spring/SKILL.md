---
name: backend-java-spring
description: Java 17 + Spring Boot 3 后端开发规范与常用模式。在编写或修改 Controller、Service、req/resp DTO、异常处理、数据库访问时使用。适用于 backend 目录下 Java 代码、REST 接口、业务逻辑相关任务。
---

# Java/Spring 后端开发

## 技术栈

- Java 17
- Spring Boot 3.x（spring-boot-starter-web）
- Jakarta 命名空间（jakarta.servlet、jakarta.validation）
- 数据库：PostgreSQL / MySQL / Oracle / MongoDB（按需）

## 包与分层

- `controller` - REST 接口，仅做参数校验与调用 Service
- `service` / `service.impl` - 业务逻辑
- `req` - 请求 DTO（如 `CreateConversationRequest`）
- `resp` - 响应 DTO（如 `ChatConversationResponse`）
- `common.controller.BaseController` - 公共方法（如 `getUserId(HttpServletRequest)`）
- `common.controller.GlobalExceptionHandler` - 统一异常与 `ApiResponse` 格式

## Controller 约定

- 使用 `@RestController` + `@RequestMapping("/api/...")`，路径与前端 `src/api/` 对应
- 继承 `BaseController` 时可用 `getUserId(httpRequest)` 等
- 入参：`@Validated @RequestBody ReqType req` 或 `@RequestParam` / `@PathVariable`
- 返回：`ResponseEntity<RespType>`，成功用 `ResponseEntity.ok(response)`
- 为 Swagger 添加 `@Tag(name = "模块名")`、`@Operation(summary = "接口说明")`

## 请求与响应

- 请求体使用 `req` 包下 DTO，配合 `@Validated` 与 JSR-303 注解（如 `@NotBlank`、`@NotNull`）
- 响应体使用 `resp` 包下 DTO；分页使用 `common.resp.PageResponse<T>`
- 异常由 `GlobalExceptionHandler` 统一转成 `ApiResponse`，Controller 不直接 catch 业务异常

## 新增接口检查清单

- [ ] Controller 路径是否与前端 API 模块一致（如 `/api/chat/history`）
- [ ] 是否已定义 req/resp 并放在对应 req、resp 包下
- [ ] 需要登录的接口是否通过 `getUserId(httpRequest)` 等做鉴权
- [ ] 是否补充了 `@Tag`、`@Operation` 便于文档生成
