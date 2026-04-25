# 诊断日志系统实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 或 TDD 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 新增只记录错误与性能异常的诊断日志链路，支持 App 上传到 Worker/D1，并提供开发期查询/清理能力。

**架构：** App 端通过轻量 `DiagnosticLogUploader` 复用 Worker URL 和 Token 批量上传；Worker 新增 `/diagnostics/logs/batch`、`/diagnostics/logs`、`/diagnostics/logs/resolve` 路由；D1 使用 `diagnostic_log` 表去重、查询和标记解决。

**技术栈：** Android Kotlin、WorkManager、Cloudflare Workers TypeScript、Cloudflare D1、Vitest、Gradle/JUnit。

---

### 任务 1：Worker/D1 诊断日志接口

**文件：**
- 修改：`worker/src/schema.sql`
- 修改：`worker/src/types.ts`
- 修改：`worker/src/validation.ts`
- 修改：`worker/src/d1.ts`
- 修改：`worker/src/index.ts`
- 测试：`worker/src/diagnostic-logs-index.test.ts`

- [ ] 步骤 1：写 Worker 红灯测试，验证批量写入、查询 open 日志、resolve。
- [ ] 步骤 2：运行 `npm test -- diagnostic-logs-index.test.ts`，预期路由不存在失败。
- [ ] 步骤 3：新增 D1 表、类型、校验、持久化与路由。
- [ ] 步骤 4：运行 Worker 测试通过。

### 任务 2：Android 端上传模型与 Worker

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/data/diagnostics/DiagnosticLogModels.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/diagnostics/DiagnosticLogPayloadCodec.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/diagnostics/DiagnosticLogUploadService.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/diagnostics/DiagnosticLogStore.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/diagnostics/DiagnosticLogUploadWorker.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TrackUploadScheduler.kt`
- 测试：`app/src/test/java/com/wenhao/record/data/diagnostics/*Test.kt`

- [ ] 步骤 1：写 Android 红灯测试，验证 payload 脱敏、失败日志入队、上传成功清理。
- [ ] 步骤 2：运行相关 Gradle 测试，预期缺类失败。
- [ ] 步骤 3：实现最小 App 端队列与上传 Worker。
- [ ] 步骤 4：运行 Android 测试通过。

### 任务 3：错误与性能异常埋点

**文件：**
- 修改：上传 Worker、分析流程、同步诊断刷新、轨迹分析关键路径。

- [ ] 步骤 1：在已有失败分支记录 `ERROR`。
- [ ] 步骤 2：对分析耗时、上传耗时、DB 写入耗时记录 `PERF_WARN`。
- [ ] 步骤 3：运行定向测试与 `:app:compileDebugKotlin`。

### 任务 4：开发查询脚本

**文件：**
- 修改：`worker/package.json`
- 创建：`worker/scripts/query-diagnostics.mjs`

- [ ] 步骤 1：新增查询 open/perf/cleanup 命令。
- [ ] 步骤 2：运行 Node 脚本 dry-run 或帮助输出。
