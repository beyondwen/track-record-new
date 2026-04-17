# 手动边界打标与自动记录停用过渡实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 停用当前自动记录入口与自动触发逻辑，转为纯手动开始 / 结束；同时在现有记录模型中落下手动边界来源字段与时间戳，并让训练导出只保留「手动开始 + 手动结束」的完整记录段。

**架构：** 运行时层面不再自动启动后台智能记录服务，也不再由现有决策模型触发开始 / 结束。数据层在现有历史记录模型上增加开始 / 结束来源和手动边界时间，训练导出侧新增一条基于历史记录而不是自动决策事件的手动闭环样本导出链路，并在 Python 训练脚本中按手动边界窗口生成 `start / stop` 正样本。

**技术栈：** Android / Kotlin / Room / Jetpack Compose / JSONL / Python 3 / 现有 decision-model 工具链

---

## 文件结构

### 将修改的现有文件

- `app/src/main/java/com/wenhao/record/permissions/PermissionHelper.kt` — 去掉自动启动后台智能记录服务的权限完成回调，将权限助手收敛为定位与手动记录所需能力。
- `app/src/main/java/com/wenhao/record/RecordApplication.kt` — 停止为自动记录相关缓存做预热，仅保留仍然使用的数据缓存初始化。
- `app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt` — 移除自动记录启动路径、调整主页刷新逻辑，使其不再依赖自动记录 session 作为主要状态源，并挂接手动记录来源字段。
- `app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt` — 隐藏自动记录 / 智能记录相关入口和文案，仅保留手动记录控制所需 UI。
- `app/src/main/java/com/wenhao/record/ui/dashboard/DashboardUiController.kt` — 将 Dashboard 状态从自动记录运行态收敛为手动记录过渡态文案，避免继续暗示自动决策正在接管。
- `app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt` — 为历史记录落库和读取补充开始 / 结束来源、手动边界时间字段。
- `app/src/main/java/com/wenhao/record/data/local/history/HistoryEntities.kt` — 为 Room 历史记录实体增加来源字段和手动边界字段。
- `app/src/main/java/com/wenhao/record/data/local/TrackDatabase.kt` — 提升数据库版本并注册历史记录字段变更所需 migration。
- `app/src/main/java/com/wenhao/record/data/tracking/TrainingSampleExporter.kt` — 新增手动闭环记录段导出逻辑，并过滤掉旧自动记录数据。
- `app/src/main/java/com/wenhao/record/data/tracking/TrainingSampleExportCodec.kt` — 扩展 JSONL 输出结构，加入手动来源字段与边界时间。
- `app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt` — 停用自动开始 / 自动结束触发与后台智能记录启动入口，但先保留底层实现代码。
- `app/src/main/java/com/wenhao/record/tracking/BootCompletedReceiver.kt` — 禁止开机后自动恢复后台智能记录。
- `tools/decision-model/train_decision_models.py` — 新增手动边界 JSONL 读取与窗口展开逻辑，只使用双手动闭环记录段构造训练行。
- `tools/decision-model/tests/test_train_decision_models.py` — 覆盖手动边界窗口展开与训练样本过滤规则。
- `docs/superpowers/specs/decision-model-export-format.md` — 更新导出契约，说明新的手动边界字段和样本来源。

### 将新增的文件

- `app/src/main/java/com/wenhao/record/data/history/TrackRecordSource.kt` — 定义 `MANUAL / AUTO / UNKNOWN` 的来源枚举及其序列化规则。
- `app/src/test/java/com/wenhao/record/data/history/HistoryStorageSourceMetadataTest.kt` — 验证历史记录来源字段与手动边界时间的读写。
- `app/src/test/java/com/wenhao/record/data/tracking/ManualBoundaryTrainingSampleExporterTest.kt` — 验证只有双手动闭环记录段会进入训练导出。

### 可能需要查阅的文件

