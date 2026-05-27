# Agent Factory

## 项目概览

Agent Factory 是一个 **AI Agent 编排平台**，围绕三大支柱构建：

1. **任务定义**（AI 辅助多轮对话澄清意图）
2. **任务投递**（沙箱化执行 + 直跑两种模式）
3. **资源管理**（LLM Provider、API Key、Artifact 制品）

**当前阶段**：V1，4 种 agent 类型（web-scraper / code-analyst / general-purpose / coding-agent）。知识库能力推迟到 V2。

## 三模块架构

```
   frontend (Next.js :3000)
          │  REST + SSE
          ▼
   platform (Spring Boot :8080)  ────► PostgreSQL :5432
          │   │                          MinIO :9000 (S3-compatible)
          │   └─ Feishu WebSocket（双向）
          ▼
   agent-service (FastAPI :8000)  ────► LLM API / 本地 `claude` CLI
          │
          └─ 反向调用 platform `/api/projects`（查项目注册表）
```

各模块一句话职责：
- **platform**：任务/会话/制品的持久化与编排，对外 REST + SSE，对 Feishu 提供机器人交互
- **agent-service**：纯计算层，承接 `/define` 和 `/execute`，调用 LLM 或 Claude Code CLI 完成实际工作
- **frontend**：任务管理、对话式任务定义、实时进度查看、设置面板

## 技术栈速查

| 模块 | 核心栈 |
|------|--------|
| platform | Java 25 · Spring Boot 3.4.4 · Gradle 9.3 · Spring Data JPA/Hibernate · Virtual Threads · WebFlux · Docker Java SDK · AWS S3 SDK · Feishu OApi SDK |
| agent-service | Python 3.13 · FastAPI 0.115 · Uvicorn · litellm 1.63 · hermes-agent ≥0.14 · httpx · beautifulsoup4 · `pip + requirements.txt` |
| frontend | Next.js 16（App Router）· React 19 · TypeScript（strict）· Tailwind v4 · 原生 Fetch（无 UI 库，自建组件） |
| 基础设施 | PostgreSQL 16 · MinIO（S3 兼容）· Docker Compose |

## 模块入口与关键路径

### platform（`/platform`）
- 包根：`com.agentfactory`，入口 `AgentFactoryApp`，端口 8080
- 分层：`controller/` `service/` `repository/` `model/` `dto/` `event/` `config/`
- 关键 service：
  - `TaskExecutionService` —— 虚拟线程执行任务
  - `AgentServiceClient` —— 调用 Python agent-service（重试 3 次，指数退避）
  - `FeishuBotService` —— WebSocket 接消息，异步处理
  - `SandboxService` —— Docker 容器内跑 Python agent
  - `S3StorageService` —— Artifact 存取（MinIO）
  - `SseEmitterService` —— 实时事件推送

### agent-service（`/agent-service`）
- 入口：`main.py`，端口 8000；DTO 内联在 main.py（无独立 schemas 目录）
- 编排链：`orchestrator.py` → `agent_runner.py` / `coding_executor.py` / `task_definer.py`
- 桥接层：`hermes_bridge.py`（同步 Hermes Agent → asyncio）、`project_registry.py`（反向调用 platform）
- 对外端点：`POST /define`（任务澄清对话）、`POST /execute`（执行任务）、`GET /health`

### frontend（`/frontend`）
- App Router：`/src/app/{tasks/new, tasks/[id], settings}/page.tsx`
- API 封装统一在 `/src/lib/api.ts`（所有类型 + fetch 都在这一个文件）
- 组件：`/src/components/sidebar.tsx`
- 主题：深色优先（`--background: #0a0a0b`），Geist 字体

## 关键非显然约定（容易踩坑）

- **认证三层模型**：
  - Frontend → Platform：`Authorization: Bearer <AF_API_KEY>`
  - Platform → Agent-service：`X-Internal-Key: <AF_INTERNAL_KEY>`（默认 `internal-dev-key`）
  - **localhost 免认证**：Platform 对来自 `127.0.0.1` / `::1` 的请求跳过 ApiKeyFilter。这是为了让 agent-service 能反向调用 `/api/projects` 而不必持有 token。**改动认证逻辑务必保留此行为**。

