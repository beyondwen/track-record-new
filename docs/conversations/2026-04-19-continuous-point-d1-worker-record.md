# 2026-04-19 对话纪要：持续采点、D1 上云、Worker 清理与手机安装

## 1. 记录目的

本文用于整理 2026-04-19 这轮围绕「持续采点位 + 本地分析 + Cloudflare Worker + D1」展开的对话过程，记录本次会话中的产品判断、代码改动、线上配置、验证结果与当前落地状态，作为后续继续迭代和跨设备续开发的依据。

## 2. 本次会话的核心结论

本轮会话最终收敛为以下几条结论：

1. App 对外语义不再使用「自动开始记录 / 自动结束记录」，而是统一为「持续采点位，后续由算法分析动态段和静止段」。
2. 端侧主链路调整为：
   - 后台持续采点位
   - 本地算法切分动态段 / 静止段
   - 原始点位、分析结果、历史结果自动上传到 Cloudflare Worker
   - Worker 写入 Cloudflare D1
3. 数据库方案不再使用 MySQL，已全量切换到 D1。
4. Worker 侧历史遗留的训练样本路由和旧表已经清理，只保留当前业务真正使用的表与接口。

## 3. 产品思路的变化

### 3.1 从「开始 / 结束记录」转为「持续采点 + 延迟分析」

对话中再次确认，当前产品思路不再是：

- 判断什么时候开始记录
- 判断什么时候结束记录

而是：

- 后台持续采集点位
- 后续再通过算法分析哪些区间属于动态段，哪些区间属于静止段

这意味着：

- 用户层面不再需要感知「开始」和「结束」按钮
- `START / STOP` 如果继续存在，也只应保留为内部算法切段事件，不再直接暴露到 UI

### 3.2 UI 语义统一

基于上述判断，本轮对首页、状态弹窗、历史纠错和反馈提示做了文案清理。

已统一为以下表达：

- 持续采点
- 动态段
- 静止段
- 切段判定
- 动态段起点 / 动态段终点

不再对用户展示：

- 自动开始记录
- 自动结束记录
- 手动开始记录
- 手动结束记录

## 4. App 能力收敛结果

围绕「冗余功能清理」反复确认后，最终保留的能力为：

- 自动采点 / 自动分析
- 历史查看
- Mapbox Token 配置
- Worker 配置
- 测试 Worker 连通性
- 检查更新

删除或停用的入口包括：

- 手动记录入口
- 手动上传样本
- 手动上传历史
- 历史导入 / 导出
- 训练样本导出
- 决策模型导入
- 第三个 Tab

## 5. 云端方案的变化

### 5.1 数据存储从 MySQL 切到 D1

用户最初提出希望通过 Worker 写入数据库，之后又明确要求切换为 Cloudflare D1，并要求「不要有残留」。

当前线上配置为：

- Worker 地址：`https://track-record-worker.beyondlenovo.workers.dev`
- D1 数据库名：`track-record`
- D1 `database_id`：`917118ea-57f5-4483-aec9-3dc83758c9e2`

### 5.2 Worker 鉴权配置

本轮会话中已完成 Worker Secret 配置：

- Secret 名：`UPLOAD_TOKEN`
- 当前线上值：`9dfe8ffe84d661b35bc3664143e0a44a1ec4f164eb13411f`

### 5.3 当前保留的 Worker 路由

当前 Worker 仅保留以下 3 个业务接口：

- `POST /raw-points/batch`
- `POST /analysis/batch`
- `POST /histories/batch`

已移除：

- `POST /samples/batch`

## 6. 数据链路当前真实状态

本轮会话结束后，当前完整链路已经是：

1. `BackgroundTrackingService` 在后台持续采点位。
2. 点位写入本地连续轨迹存储。
3. 本地分析器对点位窗口做切段分析。
4. 分析结果投影为历史记录。
5. App 通过 Worker 自动上传以下 3 类数据：
   - 原始点位（raw points）
   - 分析结果（analysis segments / stay clusters）
   - 历史结果（histories）
6. Worker 将上述数据写入 D1。

## 7. 本轮新增与清理的关键实现

### 7.1 App 侧新增历史自动上传

之前自动链路只覆盖：

- 原始点位上传
- 分析结果上传

本轮新增：

- `HistoryUploadWorker`

新增后，历史结果会通过以下方式自动上云：

- 应用启动时由 `TrackUploadScheduler.ensureScheduled()` 注册周期任务
- 每次分析结果写入本地历史后，由 `BackgroundTrackingService` 触发一次即时同步

关键文件：

- [HistoryUploadWorker.kt](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/data/history/HistoryUploadWorker.kt)
- [TrackUploadScheduler.kt](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/data/tracking/TrackUploadScheduler.kt)
- [BackgroundTrackingService.kt](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt)

### 7.2 Worker 侧清理旧样本链路

本轮已删除与当前架构不再匹配的旧训练样本链路，包括：

- `/samples/batch` 路由
- `training_samples` 表
- 对应的类型定义、验证逻辑和 D1 持久化代码
- 对应旧测试

关键文件：

- [worker/src/index.ts](/Users/a555/StudioProjects/track-record-new/worker/src/index.ts)
- [worker/src/d1.ts](/Users/a555/StudioProjects/track-record-new/worker/src/d1.ts)
- [worker/src/types.ts](/Users/a555/StudioProjects/track-record-new/worker/src/types.ts)
- [worker/src/validation.ts](/Users/a555/StudioProjects/track-record-new/worker/src/validation.ts)
- [worker/src/schema.sql](/Users/a555/StudioProjects/track-record-new/worker/src/schema.sql)