- `app/src/main/java/com/wenhao/record/ui/history/HistoryComposeScreen.kt` — 参考历史页导出入口与文案位置。
- `app/src/main/java/com/wenhao/record/data/history/HistoryItem.kt` — 确认历史记录模型字段和复制语义。
- `docs/superpowers/specs/2026-04-17-manual-boundary-labeling-transition-design.md` — 本计划的规格来源，逐项对照实现。

---

### 任务 1：先锁定历史记录来源字段与边界时间模型

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/data/history/TrackRecordSource.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/history/HistoryItem.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/local/history/HistoryEntities.kt`
- 测试：`app/src/test/java/com/wenhao/record/data/history/HistoryStorageSourceMetadataTest.kt`

- [ ] **步骤 1：编写失败的测试，定义历史记录必须携带来源字段**

```kotlin
package com.wenhao.record.data.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryStorageSourceMetadataTest {
    @Test
    fun `history item keeps manual boundary metadata`() {
        val item = HistoryItem(
            id = 1L,
            timestamp = 1_700_000_000_000L,
            distanceKm = 1.2,
            durationSeconds = 180,
            averageSpeedKmh = 24.0,
            points = emptyList(),
            title = "测试记录",
            startSource = TrackRecordSource.MANUAL,
            stopSource = TrackRecordSource.MANUAL,
            manualStartAt = 1_700_000_000_000L,
            manualStopAt = 1_700_000_180_000L,
        )

        assertEquals(TrackRecordSource.MANUAL, item.startSource)
        assertEquals(TrackRecordSource.MANUAL, item.stopSource)
        assertEquals(1_700_000_000_000L, item.manualStartAt)
        assertEquals(1_700_000_180_000L, item.manualStopAt)
    }

    @Test
    fun `history item defaults unknown sources for legacy records`() {
        val item = HistoryItem(
            id = 2L,
            timestamp = 1_700_000_000_000L,
            distanceKm = 0.8,
            durationSeconds = 90,
            averageSpeedKmh = 12.0,
            points = emptyList(),
            title = null,
        )

        assertEquals(TrackRecordSource.UNKNOWN, item.startSource)
        assertEquals(TrackRecordSource.UNKNOWN, item.stopSource)
        assertNull(item.manualStartAt)
        assertNull(item.manualStopAt)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
sh gradlew testDebugUnitTest --tests com.wenhao.record.data.history.HistoryStorageSourceMetadataTest
```

预期：FAIL，报错 `Unresolved reference: TrackRecordSource` 或 `HistoryItem` 缺少新字段。

- [ ] **步骤 3：补充来源枚举与 HistoryItem 字段**

在 `TrackRecordSource.kt` 中新增：

```kotlin
package com.wenhao.record.data.history

enum class TrackRecordSource {
    MANUAL,
    AUTO,
    UNKNOWN;

    companion object {
        fun fromStorage(value: String?): TrackRecordSource {
            return entries.firstOrNull { it.name == value } ?: UNKNOWN
        }
    }
}
```

在 `HistoryItem.kt` 中补充字段：

```kotlin
data class HistoryItem(
    val id: Long,
    val timestamp: Long,
    val distanceKm: Double,
    val durationSeconds: Int,
    val averageSpeedKmh: Double,
    val points: List<TrackPoint>,
    val title: String? = null,
    val startSource: TrackRecordSource = TrackRecordSource.UNKNOWN,
    val stopSource: TrackRecordSource = TrackRecordSource.UNKNOWN,
    val manualStartAt: Long? = null,
    val manualStopAt: Long? = null,
)
```

- [ ] **步骤 4：让 Room 实体对齐新字段**

在 `HistoryEntities.kt` 的 `HistoryRecordEntity` 中增加：

```kotlin
    val startSource: String,
    val stopSource: String,
    val manualStartAt: Long?,
    val manualStopAt: Long?,
```

并确保实体与模型互转时显式调用：

```kotlin
startSource = item.startSource.name,
stopSource = item.stopSource.name,
manualStartAt = item.manualStartAt,
manualStopAt = item.manualStopAt,
```

- [ ] **步骤 5：运行测试验证通过**

运行：
```bash
sh gradlew testDebugUnitTest --tests com.wenhao.record.data.history.HistoryStorageSourceMetadataTest
```

预期：PASS。

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/java/com/wenhao/record/data/history/TrackRecordSource.kt \
  app/src/main/java/com/wenhao/record/data/history/HistoryItem.kt \
  app/src/main/java/com/wenhao/record/data/local/history/HistoryEntities.kt \
  app/src/test/java/com/wenhao/record/data/history/HistoryStorageSourceMetadataTest.kt
git commit -m "feat(历史记录): 添加手动边界来源字段"
```

### 任务 2：补齐历史存储与数据库迁移

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/local/TrackDatabase.kt`
- 测试：`app/src/test/java/com/wenhao/record/data/history/HistoryStorageSourceMetadataTest.kt`

- [ ] **步骤 1：扩展 HistoryStorage 的落库映射**

在 `HistoryStorage.save()` 和 `HistoryStorage.add()` 的记录实体映射中，把新字段写入：

```kotlin
HistoryRecordEntity(
    historyId = item.id,
    timestamp = item.timestamp,
    distanceKm = item.distanceKm,
    durationSeconds = item.durationSeconds,
    averageSpeedKmh = item.averageSpeedKmh,
    title = item.title,
    startSource = item.startSource.name,
    stopSource = item.stopSource.name,
    manualStartAt = item.manualStartAt,
    manualStopAt = item.manualStopAt,
)
```

- [ ] **步骤 2：扩展 HistoryStorage 的回读映射**

在 `HistoryRecordEntity -> HistoryItem` 转换中使用：

```kotlin
startSource = TrackRecordSource.fromStorage(record.startSource),
stopSource = TrackRecordSource.fromStorage(record.stopSource),
manualStartAt = record.manualStartAt,
manualStopAt = record.manualStopAt,
```

- [ ] **步骤 3：增加数据库 migration**

在 `TrackDatabase.kt` 中提升版本号，并新增 migration：

```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE history_record ADD COLUMN startSource TEXT NOT NULL DEFAULT 'UNKNOWN'"
        )
        database.execSQL(
            "ALTER TABLE history_record ADD COLUMN stopSource TEXT NOT NULL DEFAULT 'UNKNOWN'"
        )
        database.execSQL(
            "ALTER TABLE history_record ADD COLUMN manualStartAt INTEGER"
        )
        database.execSQL(
            "ALTER TABLE history_record ADD COLUMN manualStopAt INTEGER"
        )
    }
}
```

- [ ] **步骤 4：运行相关单测**

运行：
```bash
sh gradlew testDebugUnitTest --tests com.wenhao.record.data.history.HistoryStorageSourceMetadataTest
```

预期：PASS。

- [ ] **步骤 5：编译验证 Room 与 migration**

运行：
```bash
sh gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt \
  app/src/main/java/com/wenhao/record/data/local/TrackDatabase.kt