- **Feishu 消息必须异步**：`FeishuBotService` 用虚拟线程异步处理消息。同步处理会让 LLM 调用阻塞 WebSocket 事件循环，导致超时和消息丢失。

- **任务执行用虚拟线程**：`TaskExecutionService` 使用 `Executors.newVirtualThreadPerTaskExecutor()`，适用于 I/O 密集（等 LLM、等沙箱）。不要随意改回固定线程池。

- **沙箱 vs 直跑由 AgentType 决定**：`AgentType.sandboxRequired = true` → `SandboxService`（Docker `python:3.13-slim`，挂卷 `/workspace`，轮询 `output.json`）；`false` → `AgentServiceClient` 直接 HTTP。

- **coding-agent 依赖外部 CLI**：`coding_executor.py` 通过 `subprocess` 调用本机 `claude` 命令，并需要 `gh`（GitHub PR）或 `glab`（GitLab MR）。容器/CI 环境必须预装这些。

- **SSE 协议**：`StreamController` 推送事件类型 = `STEP / COST / ERROR / COMPLETION / CLARIFICATION`，支持 `Last-Event-ID` 断线重连。前端 EventSource 监听 `/api/tasks/{id}/stream`。

- **Provider API Key 加密入库**：通过 `EncryptionService` AES 加密，调用前才解密。新增 provider 不要存明文，加密 key 来自 `AF_ENCRYPTION_KEY`（32 字节）。

- **Artifact 双类型**：`PRIMARY`（任务最终产物，用于导出）+ `SUPPLEMENTARY`（中间文件）。存 MinIO，UUID 寻址。

- **DataInitializer 启动种子**：启动时插入 4 种 agent type。改动种子要同步前端的 agent 类型选项。

- **任务定义状态机**（Feishu 场景）：`IDLE → DEFINING → CONFIRMING → EXECUTING`，单会话 10 轮上限、1 小时过期，带 LRU 去重（200 条）。

## 本地开发启动

```bash
# 一键启动整套（推荐）
docker-compose up --build
# 浏览器访问 http://localhost:3000

# 仅前端热开发（后端用 Docker）
docker-compose up postgres minio platform agent-service
cd frontend && npm install && npm run dev

# 仅后端 platform 开发
cd platform && ./gradlew bootRun
```

**必备环境变量**（项目根目录 `.env`）：
- `OPENAI_API_KEY` / `ANTHROPIC_API_KEY`（agent-service 调用 LLM 用）
- 默认 `AF_API_KEY=dev-api-key-change-me`、`AF_ENCRYPTION_KEY` 已在 docker-compose 里

**端口分配**：3000 前端 / 8080 platform / 8000 agent-service / 5432 PostgreSQL / 9000-9001 MinIO

## 协作注意事项

- **暂无单元测试**：platform 和 agent-service 都没建测试体系。改动后用 `docker-compose up` 起整套手测，重点验证 `/health`、任务 define→execute 全链路、SSE 事件流。
- **TODOS.md 是路线图**：动 agent 类型、设计系统、Dashboard 前先读 `/TODOS.md`。
- **设计文档位置**：`~/.gstack/projects/agent-factory/corvalds-unknown-design-20260417-103911.md`（已审批通过的设计基线）。
- **沟通语言**：项目维护者主要用中文，代码/技术术语保留英文。

---

## Skill routing

When the user's request matches an available skill, ALWAYS invoke it using the Skill
tool as your FIRST action. Do NOT answer directly, do NOT use other tools first.
The skill has specialized workflows that produce better results than ad-hoc answers.

Key routing rules:
- Product ideas, "is this worth building", brainstorming → invoke office-hours
- Bugs, errors, "why is this broken", 500 errors → invoke investigate
- Ship, deploy, push, create PR → invoke ship
- QA, test the site, find bugs → invoke qa
- Code review, check my diff → invoke review
- Update docs after shipping → invoke document-release
- Weekly retro → invoke retro
- Design system, brand → invoke design-consultation
- Visual audit, design polish → invoke design-review
- Architecture review → invoke plan-eng-review
- Save progress, checkpoint, resume → invoke checkpoint
- Code quality, health check → invoke health
