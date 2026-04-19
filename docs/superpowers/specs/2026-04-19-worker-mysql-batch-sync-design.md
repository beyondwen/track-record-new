# 连续点位与分析结果通过 Worker 批量同步到 MySQL 的设计

## 文档定位

- 文档类型：数据同步设计
- 适用范围：连续点位、分析结果、本地上传游标、后台批量同步、Worker 落 MySQL
- 相关文档：
  - [连续点流采集与延迟分段分析设计](/Users/a555/StudioProjects/track-record-new/docs/superpowers/specs/2026-04-19-continuous-point-stream-analysis-design.md)
  - [训练样本上传到 Cloudflare Worker 的设计](/Users/a555/StudioProjects/track-record-new/docs/superpowers/specs/2026-04-18-training-sample-worker-upload-design.md)

## 1. 摘要

本设计在现有「连续点流采集 + 本地延迟分析」架构之上，新增一条后台批量同步链路，将以下两类数据通过 Worker 写入 MySQL：

- 原始点位流 `raw_location_point`
- 分析结果 `analysis_segment` 与 `stay_cluster`

本次设计采用以下原则：

- App 不直连 MySQL，只与 Worker 通信。
- App 继续在本地完成分析，Worker 不重复跑算法。
- 同步采用后台批量方式，允许延迟，优先保证稳定性与续航。
- 原始点位与分析结果拆成两条独立上传链路，分别维护上传游标。
- 不兼容旧的已上传状态集合，直接从当前本地事实表重新开始同步。

## 2. 背景与问题定义

当前项目已经具备以下基础能力：

- 后台服务持续采集点位，并写入 `raw_location_point`
- 本地分析链路可在内存中识别动态段
- App 已有一套 Worker 地址与 Token 配置能力
- 训练样本与历史轨迹已有手动上传到 Worker 的实现

但当前连续点流方案仍缺少两个关键能力：

- 分析结果没有形成完整、稳定的长期持久化上传源
- 点位与分析结果无法自动、批量、后台同步到服务端数据库

如果直接让 App 连接 MySQL，会带来以下问题：

- 数据库凭据必须进入客户端
- 公网暴露 MySQL 风险高
- 移动网络环境下连接可靠性差
- 服务端表结构调整会直接牵动客户端

因此，本次把问题定义为：

- App 端如何把本地点位与分析结果稳定地增量同步到 Worker
- Worker 如何做鉴权、校验、幂等去重并写入 MySQL

## 3. 设计目标

### 3.1 目标

- 将原始点位与分析结果都同步到 MySQL。
- 复用现有 Worker 地址与 Token 配置，不另起一套上传配置体系。
- 使用后台批量同步，而不是实时推送。
- 让上传状态适配连续追加的数据流，避免使用大集合标记已上传 ID。
- 保证失败可重试、成功可推进游标、重复提交可幂等。

### 3.2 非目标

- 不做 App 直连 MySQL。
- 不把分析逻辑搬到 Worker 端重跑。
- 不兼容旧的训练样本/历史轨迹已上传状态结构。
- 不实现服务端管理后台。
- 不实现端到端实时秒级同步。

## 4. 方案对比

### 4.1 方案 A：只上传原始点位，由 Worker 端分析后写 MySQL

优点：

- App 侧改动较少。
- 算法可以统一在服务端演进。

缺点：

- 当前项目已经有本地分析链路，服务端需要重复实现算法。
- 本地展示结果与服务端结果容易漂移。
- Worker 复杂度显著上升。

结论：

- 不采用。

### 4.2 方案 B：App 本地分析并落本地表，再由后台 Worker 批量上传到服务端

优点：

- 最大化复用当前项目能力。
- 保持本地 UI 与上传结果语义一致。
- Worker 只负责鉴权、校验、去重和落库。
- 后续 MySQL 表结构演进只影响 Worker 适配层。

缺点：

- App 侧需要补分析结果持久化和自动后台同步能力。

结论：

- 采用本方案。

### 4.3 方案 C：只复用现有历史上传链路，不上传完整分析结果

优点：

- 实现最快。

缺点：

- `history` 只是动态段投影，不包含完整静止段和停留簇。
- 不满足「原始点位和分析结果都要」的范围要求。

结论：

- 不采用。

## 5. 总体架构

系统按职责分为 4 层：

- `采集层`
  持续接收定位点并追加写入 `raw_location_point`
- `分析层`
  读取未分析点流，产出 `analysis_segment` 与 `stay_cluster`