git commit -m "feat(存储): 持久化手动边界元数据"
```

### 任务 3：停用自动记录入口与自动运行链路

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/permissions/PermissionHelper.kt`
- 修改：`app/src/main/java/com/wenhao/record/RecordApplication.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/BootCompletedReceiver.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/dashboard/DashboardUiController.kt`
- 测试：`app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServicePhasePolicyTest.kt`

- [ ] **步骤 1：编写失败的服务停用测试**

在 `BackgroundTrackingServicePhasePolicyTest.kt` 中增加一个意图测试占位，先锁定过渡期常量：

```kotlin
@Test
fun `manual boundary mode disables automatic decision promotion`() {
    assertFalse(BackgroundTrackingService.isAutomaticTrackingEnabled())
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
sh gradlew testDebugUnitTest --tests com.wenhao.record.tracking.BackgroundTrackingServicePhasePolicyTest
```

预期：FAIL，报错 `Unresolved reference: isAutomaticTrackingEnabled`。

- [ ] **步骤 3：在后台服务中增加过渡期开关并拦截自动触发**

在 `BackgroundTrackingService.kt` 中新增：

```kotlin
companion object {
    internal fun isAutomaticTrackingEnabled(): Boolean = false
}
```

并在自动开始 / 自动结束入口前短路：

