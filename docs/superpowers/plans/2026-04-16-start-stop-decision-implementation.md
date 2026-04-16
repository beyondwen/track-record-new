# 开始 / 结束记录决策链路实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在现有 `master` 分支上落地「双二分类轻量模型 + 决策平滑器 + 人工纠错闭环」第一阶段能力，让后台记录链路具备可采样、可推理、可纠错、可导出和可回放的实现基础。

**架构：** 以 `BackgroundTrackingService` 为信号入口，但把窗口聚合、模型推理、平滑决策、事件落库和反馈回灌拆到独立模块。第一阶段继续复用现有自动记录流程作为执行器，同时新增一条并行的决策流水线，先完成数据闭环和决策可观测性，再逐步让模型接管开始 / 结束边界。

**技术栈：** Kotlin、Android Service、Room、Jetpack Compose、JUnit4、JSON 配置、Python（WSL 训练脚本）

---

## 任务 1：建立决策流水线的数据契约与窗口聚合

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/tracking/pipeline/SignalSnapshot.kt`
- 创建：`app/src/main/java/com/wenhao/record/tracking/pipeline/FeatureVector.kt`
- 创建：`app/src/main/java/com/wenhao/record/tracking/pipeline/FeatureWindowAggregator.kt`
- 创建：`app/src/test/java/com/wenhao/record/tracking/pipeline/FeatureWindowAggregatorTest.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt`

- [ ] **步骤 1：先写聚合器失败测试，锁定窗口输出契约**

```kotlin
class FeatureWindowAggregatorTest {