- `同步层`
  使用 `WorkManager` 按上传游标读取本地数据并分批同步到 Worker
- `服务端落库层`
  Worker 进行鉴权、参数校验、幂等去重并写入 MySQL

数据流如下：

1. 后台采集服务将原始点位写入 `raw_location_point`
2. 本地分析器消费未分析点位并持久化分析结果
3. `RawPointUploadWorker` 按 `upload_cursor` 中 `RAW_POINT` 记录批量上传原始点位
4. `AnalysisUploadWorker` 按 `upload_cursor` 中 `ANALYSIS_SEGMENT` 记录批量上传分析结果与关联停留簇
5. Worker 将有效数据写入 MySQL，并返回已确认接收的最大游标
6. App 仅在服务端确认成功后推进对应上传游标

## 6. Android 端设计

### 6.1 本地事实表与衍生表边界

本地数据边界定义如下：

- `raw_location_point`
  原始事实表，只追加写入，不因上传成功而删除
- `analysis_segment`
  分析输出表，长期保留，用于 UI 和上传
- `stay_cluster`
  分析输出表，长期保留，用于 UI 和上传
- `analysis_cursor`
  仅表示分析器已经处理到哪个 `pointId`

本次新增的同步状态不能混入 `analysis_cursor`，需要单独维护上传进度。

### 6.2 上传状态模型

上传状态从当前「已上传 ID 集合」切换为「游标模型」。

建议新增本地上传游标表，例如 `upload_cursor`，至少包含以下 2 条记录：

- `RAW_POINT`
  表示原始点位已成功上传到哪个 `pointId`
- `ANALYSIS_SEGMENT`
  表示分析结果已成功上传到哪个 `segmentId`

字段建议如下：

- `cursorType`
- `lastUploadedId`
- `updatedAt`

采用游标而不是 ID 集合的原因如下：

- 连续点流是单调追加数据，更适合用主键递增游标表达进度
- 点位量会持续增长，集合式存储会越来越重
- 按最大已确认游标推进，更适合批量重试和幂等处理

### 6.3 分析结果持久化要求

当前分析链路已经有 `analysis_segment`、`stay_cluster`、`analysis_cursor` 数据模型，但需要保证以下要求真正落地：

- 分析器输出必须持久化到 `analysis_segment`
- 与静止段相关的停留簇必须持久化到 `stay_cluster`
- 上传链路只读取已经完整持久化的分析结果

这意味着本次同步方案以 Room 表为唯一上传源，不直接从内存对象上传。

### 6.4 后台同步任务拆分

同步层采用两个独立的 `WorkManager` 任务：

- `RawPointUploadWorker`
- `AnalysisUploadWorker`

拆分原因如下：

- 原始点位量大，节奏快
- 分析结果量小，产出更慢
- 两者失败、重试和调优策略不同

### 6.5 任务触发策略

采用「周期任务 + 去抖动的一次性任务」组合：

- 应用启动后注册两个 `PeriodicWork`
- 当采集到新点位或分析出新结果时，补一个唯一的 `OneTimeWork`
- 使用 unique work 去重，避免高频排队

约束条件：

- 仅要求网络可用
- 第一阶段不增加「仅 Wi-Fi」或「充电时」限制

这样可以在保证续航的前提下，让数据在有网时逐步同步，而不是长时间堆积。

### 6.6 批次策略

建议批次如下：

- 原始点位：按 `pointId ASC` 读取，每批 `200 到 500` 条
- 分析结果：按 `segmentId ASC` 读取，每批 `50 到 200` 个 segment
- `stay_cluster` 按 segment 归属与对应 batch 一起上传

单次 worker 执行不要无限循环，建议一次最多处理 `3 到 5` 个批次，避免后台任务占用过久。

### 6.7 执行顺序

推荐上传顺序固定为：

1. 先传 `raw_location_point`
2. 再传 `analysis_segment` 与 `stay_cluster`

这样 MySQL 中分析结果引用到的原始点位大概率已经先落库，数据关系更稳定。

### 6.8 错误处理

客户端需要区分以下错误：

- `401 / 403`
  视为鉴权失败，不自动重试，等待用户更新 Token
- 网络错误、超时、`5xx`
  视为可重试失败，返回 `Result.retry()`
- 非鉴权类 `4xx`
  视为请求格式或服务端校验问题，停止自动重试，等待修复
- 响应解析失败
  视为临时失败，保守重试