```kotlin
if (!isAutomaticTrackingEnabled()) return
```

至少覆盖：

```kotlin
onStart = {
    if (isAutomaticTrackingEnabled()) {
        maybePromoteToSuspect("模型建议开始记录")
    }
}
onStop = {
    if (isAutomaticTrackingEnabled()) {
        maybePromoteToStopping("模型建议结束记录")
    }
}
```

- [ ] **步骤 4：停掉自动启动路径**

做以下收敛：

```kotlin
// PermissionHelper
fun ensureSmartTrackingEnabled() {
    onRefreshDashboard()
}

fun startBackgroundTrackingServiceIfReady() = Unit
```

```kotlin
// RecordApplication
override fun onCreate() {
    super.onCreate()
    HistoryStorage.warmUp(this)
}
```

```kotlin
// BootCompletedReceiver
override fun onReceive(context: Context, intent: Intent?) = Unit
```

- [ ] **步骤 5：隐藏自动记录 UI 入口与暗示性文案**

在 `MainComposeScreen.kt` 与 `DashboardUiController.kt` 中移除或替换：

```kotlin
text = "智能记录"
text = "正在智能记录行程"
text = "后台智能记录已停止"
```

改成不暗示当前仍由模型接管的手动模式文案，例如：

```kotlin
title = "手动记录"
meta = "当前阶段仅支持手动开始与结束"
status = "自动记录已停用"
```

- [ ] **步骤 6：运行测试验证通过**

运行：
```bash
sh gradlew testDebugUnitTest --tests com.wenhao.record.tracking.BackgroundTrackingServicePhasePolicyTest
```

预期：PASS。

- [ ] **步骤 7：编译验证运行时改动**

运行：
```bash
sh gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 8：Commit**

```bash
git add app/src/main/java/com/wenhao/record/permissions/PermissionHelper.kt \
  app/src/main/java/com/wenhao/record/RecordApplication.kt \
  app/src/main/java/com/wenhao/record/tracking/BootCompletedReceiver.kt \
  app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt \
  app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt \
  app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt \
  app/src/main/java/com/wenhao/record/ui/dashboard/DashboardUiController.kt \
  app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServicePhasePolicyTest.kt
git commit -m "refactor(记录): 停用自动记录过渡到手动模式"
```

### 任务 4：让手动开始与手动结束写入边界来源元数据

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt`
- 测试：`app/src/test/java/com/wenhao/record/data/history/HistoryStorageSourceMetadataTest.kt`

- [ ] **步骤 1：定位手动开始 / 结束入口并补测试样本**

在 `HistoryStorageSourceMetadataTest.kt` 中新增一个纯模型测试，约束手动记录段元数据：

```kotlin
@Test
fun `manual record segment carries manual sources on both ends`() {
    val item = HistoryItem(
        id = 3L,
        timestamp = 1_700_000_000_000L,
        distanceKm = 2.0,
        durationSeconds = 600,
        averageSpeedKmh = 12.0,
        points = emptyList(),
        startSource = TrackRecordSource.MANUAL,
        stopSource = TrackRecordSource.MANUAL,
        manualStartAt = 1_700_000_000_000L,
        manualStopAt = 1_700_000_600_000L,
    )

    assertEquals(TrackRecordSource.MANUAL, item.startSource)
    assertEquals(TrackRecordSource.MANUAL, item.stopSource)
}
```

- [ ] **步骤 2：运行测试验证当前手动链路尚未落字段**

运行：
```bash
sh gradlew testDebugUnitTest --tests com.wenhao.record.data.history.HistoryStorageSourceMetadataTest
```

预期：若前面任务已完成则 PASS；此步骤主要作为回归门。

- [ ] **步骤 3：在 MainActivity 的手动开始逻辑写入开始来源**

在实际创建手动记录段的代码处，构造 `HistoryItem` 或中间模型时显式带上：

