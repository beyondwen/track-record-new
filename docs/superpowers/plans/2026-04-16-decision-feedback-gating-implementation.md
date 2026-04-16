# 自动开始/结束硬门槛与待标记列表筛选实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为自动开始、自动结束和历史页待标记列表补上保守型硬门槛，让低质量事件不再触发自动开始，也不再进入待标记反馈列表。

**架构：** 在现有 `SignalSnapshot -> FeatureWindowAggregator -> TrackingDecisionEngine -> DecisionEventStorage -> HistoryController` 链路中新增一层轻量门槛评估器。自动开始采用严格一票否决，自动结束采用不对称保守门槛，最终通过事件落库字段把“是否允许反馈”和“阻断原因”传到历史页列表。

**技术栈：** Kotlin、Android Service、Room、Jetpack Compose、JUnit4、Robolectric

---

## 文件结构

**创建：**

- `app/src/main/java/com/wenhao/record/tracking/decision/DecisionGateBlockReason.kt`
  - 定义硬门槛阻断原因枚举。
- `app/src/main/java/com/wenhao/record/tracking/decision/DecisionGateResult.kt`
  - 定义开始门槛、结束门槛、反馈门槛的统一结果模型。
- `app/src/main/java/com/wenhao/record/tracking/decision/DecisionGateEvaluator.kt`
  - 基于当前窗口特征和上下文判断门槛是否通过。
- `app/src/test/java/com/wenhao/record/tracking/decision/DecisionGateEvaluatorTest.kt`
  - 覆盖 GPS 缺失、运动证据缺失、常驻地点阻断、结束反馈筛选。

**修改：**

- `app/src/main/java/com/wenhao/record/tracking/pipeline/SignalSnapshot.kt`
  - 为门槛评估补充原始证据字段。
- `app/src/main/java/com/wenhao/record/tracking/pipeline/FeatureVector.kt`
  - 挂载门槛评估需要的上下文摘要。
- `app/src/main/java/com/wenhao/record/tracking/pipeline/FeatureWindowAggregator.kt`
  - 产出 GPS 质量、运动证据、常驻地点等门槛输入。
- `app/src/main/java/com/wenhao/record/tracking/decision/TrackingDecisionEngine.kt`
  - 在现有模型分数和平滑器结果之外，返回门槛评估结果。
- `app/src/main/java/com/wenhao/record/tracking/decision/DecisionRuntimeCoordinator.kt`
  - 只在门槛允许时触发自动开始/自动结束动作。
- `app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt`
  - 组装门槛输入，接入门槛评估，阻断低质量自动开始，并保留可结束但不可反馈的结束事件。
- `app/src/main/java/com/wenhao/record/data/local/decision/DecisionEntities.kt`
  - 为事件表新增门槛结果和反馈可见性字段。
- `app/src/main/java/com/wenhao/record/data/local/decision/DecisionDao.kt`
  - 列表查询只返回 `feedbackEligible = 1` 的事件。
- `app/src/main/java/com/wenhao/record/data/local/TrackDatabase.kt`
  - 增加 Room 迁移。
- `app/src/main/java/com/wenhao/record/data/tracking/DecisionEventStorage.kt`
  - 存取门槛结果，读取时带上反馈可见性。
- `app/src/main/java/com/wenhao/record/data/tracking/TrainingSampleExporter.kt`
  - 导出门槛结果和阻断原因，便于训练侧过滤。
- `app/src/main/java/com/wenhao/record/ui/history/HistoryController.kt`
  - 历史页只接收通过反馈门槛的事件。
- `app/src/test/java/com/wenhao/record/data/tracking/DecisionEventStorageTest.kt`
  - 补充门槛字段落库与读取测试。
- `app/src/test/java/com/wenhao/record/ui/history/HistoryControllerTest.kt`
  - 验证历史页只显示 `feedbackEligible = true` 的事件。

## 任务 1：建立门槛结果模型与评估器

**文件：**

- 创建：`app/src/main/java/com/wenhao/record/tracking/decision/DecisionGateBlockReason.kt`
- 创建：`app/src/main/java/com/wenhao/record/tracking/decision/DecisionGateResult.kt`
- 创建：`app/src/main/java/com/wenhao/record/tracking/decision/DecisionGateEvaluator.kt`
- 测试：`app/src/test/java/com/wenhao/record/tracking/decision/DecisionGateEvaluatorTest.kt`