只有当服务端明确成功并返回有效游标时，客户端才推进上传游标。

### 6.9 兼容策略

用户已明确接受「可以重新开始」，因此本次采取以下兼容策略：

- 不迁移旧的已上传 ID 集合
- 不兼容旧的上传状态存储格式
- 保留本地业务事实数据，不清空 `raw_location_point`、`analysis_segment`、`stay_cluster`
- 新增上传游标后，从当前库内数据重新批量同步

## 7. 上传接口设计

### 7.1 接口拆分

Worker 侧新增两组接口：

- `POST /raw-points/batch`
- `POST /analysis/batch`

不复用现有 `/samples/batch`、`/histories/batch`，原因如下：

- 语义更清晰
- 日志与排障更容易
- 后续演进不会和训练样本、历史轨迹上传互相污染

### 7.2 原始点位请求格式

请求头：

- `Authorization: Bearer <token>`
- `Content-Type: application/json`

请求体结构建议如下：

```json
{
  "deviceId": "android-local",
  "appVersion": "1.0.23",
  "points": [
    {
      "pointId": 1001,
      "timestampMillis": 1710000000000,
      "latitude": 39.9,
      "longitude": 116.4,
      "accuracyMeters": 12.5,
      "altitudeMeters": 45.8,
      "speedMetersPerSecond": 0.0,
      "bearingDegrees": 0.0,
      "provider": "fused",
      "sourceType": "LOCATION_UPDATE",
      "isMock": false,
      "wifiFingerprintDigest": "abc123",
      "activityType": "STILL",
      "activityConfidence": 0.92,
      "samplingTier": "IDLE"
    }
  ]
}
```

### 7.3 分析结果请求格式

请求体结构建议如下：

```json
{
  "deviceId": "android-local",
  "appVersion": "1.0.23",
  "segments": [
    {
      "segmentId": 9001,
      "startPointId": 1001,
      "endPointId": 1100,
      "startTimestamp": 1710000000000,
      "endTimestamp": 1710001200000,
      "segmentType": "STATIC",
      "confidence": 0.96,
      "distanceMeters": 18.0,
      "durationMillis": 1200000,
      "avgSpeedMetersPerSecond": 0.01,
      "maxSpeedMetersPerSecond": 0.11,
      "analysisVersion": 1,
      "stayClusters": [
        {
          "stayId": 7001,
          "centerLat": 39.9,
          "centerLng": 116.4,
          "radiusMeters": 25.0,
          "arrivalTime": 1710000000000,
          "departureTime": 1710001200000,
          "confidence": 0.94,
          "analysisVersion": 1
        }
      ]
    }
  ]
}
```

`stay_cluster` 以内嵌形式跟随所属 `segment` 上传，避免单独维护第三条同步链路。

### 7.4 响应格式

原始点位上传响应：

```json
{
  "ok": true,
  "insertedCount": 120,
  "dedupedCount": 30,
  "acceptedMaxPointId": 1300,
  "message": "ok"
}
```

分析结果上传响应：

```json
{
  "ok": true,
  "insertedCount": 28,
  "dedupedCount": 4,
  "acceptedMaxSegmentId": 9050,
  "message": "ok"
}
```

返回最大已确认游标，而不是一长串已接收 ID。这样客户端只需推进游标，不需要持久化大型集合。

## 8. MySQL 表结构设计

### 8.1 原始点位表

建议表名：`raw_location_point`

核心字段：

- `device_id`
- `point_id`
- `timestamp_millis`
- `latitude`
- `longitude`
- `accuracy_meters`
- `altitude_meters`
- `speed_meters_per_second`
- `bearing_degrees`
- `provider`
- `source_type`
- `is_mock`
- `wifi_fingerprint_digest`
- `activity_type`
- `activity_confidence`
- `sampling_tier`
- `app_version`
- `created_at`

唯一键：

- `uniq_device_point (device_id, point_id)`

### 8.2 分析分段表

建议表名：`analysis_segment`

核心字段：

- `device_id`
- `segment_id`
- `start_point_id`
- `end_point_id`
- `start_timestamp`
- `end_timestamp`
- `segment_type`
- `confidence`
- `distance_meters`
- `duration_millis`
- `avg_speed_meters_per_second`
- `max_speed_meters_per_second`
- `analysis_version`
- `app_version`
- `created_at`

唯一键：

- `uniq_device_segment (device_id, segment_id)`

### 8.3 停留簇表