```kotlin
startSource = TrackRecordSource.MANUAL,
manualStartAt = System.currentTimeMillis(),
stopSource = TrackRecordSource.UNKNOWN,
manualStopAt = null,
```

- [ ] **步骤 4：在手动结束逻辑补齐结束来源**

在结束时更新对应记录：

```kotlin
stopSource = TrackRecordSource.MANUAL,
manualStopAt = System.currentTimeMillis(),
```

若结束逻辑是生成最终 `HistoryItem`，则完整写成：

```kotlin
HistoryItem(
    id = historyId,
    timestamp = startTimestamp,
    distanceKm = distanceKm,
    durationSeconds = durationSeconds,
    averageSpeedKmh = averageSpeedKmh,
    points = points,
    title = title,
    startSource = TrackRecordSource.MANUAL,
    stopSource = TrackRecordSource.MANUAL,
    manualStartAt = startTimestamp,
    manualStopAt = endTimestamp,
)
```

- [ ] **步骤 5：运行相关测试**

运行：
```bash
sh gradlew testDebugUnitTest --tests com.wenhao.record.data.history.HistoryStorageSourceMetadataTest
```

预期：PASS。

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt \
  app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt \
  app/src/test/java/com/wenhao/record/data/history/HistoryStorageSourceMetadataTest.kt
git commit -m "feat(记录): 写入手动开始结束来源"
```

### 任务 5：新增手动闭环训练样本导出

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TrainingSampleExporter.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TrainingSampleExportCodec.kt`
- 创建：`app/src/test/java/com/wenhao/record/data/tracking/ManualBoundaryTrainingSampleExporterTest.kt`

- [ ] **步骤 1：编写失败的导出测试**

```kotlin
package com.wenhao.record.data.tracking

import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.history.TrackRecordSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManualBoundaryTrainingSampleExporterTest {
    @Test
    fun `exports only fully manual record segments`() {
        val rows = TrainingSampleExporter.filterManualBoundaryItems(
            listOf(
                HistoryItem(
                    id = 1L,
                    timestamp = 1000L,
                    distanceKm = 1.0,
                    durationSeconds = 120,
                    averageSpeedKmh = 10.0,
                    points = emptyList(),
                    startSource = TrackRecordSource.MANUAL,
                    stopSource = TrackRecordSource.MANUAL,
                    manualStartAt = 1000L,
                    manualStopAt = 121000L,
                ),
                HistoryItem(
                    id = 2L,
                    timestamp = 2000L,
                    distanceKm = 1.0,
                    durationSeconds = 120,
                    averageSpeedKmh = 10.0,
                    points = emptyList(),
                    startSource = TrackRecordSource.AUTO,
                    stopSource = TrackRecordSource.AUTO,
                    manualStartAt = null,
                    manualStopAt = null,
                )
            )
        )

        assertEquals(1, rows.size)
        assertTrue(rows.all { it.startSource == "MANUAL" && it.stopSource == "MANUAL" })
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
sh gradlew testDebugUnitTest --tests com.wenhao.record.data.tracking.ManualBoundaryTrainingSampleExporterTest
```

预期：FAIL，报错 `Unresolved reference: filterManualBoundaryItems` 或导出行缺少来源字段。

- [ ] **步骤 3：扩展导出行模型**

在 `TrainingSampleExporter.kt` 中为导出行补充：

```kotlin
val startSource: String,
val stopSource: String,
val manualStartAt: Long?,
val manualStopAt: Long?,
val recordId: Long?,
```

并新增手动闭环过滤函数：

```kotlin
internal fun filterManualBoundaryItems(items: List<HistoryItem>): List<HistoryItem> {
    return items.filter { item ->
        item.startSource == TrackRecordSource.MANUAL &&
            item.stopSource == TrackRecordSource.MANUAL &&
            item.manualStartAt != null &&
            item.manualStopAt != null
    }
}
```

- [ ] **步骤 4：新增历史记录导出路径**

在 `exportRows(context)` 中加入历史记录读取并映射：