- [ ] **步骤 1：先写门槛评估失败测试**

```kotlin
class DecisionGateEvaluatorTest {

    @Test
    fun `start gate fails when gps is missing`() {
        val result = DecisionGateEvaluator.evaluate(
            DecisionGateInput(
                gpsSampleCount30s = 0.0,
                gpsAccuracyAvg30s = 0.0,
                motionEvidence30s = false,
                insideFrequentPlace = false,
                isRecording = false,
                stopScore = 0.0,
                startScore = 0.92,
                recordingDurationSeconds = 0.0,
                stopObservationPassed = false,
            )
        )

        assertFalse(result.startEligible)
        assertEquals(DecisionGateBlockReason.GPS_MISSING, result.startBlockedReason)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`sh gradlew testDebugUnitTest --no-daemon --tests "com.wenhao.record.tracking.decision.DecisionGateEvaluatorTest"`

预期：FAIL，报错 `Unresolved reference: DecisionGateEvaluator`

- [ ] **步骤 3：定义阻断原因和门槛结果模型**

```kotlin
enum class DecisionGateBlockReason {
    GPS_MISSING,
    GPS_POOR_ACCURACY,
    MOTION_MISSING,
    INSIDE_FREQUENT_PLACE,
    STOP_LOW_CONFIDENCE,
    FEEDBACK_BLOCKED_LOW_QUALITY,
}

data class DecisionGateInput(
    val gpsSampleCount30s: Double,
    val gpsAccuracyAvg30s: Double,
    val motionEvidence30s: Boolean,
    val insideFrequentPlace: Boolean,
    val isRecording: Boolean,
    val startScore: Double,
    val stopScore: Double,
    val recordingDurationSeconds: Double,
    val stopObservationPassed: Boolean,
)

data class DecisionGateResult(
    val startEligible: Boolean,
    val stopEligible: Boolean,
    val startFeedbackEligible: Boolean,
    val stopFeedbackEligible: Boolean,
    val startBlockedReason: DecisionGateBlockReason?,
    val stopBlockedReason: DecisionGateBlockReason?,
    val feedbackBlockedReason: DecisionGateBlockReason?,
)
```

- [ ] **步骤 4：实现保守型硬门槛评估器**

```kotlin
object DecisionGateEvaluator {
    private const val MIN_GPS_SAMPLES_30S = 2.0
    private const val MAX_GOOD_ACCURACY_METERS = 35.0
    private const val MIN_RECORDING_SECONDS_FOR_STOP = 120.0

