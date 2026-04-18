# 训练样本上传到 Cloudflare Worker 的设计

## 背景

当前训练样本保存在本地数据库中，并可由 App 导出为 JSON Lines 文件。用户希望直接在 App 内手动上传样本到公网服务，再由服务写入 MySQL，用于后续模型训练与分析。

约束如下：

- 仅个人使用，但仓库后续可能公开。
- 不希望 App 直连公网 MySQL。
- 不希望额外维护常驻中转服务。
- 上传功能先以手动触发为主，不做自动上传。
- 上传配置需要在客户端输入并保存在本机，不打进 APK。

## 目标

在当前仓库中同时实现 Android 端与 Cloudflare Worker 端能力，使用户可以在 App 的关于页中：

- 配置 Worker 地址与上传 Token。
- 手动上传全部未上传的训练样本。
- 看到明确的上传状态反馈。

Cloudflare Worker 负责鉴权、数据校验与写入 MySQL，并基于 `eventId` 做幂等去重。

## 非目标

本次不实现以下能力：

- 自动上传或后台定时同步。
- 上传进度条、断点续传、失败重试队列。
- 服务端管理后台。
- 训练样本字段的在线特征拆列。
- 多用户、多租户或复杂权限系统。

## 现状分析

当前 App 已具备以下基础：

- 关于页已经支持客户端输入并保存 Mapbox Token。
- 训练样本已可由 `TrainingSampleExporter` 导出为结构化行对象。
- 导出编码器 `TrainingSampleExportCodec` 已可将样本编码为 JSON。
- 训练样本的核心主键为 `eventId`，适合用作服务端幂等键。

当前数据模型 `TrainingSampleRow` 已包含：

- 事件标识：`eventId`
- 关联记录：`recordId`
- 时间：`timestampMillis`、`manualStartAt`、`manualStopAt`
- 判定上下文：`phase`、`isRecording`、`finalDecision`
- 分数：`startScore`、`stopScore`
- Gate 状态：`gpsQualityPass`、`motionEvidencePass`、`frequentPlaceClearPass`
- 反馈：`feedbackEligible`、`feedbackBlockedReason`、`feedbackLabel`
- 元特征：`features`
- 会话来源：`startSource`、`stopSource`

因此，本次不需要重做样本采集，只需要补足上传链路与已上传状态管理。

## 方案对比

### 方案 A：App 直连公网 MySQL

优点：

- 服务端代码最少。

缺点：

- 数据库账号必须进入客户端。
- MySQL 需要暴露公网端口。
- 移动端网络环境下连接可靠性差。
- 未来表结构变更会直接影响客户端。
- 安全性与可维护性最差。

结论：

- 不采用。

### 方案 B：App 导出文件，再人工上传

优点：

- 实现最简单。
- 服务端耦合低。

缺点：

- 使用路径多一步，体验不符合当前目标。
- 无法在 App 内形成闭环。

结论：

- 不采用。

### 方案 C：App 上传到 Cloudflare Worker，Worker 写 MySQL

优点：

- 客户端不接触数据库凭据。
- 不需要自建常驻服务器。
- 服务端可集中做鉴权、校验与幂等。
- 后续表结构调整只改 Worker 即可。

缺点：

- 需要同时维护 App 与 Worker 两端代码。

结论：

- 采用本方案。

## 总体架构

数据流如下：

1. 用户在关于页输入 `Worker 地址` 与 `上传 Token`，并保存到当前设备。
2. 用户点击「上传未上传样本」。
3. App 从本地数据库导出训练样本，并过滤掉已经上传成功的 `eventId`。
4. App 将剩余样本打包为一个 JSON 请求发送到 Worker。
5. Worker 校验 `Authorization: Bearer <token>`。
6. Worker 对每条样本做基础字段校验。
7. Worker 将样本写入 MySQL，并以 `event_id` 唯一键做幂等去重。
8. Worker 返回本次成功处理的 `eventId` 列表与计数。
9. App 收到成功响应后，将这些 `eventId` 标记为已上传。
10. 关于页展示上传结果。

## Android 端设计

### UI 入口

上传配置与手动上传入口统一放在关于页，原因如下：

- 当前关于页已经承载客户端本地配置（Mapbox Token）。
- 上传功能属于基础设施与运维配置，不适合进入记录主流程。
- 可复用现有的状态提示展示区域。