```kotlin
val manualRows = HistoryStorage.load(context)
    .let(::filterManualBoundaryItems)
    .map { item ->
        TrainingSampleRow(
            eventId = -1L,
            recordId = item.id,
            timestampMillis = item.timestamp,
            phase = "MANUAL_SEGMENT",
            isRecording = false,
            startScore = 0.0,
            stopScore = 0.0,
            finalDecision = "MANUAL_SEGMENT",
            gpsQualityPass = false,
            motionEvidencePass = false,
            frequentPlaceClearPass = false,
            feedbackEligible = false,
            feedbackBlockedReason = null,
            features = emptyMap(),
            feedbackLabel = null,
            startSource = item.startSource.name,
            stopSource = item.stopSource.name,
            manualStartAt = item.manualStartAt,
            manualStopAt = item.manualStopAt,
        )
    }
```

并把现有自动决策事件行与新行合并输出。

- [ ] **步骤 5：扩展 JSONL 编码**

在 `TrainingSampleExportCodec.kt` 中增加：

```kotlin
put("recordId", row.recordId)
put("startSource", row.startSource)
put("stopSource", row.stopSource)
put("manualStartAt", row.manualStartAt)
put("manualStopAt", row.manualStopAt)
```

- [ ] **步骤 6：运行测试验证通过**

运行：
```bash
sh gradlew testDebugUnitTest --tests com.wenhao.record.data.tracking.ManualBoundaryTrainingSampleExporterTest
```

预期：PASS。

- [ ] **步骤 7：运行现有导出相关测试回归**

运行：
```bash
sh gradlew testDebugUnitTest --tests com.wenhao.record.data.tracking.TrainingSampleExportCodecTest --tests com.wenhao.record.data.tracking.DecisionEventStorageTest
```

预期：PASS。

- [ ] **步骤 8：Commit**

```bash
git add app/src/main/java/com/wenhao/record/data/tracking/TrainingSampleExporter.kt \
  app/src/main/java/com/wenhao/record/data/tracking/TrainingSampleExportCodec.kt \
  app/src/test/java/com/wenhao/record/data/tracking/ManualBoundaryTrainingSampleExporterTest.kt
git commit -m "feat(训练样本): 导出手动闭环记录段"
```

### 任务 6：更新 Python 训练脚本，按手动边界窗口生成样本

**文件：**
- 修改：`tools/decision-model/train_decision_models.py`
- 修改：`tools/decision-model/tests/test_train_decision_models.py`
- 修改：`docs/superpowers/specs/decision-model-export-format.md`

- [ ] **步骤 1：编写失败的 Python 测试**

在 `tools/decision-model/tests/test_train_decision_models.py` 中增加：

```python
def test_build_training_rows_from_manual_boundary_segments():
    rows = [
        {
            "recordId": 1,
            "timestampMillis": 1700000000000,
            "startSource": "MANUAL",
            "stopSource": "MANUAL",
            "manualStartAt": 1700000000000,
            "manualStopAt": 1700000180000,
            "features": {},
        },
        {
            "recordId": 2,
            "timestampMillis": 1700001000000,
            "startSource": "AUTO",
            "stopSource": "AUTO",
            "manualStartAt": None,
            "manualStopAt": None,
            "features": {},
        },
    ]

    training_rows = build_manual_boundary_rows(rows)

    assert len(training_rows) == 1
    assert training_rows[0]["record_id"] == 1
    assert training_rows[0]["start_window_start"] == 1700000000000 - 30000
    assert training_rows[0]["start_window_end"] == 1700000000000 + 60000
    assert training_rows[0]["stop_window_start"] == 1700000180000 - 30000
    assert training_rows[0]["stop_window_end"] == 1700000180000 + 60000
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
python3 -m pytest tools/decision-model/tests/test_train_decision_models.py -q
```

预期：FAIL，报错 `NameError: name 'build_manual_boundary_rows' is not defined`。

- [ ] **步骤 3：在训练脚本中新增手动闭环过滤与窗口展开**

在 `train_decision_models.py` 中增加：

