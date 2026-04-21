# Worker Mapbox Config Sync 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让已配置 Worker 地址和上传 Token 的 App 自动从 Worker 拉取并缓存 Mapbox 公共 token。

**架构：** Worker 新增一个沿用现有 Bearer `UPLOAD_TOKEN` 鉴权的只读 `/app-config` 接口，返回受控下发的 `mapboxPublicToken`。Android 端新增配置拉取服务，在保存 Worker 配置后和应用启动时按需同步到本地 `MapboxTokenStorage`，地图层继续复用现有本地缓存读取逻辑。

**技术栈：** Cloudflare Workers + Vitest，Android/Kotlin + org.json + 现有 SharedPreferences 存储。

---

### 任务 1：Worker `app-config` 接口

**文件：**
- 修改：`worker/src/index.ts`
- 修改：`worker/src/types.ts`
- 测试：`worker/src/app-config.test.ts`

- [ ] **步骤 1：编写失败的测试**
- [ ] **步骤 2：运行 Worker 测试验证失败**
- [ ] **步骤 3：实现 `/app-config` 鉴权读取与 JSON 响应**
- [ ] **步骤 4：运行 Worker 测试验证通过**

### 任务 2：Android 端配置拉取服务

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/data/tracking/WorkerAppConfigService.kt`
- 测试：`app/src/test/java/com/wenhao/record/data/tracking/WorkerAppConfigServiceTest.kt`

- [ ] **步骤 1：编写失败的测试**
- [ ] **步骤 2：运行 Android 单测验证失败**
- [ ] **步骤 3：实现请求、鉴权、JSON 解析和结果映射**
- [ ] **步骤 4：运行 Android 单测验证通过**

### 任务 3：接入自动同步流程

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt`
- 修改：`worker/README.md`

- [ ] **步骤 1：在保存 Worker 配置后自动拉取并缓存 Mapbox token**
- [ ] **步骤 2：在应用启动时按需补拉 token**
- [ ] **步骤 3：更新 Worker 配置文档**
- [ ] **步骤 4：运行针对性测试验证通过**
