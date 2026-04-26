# 历史轨迹详情页细节保留修复设计

## 背景

当前历史轨迹详情页在展示轨迹时，存在明显的过度简化问题。用户看到的路线会被压成几段近似直线，体感上像“横线连接”，无法反映真实行走或移动过程中的弯折、停留和局部变化。

经排查，问题不在记录链路本身，而在历史详情页的地图渲染链路进行了多层压缩：

1. `TrackRenderClusterCollapser` 会折叠局部点簇。
2. `HistoryMapGeometryLimiter` 会限制可渲染段数和点数。
3. `TrackPolylineBuilder.buildCompact()` 会再次对每段轨迹做降采样。

这 3 层压缩叠加后，导致历史详情页的轨迹细节损失过多。

## 目标

- 让所有历史轨迹详情页尽量保留原始轨迹细节。
- 恢复弯折、停留和局部路径变化，避免轨迹被展示成几段直线。
- 仅调整历史详情页的地图展示策略，不影响列表页、缩略图或其他轻量化地图场景。
- 保留必要的渲染保护，避免超大轨迹导致地图卡顿或失控。

## 非目标

- 不修改轨迹采集、存储或上传逻辑。
- 不修改历史列表页文案或摘要展示。
- 不为不同轨迹类型引入新的配置开关。
- 不新增一整套独立的历史地图渲染架构。

## 用户决策

本次设计已与用户确认：

- 视觉方向：选择 **A**，即尽量保留原始细节。
- 适用范围：选择 **A1**，即对所有历史轨迹统一生效。
- 实现方向：选择 **方案 2**，即放宽历史详情页整条渲染链路，而不是只调整单个参数。

## 方案概述

在历史轨迹详情页的渲染链路中，整体放宽压缩强度，但不移除所有保护机制。

具体做法：

1. 放宽 `HistoryMapGeometryLimiter` 的段数和点数上限。
2. 放宽 `MapActivity` 中传给 `TrackPolylineBuilder.buildCompact()` 的每段最大点数。
3. 视当前效果，降低 `TrackRenderClusterCollapser` 的折叠激进度，但不完全移除该步骤。
4. 保留 `TrackPathSanitizer.renderableSegments()`，继续负责非法段过滤和基础可渲染性判断。

这样可以在不改动数据层的前提下，直接提升历史详情页的视觉保真度。

## 具体修改点

### 1. 放宽几何限流

文件：`app/src/main/java/com/wenhao/record/ui/map/HistoryMapGeometryLimiter.kt`

当前这里对历史详情轨迹设置了全局渲染预算：

- `MAX_RENDER_SEGMENTS = 12`
- `MAX_RENDER_POINTS = 480`

这组参数对“预览型地图”是合理的，但对“详情型地图”偏激进。需要提高上限，让更多段和更多点进入最终渲染链路。

设计要求：

- 提高 `MAX_RENDER_POINTS`，优先保证曲线细节恢复。
- 适当提高 `MAX_RENDER_SEGMENTS`，避免多段轨迹被过早抽样掉。
- 仍保留上限，不改为无限制渲染。

### 2. 放宽最终折线降采样

文件：`app/src/main/java/com/wenhao/record/ui/map/MapActivity.kt`

当前历史详情页最终调用：

- `TrackPolylineBuilder.buildCompact()`
- `maxPointsPerSegment = 90`
- `altitudeBuckets = 2`

其中 `maxPointsPerSegment = 90` 过低，且前面已经经过一轮限流，所以这里会进一步放大细节损失。

设计要求：

- 提高 `maxPointsPerSegment`，让单段轨迹保留更多折线拐点。
- 适度提高 `altitudeBuckets`，避免大段合并造成视觉连续性过强。
- 保持 `buildCompact()` 方案不变，不切换到完全未压缩的逐段逐线构建方式。

### 3. 适度降低点簇折叠强度

文件：`app/src/main/java/com/wenhao/record/ui/map/TrackRenderClusterCollapser.kt`

该组件会将一定范围内的点簇折叠为中心点，这对长时间停留或密集采样场景有价值，但当前参数可能对历史详情页过于激进。

设计要求：

- 优先通过前两项修改解决问题。
- 如果仍明显存在“局部路径被拉直”的现象，再小幅下调折叠强度。
- 不完全移除折叠逻辑，避免静止点云原样灌入地图。

## 数据流影响

本次修改只影响历史详情页的显示链路：

`RemoteHistoryRepository.loadDay()`
→ `MapActivity.prepareHistoryGeometry()`
→ `HistoryMapGeometryLimiter.limitSegments()`
→ `TrackPathSanitizer.renderableSegments()`
→ `TrackPolylineBuilder.buildCompact()`
→ `TrackMapboxCanvas`

其中：

- 数据来源不变。
- 轨迹过滤逻辑主体不变。
- 地图组件不变。
- 变化点仅在“进入地图前保留多少几何细节”。

## 测试与验证

本次修复按 TDD 执行。

### 测试目标

需要新增针对历史详情页几何压缩行为的测试，证明：

1. 当前策略会对高细节轨迹做过度压缩。
2. 放宽参数后，输出的可渲染点数明显增加。
3. 起点、终点和基本路径顺序保持正确。
4. 原有的基础保护仍在，例如空段、单点段、不可渲染段不会异常进入地图。

### 推荐测试范围

优先为以下代码补测试：

- `HistoryMapGeometryLimiter`
- `TrackPolylineBuilder.buildCompact()`

如果需要，也可以提取一小段可测试的历史几何准备逻辑，避免直接为 `MapActivity` 写过重的 UI / Activity 级测试。

### 手动验证

实现完成后，至少手动确认以下场景：

- 多弯折轨迹不再被压成几段直线。
- 多段轨迹仍能正确显示起终点。
- 长轨迹在地图上可正常打开，不出现明显卡死。

## 风险与控制

### 风险

- 渲染点数增加后，历史详情页的地图初始化成本会上升。
- 超长轨迹可能在低性能设备上带来额外压力。

### 控制措施

- 继续保留全局点数和段数上限。
- 不直接取消所有压缩步骤。
- 优先放宽参数，而不是替换整套渲染实现。

## 实施顺序

1. 先补失败测试，证明当前压缩过度。
2. 调整 `HistoryMapGeometryLimiter` 的预算参数。
3. 调整 `MapActivity` 中 `buildCompact()` 的参数。
4. 如有必要，再小幅调整 `TrackRenderClusterCollapser`。
5. 跑测试并做手动验证。

## 成功标准

满足以下条件即可认为修复成功：

- 历史详情页中的轨迹细节明显增加。
- 用户不再感知为“横线连接”。
- 所有历史轨迹统一应用该改进。
- 未影响历史列表页和其他轻量地图场景。
- 测试通过，且没有引入明显回归。