```python
def build_manual_boundary_rows(rows):
    output = []
    for row in rows:
        if row.get("startSource") != "MANUAL":
            continue
        if row.get("stopSource") != "MANUAL":
            continue
        manual_start_at = row.get("manualStartAt")
        manual_stop_at = row.get("manualStopAt")
        if manual_start_at is None or manual_stop_at is None:
            continue
        output.append(
            {
                "record_id": row.get("recordId"),
                "start_window_start": manual_start_at - 30_000,
                "start_window_end": manual_start_at + 60_000,
                "stop_window_start": manual_stop_at - 30_000,
                "stop_window_end": manual_stop_at + 60_000,
                "source": "manual_boundary",
            }
        )
    return output
```

- [ ] **步骤 4：把新导出契约写进规格文档**

在 `decision-model-export-format.md` 中补充一节：

```md
## 手动闭环记录段字段

- `recordId`
- `startSource`
- `stopSource`
- `manualStartAt`
- `manualStopAt`

当且仅当 `startSource = MANUAL` 且 `stopSource = MANUAL` 时，
训练脚本才会将该记录段展开为：

- `start_window_start = manualStartAt - 30000`
- `start_window_end = manualStartAt + 60000`
- `stop_window_start = manualStopAt - 30000`
- `stop_window_end = manualStopAt + 60000`
```

- [ ] **步骤 5：运行测试验证通过**

运行：
```bash
python3 -m pytest tools/decision-model/tests/test_train_decision_models.py -q
```

预期：PASS。

- [ ] **步骤 6：Commit**

```bash
git add tools/decision-model/train_decision_models.py \
  tools/decision-model/tests/test_train_decision_models.py \
  docs/superpowers/specs/decision-model-export-format.md
git commit -m "feat(训练): 基于手动边界生成窗口样本"
```

### 任务 7：最终回归验证与文档收口

**文件：**
- 修改：必要时更新受影响文案文件
- 测试：Android 单测、Python 测试、Debug 构建

- [ ] **步骤 1：运行 Android 相关测试集合**

运行：
```bash
sh gradlew testDebugUnitTest \
  --tests com.wenhao.record.data.history.HistoryStorageSourceMetadataTest \
  --tests com.wenhao.record.data.tracking.ManualBoundaryTrainingSampleExporterTest \
  --tests com.wenhao.record.data.tracking.TrainingSampleExportCodecTest \
  --tests com.wenhao.record.tracking.BackgroundTrackingServicePhasePolicyTest
```

预期：PASS。

- [ ] **步骤 2：运行 Python 训练测试**

运行：
```bash
python3 -m pytest tools/decision-model/tests/test_train_decision_models.py -q
```

预期：PASS。

- [ ] **步骤 3：运行完整 Debug 构建**

运行：
```bash
sh gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 4：检查工作区与差异**

运行：
```bash
git status --short
git diff --stat
```

预期：只包含本计划涉及的文件，没有无关修改。

- [ ] **步骤 5：最终 Commit**

```bash
git add app/src/main/java/com/wenhao/record \
  app/src/test/java/com/wenhao/record \
  tools/decision-model \
  docs/superpowers/specs/decision-model-export-format.md
git commit -m "feat(训练): 切换到手动边界打标过渡模式"
```

---

## 自检

- 规格覆盖度：
  - 自动记录入口停用：任务 3
  - 旧自动记录数据继续展示但训练忽略：任务 5、任务 6
  - 在现有记录模型中补来源字段：任务 1、任务 2
  - 只保留双手动闭环记录段：任务 4、任务 5
  - `start / stop` 边界窗口：任务 6
- 占位符扫描：计划中没有 `TODO`、`待定`、`后续补充` 之类占位符。
- 类型一致性：
  - 统一使用 `TrackRecordSource`
  - 统一使用 `manualStartAt / manualStopAt`
  - 统一使用 `recordId / startSource / stopSource`

## 执行交接

计划已完成并保存到 `docs/superpowers/plans/2026-04-17-manual-boundary-labeling-transition-plan.md`。两种执行方式：

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

选哪种方式？