关于页新增以下内容：

- `Worker 地址` 输入框
- `上传 Token` 输入框
- `保存上传配置` 按钮
- `清空上传配置` 按钮
- `上传未上传样本` 按钮
- 上传状态文案

### 本地配置存储

新增本地存储对象，职责与 `MapboxTokenStorage` 一致：

- 读取保存的 Worker 地址
- 读取保存的上传 Token
- 保存配置时做最小清洗，例如 `trim()`
- 支持清空

保存范围仅限当前设备，不参与构建注入。

### 上传状态管理

客户端需要区分 3 类状态：

- 上传配置状态
- 当前是否正在上传
- 哪些 `eventId` 已经上传成功

已上传状态建议单独本地持久化，而不是改动现有训练样本源表结构。原因如下：

- 范围更小，不侵入当前决策事件存储模型。
- 只需要围绕 `eventId` 做布尔语义。
- 后续如需重传或清理，也更容易独立处理。

建议新增一个上传状态存储组件，负责：

- 读取已上传 `eventId` 集合
- 批量标记上传成功的 `eventId`
- 清空上传记录（本次不暴露 UI，内部预留）

### 上传请求格式

Android 端向 Worker 发送：

- Method：`POST`
- URL：`${workerBaseUrl}/samples/batch`
- Header：
  - `Authorization: Bearer <token>`
  - `Content-Type: application/json`

请求体结构：

```json
{
  "deviceId": "android-local",
  "appVersion": "1.0.17",
  "samples": [
    {
      "eventId": 101,
      "recordId": 22,
      "timestampMillis": 1710000000000,
      "phase": "SUSPECT_MOVING",
      "isRecording": false,
      "startScore": 0.92,
      "stopScore": 0.01,
      "finalDecision": "START",
      "gpsQualityPass": true,
      "motionEvidencePass": true,
      "frequentPlaceClearPass": true,
      "feedbackEligible": true,
      "feedbackBlockedReason": null,
      "features": {
        "steps_30s": 8.0
      },
      "feedbackLabel": "START_TOO_EARLY",
      "startSource": "MANUAL",
      "stopSource": "MANUAL",
      "manualStartAt": 1710000000000,
      "manualStopAt": 1710000300000
    }
  ]
}
```

其中：

- `deviceId` 先使用固定语义值或本地生成值，仅用于后续排查，不做强依赖。
- `appVersion` 用于服务端日志和回溯。
- `samples` 直接复用现有训练样本语义，避免双重映射损耗。

### 上传结果处理

Worker 成功返回后，App 只标记服务端确认处理成功的 `eventId`。

成功响应格式约定为：

```json
{
  "ok": true,
  "insertedCount": 12,
  "dedupedCount": 3,
  "acceptedEventIds": [101, 102, 103]
}
```

客户端行为：

- `acceptedEventIds` 为空：提示当前没有可上传的新样本或服务端未接收数据。
- `acceptedEventIds` 非空：将这些事件标记为已上传。
- 请求失败或解析失败：不修改本地上传状态。

### 错误处理

Android 端需要处理以下错误：

- 未配置 Worker 地址：阻止上传并提示先保存配置。
- 未配置 Token：阻止上传并提示先保存配置。
- 当前没有可上传样本：直接提示，无网络请求。
- 网络错误：提示上传失败。
- 服务端返回 401/403：提示鉴权失败。
- 服务端返回 4xx/5xx：提示上传失败，并带简短原因。

## Cloudflare Worker 端设计

### 仓库结构

Worker 代码直接放在当前仓库中，建议目录如下：

- `worker/`
- `worker/src/index.ts`
- `worker/package.json`
- `worker/tsconfig.json`
- `worker/wrangler.jsonc`
- `worker/README.md`

### 接口设计

唯一需要的接口：

- `POST /samples/batch`

行为：

- 校验 Bearer Token
- 校验请求体结构
- 校验 `samples` 是否为数组
- 校验每条样本的关键字段
- 批量写入 MySQL
- 返回处理结果

本次不做分页查询、不做回读接口、不做管理接口。

### 鉴权设计

Worker 使用环境变量保存上传 Token，例如：

- `UPLOAD_TOKEN`

校验规则：

- Header 不存在或格式不正确：返回 `401`
- Token 不匹配：返回 `403`

之所以仍保留 Token，而不是仅靠地址保密，原因是：