建议表名：`stay_cluster`

核心字段：

- `device_id`
- `stay_id`
- `segment_id`
- `center_lat`
- `center_lng`
- `radius_meters`
- `arrival_time`
- `departure_time`
- `confidence`
- `analysis_version`
- `app_version`
- `created_at`

唯一键：

- `uniq_device_stay (device_id, stay_id)`

### 8.4 幂等与关系约束

幂等责任以 Worker + MySQL 为准，不依赖客户端猜测。

设计要求如下：

- 原始点位以 `(device_id, point_id)` 幂等去重
- 分析分段以 `(device_id, segment_id)` 幂等去重
- 停留簇以 `(device_id, stay_id)` 幂等去重
- 分析结果上传可重复提交，同一批次重复发送不应产生脏数据

## 9. Worker 端设计

### 9.1 职责边界

Worker 只负责：

- Bearer Token 鉴权
- 基础字段校验
- 批量写入 MySQL
- 幂等去重
- 返回最大已确认游标与计数

Worker 不负责：

- 重跑点流分析算法
- 维护客户端上传状态
- 为客户端提供复杂查询接口

### 9.2 处理流程

原始点位接口处理流程：

1. 校验 `Authorization` 头
2. 校验请求体结构与必要字段
3. 按批量 SQL 写入 `raw_location_point`
4. 统计插入数量和去重数量
5. 计算最大已确认 `pointId`
6. 返回响应

分析结果接口处理流程：

1. 校验 `Authorization` 头
2. 校验 `segments` 与内嵌 `stayClusters`
3. 先写 `analysis_segment`
4. 再写关联 `stay_cluster`
5. 统计插入数量和去重数量
6. 计算最大已确认 `segmentId`
7. 返回响应

### 9.3 局部成功策略

为降低实现复杂度，第一阶段建议采用保守策略：

- 单个请求体中的数据要么整体成功，要么整体失败
- 不做细粒度「部分成功 + 部分失败明细」返回
- 如果未来需要做更细的局部成功，再在协议上扩展

在客户端批次较小、可重试且具备幂等去重的前提下，这个策略足够稳定。

## 10. 测试策略

测试范围控制在以下 4 块：

### 10.1 Payload Codec 单测

验证：

- 字段命名稳定
- 空值序列化符合预期
- `stayClusters` 内嵌结构正确

### 10.2 Upload Service 单测

验证：

- `2xx` 成功解析
- `401 / 403` 鉴权失败
- `4xx / 5xx` 错误处理
- 空响应和非法 JSON 响应

### 10.3 Worker 调度单测

验证：

- 按游标分页
- 成功推进游标
- 失败不推进游标
- retry 行为正确
- unique work 去抖生效

### 10.4 Room DAO 单测

验证：

- 按上传游标读取原始点位
- 按上传游标读取分析结果
- 上传游标读写正确

## 11. 实施边界

本次设计默认以下实施边界：

- 保留现有 Worker 配置输入 UI 与 Token 存储方式
- 新增连续点流与分析结果的上传服务，不替换现有训练样本和历史轨迹上传功能
- Room 本地事实数据继续作为上传源
- `WorkManager` 作为唯一后台自动同步执行器

## 12. 风险与后续演进

当前方案的主要风险如下：

- 如果本地点位增长过快，单设备重新全量追平需要一定时间
- 如果分析结果主键生成策略后续变化，需要同步校准服务端幂等键
- 如果后续要支持多设备汇总查询，需要进一步规范 `deviceId` 生成与持久化策略

可预留的后续演进方向：

- 增加「仅 Wi-Fi 上传」或「充电时上传」设置项
- 为 Worker 增加批量写入性能优化与监控日志
- 增加服务端回查接口，用于设备端对账
- 为上传状态增加简单诊断页，便于排查卡游标问题

## 13. 结论

本次采用「App 本地持久化分析结果 + `WorkManager` 双 Worker 后台批量同步 + Worker 幂等写入 MySQL」方案。

该方案的核心价值如下：

- 复用现有 App 端分析与 Worker 配置能力
- 避免 App 暴露 MySQL 凭据
- 适配连续点流的增量上传模型
- 在稳定性、续航和实现复杂度之间取得平衡

后续实现阶段将围绕以下主线展开：

- 完成分析结果真正持久化
- 新增上传游标模型与 DAO
- 新增原始点位和分析结果上传服务
- 接入 `WorkManager` 自动调度
- 补齐单测与验证链路