    fun evaluate(input: DecisionGateInput): DecisionGateResult {
        val gpsPass = input.gpsSampleCount30s >= MIN_GPS_SAMPLES_30S &&
            input.gpsAccuracyAvg30s in 0.0..MAX_GOOD_ACCURACY_METERS
        val gpsBlockedReason = when {
            input.gpsSampleCount30s < MIN_GPS_SAMPLES_30S -> DecisionGateBlockReason.GPS_MISSING
            !gpsPass -> DecisionGateBlockReason.GPS_POOR_ACCURACY
            else -> null
        }
        val motionPass = input.motionEvidence30s
        val placePass = !input.insideFrequentPlace

        val startBlocked = gpsBlockedReason
            ?: if (!motionPass) DecisionGateBlockReason.MOTION_MISSING else null
            ?: if (!placePass) DecisionGateBlockReason.INSIDE_FREQUENT_PLACE else null

        val stopEligible = input.isRecording &&
            input.recordingDurationSeconds >= MIN_RECORDING_SECONDS_FOR_STOP &&
            input.stopObservationPassed

        val stopFeedbackEligible = stopEligible && gpsPass && motionPass

        return DecisionGateResult(
            startEligible = startBlocked == null,
            stopEligible = stopEligible,
            startFeedbackEligible = startBlocked == null,
            stopFeedbackEligible = stopFeedbackEligible,
            startBlockedReason = startBlocked,
            stopBlockedReason = if (stopEligible) null else DecisionGateBlockReason.STOP_LOW_CONFIDENCE,
            feedbackBlockedReason = if (stopFeedbackEligible || startBlocked == null) {
                null
            } else {
                DecisionGateBlockReason.FEEDBACK_BLOCKED_LOW_QUALITY
            },
        )
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：`sh gradlew testDebugUnitTest --no-daemon --tests "com.wenhao.record.tracking.decision.DecisionGateEvaluatorTest"`

预期：PASS

- [ ] **步骤 6：提交本任务**

```bash
git add \
  app/src/main/java/com/wenhao/record/tracking/decision/DecisionGateBlockReason.kt \
  app/src/main/java/com/wenhao/record/tracking/decision/DecisionGateResult.kt \
  app/src/main/java/com/wenhao/record/tracking/decision/DecisionGateEvaluator.kt \
  app/src/test/java/com/wenhao/record/tracking/decision/DecisionGateEvaluatorTest.kt
git commit -m "feat(决策门槛): 添加硬门槛评估器"
```

## 任务 2：扩展窗口特征，为门槛评估提供输入

**文件：**

- 修改：`app/src/main/java/com/wenhao/record/tracking/pipeline/SignalSnapshot.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/pipeline/FeatureVector.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/pipeline/FeatureWindowAggregator.kt`
- 测试：`app/src/test/java/com/wenhao/record/tracking/pipeline/FeatureWindowAggregatorTest.kt`

- [ ] **步骤 1：补充失败测试，锁定门槛输入字段**

```kotlin
@Test
fun `build vector includes gate inputs`() {
    val aggregator = FeatureWindowAggregator(clock = { 180_000L })
    aggregator.append(
        SignalSnapshot(
            timestampMillis = 160_000L,
            phase = TrackingPhase.SUSPECT_MOVING,
            isRecording = false,
            latitude = 39.9,
            longitude = 116.4,
            accuracyMeters = 18f,
            speedMetersPerSecond = 1.8f,
            stepDelta = 5,
            accelerationMagnitude = 1.2f,
            wifiChanged = true,
            insideFrequentPlace = false,
            candidateStateDurationMillis = 30_000L,
            protectionRemainingMillis = 0L,
        )
    )

    val vector = aggregator.buildVector()!!

    assertEquals(1.0, vector.features.getValue("gps_sample_count_30s"), 0.0001)
    assertEquals(1.0, vector.features.getValue("motion_evidence_30s"), 0.0001)
    assertEquals(0.0, vector.features.getValue("inside_frequent_place_current"), 0.0001)
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`sh gradlew testDebugUnitTest --no-daemon --tests "com.wenhao.record.tracking.pipeline.FeatureWindowAggregatorTest"`

预期：FAIL，缺少新特征字段

- [ ] **步骤 3：为快照和向量补充门槛所需字段**

```kotlin
data class FeatureVector(
    val timestampMillis: Long,
    val features: Map<String, Double>,
    val isRecording: Boolean,
    val phase: TrackingPhase,
    val gateInput: DecisionGateInput,
)
```

- [ ] **步骤 4：在聚合器中计算 GPS 样本、运动证据和常驻地点状态**

```kotlin
features["gps_sample_count_30s"] = windowSnapshots.count { it.accuracyMeters != null }.toDouble()
features["motion_evidence_30s"] = if (
    windowSnapshots.any { it.stepDelta > 0 || (it.accelerationMagnitude ?: 0f) >= 0.85f }
) 1.0 else 0.0
features["inside_frequent_place_current"] = if (latest.insideFrequentPlace) 1.0 else 0.0
```

- [ ] **步骤 5：在 `buildVector()` 中直接组装 `DecisionGateInput`**

```kotlin
val gateInput = DecisionGateInput(
    gpsSampleCount30s = features.getValue("gps_sample_count_30s"),
    gpsAccuracyAvg30s = features.getValue("accuracy_avg_30s"),
    motionEvidence30s = features.getValue("motion_evidence_30s") >= 1.0,
    insideFrequentPlace = latest.insideFrequentPlace,
    isRecording = latest.isRecording,
    startScore = 0.0,
    stopScore = 0.0,
    recordingDurationSeconds = latest.candidateStateDurationMillis / 1000.0,
    stopObservationPassed = latest.phase == TrackingPhase.SUSPECT_STOPPING,
)
```

- [ ] **步骤 6：运行测试验证通过**

运行：`sh gradlew testDebugUnitTest --no-daemon --tests "com.wenhao.record.tracking.pipeline.FeatureWindowAggregatorTest"`

预期：PASS

- [ ] **步骤 7：提交本任务**

```bash
git add \
  app/src/main/java/com/wenhao/record/tracking/pipeline/SignalSnapshot.kt \
  app/src/main/java/com/wenhao/record/tracking/pipeline/FeatureVector.kt \
  app/src/main/java/com/wenhao/record/tracking/pipeline/FeatureWindowAggregator.kt \
  app/src/test/java/com/wenhao/record/tracking/pipeline/FeatureWindowAggregatorTest.kt
git commit -m "feat(决策门槛): 扩展窗口特征输入"
```

## 任务 3：在决策引擎和服务层接入门槛

**文件：**

- 修改：`app/src/main/java/com/wenhao/record/tracking/decision/TrackingDecisionEngine.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/decision/DecisionRuntimeCoordinator.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt`
- 测试：`app/src/test/java/com/wenhao/record/tracking/decision/TrackingDecisionEngineTest.kt`
- 测试：`app/src/test/java/com/wenhao/record/tracking/decision/DecisionRuntimeCoordinatorTest.kt`

- [ ] **步骤 1：先写失败测试，锁定开始门槛阻断**

```kotlin
@Test
fun `engine blocks start when gate rejects event`() {
    val engine = TrackingDecisionEngine(
        startModel = StartDecisionModel(config),
        stopModel = StopDecisionModel(config),
        smoother = smoother,
    )
    val blockedVector = vector.copy(
        gateInput = vector.gateInput.copy(
            gpsSampleCount30s = 0.0,
            motionEvidence30s = false,
        )
    )

    val frame = engine.evaluate(blockedVector, nowMillis = 30_000L)

    assertEquals(FinalDecision.HOLD, frame.finalDecision)
    assertFalse(frame.gateResult.startEligible)
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`sh gradlew testDebugUnitTest --no-daemon --tests "com.wenhao.record.tracking.decision.TrackingDecisionEngineTest" --tests "com.wenhao.record.tracking.decision.DecisionRuntimeCoordinatorTest"`

预期：FAIL，`DecisionFrame` 缺少 `gateResult`

- [ ] **步骤 3：扩展 `DecisionFrame`，携带门槛结果**

```kotlin
data class DecisionFrame(
    val vector: FeatureVector,
    val startScore: Double,
    val stopScore: Double,
    val finalDecision: FinalDecision,
    val gateResult: DecisionGateResult,
)
```

- [ ] **步骤 4：在引擎中先评估门槛，再决定是否允许开始/结束动作**

```kotlin
val gateInput = vector.gateInput.copy(
    startScore = startScore,
    stopScore = stopScore,
    isRecording = vector.isRecording,
)
val gateResult = DecisionGateEvaluator.evaluate(gateInput)
val rawDecision = smoother.consume(...)
val finalDecision = when (rawDecision) {
    FinalDecision.START -> if (gateResult.startEligible) FinalDecision.START else FinalDecision.HOLD
    FinalDecision.STOP -> if (gateResult.stopEligible) FinalDecision.STOP else FinalDecision.HOLD
    FinalDecision.HOLD -> FinalDecision.HOLD
}
```

- [ ] **步骤 5：在运行时协调器中保留事件落库，但只在门槛通过时执行服务动作**

```kotlin
when (frame.finalDecision) {
    FinalDecision.START -> onStart()
    FinalDecision.STOP -> onStop()
    FinalDecision.HOLD -> Unit
}
```

本步骤不修改结构，只保证 `frame.gateResult` 后续可落库。

- [ ] **步骤 6：在服务层为 `SignalSnapshot` 填真实上下文**

```kotlin
SignalSnapshot(
    ...,
    insideFrequentPlace = insideAnchorIds.isNotEmpty(),
    candidateStateDurationMillis = (now - phaseEnteredAt).coerceAtLeast(0L),
    protectionRemainingMillis = 0L,
)
```

- [ ] **步骤 7：运行测试验证通过**

运行：`sh gradlew testDebugUnitTest --no-daemon --tests "com.wenhao.record.tracking.decision.TrackingDecisionEngineTest" --tests "com.wenhao.record.tracking.decision.DecisionRuntimeCoordinatorTest"`

预期：PASS

- [ ] **步骤 8：提交本任务**

```bash
git add \
  app/src/main/java/com/wenhao/record/tracking/decision/TrackingDecisionEngine.kt \
  app/src/main/java/com/wenhao/record/tracking/decision/DecisionRuntimeCoordinator.kt \
  app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt \
  app/src/test/java/com/wenhao/record/tracking/decision/TrackingDecisionEngineTest.kt \
  app/src/test/java/com/wenhao/record/tracking/decision/DecisionRuntimeCoordinatorTest.kt
git commit -m "feat(决策门槛): 接入自动开始结束准入逻辑"
```

## 任务 4：扩展事件存储和历史页列表筛选

**文件：**

- 修改：`app/src/main/java/com/wenhao/record/data/local/decision/DecisionEntities.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/local/decision/DecisionDao.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/local/TrackDatabase.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/DecisionEventStorage.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TrainingSampleExporter.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/history/HistoryController.kt`
- 测试：`app/src/test/java/com/wenhao/record/data/tracking/DecisionEventStorageTest.kt`
- 测试：`app/src/test/java/com/wenhao/record/ui/history/HistoryControllerTest.kt`

- [ ] **步骤 1：先写存储失败测试，锁定反馈可见性**

```kotlin
@Test
fun `only feedback eligible events are returned to history`() {
    DecisionEventStorage.saveFrame(context, startFrame.copy(gateResult = eligibleGate))
    DecisionEventStorage.saveFrame(context, stopFrame.copy(gateResult = blockedGate))

    val items = DecisionEventStorage.loadReviewItems(context)

    assertEquals(1, items.size)
    assertTrue(items.all { it.feedbackEligible })
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`sh gradlew testDebugUnitTest --no-daemon --tests "com.wenhao.record.data.tracking.DecisionEventStorageTest" --tests "com.wenhao.record.ui.history.HistoryControllerTest"`

预期：FAIL，实体和查询缺少 `feedbackEligible`

- [ ] **步骤 3：为 `decision_event` 增加门槛与反馈字段**

```kotlin
@Entity(tableName = "decision_event")
data class DecisionEventEntity(
    ...,
    val gpsQualityPass: Boolean,
    val motionEvidencePass: Boolean,
    val frequentPlaceClearPass: Boolean,
    val feedbackEligible: Boolean,
    val feedbackBlockedReason: String?,
)
```

- [ ] **步骤 4：更新 DAO 查询，只返回可反馈事件**

```kotlin
@Query(
    """
    SELECT ...
    FROM decision_event e
    WHERE e.finalDecision IN ('START', 'STOP')
      AND e.feedbackEligible = 1
    ORDER BY e.timestampMillis DESC
    LIMIT :limit
    """
)
fun getRecentDecisionEvents(limit: Int): List<DecisionEventWithFeedbackRow>
```

- [ ] **步骤 5：在 `DecisionEventStorage.saveFrame()` 中落库门槛结果**

```kotlin
DecisionEventEntity(
    ...,
    gpsQualityPass = frame.gateResult.startBlockedReason !in setOf(
        DecisionGateBlockReason.GPS_MISSING,
        DecisionGateBlockReason.GPS_POOR_ACCURACY,
    ),
    motionEvidencePass = frame.gateResult.startBlockedReason != DecisionGateBlockReason.MOTION_MISSING,
    frequentPlaceClearPass = frame.gateResult.startBlockedReason != DecisionGateBlockReason.INSIDE_FREQUENT_PLACE,
    feedbackEligible = when (frame.finalDecision) {
        FinalDecision.START -> frame.gateResult.startFeedbackEligible
        FinalDecision.STOP -> frame.gateResult.stopFeedbackEligible
        FinalDecision.HOLD -> false
    },
    feedbackBlockedReason = frame.gateResult.feedbackBlockedReason?.name,
)
```

- [ ] **步骤 6：让历史页只消费 `feedbackEligible = true` 的事件**

```kotlin
decisionFeedbackItems = DecisionEventStorage.loadReviewItems(context).map { item -> ... }
```

本步骤主要依赖 DAO 过滤，不额外在控制器重复筛选。

- [ ] **步骤 7：运行测试验证通过**

运行：`sh gradlew testDebugUnitTest --no-daemon --tests "com.wenhao.record.data.tracking.DecisionEventStorageTest" --tests "com.wenhao.record.ui.history.HistoryControllerTest"`

预期：PASS

- [ ] **步骤 8：提交本任务**

```bash
git add \
  app/src/main/java/com/wenhao/record/data/local/decision/DecisionEntities.kt \
  app/src/main/java/com/wenhao/record/data/local/decision/DecisionDao.kt \
  app/src/main/java/com/wenhao/record/data/local/TrackDatabase.kt \
  app/src/main/java/com/wenhao/record/data/tracking/DecisionEventStorage.kt \
  app/src/main/java/com/wenhao/record/data/tracking/TrainingSampleExporter.kt \
  app/src/main/java/com/wenhao/record/ui/history/HistoryController.kt \
  app/src/test/java/com/wenhao/record/data/tracking/DecisionEventStorageTest.kt \
  app/src/test/java/com/wenhao/record/ui/history/HistoryControllerTest.kt
git commit -m "feat(决策反馈): 过滤低质量待标记事件"
```

## 任务 5：补诊断信息与最终验证

**文件：**

- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/AutoTrackDiagnosticsStorage.kt`
- 测试：`app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServicePhasePolicyTest.kt`
- 测试：`app/src/test/java/com/wenhao/record/tracking/decision/DecisionGateEvaluatorTest.kt`
- 测试：`app/src/test/java/com/wenhao/record/data/tracking/DecisionEventStorageTest.kt`
- 测试：`app/src/test/java/com/wenhao/record/ui/history/HistoryControllerTest.kt`

- [ ] **步骤 1：在诊断层增加门槛结果展示**

```kotlin
AutoTrackDiagnosticsStorage.markDecisionScores(
    context = this,
    startScore = frame.startScore,
    stopScore = frame.stopScore,
    finalDecision = frame.finalDecision.name,
    extra = "gate=${frame.gateResult.feedbackBlockedReason ?: "PASS"}"
)
```

- [ ] **步骤 2：补充回归测试用例**

```kotlin
@Test
fun `stop event may be stored but hidden from feedback list when gps is poor`() {
    ...
    assertTrue(exportRows.any { it.finalDecision == "STOP" })
    assertTrue(loadReviewItems.none { it.finalDecision == "STOP" })
}
```

- [ ] **步骤 3：运行最终验证命令**

运行：

```bash
sh gradlew testDebugUnitTest --no-daemon \
  --tests "com.wenhao.record.tracking.decision.DecisionGateEvaluatorTest" \
  --tests "com.wenhao.record.tracking.pipeline.FeatureWindowAggregatorTest" \
  --tests "com.wenhao.record.tracking.decision.TrackingDecisionEngineTest" \
  --tests "com.wenhao.record.tracking.decision.DecisionRuntimeCoordinatorTest" \
  --tests "com.wenhao.record.data.tracking.DecisionEventStorageTest" \
  --tests "com.wenhao.record.ui.history.HistoryControllerTest" \
  --tests "com.wenhao.record.tracking.BackgroundTrackingServicePhasePolicyTest"
```

预期：`BUILD SUCCESSFUL`

- [ ] **步骤 4：提交本任务**

```bash
git add \
  app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt \
  app/src/main/java/com/wenhao/record/data/tracking/AutoTrackDiagnosticsStorage.kt \
  app/src/test/java/com/wenhao/record/tracking/decision/DecisionGateEvaluatorTest.kt \
  app/src/test/java/com/wenhao/record/data/tracking/DecisionEventStorageTest.kt \
  app/src/test/java/com/wenhao/record/ui/history/HistoryControllerTest.kt
git commit -m "feat(决策反馈): 补齐门槛诊断与最终验证"
```

## 自检

- 规格覆盖度：
  - 自动开始硬门槛：任务 1、2、3
  - 自动结束不对称门槛：任务 1、3、4、5
  - 待标记列表只展示高质量事件：任务 4、5
  - 阻断原因落库与可观测：任务 1、4、5
- 占位符扫描：
  - 无 `TODO`、`后续实现`、`类似任务 N`
  - 每个任务均包含测试、命令和提交点
- 类型一致性：
  - 统一使用 `DecisionGateInput`、`DecisionGateResult`、`DecisionGateBlockReason`
  - 统一使用 `feedbackEligible` / `feedbackBlockedReason` 作为存储字段