- 仓库后续可能公开
- 客户端配置不等于服务端安全
- 最小成本即可建立基本访问控制

### MySQL 写入策略

Worker 写 MySQL 时按 `event_id` 做唯一键，并使用幂等 UPSERT 语义。

建议写入规则：

- 首次上传：插入新行
- 重复上传：不新增重复数据
- 若重复数据内容相同：视为去重成功
- 本次先不做冲突更新覆盖，避免非预期改写历史样本

### 响应格式

成功：

```json
{
  "ok": true,
  "insertedCount": 10,
  "dedupedCount": 2,
  "acceptedEventIds": [1, 2, 3, 4]
}
```

失败：

```json
{
  "ok": false,
  "message": "invalid token"
}
```

### 观测性

Worker 至少要记录：

- 请求时间
- 样本数量
- 插入数量
- 去重数量
- 失败原因

不记录明文 Token。

## MySQL 表设计

建议新增表：`training_samples`

建议字段：

- `event_id BIGINT NOT NULL`
- `record_id BIGINT NULL`
- `timestamp_millis BIGINT NOT NULL`
- `phase VARCHAR(64) NOT NULL`
- `is_recording TINYINT(1) NOT NULL`
- `start_score DOUBLE NOT NULL`
- `stop_score DOUBLE NOT NULL`
- `final_decision VARCHAR(32) NOT NULL`
- `gps_quality_pass TINYINT(1) NOT NULL`
- `motion_evidence_pass TINYINT(1) NOT NULL`
- `frequent_place_clear_pass TINYINT(1) NOT NULL`
- `feedback_eligible TINYINT(1) NOT NULL`
- `feedback_blocked_reason VARCHAR(128) NULL`
- `feedback_label VARCHAR(128) NULL`
- `start_source VARCHAR(32) NULL`
- `stop_source VARCHAR(32) NULL`
- `manual_start_at BIGINT NULL`
- `manual_stop_at BIGINT NULL`
- `features_json JSON NOT NULL`
- `source_device_id VARCHAR(128) NULL`
- `source_app_version VARCHAR(32) NULL`
- `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`

索引建议：

- 主唯一键：`UNIQUE KEY uk_training_samples_event_id (event_id)`
- 普通索引：`KEY idx_training_samples_timestamp (timestamp_millis)`
- 普通索引：`KEY idx_training_samples_record_id (record_id)`

说明：

- `features` 先整体存入 `features_json`，不在线拆列。
- 训练前可用离线脚本再展开为特征表或 CSV。

## 客户端与服务端的边界

客户端负责：

- 配置采集
- 样本筛选
- 鉴权头附带
- 上传触发
- 已上传状态记录
- 用户提示

服务端负责：

- 鉴权
- 数据校验
- 幂等去重
- 持久化写库
- 错误响应

这样可以确保：

- App 不需要知道数据库结构细节
- 表结构未来调整时，不需要同步发新版 App

## 测试策略

### Android 端

至少补以下测试：

- 上传配置存储的保存、读取、清空
- 训练样本过滤未上传逻辑
- 上传请求编码与响应解析
- 关于页上传按钮状态与状态文案
- 上传成功后本地已上传标记更新

### Worker 端

至少补以下测试：

- 缺失 Token 返回 `401`
- 错误 Token 返回 `403`
- 非法请求体返回 `400`
- 正常写入返回成功结果
- 重复 `eventId` 上传时返回去重结果

## 风险与后续演进

当前方案的已知边界：

- Token 保存在客户端本地，适合个人使用，但不适合多用户公开分发。
- 仅手动上传，不处理后台自动同步。
- 已上传状态仅保存在客户端，换机后如果重装 App，可能再次上传同批数据，但服务端会通过 `event_id` 去重兜底。

后续可以演进的方向：

- 增加「重传失败样本」
- 增加「清空已上传标记」
- 增加「自动上传最近结束的手动记录」
- 增加基于设备标识的更细粒度服务端审计

## 本次实现范围总结

本次实现只覆盖以下内容：

- 关于页新增 Worker 地址与 Token 配置
- App 内手动上传全部未上传样本
- 本地记录已上传的 `eventId`
- Cloudflare Worker 提供批量写入接口
- MySQL 幂等去重写入
- 基础测试与构建验证

不包含自动同步和后台任务。
