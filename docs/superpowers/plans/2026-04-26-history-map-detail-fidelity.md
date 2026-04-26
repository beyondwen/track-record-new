# 历史轨迹详情页细节保留修复实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复历史轨迹详情页被过度压缩的问题，让所有历史轨迹在详情地图中保留更多弯折、停留和局部路径变化。

**架构：** 保持现有历史详情数据来源、轨迹清洗和地图组件不变，只调整历史详情页进入地图前的几何预算与最终折线降采样参数。实现上优先修改 `HistoryMapGeometryLimiter` 和 `MapActivity` 的历史详情渲染参数，不在本轮引入新的配置开关或独立渲染链路。

**技术栈：** Android Kotlin、Jetpack Compose、Mapbox Compose、JUnit 4、Kotlin Test。

---

## 文件结构

- 修改：`app/src/main/java/com/wenhao/record/ui/map/HistoryMapGeometryLimiter.kt`，放宽历史详情页允许进入渲染链路的段数和点数预算。
- 修改：`app/src/main/java/com/wenhao/record/ui/map/MapActivity.kt`，提高历史详情页最终 `buildCompact()` 的每段点数预算和高度分桶数。
- 修改：`app/src/test/java/com/wenhao/record/ui/map/HistoryMapGeometryLimiterTest.kt`，新增失败测试证明当前几何限流过于激进，并验证放宽后的输出预算。
- 创建：`app/src/test/java/com/wenhao/record/ui/map/TrackPolylineBuilderTest.kt`，验证 `buildCompact()` 在更高点数预算下保留更多折线拐点，同时保持起终点不变。
- 只读参考：`docs/superpowers/specs/2026-04-26-history-map-detail-fidelity-design.md`，本计划的需求来源。

## 任务 1：放宽历史几何限流预算

**文件：**
- 修改：`app/src/test/java/com/wenhao/record/ui/map/HistoryMapGeometryLimiterTest.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/map/HistoryMapGeometryLimiter.kt`

- [ ] **步骤 1：编写失败测试**

在 `HistoryMapGeometryLimiterTest` 中新增一个高细节轨迹用例，断言当前预算过低：

```kotlin
@Test
fun `keeps at least 24 segments and 960 points for detailed history rendering`() {
    val segments = List(40) { segmentIndex ->
        List(120) { pointIndex ->
            TrackPoint(
                latitude = 30.0 + segmentIndex * 0.001 + pointIndex * 0.00001,
                longitude = 120.0 + segmentIndex * 0.001 + pointIndex * 0.00001,
                timestampMillis = 1_713_420_000_000L + segmentIndex * 120_000L + pointIndex * 1_000L,
            )
        }
    }

    val limited = HistoryMapGeometryLimiter.limitSegments(segments)

    assertEquals(24, HistoryMapGeometryLimiter.MAX_RENDER_SEGMENTS)
    assertEquals(960, HistoryMapGeometryLimiter.MAX_RENDER_POINTS)
    assertTrue(limited.size <= 24)
    assertTrue(limited.sumOf { it.size } <= 960)
    assertEquals(segments.first().first(), limited.first().first())
    assertEquals(segments.last().last(), limited.last().last())
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :app:testDebugUnitTest --tests com.wenhao.record.ui.map.HistoryMapGeometryLimiterTest`

预期：FAIL，`MAX_RENDER_SEGMENTS` 仍是 `12`，`MAX_RENDER_POINTS` 仍是 `480`。

- [ ] **步骤 3：实现最少代码**

在 `HistoryMapGeometryLimiter.kt` 中把历史详情预算调高到：

```kotlin
const val MAX_RENDER_SEGMENTS = 24
const val MAX_RENDER_POINTS = 960
const val MAX_CLUSTER_MARKERS = 24
```

其余采样逻辑保持不变，不改 `evenlySample()` 和 `downsample()` 的行为。

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :app:testDebugUnitTest --tests com.wenhao.record.ui.map.HistoryMapGeometryLimiterTest`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/wenhao/record/ui/map/HistoryMapGeometryLimiter.kt app/src/test/java/com/wenhao/record/ui/map/HistoryMapGeometryLimiterTest.kt
git commit -m "test(map): relax history geometry limiter budget"
```

## 任务 2：放宽历史详情折线降采样

**文件：**
- 创建：`app/src/test/java/com/wenhao/record/ui/map/TrackPolylineBuilderTest.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/map/MapActivity.kt`

- [ ] **步骤 1：编写失败测试**