### 7.3 UI 文案清理

本轮完成了用户可见语义的统一，主要改动文件包括：

- [HistoryController.kt](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/history/HistoryController.kt)
- [MainActivity.kt](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt)
- [strings.xml](/Users/a555/StudioProjects/track-record-new/app/src/main/res/values/strings.xml)
- [strings_compose_dashboard.xml](/Users/a555/StudioProjects/track-record-new/app/src/main/res/values/strings_compose_dashboard.xml)
- [strings_compose_dashboard_dialog.xml](/Users/a555/StudioProjects/track-record-new/app/src/main/res/values/strings_compose_dashboard_dialog.xml)
- [strings_compose_dashboard_ui.xml](/Users/a555/StudioProjects/track-record-new/app/src/main/res/values/strings_compose_dashboard_ui.xml)
- [strings_compose_history.xml](/Users/a555/StudioProjects/track-record-new/app/src/main/res/values/strings_compose_history.xml)

## 8. D1 当前表结构状态

会话中实际查询了线上 D1 表清单，并进行了清理。

清理前存在的业务表包括：

- `training_samples`
- `raw_location_point`
- `analysis_segment`
- `stay_cluster`
- `uploaded_histories`

本轮已删除：

- `training_samples`

当前线上 D1 中保留的业务表为：

- `raw_location_point`
- `analysis_segment`
- `stay_cluster`
- `uploaded_histories`

此外还会看到系统表：

- `_cf_KV`
- `sqlite_sequence`

## 9. Worker 与 D1 的实际执行记录

本轮会话中已实际完成并验证过以下操作：

- `npx wrangler whoami`
- `npx wrangler d1 list`
- `npx wrangler d1 execute track-record --remote --file=src/schema.sql`
- `npx wrangler secret put UPLOAD_TOKEN`
- `npx wrangler deploy`
- 查询线上 D1 表清单
- 删除线上旧表 `training_samples`

本轮最新 Worker 部署结果：

- 线上地址：`https://track-record-worker.beyondlenovo.workers.dev`
- 最新版本 ID：`82211933-2603-4445-a152-8f37244ee978`

## 10. Android 侧验证记录

本轮已实际通过的验证包括：

- `:app:testDebugUnitTest --tests com.wenhao.record.ui.history.HistoryControllerTest`
- `:app:testDebugUnitTest --tests com.wenhao.record.data.history.HistoryUploadWorkerTest`
- `:app:assembleDebug`

Worker 侧已通过的验证包括：

- `npm test -- cleanup-index.test.ts d1.test.ts history-index.test.ts raw-point-index.test.ts analysis-index.test.ts`

## 11. 手机安装记录

本轮会话中通过 ADB 将最新版 Debug 包安装到一加手机，并确认应用可以正常拉起。

设备连接过程中曾出现：

- 旧 `ip:port` 失效
- mDNS 别名链路不稳定

最终成功使用的新设备地址为：

- `192.168.100.197:42795`

最新安装结果为：

- 包名：`com.wenhao.record`
- 版本：`1.0.23`
- 安装时间：`2026-04-19 22:47:00`

## 12. 当前可对外确认的系统状态

截至本次会话结束，当前系统可以确认的状态如下：

- App 已改为持续采点位，不再以「开始 / 结束记录」作为对外语义
- 本地会自动做动态段 / 静止段分析
- 原始点位、分析结果、历史结果都会自动走 Worker 上传
- Worker 已写入 D1，而非 MySQL
- 旧训练样本链路已从 Worker 和 D1 中清理
- 最新 APK 已安装到手机

## 13. 换电脑续开发所需的最小信息

如果后续在另一台电脑继续开发 Worker，只需要确认以下信息一致即可续上：

- Worker 名称：`track-record-worker`
- Worker 地址：`https://track-record-worker.beyondlenovo.workers.dev`
- D1 数据库名：`track-record`
- D1 `database_id`：`917118ea-57f5-4483-aec9-3dc83758c9e2`
- Secret：`UPLOAD_TOKEN`

建议续开发前先执行：

- `npx wrangler whoami`
- `npx wrangler d1 list`

## 14. 后续建议

虽然本轮主链路已经打通，但仍建议后续继续补以下内容：

1. 增加历史上传的端到端可视化诊断，便于直接在 App 中确认 `histories` 是否已成功上云。
2. 为 D1 增加更面向分析和导出的查询脚本，方便后续模型训练和数据核查。
3. 视数据规模决定是否继续拆分 `uploaded_histories` 的结构，避免长期把点位完整塞在 `points_json` 中。

## 15. 相关文档

本轮会话与以下设计 / 实现文档直接相关：

- [持续采点与分析设计](/Users/a555/StudioProjects/track-record-new/docs/superpowers/specs/2026-04-19-continuous-point-stream-analysis-design.md)
- [持续采点实现计划](/Users/a555/StudioProjects/track-record-new/docs/superpowers/plans/2026-04-19-continuous-point-stream-analysis-implementation.md)
- [Worker 数据同步设计（旧 MySQL 阶段）](/Users/a555/StudioProjects/track-record-new/docs/superpowers/specs/2026-04-19-worker-mysql-batch-sync-design.md)
- [Worker 数据同步实现计划（旧 MySQL 阶段）](/Users/a555/StudioProjects/track-record-new/docs/superpowers/plans/2026-04-19-worker-mysql-batch-sync-implementation.md)
