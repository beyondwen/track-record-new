# 当天显示与历史存储查询重整设计

**目标：** 统一当天实时显示、本地历史查询和开发阶段的 D1 容灾同步，让轨迹数据既能本地快速展示，又能在频繁卸载重装时恢复。

## 背景

当前项目已经具备 3 条基础链路：

- 当天实时显示缓存：`TodayTrackDisplayCache`
- 原始轨迹点本地落库：`ContinuousPointStorage`
- 历史记录本地存储与查询：`HistoryStorage`

这套结构能支撑本地使用，但在当前开发阶段存在两个明显问题：

1. 频繁卸载重装会清空 App 沙箱，本地 today / history / raw points 全部丢失。
2. 当天显示、本地历史、远端同步之间没有清晰的分层，后续继续扩展会越来越重。

## 设计目标

本次重整聚焦以下目标：

- **当天实时显示要快：** 首页当天轨迹仍然优先走本地数据。
- **历史列表与详情要稳：** 历史记录和历史点的查询入口分层明确。
- **开发阶段卸载不怕丢：** 通过 D1 小批次同步保留当天未完成轨迹和历史记录。
- **正式版策略可切换：** 当前先服务开发阶段，后续可决定正式版是否继续常开 D1 同步。
- **控制本地体积：** 原始点和当天点短期保留，历史结果长期保留。

## 核心方案

采用 **三层模型**：

1. **Today Session Layer（当天会话层）**
2. **Local History Layer（本地历史层）**
3. **D1 Mirror Layer（远端镜像层）**

D1 在当前阶段定位为 **镜像层**，不是唯一真相源。当天显示和历史查询仍然优先走本地；D1 负责开发阶段容灾与卸载后恢复。

## 数据分层与职责边界

### 1. Today Session Layer（当天会话层）

职责：

- 保存当天未完成轨迹
- 支撑首页实时显示
- 保存会话级状态，便于恢复

特征：

- 读写频繁
- 以“今天正在进行或刚中断的轨迹”为核心
- 重点是快和可恢复

### 2. Local History Layer（本地历史层）

职责：

- 保存已经整理完成的历史记录
- 支撑历史列表查询
- 支撑历史详情点位查询

特征：

- 查询优先
- 需要稳定离线可用
- 列表与详情分层读取，避免一把抓全量点

### 3. D1 Mirror Layer（远端镜像层）

职责：

- 小批次同步当天未完成会话
- 镜像整理后的历史记录
- 提供卸载重装后的恢复来源

特征：

- 先服务开发阶段容灾
- 采用幂等 upsert
- 后续可决定是否在正式版继续常开

## 数据流转

### 当天实时记录

采点后按以下顺序处理：

1. 先写本地当天会话层
2. 首页当天轨迹直接读取本地当天会话层
3. 同时将新增点加入待同步队列

### 小批次同步到 D1

满足任一条件就触发一次同步：

- 距上次同步超过 30 秒
- 新增点数达到 20 个
- 相位发生切换
- 暂停 / 恢复 / 结束
- App 进入后台
- 开发阶段手动触发同步

同步顺序：

1. `today_session`
2. `today_session_points`
3. 若会话已整理完成，再同步 `history_records`
4. 再同步 `history_points`

### 生成历史记录

当天轨迹结束或达到整理条件后：

1. 本地把当天会话整理成历史记录
2. 写入本地历史层
3. 再把整理后的历史记录同步到 D1

### 卸载重装后的恢复

恢复流程：

1. 启动后先恢复本地历史层
2. 再检查 D1 是否有更新数据
3. 如果 D1 有更完整的历史，补回本地历史层
4. 如果 D1 有未完成会话，恢复到本地当天会话层
5. 首页继续显示当天未完成轨迹

本次按用户确认，恢复目标包含：

- 历史列表与历史详情
- 当天未完成轨迹

## 本地数据模型

### `today_session`

建议字段：

- `session_id`
- `day_start`
- `status`（active / paused / completed / recovered）
- `started_at`
- `last_point_at`
- `ended_at`
- `last_synced_at`
- `sync_state`
- `phase`
- `anchor_point_ref`
- `recovered_from_remote`

用途：

- 判断今天是否存在未完成轨迹
- 恢复当天会话
- 管理同步状态

### `today_session_points`

建议字段：

- `session_id`
- `point_id`
- `timestamp`
- `lat`
- `lng`
- `accuracy`
- `altitude`
- `speed`
- `provider`
- `sampling_tier`
- `sync_state`

用途：

- 首页实时画线
- 会话恢复
- D1 小批次同步来源

说明：

- 保留上限控制，避免当天缓存无限增长。
- 可继续沿用 `TodayTrackDisplayCache.MAX_DISPLAY_POINTS` 这类限制思路。