新建 `TrackPolylineBuilderTest.kt`，覆盖同一段轨迹在低预算与高预算下的保真差异：

```kotlin
package com.wenhao.record.ui.map

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackPolylineBuilderTest {

    @Test
    fun `buildCompact keeps more vertices when history detail budget is increased`() {
        val detailedSegment = List(240) { index ->
            val offset = if (index % 2 == 0) 0.00018 else -0.00018
            TrackPoint(
                latitude = 30.0 + index * 0.00004,
                longitude = 120.0 + offset,
                timestampMillis = 1_713_420_000_000L + index * 1_000L,
                altitudeMeters = 20.0 + (index % 6),
            )
        }

        val compactLow = TrackPolylineBuilder.buildCompact(
            segments = listOf(detailedSegment),
            idPrefix = "history-low",
            width = 6.6,
            maxPointsPerSegment = 90,
            altitudeBuckets = 2,
        )
        val compactHigh = TrackPolylineBuilder.buildCompact(
            segments = listOf(detailedSegment),
            idPrefix = "history-high",
            width = 6.6,
            maxPointsPerSegment = 180,
            altitudeBuckets = 4,
        )

        val lowVertexCount = compactLow.sumOf { it.points.size }
        val highVertexCount = compactHigh.sumOf { it.points.size }

        assertTrue(highVertexCount > lowVertexCount)
        assertEquals(detailedSegment.first().toGeoCoordinate(), compactHigh.first().points.first())
        assertEquals(detailedSegment.last().toGeoCoordinate(), compactHigh.last().points.last())
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :app:testDebugUnitTest --tests com.wenhao.record.ui.map.TrackPolylineBuilderTest`

预期：FAIL，测试文件尚不存在。

- [ ] **步骤 3：实现最少代码**

在 `MapActivity.kt` 的 `historySceneState()` 中，把历史详情折线参数改成：

```kotlin
polylines = TrackPolylineBuilder.buildCompact(
    segments = preparedGeometry.renderableSegments,
    idPrefix = "history",
    width = 6.6,
    maxPointsPerSegment = 180,
    altitudeBuckets = 4,
)
```

不要改 `TrackPolylineBuilder` 的通用实现逻辑；本轮只放宽历史详情页的调用参数。

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :app:testDebugUnitTest --tests com.wenhao.record.ui.map.TrackPolylineBuilderTest`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/wenhao/record/ui/map/MapActivity.kt app/src/test/java/com/wenhao/record/ui/map/TrackPolylineBuilderTest.kt
git commit -m "fix(map): preserve more history route detail"
```

## 任务 3：回归验证与人工检查

**文件：**
- 修改：本计划涉及的 Kotlin 测试与实现文件

- [ ] **步骤 1：运行目标单元测试**

运行：`./gradlew :app:testDebugUnitTest --tests com.wenhao.record.ui.map.HistoryMapGeometryLimiterTest --tests com.wenhao.record.ui.map.TrackPolylineBuilderTest --tests com.wenhao.record.ui.map.TrackRenderClusterCollapserTest`

预期：PASS。

- [ ] **步骤 2：运行 Kotlin 编译**

运行：`./gradlew :app:compileDebugKotlin`

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 3：检查 diff 范围**

运行：`git diff -- app/src/main/java/com/wenhao/record/ui/map/HistoryMapGeometryLimiter.kt app/src/main/java/com/wenhao/record/ui/map/MapActivity.kt app/src/test/java/com/wenhao/record/ui/map/HistoryMapGeometryLimiterTest.kt app/src/test/java/com/wenhao/record/ui/map/TrackPolylineBuilderTest.kt docs/superpowers/specs/2026-04-26-history-map-detail-fidelity-design.md docs/superpowers/plans/2026-04-26-history-map-detail-fidelity.md`

预期：只包含历史详情轨迹细节保留相关改动。

- [ ] **步骤 4：人工验证历史详情页**

在应用中打开至少 2 条历史轨迹详情页，逐项确认：

- 多弯折轨迹不再表现为几段直线。
- 起点和终点标记仍然正确。
- 多段轨迹仍能完整显示。
- 地图页面没有明显卡顿或闪退。

- [ ] **步骤 5：如果仍明显拉直，则停止并单独起新计划**

如果完成前两项参数放宽后，人工验证仍存在“局部路径被拉直”的情况，不要直接继续修改 `TrackRenderClusterCollapser.kt`。先记录现象、保存截图或复现轨迹，再为“降低点簇折叠强度”单独编写补充规格与实现计划，避免在同一轮中把问题范围扩大。