    @Test
    fun `build vector from 30s 60s 180s windows`() {
        val aggregator = FeatureWindowAggregator(
            clock = { 180_000L }
        )

        aggregator.append(
            SignalSnapshot(
                timestampMillis = 30_000L,
                phase = TrackingPhase.IDLE,
                isRecording = false,
                latitude = 39.90,
                longitude = 116.40,
                accuracyMeters = 12f,
                speedMetersPerSecond = 0.4f,
                stepDelta = 3,
                accelerationMagnitude = 0.9f,
                wifiChanged = false,
                insideFrequentPlace = true,
                candidateStateDurationMillis = 30_000L,
                protectionRemainingMillis = 0L,
            )
        )

        val vector = aggregator.buildVector()!!

        assertEquals(3.0, vector.features.getValue("steps_30s"), 0.0001)
        assertEquals(12.0, vector.features.getValue("accuracy_avg_30s"), 0.0001)
        assertEquals(1.0, vector.features.getValue("inside_frequent_place_180s_ratio"), 0.0001)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.tracking.pipeline.FeatureWindowAggregatorTest"`

预期：FAIL，报错 `Unresolved reference: FeatureWindowAggregator`

- [ ] **步骤 3：实现 `SignalSnapshot` 和 `FeatureVector` 基础类型**

```kotlin
data class SignalSnapshot(
    val timestampMillis: Long,
    val phase: TrackingPhase,
    val isRecording: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val stepDelta: Int,
    val accelerationMagnitude: Float?,
    val wifiChanged: Boolean,
    val insideFrequentPlace: Boolean,
    val candidateStateDurationMillis: Long,
    val protectionRemainingMillis: Long,
)

data class FeatureVector(
    val timestampMillis: Long,
    val features: Map<String, Double>,
    val isRecording: Boolean,
    val phase: TrackingPhase,
)
```

- [ ] **步骤 4：实现滚动窗口聚合器**

```kotlin
class FeatureWindowAggregator(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val snapshots = ArrayDeque<SignalSnapshot>()

    fun append(snapshot: SignalSnapshot) {
        snapshots += snapshot
        trimBefore(clock() - 180_000L)
    }

    fun buildVector(): FeatureVector? {
        if (snapshots.isEmpty()) return null
        val latest = snapshots.last()
        val features = linkedMapOf<String, Double>()
        fillWindow(features, "30s", latest.timestampMillis - 30_000L)
        fillWindow(features, "60s", latest.timestampMillis - 60_000L)
        fillWindow(features, "180s", latest.timestampMillis - 180_000L)
        features["candidate_duration_seconds"] = latest.candidateStateDurationMillis / 1000.0
        features["protection_remaining_seconds"] = latest.protectionRemainingMillis / 1000.0
        return FeatureVector(latest.timestampMillis, features, latest.isRecording, latest.phase)
    }
}
```

- [ ] **步骤 5：在 `BackgroundTrackingService` 中增加快照拼装入口，但先不接管决策**

```kotlin
private val featureWindowAggregator = FeatureWindowAggregator()

private fun appendDecisionSnapshot(location: Location?, stepDelta: Int, accelerationMagnitude: Float?) {
    featureWindowAggregator.append(
        SignalSnapshot(
            timestampMillis = System.currentTimeMillis(),
            phase = currentPhase,
            isRecording = currentSession != null,
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracyMeters = location?.accuracy,
            speedMetersPerSecond = location?.speed,
            stepDelta = stepDelta,
            accelerationMagnitude = accelerationMagnitude,
            wifiChanged = currentWifiSnapshot.changedRecently,
            insideFrequentPlace = insideAnchorIds.isNotEmpty(),
            candidateStateDurationMillis = System.currentTimeMillis() - phaseEnteredAt,
            protectionRemainingMillis = 0L,
        )
    )
}
```

- [ ] **步骤 6：重新运行聚合器测试**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.tracking.pipeline.FeatureWindowAggregatorTest"`

预期：PASS

- [ ] **步骤 7：提交本任务**

```bash
git add app/src/main/java/com/wenhao/record/tracking/pipeline/SignalSnapshot.kt \
  app/src/main/java/com/wenhao/record/tracking/pipeline/FeatureVector.kt \
  app/src/main/java/com/wenhao/record/tracking/pipeline/FeatureWindowAggregator.kt \
  app/src/test/java/com/wenhao/record/tracking/pipeline/FeatureWindowAggregatorTest.kt \
  app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt
git commit -m "feat(决策流水线): 添加信号快照与窗口聚合器"
```

## 任务 2：实现模型运行时、平滑器与决策引擎

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/tracking/model/LinearModelConfig.kt`
- 创建：`app/src/main/java/com/wenhao/record/tracking/model/LinearModelRunner.kt`
- 创建：`app/src/main/java/com/wenhao/record/tracking/model/StartDecisionModel.kt`
- 创建：`app/src/main/java/com/wenhao/record/tracking/model/StopDecisionModel.kt`
- 创建：`app/src/main/java/com/wenhao/record/tracking/decision/DecisionSmoother.kt`
- 创建：`app/src/main/java/com/wenhao/record/tracking/decision/TrackingDecisionEngine.kt`
- 创建：`app/src/test/java/com/wenhao/record/tracking/model/LinearModelRunnerTest.kt`
- 创建：`app/src/test/java/com/wenhao/record/tracking/decision/DecisionSmootherTest.kt`
- 创建：`app/src/test/java/com/wenhao/record/tracking/decision/TrackingDecisionEngineTest.kt`

- [ ] **步骤 1：先写线性模型和决策平滑器失败测试**

```kotlin
class LinearModelRunnerTest {

    @Test
    fun `score uses ordered features and sigmoid`() {
        val config = LinearModelConfig(
            bias = -1.0,
            featureOrder = listOf("speed_avg_30s", "steps_30s"),
            weights = listOf(0.5, 0.25),
            means = listOf(0.0, 0.0),
            scales = listOf(1.0, 1.0),
        )

        val score = LinearModelRunner.score(
            config = config,
            features = mapOf("speed_avg_30s" to 2.0, "steps_30s" to 4.0),
        )

        assertTrue(score > 0.5)
    }
}
```

```kotlin
class DecisionSmootherTest {

    @Test
    fun `start requires consecutive high scores`() {
        val smoother = DecisionSmoother(
            startTriggerCount = 2,
            stopTriggerCount = 4,
            startThreshold = 0.8,
            stopThreshold = 0.9,
            startProtectionMillis = 180_000L,
            minimumRecordingMillis = 120_000L,
        )

        assertEquals(FinalDecision.HOLD, smoother.consume(startScore = 0.82, stopScore = 0.10, nowMillis = 10_000L, isRecording = false))
        assertEquals(FinalDecision.START, smoother.consume(startScore = 0.84, stopScore = 0.12, nowMillis = 25_000L, isRecording = false))
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.tracking.model.LinearModelRunnerTest" --tests "com.wenhao.record.tracking.decision.DecisionSmootherTest"`

预期：FAIL，报错 `Unresolved reference: LinearModelConfig` 与 `Unresolved reference: DecisionSmoother`

- [ ] **步骤 3：实现线性模型配置与运行器**

```kotlin
data class LinearModelConfig(
    val bias: Double,
    val featureOrder: List<String>,
    val weights: List<Double>,
    val means: List<Double>,
    val scales: List<Double>,
)

object LinearModelRunner {
    fun score(config: LinearModelConfig, features: Map<String, Double>): Double {
        var raw = config.bias
        config.featureOrder.forEachIndexed { index, name ->
            val value = features[name] ?: 0.0
            val normalized = (value - config.means[index]) / config.scales[index]
            raw += normalized * config.weights[index]
        }
        return 1.0 / (1.0 + kotlin.math.exp(-raw))
    }
}
```

- [ ] **步骤 4：实现双模型包装与平滑器**

```kotlin
class StartDecisionModel(private val config: LinearModelConfig) {
    fun score(vector: FeatureVector): Double = LinearModelRunner.score(config, vector.features)
}

class StopDecisionModel(private val config: LinearModelConfig) {
    fun score(vector: FeatureVector): Double = LinearModelRunner.score(config, vector.features)
}

enum class FinalDecision { START, STOP, HOLD }
```

```kotlin
class DecisionSmoother(
    private val startTriggerCount: Int,
    private val stopTriggerCount: Int,
    private val startThreshold: Double,
    private val stopThreshold: Double,
    private val startProtectionMillis: Long,
    private val minimumRecordingMillis: Long,
) {
    private var startHits = 0
    private var stopHits = 0
    private var lastStartedAt = Long.MIN_VALUE

    fun consume(
        startScore: Double,
        stopScore: Double,
        nowMillis: Long,
        isRecording: Boolean,
    ): FinalDecision {
        if (!isRecording) {
            startHits = if (startScore >= startThreshold) startHits + 1 else 0
            stopHits = 0
            if (startHits >= startTriggerCount) {
                startHits = 0
                lastStartedAt = nowMillis
                return FinalDecision.START
            }
            return FinalDecision.HOLD
        }

        if (nowMillis - lastStartedAt < startProtectionMillis ||
            nowMillis - lastStartedAt < minimumRecordingMillis
        ) {
            stopHits = 0
            return FinalDecision.HOLD
        }

        stopHits = if (stopScore >= stopThreshold) stopHits + 1 else 0
        return if (stopHits >= stopTriggerCount) {
            stopHits = 0
            FinalDecision.STOP
        } else {
            FinalDecision.HOLD
        }
    }
}
```

- [ ] **步骤 5：实现决策引擎，把聚合向量、模型分数和平滑器串起来**

```kotlin
data class DecisionFrame(
    val vector: FeatureVector,
    val startScore: Double,
    val stopScore: Double,
    val finalDecision: FinalDecision,
)

class TrackingDecisionEngine(
    private val startModel: StartDecisionModel,
    private val stopModel: StopDecisionModel,
    private val smoother: DecisionSmoother,
) {
    fun evaluate(vector: FeatureVector, nowMillis: Long): DecisionFrame {
        val startScore = startModel.score(vector)
        val stopScore = stopModel.score(vector)
        val finalDecision = smoother.consume(startScore, stopScore, nowMillis, vector.isRecording)
        return DecisionFrame(vector, startScore, stopScore, finalDecision)
    }
}
```

- [ ] **步骤 6：补齐引擎测试，验证开始 / 结束分支**

```kotlin
class TrackingDecisionEngineTest {

    @Test
    fun `engine returns hold before smoother threshold is satisfied`() {
        val config = LinearModelConfig(
            bias = -2.0,
            featureOrder = listOf("speed_avg_30s", "steps_30s"),
            weights = listOf(0.3, 0.05),
            means = listOf(0.0, 0.0),
            scales = listOf(1.0, 1.0),
        )
        val vector = FeatureVector(
            timestampMillis = 15_000L,
            features = mapOf("speed_avg_30s" to 1.8, "steps_30s" to 12.0),
            isRecording = false,
            phase = TrackingPhase.SUSPECT_MOVING,
        )
        val engine = TrackingDecisionEngine(
            startModel = StartDecisionModel(config),
            stopModel = StopDecisionModel(config),
            smoother = DecisionSmoother(
                startTriggerCount = 2,
                stopTriggerCount = 4,
                startThreshold = 0.8,
                stopThreshold = 0.9,
                startProtectionMillis = 180_000L,
                minimumRecordingMillis = 120_000L,
            ),
        )

        val frame = engine.evaluate(vector, nowMillis = 15_000L)

        assertEquals(FinalDecision.HOLD, frame.finalDecision)
    }
}
```

- [ ] **步骤 7：运行本任务全部单测**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.tracking.model.LinearModelRunnerTest" --tests "com.wenhao.record.tracking.decision.DecisionSmootherTest" --tests "com.wenhao.record.tracking.decision.TrackingDecisionEngineTest"`

预期：PASS

- [ ] **步骤 8：提交本任务**

```bash
git add app/src/main/java/com/wenhao/record/tracking/model/LinearModelConfig.kt \
  app/src/main/java/com/wenhao/record/tracking/model/LinearModelRunner.kt \
  app/src/main/java/com/wenhao/record/tracking/model/StartDecisionModel.kt \
  app/src/main/java/com/wenhao/record/tracking/model/StopDecisionModel.kt \
  app/src/main/java/com/wenhao/record/tracking/decision/DecisionSmoother.kt \
  app/src/main/java/com/wenhao/record/tracking/decision/TrackingDecisionEngine.kt \
  app/src/test/java/com/wenhao/record/tracking/model/LinearModelRunnerTest.kt \
  app/src/test/java/com/wenhao/record/tracking/decision/DecisionSmootherTest.kt \
  app/src/test/java/com/wenhao/record/tracking/decision/TrackingDecisionEngineTest.kt
git commit -m "feat(决策模型): 添加线性推理与平滑决策引擎"
```

## 任务 3：为决策事件、反馈与训练样本建立持久化层

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/data/local/decision/DecisionEntities.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/local/decision/DecisionDao.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/tracking/DecisionFeedbackType.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/tracking/DecisionEventStorage.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/tracking/DecisionFeedbackStore.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/tracking/TrainingSampleExporter.kt`
- 创建：`app/src/test/java/com/wenhao/record/data/tracking/DecisionEventStorageTest.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/local/TrackDatabase.kt`
- 修改：`app/src/main/java/com/wenhao/record/RecordApplication.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TrackDataChangeNotifier.kt`
- 创建：`app/schemas/com.wenhao.record.data.local.TrackDatabase/5.json`

- [ ] **步骤 1：先写存储测试，锁定事件、反馈和导出行的最小行为**

```kotlin
class DecisionEventStorageTest {

    @Test
    fun `save frame and feedback then export rows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val frame = DecisionFrame(
            vector = FeatureVector(
                timestampMillis = 60_000L,
                features = mapOf("steps_30s" to 4.0),
                isRecording = false,
                phase = TrackingPhase.SUSPECT_MOVING,
            ),
            startScore = 0.91,
            stopScore = 0.02,
            finalDecision = FinalDecision.START,
        )

        val eventId = DecisionEventStorage.saveFrame(context, frame)
        DecisionFeedbackStore.markStartTooEarly(context, eventId)

        val rows = TrainingSampleExporter.exportRows(context)

        assertEquals(1, rows.size)
        assertEquals("START_TOO_EARLY", rows.single().feedbackLabel)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.data.tracking.DecisionEventStorageTest"`

预期：FAIL，报错 `Unresolved reference: DecisionEventStorage`

- [ ] **步骤 3：增加 Room 实体、DAO 和数据库迁移**

```kotlin
@Entity(tableName = "decision_event")
data class DecisionEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0L,
    val timestampMillis: Long,
    val phase: String,
    val isRecording: Boolean,
    val startScore: Double,
    val stopScore: Double,
    val finalDecision: String,
    val featureJson: String,
)

@Entity(tableName = "decision_feedback")
data class DecisionFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val feedbackId: Long = 0L,
    val eventId: Long,
    val feedbackType: String,
    val createdAt: Long,
)
```

```kotlin
@Dao
interface DecisionDao {
    @Insert fun insertEvent(entity: DecisionEventEntity): Long
    @Insert fun insertFeedback(entity: DecisionFeedbackEntity)
    @Query("SELECT * FROM decision_event ORDER BY eventId DESC") fun getEvents(): List<DecisionEventEntity>
    @Query("SELECT * FROM decision_feedback") fun getFeedback(): List<DecisionFeedbackEntity>
}
```

```kotlin
@Database(
    entities = [
        AutoTrackSessionEntity::class,
        AutoTrackPointEntity::class,
        HistoryRecordEntity::class,
        HistoryPointEntity::class,
        DecisionEventEntity::class,
        DecisionFeedbackEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
```

- [ ] **步骤 4：实现存储层与导出器**

```kotlin
enum class DecisionFeedbackType {
    START_TOO_EARLY,
    START_TOO_LATE,
    STOP_TOO_EARLY,
    STOP_TOO_LATE,
    CORRECT,
}
```

```kotlin
object DecisionEventStorage {
    fun saveFrame(context: Context, frame: DecisionFrame): Long {
        val entity = DecisionEventEntity(
            timestampMillis = frame.vector.timestampMillis,
            phase = frame.vector.phase.name,
            isRecording = frame.vector.isRecording,
            startScore = frame.startScore,
            stopScore = frame.stopScore,
            finalDecision = frame.finalDecision.name,
            featureJson = JSONObject(frame.vector.features).toString(),
        )
        return TrackDatabase.getInstance(context).decisionDao().insertEvent(entity)
    }
}
```

```kotlin
object DecisionFeedbackStore {
    fun markStartTooEarly(context: Context, eventId: Long) {
        TrackDatabase.getInstance(context).decisionDao().insertFeedback(
            DecisionFeedbackEntity(
                eventId = eventId,
                feedbackType = "START_TOO_EARLY",
                createdAt = System.currentTimeMillis(),
            )
        )
        TrackDataChangeNotifier.notifyHistoryChanged()
    }
}
```

- [ ] **步骤 5：修正 `TrackDatabase`、`RecordApplication` 和 schema 导出**

```kotlin
abstract fun decisionDao(): DecisionDao
```

```kotlin
override fun onCreate() {
    super.onCreate()
    CrashLogStore.install(this)
    AutoTrackStorage.warmUp(this)
    HistoryStorage.warmUp(this)
    DecisionEventStorage.warmUp(this)
}
```

- [ ] **步骤 6：运行存储测试并刷新 schema**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.data.tracking.DecisionEventStorageTest"`

运行：`./gradlew :app:kspDebugKotlin`

预期：测试 PASS，且 `app/schemas/com.wenhao.record.data.local.TrackDatabase/5.json` 生成成功

- [ ] **步骤 7：提交本任务**

```bash
git add app/src/main/java/com/wenhao/record/data/local/decision/DecisionEntities.kt \
  app/src/main/java/com/wenhao/record/data/local/decision/DecisionDao.kt \
  app/src/main/java/com/wenhao/record/data/tracking/DecisionFeedbackType.kt \
  app/src/main/java/com/wenhao/record/data/tracking/DecisionEventStorage.kt \
  app/src/main/java/com/wenhao/record/data/tracking/DecisionFeedbackStore.kt \
  app/src/main/java/com/wenhao/record/data/tracking/TrainingSampleExporter.kt \
  app/src/test/java/com/wenhao/record/data/tracking/DecisionEventStorageTest.kt \
  app/src/main/java/com/wenhao/record/data/local/TrackDatabase.kt \
  app/src/main/java/com/wenhao/record/RecordApplication.kt \
  app/src/main/java/com/wenhao/record/data/tracking/TrackDataChangeNotifier.kt \
  app/schemas/com.wenhao.record.data.local.TrackDatabase/5.json
git commit -m "feat(决策存储): 添加事件反馈落库与样本导出"
```

## 任务 4：把新决策链路接入 `BackgroundTrackingService`

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/tracking/decision/DecisionRuntimeCoordinator.kt`
- 创建：`app/src/test/java/com/wenhao/record/tracking/decision/DecisionRuntimeCoordinatorTest.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/AutoTrackDiagnosticsStorage.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/AutoTrackStorage.kt`

- [ ] **步骤 1：先写运行时协调器测试，锁定“旧状态机执行，新引擎判定”的边界**

```kotlin
class DecisionRuntimeCoordinatorTest {

    @Test
    fun `emits start action when engine returns start`() {
        var started = false
        var stopped = false
        var captured: DecisionFrame? = null
        val config = LinearModelConfig(
            bias = 3.0,
            featureOrder = listOf("speed_avg_30s"),
            weights = listOf(0.0),
            means = listOf(0.0),
            scales = listOf(1.0),
        )
        val coordinator = DecisionRuntimeCoordinator(
            engine = TrackingDecisionEngine(
                startModel = StartDecisionModel(config),
                stopModel = StopDecisionModel(config),
                smoother = DecisionSmoother(
                    startTriggerCount = 1,
                    stopTriggerCount = 4,
                    startThreshold = 0.5,
                    stopThreshold = 0.95,
                    startProtectionMillis = 180_000L,
                    minimumRecordingMillis = 120_000L,
                ),
            ),
            onStart = { started = true },
            onStop = { stopped = true },
            onFrame = { frame -> captured = frame },
        )

        coordinator.onVector(
            FeatureVector(
                timestampMillis = 30_000L,
                features = mapOf("speed_avg_30s" to 1.2),
                isRecording = false,
                phase = TrackingPhase.SUSPECT_MOVING,
            ),
            nowMillis = 30_000L,
        )

        assertTrue(started)
        assertFalse(stopped)
        assertEquals(FinalDecision.START, captured!!.finalDecision)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.tracking.decision.DecisionRuntimeCoordinatorTest"`

预期：FAIL，报错 `Unresolved reference: DecisionRuntimeCoordinator`

- [ ] **步骤 3：实现协调器，封装引擎输出、副作用回调和事件落库**

```kotlin
class DecisionRuntimeCoordinator(
    private val engine: TrackingDecisionEngine,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
    private val onFrame: (DecisionFrame) -> Unit,
) {
    fun onVector(vector: FeatureVector, nowMillis: Long) {
        val frame = engine.evaluate(vector, nowMillis)
        onFrame(frame)
        when (frame.finalDecision) {
            FinalDecision.START -> onStart()
            FinalDecision.STOP -> onStop()
            FinalDecision.HOLD -> Unit
        }
    }
}
```

- [ ] **步骤 4：在服务中接入协调器，但保留现有规则作为执行器与兜底**

```kotlin
private lateinit var decisionCoordinator: DecisionRuntimeCoordinator

override fun onCreate() {
    super.onCreate()
    decisionCoordinator = DecisionRuntimeCoordinator(
        engine = buildDecisionEngine(),
        onStart = { maybePromoteToSuspect("模型建议开始记录") },
        onStop = { maybePromoteToStopping("模型建议结束记录") },
        onFrame = { frame ->
            DecisionEventStorage.saveFrame(this, frame)
            AutoTrackDiagnosticsStorage.markDecisionScores(
                context = this,
                startScore = frame.startScore,
                stopScore = frame.stopScore,
                finalDecision = frame.finalDecision.name,
            )
        },
    )
}
```

- [ ] **步骤 5：扩展诊断信息，显示最近一次模型分数和最终决策**

```kotlin
data class AutoTrackDiagnostics(
    val lastStartScore: Double? = null,
    val lastStopScore: Double? = null,
    val lastDecision: String? = null,
    val serviceStatus: String = "后台待命中",
    val lastEvent: String = "应用已启动，等待低功耗探测",
)
```

```kotlin
fun markDecisionScores(
    context: Context,
    startScore: Double,
    stopScore: Double,
    finalDecision: String,
) {
    update(context) { current ->
        current.copy(
            lastStartScore = startScore,
            lastStopScore = stopScore,
            lastDecision = finalDecision,
        )
    }
}
```

- [ ] **步骤 6：运行协调器测试与现有关键回归测试**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.tracking.decision.DecisionRuntimeCoordinatorTest" --tests "com.wenhao.record.data.tracking.AutoTrackSessionPersistPolicyTest" --tests "com.wenhao.record.tracking.TrackNoiseFilterTest"`

预期：PASS

- [ ] **步骤 7：提交本任务**

```bash
git add app/src/main/java/com/wenhao/record/tracking/decision/DecisionRuntimeCoordinator.kt \
  app/src/test/java/com/wenhao/record/tracking/decision/DecisionRuntimeCoordinatorTest.kt \
  app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt \
  app/src/main/java/com/wenhao/record/data/tracking/AutoTrackDiagnosticsStorage.kt \
  app/src/main/java/com/wenhao/record/data/tracking/AutoTrackStorage.kt
git commit -m "refactor(后台追踪): 接入决策运行时与模型诊断"
```

## 任务 5：在历史页补齐人工纠错入口

**文件：**
- 创建：`app/src/test/java/com/wenhao/record/ui/history/HistoryControllerTest.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/history/HistoryController.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/history/HistoryComposeScreen.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt`
- 修改：`app/src/main/res/values/strings_compose_history.xml`

- [ ] **步骤 1：先写 `HistoryController` 测试，锁定纠错入口的数据流**

```kotlin
class HistoryControllerTest {

    @Test
    fun `submitting feedback updates ui state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = HistoryController(context)

        controller.setDecisionFeedbackSheet(eventId = 42L, visible = true)
        controller.submitFeedback(DecisionFeedbackType.START_TOO_EARLY)

        assertFalse(controller.uiState.isFeedbackSheetVisible)
        assertEquals(42L, controller.uiState.lastSubmittedFeedbackEventId)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.ui.history.HistoryControllerTest"`

预期：FAIL，报错 `Unresolved reference: setDecisionFeedbackSheet`

- [ ] **步骤 3：扩展历史页状态和控制器**

```kotlin
data class HistoryScreenUiState(
    val items: List<HistoryDayItem> = emptyList(),
    val selectedDayStartMillis: Long? = null,
    val isFeedbackSheetVisible: Boolean = false,
    val feedbackEventId: Long? = null,
    val lastSubmittedFeedbackEventId: Long? = null,
    val totalDistanceText: String = "",
    val totalDurationText: String = "",
    val totalCountText: String = "",
)
```

```kotlin
fun submitFeedback(type: DecisionFeedbackType) {
    val eventId = uiState.feedbackEventId ?: return
    DecisionFeedbackStore.save(context, eventId, type)
    uiState = uiState.copy(
        isFeedbackSheetVisible = false,
        lastSubmittedFeedbackEventId = eventId,
    )
}
```

- [ ] **步骤 4：在 `HistoryComposeScreen` 增加纠错面板和按钮文案**

```kotlin
if (state.isFeedbackSheetVisible) {
    DecisionFeedbackBottomSheet(
        onStartTooEarly = { onFeedbackSubmit(DecisionFeedbackType.START_TOO_EARLY) },
        onStartTooLate = { onFeedbackSubmit(DecisionFeedbackType.START_TOO_LATE) },
        onStopTooEarly = { onFeedbackSubmit(DecisionFeedbackType.STOP_TOO_EARLY) },
        onStopTooLate = { onFeedbackSubmit(DecisionFeedbackType.STOP_TOO_LATE) },
        onCorrect = { onFeedbackSubmit(DecisionFeedbackType.CORRECT) },
    )
}
```

- [ ] **步骤 5：在 `MainActivity` 中连接 UI 事件与控制器**

```kotlin
MainComposeScreen(
    currentTab = currentTab,
    dashboardState = dashboardUiController.panelState,
    dashboardOverlayState = dashboardUiController.overlayState,
    historyState = historyController.uiState,
    barometerState = barometerController.uiState,
    dashboardMapState = homeMapController.renderState,
    onHistoryDecisionFeedback = { eventId ->
        historyController.setDecisionFeedbackSheet(eventId = eventId, visible = true)
    },
    onHistoryFeedbackSubmit = { type ->
        historyController.submitFeedback(type)
        Toast.makeText(this, R.string.compose_history_feedback_saved, Toast.LENGTH_SHORT).show()
    },
)
```

- [ ] **步骤 6：运行控制器测试并做一次手工验证**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.ui.history.HistoryControllerTest"`

手工验证：
- 打开历史页
- 触发某次自动开始 / 自动结束记录的纠错入口
- 提交「开始太早」后看到提示文案

预期：单测 PASS，且 UI 流转可用

- [ ] **步骤 7：提交本任务**

```bash
git add app/src/test/java/com/wenhao/record/ui/history/HistoryControllerTest.kt \
  app/src/main/java/com/wenhao/record/ui/history/HistoryController.kt \
  app/src/main/java/com/wenhao/record/ui/history/HistoryComposeScreen.kt \
  app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt \
  app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt \
  app/src/main/res/values/strings_compose_history.xml
git commit -m "feat(历史页): 添加开始结束决策纠错入口"
```

## 任务 6：补齐 WSL 训练脚本、回放工具与最终验证

**文件：**
- 创建：`tools/decision-model/requirements.txt`
- 创建：`tools/decision-model/train_decision_models.py`
- 创建：`tools/decision-model/replay_decision_run.py`
- 创建：`tools/decision-model/tests/test_train_decision_models.py`
- 创建：`docs/superpowers/specs/decision-model-export-format.md`
- 修改：`docs/superpowers/specs/2026-04-15-start-stop-decision-model-design.md`

- [ ] **步骤 1：先写训练脚本测试，锁定输入输出格式**

```python
from train_decision_models import build_outputs


def test_build_outputs_generates_two_model_json_files(tmp_path):
    rows = [
        {"steps_30s": 4.0, "speed_avg_30s": 1.6, "start_target": 1, "stop_target": 0},
        {"steps_30s": 0.0, "speed_avg_30s": 0.1, "start_target": 0, "stop_target": 1},
    ]

    outputs = build_outputs(rows, tmp_path)

    assert (tmp_path / "start_model.json").exists()
    assert (tmp_path / "stop_model.json").exists()
    assert outputs["feature_config"]["feature_order"] == ["steps_30s", "speed_avg_30s"]
```

- [ ] **步骤 2：运行测试验证失败**

运行：`python3 -m pytest tools/decision-model/tests/test_train_decision_models.py -q`

预期：FAIL，报错 `ModuleNotFoundError: No module named 'train_decision_models'`

- [ ] **步骤 3：实现训练脚本与模型导出格式**

```python
def build_outputs(rows, output_dir):
    feature_order = ["steps_30s", "speed_avg_30s"]
    start_model = fit_logistic(rows, feature_order, "start_target")
    stop_model = fit_logistic(rows, feature_order, "stop_target")
    write_json(output_dir / "start_model.json", start_model)
    write_json(output_dir / "stop_model.json", stop_model)
    write_json(output_dir / "feature_config.json", {"feature_order": feature_order})
    return {"feature_config": {"feature_order": feature_order}}
```

- [ ] **步骤 4：实现回放脚本，输出逐窗口分数和最终决策**

```python
def replay_rows(rows, start_model, stop_model, threshold_config):
    smoother = ReplaySmoother(threshold_config)
    for row in rows:
        start_score = score_row(start_model, row)
        stop_score = score_row(stop_model, row)
        final_decision = smoother.consume(start_score, stop_score, row["is_recording"], row["timestampMillis"])
        yield {**row, "start_score": start_score, "stop_score": stop_score, "final_decision": final_decision}
```

- [ ] **步骤 5：补一份导出格式文档，并回写主设计稿中的实现指针**

```markdown
## 导出 JSON 约束

- `start_model.json`：包含 `bias`、`feature_order`、`weights`、`means`、`scales`
- `stop_model.json`：结构与开始模型一致
- `threshold_config.json`：包含 `start_threshold`、`stop_threshold`、`start_trigger_count`、`stop_trigger_count`
```

- [ ] **步骤 6：运行最终验证命令**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.tracking.pipeline.FeatureWindowAggregatorTest" --tests "com.wenhao.record.tracking.model.LinearModelRunnerTest" --tests "com.wenhao.record.tracking.decision.DecisionSmootherTest" --tests "com.wenhao.record.tracking.decision.TrackingDecisionEngineTest" --tests "com.wenhao.record.data.tracking.DecisionEventStorageTest" --tests "com.wenhao.record.ui.history.HistoryControllerTest"`

运行：`python3 -m pytest tools/decision-model/tests/test_train_decision_models.py -q`

预期：全部 PASS

- [ ] **步骤 7：提交本任务**

```bash
git add tools/decision-model/requirements.txt \
  tools/decision-model/train_decision_models.py \
  tools/decision-model/replay_decision_run.py \
  tools/decision-model/tests/test_train_decision_models.py \
  docs/superpowers/specs/decision-model-export-format.md \
  docs/superpowers/specs/2026-04-15-start-stop-decision-model-design.md
git commit -m "feat(训练工具): 添加决策模型训练与回放脚本"
```

## 自检结论

- 设计稿中的窗口聚合、双模型、平滑器、反馈闭环、导出和回放都已映射到具体任务。
- 每个任务都给出了明确文件、测试入口、命令和 commit 边界，后续可以按任务独立推进。
- 计划默认先建立并行决策流水线，再逐步提高模型接管比例，避免一次性替换 `BackgroundTrackingService` 导致回归面过大。

计划已完成并保存到 `docs/superpowers/plans/2026-04-16-start-stop-decision-implementation.md`。两种执行方式：

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

选哪种方式？