### `history_records`

建议字段：

- `history_id`
- `source_session_id`
- `date_key`
- `started_at`
- `ended_at`
- `distance`
- `duration`
- `avg_speed`
- `title`
- `sync_state`
- `version`

用途：

- 历史列表查询
- 日期分组查询
- 恢复本地历史头部数据

### `history_points`

建议字段：

- `history_id`
- `point_order`
- `timestamp`
- `lat`
- `lng`
- `accuracy`
- `altitude`

用途：

- 历史详情页轨迹绘制
- 历史详情按需加载

## D1 数据模型

### `remote_sessions`

建议字段：

- `session_id`
- `device_id`
- `day_start`
- `status`
- `started_at`
- `last_point_at`
- `ended_at`
- `phase`
- `updated_at`

### `remote_session_points`

建议字段：

- `session_id`
- `point_id`
- `timestamp`
- `lat`
- `lng`
- `accuracy`
- `altitude`
- `speed`
- `provider`
- `sampling_tier`

### `remote_history_records`

建议字段：

- `history_id`
- `device_id`
- `source_session_id`
- `date_key`
- `started_at`
- `ended_at`
- `distance`
- `duration`
- `avg_speed`
- `title`
- `updated_at`

### `remote_history_points`

建议字段：

- `history_id`
- `point_order`
- `timestamp`
- `lat`
- `lng`
- `accuracy`
- `altitude`

## 同步策略

### 触发策略

采用小批次增量同步，不做“每个点立刻传”，也不做“只在夜间统一传”。

触发条件：

- 30 秒时间窗
- 20 个点阈值
- 相位切换
- 暂停 / 恢复 / 结束
- App 进入后台
- 开发阶段手动触发

### 同步原则

- D1 全部使用幂等 upsert
- 每条记录带 `updated_at` 或 `version`
- D1 只接受更新更晚的数据
- 同步失败只进入重试，不阻塞本地显示

## 恢复策略

### 启动恢复顺序

1. 先恢复本地 `history_records`
2. 再检查 D1 是否有更新历史
3. 如有需要，补写回本地
4. 再检查 D1 是否有未完成 `remote_sessions`
5. 若存在，则恢复本地 `today_session` 与 `today_session_points`

### 恢复后的行为

- 历史列表恢复正常
- 历史详情可查
- 首页恢复显示当天未完成轨迹
- 后续可直接继续在该会话上采集

## 清理与保留策略

### 本地长期保留

- `history_records`
- `history_points`

原因：

- 这是最常用的数据
- 需要离线可查
- 体积通常比 raw points 更可控

### 本地短期保留

- `today_session_points`
- raw points
- 分析中间数据

建议策略：

- 当天会话完成并整理成 history 后，可清理当天会话点
- raw points 在“同步成功 + 历史整理成功”后删除，或只保留最近 7～30 天
- today display cache 继续只保留当天和固定上限

### D1 长期保留

- `remote_history_records`
- `remote_history_points`

### D1 可定期清理

- 已完成会话对应的 `remote_sessions`
- 已归档后的 `remote_session_points`

## 需要调整的模块

### 模块 1：当天会话层重整

涉及方向：

- `TodayTrackDisplayCache`
- `TrackingRuntimeSnapshotStorage`
- `BackgroundTrackingService`

目标：

- 将“当天显示缓存”升级为明确的 today session 模型
- 同时维护会话元信息与显示点

### 模块 2：本地历史层整理

涉及方向：

- `HistoryStorage`
- 历史列表查询入口
- 历史详情查询入口

目标：

- 把缓存层、仓库层、查询层职责拆清
- 列表只查 `history_records`
- 详情按需查 `history_points`

### 模块 3：同步队列与 D1 镜像层

目标：

- 增加 today session 同步器
- 增加 history 同步器
- 支持小批次幂等同步

### 模块 4：恢复链路

目标：

- 启动时恢复历史和当天未完成轨迹
- 保证卸载重装后的开发阶段体验

### 模块 5：清理策略

目标：

- 控制本地数据库增长
- 明确 short-term 与 long-term 数据边界

## 推荐落地顺序

1. 先重整当天会话层
2. 再重整本地历史查询结构
3. 接入 D1 小批次同步
4. 完成恢复链路
5. 最后补清理与保留策略

## 结论

本次不建议把 D1 直接做成历史页唯一数据源，而是采用：

- **当天显示 = today session**
- **历史查询 = local history**
- **开发容灾 = D1 mirror**

这样既能保住本地实时体验，也能解决开发阶段频繁卸载重装导致的数据丢失问题，并为后续正式版是否常开 D1 同步保留选择空间。
