# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

DifyApp is an enterprise AI application platform built with Spring Boot 3.5 (Java 17) backend and Vue 3 frontend. It provides AI-powered chat, knowledge base management (RAG), document reading, AI drawing, and multi-tenant administration. The project integrates LLMs via Dify API, supports multiple vector databases (Chroma/FAISS/Milvus/Qdrant/Weaviate/PgVector/ES), and uses LangChain4j for retrieval-augmented generation.

## Build & Run Commands

### Backend
```bash
cd backend
mvn clean install          # Build
mvn spring-boot:run        # Run (default profile, port 9090)
mvn clean package          # Package JAR
```
- Profiles: `dev`, `prod`, `hikari`, `static`, `mcp-rag` (see `application-{profile}.yml`)
- Database init: `backend/src/main/resources/sql/init_database_complete.sql`
- API docs: http://localhost:9090/swagger-ui.html

### Frontend
```bash
cd frontend
npm install                # Install dependencies
npm run dev                # Dev server (port 3000)
npm run build              # Production build → dist/
```
- API base URL config: `frontend/src/config/api.js`

### Docker
```bash
docker compose up -d       # Full stack (backend + frontend + postgres + rustfs)
```

### Auxiliary Services (optional, separate Docker deployments)
- **OCR**: `easy_ocr/docker-compose.yml` (Python/EasyOCR, port 8000)
- **Mind map**: `mindmap/docker-compose.yml` (Python/FastAPI)
- **Object storage**: `rustfs/docker-compose.yml` (S3-compatible, port 9000/9001)
- **Dependencies**: `docker-compose.dependencies.yml` (all external services)

## Architecture

### Backend: `com.github.app.dify`

MVC module-per-feature layout under `backend/src/main/java/com/github/app/dify/`. Each business module follows the same internal structure:

```
module/
├── controller/     # REST endpoints (@RestController, @RequestMapping("/api/..."))
├── service/        # Interfaces
│   └── impl/       # Implementations
├── domain/         # JPA entities (extend common.domain.BaseEntity)
├── repository/     # Spring Data JPA repositories
├── req/            # Request DTOs (JSR-303 validation)
├── resp/           # Response DTOs (use common.resp.PageResponse<T> for pagination)
└── util/           # Module-specific utilities
```

**14 core modules:** `auth`, `chat`, `knowledgebase`, `documentreader`, `system`, `permission`, `analytics` (statistics + analysis/Neo4j), `model`, `datasource`, `memory`, `memo`, `mcp`, `ops` (userlog + observability + trace), `common`.

**Key shared infrastructure in `common`:**
- `BaseController` — provides `getUserId(HttpServletRequest)` for auth
- `GlobalExceptionHandler` — unified exception → `ApiResponse` formatting
- `BaseEntity` — all JPA entities extend this for audit fields
- `ApiResponse` / `PageResponse<T>` — unified response wrappers

**Notable subsystems:**
- `knowledgebase.langchain4j.store` — vector store implementations per DB type
- `knowledgebase.service.chunking` / `strategy` — document chunking strategies by file type
- `mcp` — MCP protocol integrations: `browsersearch` (web search strategies), `time`, `location`, `realtime`
- `ops.trace` — main + step-level tracing with async write, ES-backed
- `ops.userlog` — AOP-based behavior logging to Elasticsearch
- `ops.observability` — observability annotations, aspects, and REST API

### Frontend: `frontend/src/`

Vue 3 + Composition API (`<script setup>`), Element Plus UI, Pinia stores, Axios HTTP.

```
src/
├── api/              # Per-module API wrappers (chat.js, knowledgeBase.js, etc.)
├── composables/      # Reusable composition functions (useChat, useSSEStream, useMarkdown, etc.)
├── views/            # Page components organized by role: admin/, user/, app/, auth/, observability/
├── components/       # Shared components: common/, chat/, documentReader/, portal/, workflow/
├── stores/           # Pinia: app.js, user.js
├── router/           # Vue Router config
├── utils/            # request.js (Axios wrapper), logger, themes, helpers
├── styles/           # Design tokens (design-tokens.css), enterprise-base.css
└── config/           # API base URL configuration
```

**Frontend-backend API alignment:** Controller paths (`/api/...`) must match `src/api/*.js` endpoint definitions. Request DTO fields (camelCase) must align between frontend API calls and backend `req` classes.

## Coding Conventions

### Backend (from .cursor/skills)
- Controllers: `@RestController` + `@RequestMapping`, inherit `BaseController`, use `@Validated @RequestBody`, return `ResponseEntity<RespType>`
- Add `@Tag(name=...)` and `@Operation(summary=...)` for Swagger docs
- Auth: use `getUserId(httpRequest)` from BaseController for user identification
- Exceptions: let `GlobalExceptionHandler` handle; don't catch in controllers
- Jakarta namespace (`jakarta.servlet`, `jakarta.validation`)

### Frontend (from .cursor/skills)
- Components: `<script setup>` + `<template>` + `<style scoped>`, PascalCase naming
- Use design tokens (`var(--color-primary)`, `var(--spacing-md)`) — never hardcode colors/spacing
- API calls: encapsulate in `src/api/`, consume via composables or components using the `request` wrapper
- Reuse existing composables: `useApiCache`, `useSSEStream`, `useErrorHandler`, `useResponseHandler`

### Git Commits
Conventional Commits format: `<type>(<scope>): <subject>`
- Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`
- Scope examples: `frontend`, `backend`, `chat`, `knowledge-base`
- Subject: concise, no trailing period

## Design Documents

Detailed specs in `backend/doc/` — consult these for feature-specific design:
- System overview, auth, chat, knowledge base, document reader, memory, observability, user logs
- Front-end style guide: `backend/doc/前端样式规范.md`

## Agent Skills

`agent_skills/` contains standalone skill definitions (algorithmic-art, canvas-design, doc-coauthoring, xlsx, pdf, etc.) — these are independent of the core application.
