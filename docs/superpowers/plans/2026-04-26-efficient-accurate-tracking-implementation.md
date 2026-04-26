# 省电且准确的轨迹采集方案实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将当前连续采点服务改造成“低功耗唤醒 + 连续好点确认 + 室内不记录”的轨迹采集方案，在静止时更省电，在室外移动时更可信，在室内无精确点位时直接断段。

**架构：** 保持现有 `BackgroundTrackingService` 作为主调度入口，并保留当前 `TrackingPhase` 的四态模型（`IDLE / SUSPECT_MOVING / ACTIVE / SUSPECT_STOPPING`），避免直接破坏现有降频和分析链路。规格中的 “SIGNAL_LOST” 语义在实现中映射为 `ACTIVE` 阶段下的独立 `signalLost` 标志与停记逻辑：进入失联后不落点、不连线，恢复时必须重新累计连续好点并新开一段。低功耗唤醒由 `PASSIVE_PROVIDER`、`MotionConfidenceEngine` 和轻量传感器共同驱动。

**技术栈：** Android Kotlin、LocationManager、`PASSIVE_PROVIDER`、SensorManager、Activity Recognition、JUnit 4、Robolectric、Kotlin Test。

---

## 文件结构

- 创建：`app/src/main/java/com/wenhao/record/tracking/TrackFixQualityGate.kt`，封装“好点”定义、连续好点累计和恢复判定。
- 创建：`app/src/main/java/com/wenhao/record/tracking/TrackingLowPowerSignals.kt`，封装被动定位、缓存点和轻量传感器形成的低功耗唤醒信号快照。
- 修改：`app/src/main/java/com/wenhao/record/tracking/MotionConfidenceEngine.kt`，补足单元测试所需的可观察输出，不改变评分模型的主思路。
- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingServicePhasePolicy.kt`，保留四态相位，但让 `SUSPECT_MOVING` 由低功耗信号与连续好点共同驱动。
- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt`，接入 `PASSIVE_PROVIDER`、SensorManager、`MotionConfidenceEngine`、连续好点门槛、`signalLost` 标志、停记与恢复新段逻辑。
- 修改：`app/src/main/java/com/wenhao/record/tracking/LocationSelectionUtils.kt`，收紧 last-known 过滤，避免陈旧粗点误触发观察态。
- 修改：`app/src/main/java/com/wenhao/record/tracking/TrackNoiseFilter.kt`，减少正式记录态的过早删点，把边界判断更多留给后处理清洗。
- 不修改：`app/src/main/AndroidManifest.xml`，当前已声明 `ACTIVITY_RECOGNITION`，本轮不再新增权限。
- 创建：`app/src/test/java/com/wenhao/record/tracking/TrackFixQualityGateTest.kt`，覆盖连续好点、坏点重置和恢复门槛。
- 创建：`app/src/test/java/com/wenhao/record/tracking/TrackingLowPowerSignalsTest.kt`，覆盖 `PASSIVE_PROVIDER`、缓存点和轻量位移输入如何触发观察态。
- 创建：`app/src/test/java/com/wenhao/record/tracking/MotionConfidenceEngineTest.kt`，覆盖显著运动、步数变化、加速度波动如何形成 “movingLikely / stronglyMoving”。
- 修改：`app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServicePhasePolicyTest.kt`，改成覆盖保留四态后的切换规则。
- 修改：`app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServiceCoordinateTest.kt`，补 provider / freshness 断言，确保 `Location` 转换后可供低功耗信号和好点判定使用。
- 创建：`app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServiceSignalLossTest.kt`，覆盖“室内断段、不补点、连续好点后恢复新段”。
- 只读参考：`docs/superpowers/specs/2026-04-26-efficient-accurate-tracking-design.md`，本计划的需求来源。

## 任务 1：建立连续好点判定器

**文件：**
- 创建：`app/src/test/java/com/wenhao/record/tracking/TrackFixQualityGateTest.kt`
- 创建：`app/src/main/java/com/wenhao/record/tracking/TrackFixQualityGate.kt`

- [ ] **步骤 1：编写失败测试**

新建 `TrackFixQualityGateTest.kt`，先把连续好点门槛锁住：

```kotlin
package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackFixQualityGateTest {

    @Test
    fun `requires three consecutive fresh accurate fixes before recording starts`() {
        val gate = TrackFixQualityGate(
            requiredConsecutiveGoodFixes = 3,
            maxAcceptedAccuracyMeters = 25f,
            maxFixAgeMillis = 8_000L,
            minMeaningfulDistanceMeters = 8f,
        )
        val now = 1_713_420_000_000L
        val first = TrackPoint(latitude = 30.0, longitude = 120.0, timestampMillis = now - 4_000L, accuracyMeters = 12f)
        val second = TrackPoint(latitude = 30.00012, longitude = 120.00012, timestampMillis = now - 2_000L, accuracyMeters = 10f)
        val third = TrackPoint(latitude = 30.00026, longitude = 120.00026, timestampMillis = now - 500L, accuracyMeters = 9f)

        assertFalse(gate.noteFix(first, nowMillis = now, previousCandidate = null).isReadyToRecord)
        assertFalse(gate.noteFix(second, nowMillis = now, previousCandidate = first).isReadyToRecord)
        assertTrue(gate.noteFix(third, nowMillis = now, previousCandidate = second).isReadyToRecord)
    }

    @Test
    fun `bad indoor fix resets consecutive good fixes`() {
        val gate = TrackFixQualityGate(
            requiredConsecutiveGoodFixes = 2,
            maxAcceptedAccuracyMeters = 25f,
            maxFixAgeMillis = 8_000L,
            minMeaningfulDistanceMeters = 8f,
        )
        val now = 1_713_420_000_000L
        val first = TrackPoint(latitude = 30.0, longitude = 120.0, timestampMillis = now - 2_000L, accuracyMeters = 12f)
        val badIndoor = TrackPoint(latitude = 30.00001, longitude = 120.00001, timestampMillis = now - 1_000L, accuracyMeters = 85f)
        val recovery = TrackPoint(latitude = 30.0002, longitude = 120.0002, timestampMillis = now, accuracyMeters = 9f)

        assertFalse(gate.noteFix(first, nowMillis = now, previousCandidate = null).isReadyToRecord)
        assertFalse(gate.noteFix(badIndoor, nowMillis = now, previousCandidate = first).isReadyToRecord)
        assertFalse(gate.noteFix(recovery, nowMillis = now, previousCandidate = badIndoor).isReadyToRecord)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.TrackFixQualityGateTest`

预期：FAIL，报错 `TrackFixQualityGate` 不存在。

- [ ] **步骤 3：实现最少代码**

新建 `TrackFixQualityGate.kt`，只实现通过上述测试所需的最小逻辑：

```kotlin
package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.map.GeoMath

data class TrackFixEvaluation(
    val consecutiveGoodFixes: Int,
    val isReadyToRecord: Boolean,
    val acceptedAsGoodFix: Boolean,
)

class TrackFixQualityGate(
    private val requiredConsecutiveGoodFixes: Int,
    private val maxAcceptedAccuracyMeters: Float,
    private val maxFixAgeMillis: Long,
    private val minMeaningfulDistanceMeters: Float,
) {
    private var consecutiveGoodFixes = 0

    fun reset() {
        consecutiveGoodFixes = 0
    }

    fun noteFix(
        point: TrackPoint,
        nowMillis: Long,
        previousCandidate: TrackPoint?,
    ): TrackFixEvaluation {
        val isFresh = point.timestampMillis > 0L && nowMillis - point.timestampMillis <= maxFixAgeMillis
        val isAccurate = (point.accuracyMeters ?: Float.MAX_VALUE) <= maxAcceptedAccuracyMeters
        val hasMeaningfulMove = previousCandidate == null ||
            GeoMath.distanceMeters(previousCandidate, point) >= minMeaningfulDistanceMeters

        val accepted = isFresh && isAccurate && hasMeaningfulMove
        consecutiveGoodFixes = if (accepted) consecutiveGoodFixes + 1 else 0
        return TrackFixEvaluation(
            consecutiveGoodFixes = consecutiveGoodFixes,
            isReadyToRecord = consecutiveGoodFixes >= requiredConsecutiveGoodFixes,
            acceptedAsGoodFix = accepted,
        )
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.TrackFixQualityGateTest`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/wenhao/record/tracking/TrackFixQualityGate.kt app/src/test/java/com/wenhao/record/tracking/TrackFixQualityGateTest.kt
git commit -m "feat(tracking): add fix quality gate for recording start"
```

## 任务 2：建立低功耗唤醒信号模型并接通现有运动评分引擎

**文件：**
- 创建：`app/src/test/java/com/wenhao/record/tracking/TrackingLowPowerSignalsTest.kt`
- 创建：`app/src/test/java/com/wenhao/record/tracking/MotionConfidenceEngineTest.kt`
- 创建：`app/src/main/java/com/wenhao/record/tracking/TrackingLowPowerSignals.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/MotionConfidenceEngine.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/LocationSelectionUtils.kt`

- [ ] **步骤 1：编写失败测试**

新建 `TrackingLowPowerSignalsTest.kt`：

```kotlin
package com.wenhao.record.tracking

import android.location.Location
import android.location.LocationManager
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackingLowPowerSignalsTest {

    @Test
    fun `passive fresh fix with displacement marks suspect movement`() {
        val previous = Location(LocationManager.PASSIVE_PROVIDER).apply {
            latitude = 30.0
            longitude = 120.0
            time = 1_000L
            accuracy = 18f
        }
        val current = Location(LocationManager.PASSIVE_PROVIDER).apply {
            latitude = 30.0005
            longitude = 120.0005
            time = 8_000L
            accuracy = 16f
        }

        val snapshot = TrackingLowPowerSignals.fromLocations(previous = previous, current = current, nowMillis = 8_500L)

        assertTrue(snapshot.hasFreshLocation)
        assertTrue(snapshot.hasMeaningfulDisplacement)
        assertTrue(snapshot.shouldEnterSuspectMoving)
    }

    @Test
    fun `stale coarse fix does not trigger suspect movement`() {
        val current = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = 30.0005
            longitude = 120.0005
            time = 1_000L
            accuracy = 90f
        }

        val snapshot = TrackingLowPowerSignals.fromLocations(previous = null, current = current, nowMillis = 30_000L)

        assertFalse(snapshot.hasFreshLocation)
        assertFalse(snapshot.shouldEnterSuspectMoving)
    }
}
```

新建 `MotionConfidenceEngineTest.kt`：

```kotlin
package com.wenhao.record.tracking

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionConfidenceEngineTest {

    @Test
    fun `recent acceleration and step growth make movement likely`() {
        val engine = MotionConfidenceEngine()
        val now = 1_713_420_000_000L

        engine.noteAccelerationVariance(1.1f, now - 1_000L)
        engine.noteStepCount(10f, now - 3_000L)
        engine.noteStepCount(15f, now - 500L)

        val snapshot = engine.evaluate(
            nowMillis = now,
            signals = MotionSignals(
                stepDelta = engine.currentStepDelta(),
                effectiveDistanceMeters = 24f,
                reportedSpeedMetersPerSecond = 0.7f,
                inferredSpeedMetersPerSecond = 1.0f,
                insideAnchor = false,
                sameAnchorWifi = false,
                poorAccuracy = false,
            ),
        )

        assertTrue(snapshot.movingLikely)
        assertFalse(snapshot.summary.isBlank())
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.TrackingLowPowerSignalsTest --tests com.wenhao.record.tracking.MotionConfidenceEngineTest`

预期：FAIL，`TrackingLowPowerSignals` 尚不存在。

- [ ] **步骤 3：实现最少代码**

新建 `TrackingLowPowerSignals.kt`：

```kotlin
package com.wenhao.record.tracking

import android.location.Location
import com.wenhao.record.map.GeoMath

data class TrackingLowPowerSignalsSnapshot(
    val hasFreshLocation: Boolean,
    val hasMeaningfulDisplacement: Boolean,
    val shouldEnterSuspectMoving: Boolean,
)

object TrackingLowPowerSignals {
    private const val MAX_LOW_POWER_FIX_AGE_MS = 12_000L
    private const val MIN_SUSPECT_DISPLACEMENT_METERS = 20f
    private const val MAX_LOW_POWER_ACCURACY_METERS = 60f

    fun fromLocations(
        previous: Location?,
        current: Location?,
        nowMillis: Long,
    ): TrackingLowPowerSignalsSnapshot {
        if (current == null) return TrackingLowPowerSignalsSnapshot(false, false, false)
        val ageMs = nowMillis - current.time
        val accuracy = current.accuracy.takeIf { current.hasAccuracy() } ?: Float.MAX_VALUE
        val fresh = current.time > 0L && ageMs <= MAX_LOW_POWER_FIX_AGE_MS && accuracy <= MAX_LOW_POWER_ACCURACY_METERS
        val displacement = if (previous == null) {
            false
        } else {
            GeoMath.distanceMeters(previous.latitude, previous.longitude, current.latitude, current.longitude) >= MIN_SUSPECT_DISPLACEMENT_METERS
        }
        return TrackingLowPowerSignalsSnapshot(
            hasFreshLocation = fresh,
            hasMeaningfulDisplacement = displacement,
            shouldEnterSuspectMoving = fresh && displacement,
        )
    }
}
```

`MotionConfidenceEngine.kt` 不改评分规则，只保证测试可观察输出稳定；`LocationSelectionUtils.kt` 继续按 `maxAgeMs` 过滤，但优先较新、精度更好的候选，避免把 80 m 级别缓存点提前喂给观察态。

- [ ] **步骤 4：运行测试验证通过**

运行：`gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.TrackingLowPowerSignalsTest --tests com.wenhao.record.tracking.MotionConfidenceEngineTest --tests com.wenhao.record.tracking.BackgroundTrackingServiceCoordinateTest`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/wenhao/record/tracking/TrackingLowPowerSignals.kt app/src/main/java/com/wenhao/record/tracking/MotionConfidenceEngine.kt app/src/main/java/com/wenhao/record/tracking/LocationSelectionUtils.kt app/src/test/java/com/wenhao/record/tracking/TrackingLowPowerSignalsTest.kt app/src/test/java/com/wenhao/record/tracking/MotionConfidenceEngineTest.kt app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServiceCoordinateTest.kt
git commit -m "feat(tracking): add low power wake signals"
```

## 任务 3：在保留四态的前提下升级相位策略

**文件：**
- 修改：`app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServicePhasePolicyTest.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingServicePhasePolicy.kt`

- [ ] **步骤 1：编写失败测试**

把 `BackgroundTrackingServicePhasePolicyTest.kt` 改成覆盖保留四态后的新规则：

```kotlin
package com.wenhao.record.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundTrackingServicePhasePolicyTest {

    @Test
    fun `enters suspect moving when low power signals indicate movement`() {
        val policy = BackgroundTrackingServicePhasePolicy()
        assertEquals(
            TrackingPhase.SUSPECT_MOVING,
            policy.nextPhase(
                current = TrackingPhase.IDLE,
                lowPowerSignals = TrackingLowPowerSignalsSnapshot(
                    hasFreshLocation = true,
                    hasMeaningfulDisplacement = true,
                    shouldEnterSuspectMoving = true,
                ),
                hasEnoughGoodFixesToRecord = false,
                signalLost = false,
                prolongedStill = false,
            ),
        )
    }

    @Test
    fun `promotes to active only after continuous good fixes are ready`() {
        val policy = BackgroundTrackingServicePhasePolicy()
        assertEquals(
            TrackingPhase.ACTIVE,
            policy.nextPhase(
                current = TrackingPhase.SUSPECT_MOVING,
                lowPowerSignals = TrackingLowPowerSignalsSnapshot(true, true, true),
                hasEnoughGoodFixesToRecord = true,
                signalLost = false,
                prolongedStill = false,
            ),
        )
    }

    @Test
    fun `keeps active when signal is temporarily lost but still not proven still`() {
        val policy = BackgroundTrackingServicePhasePolicy()
        assertEquals(
            TrackingPhase.ACTIVE,
            policy.nextPhase(
                current = TrackingPhase.ACTIVE,
                lowPowerSignals = TrackingLowPowerSignalsSnapshot(false, false, false),
                hasEnoughGoodFixesToRecord = false,
                signalLost = true,
                prolongedStill = false,
            ),
        )
    }

    @Test
    fun `downshifts only after prolonged still evidence`() {
        val policy = BackgroundTrackingServicePhasePolicy()
        assertEquals(
            TrackingPhase.SUSPECT_STOPPING,
            policy.nextPhase(
                current = TrackingPhase.ACTIVE,
                lowPowerSignals = TrackingLowPowerSignalsSnapshot(false, false, false),
                hasEnoughGoodFixesToRecord = false,
                signalLost = false,
                prolongedStill = true,
            ),
        )
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.BackgroundTrackingServicePhasePolicyTest`

预期：FAIL，`nextPhase(...)` 签名与断言行为尚未更新。

- [ ] **步骤 3：实现最少代码**

把 `BackgroundTrackingServicePhasePolicy.kt` 改成保留现有四态，但接收新的低功耗输入：

```kotlin
package com.wenhao.record.tracking

class BackgroundTrackingServicePhasePolicy {
    fun nextPhase(
        current: TrackingPhase,
        lowPowerSignals: TrackingLowPowerSignalsSnapshot,
        hasEnoughGoodFixesToRecord: Boolean,
        signalLost: Boolean,
        prolongedStill: Boolean,
    ): TrackingPhase {
        return when (current) {
            TrackingPhase.IDLE -> if (lowPowerSignals.shouldEnterSuspectMoving) TrackingPhase.SUSPECT_MOVING else TrackingPhase.IDLE
            TrackingPhase.SUSPECT_MOVING -> when {
                hasEnoughGoodFixesToRecord -> TrackingPhase.ACTIVE
                !lowPowerSignals.shouldEnterSuspectMoving -> TrackingPhase.IDLE
                else -> TrackingPhase.SUSPECT_MOVING
            }
            TrackingPhase.ACTIVE -> when {
                prolongedStill -> TrackingPhase.SUSPECT_STOPPING
                else -> TrackingPhase.ACTIVE
            }
            TrackingPhase.SUSPECT_STOPPING -> when {
                hasEnoughGoodFixesToRecord -> TrackingPhase.ACTIVE
                prolongedStill -> TrackingPhase.IDLE
                signalLost -> TrackingPhase.SUSPECT_STOPPING
                else -> TrackingPhase.SUSPECT_STOPPING
            }
        }
    }
}
```

这里故意不把 `signalLost` 直接映射成新相位，而是在服务层单独处理“停记但不降相位”。

- [ ] **步骤 4：运行测试验证通过**

运行：`gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.BackgroundTrackingServicePhasePolicyTest`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingServicePhasePolicy.kt app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServicePhasePolicyTest.kt
git commit -m "feat(tracking): drive phases from wake signals and good fixes"
```

## 任务 4：把低功耗唤醒、signal lost 停记和恢复新段接入后台服务

**文件：**
- 创建：`app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServiceSignalLossTest.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt`

- [ ] **步骤 1：编写失败测试**

新建 `BackgroundTrackingServiceSignalLossTest.kt`：

```kotlin
package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundTrackingServiceSignalLossTest {

    @Test
    fun `signal lost blocks persistence until good fixes recover`() {
        val service = Robolectric.buildService(BackgroundTrackingService::class.java).get()
        setField(service, "currentPhase", TrackingPhase.ACTIVE)
        setField(service, "signalLost", true)

        val blocked = invokeShouldPersist(
            service = service,
            point = TrackPoint(latitude = 30.0, longitude = 120.0, timestampMillis = 1_000L, accuracyMeters = 80f),
            goodFixReady = false,
        )
        val recovered = invokeShouldPersist(
            service = service,
            point = TrackPoint(latitude = 30.0002, longitude = 120.0002, timestampMillis = 6_000L, accuracyMeters = 10f),
            goodFixReady = true,
        )

        assertEquals(false, blocked)
        assertEquals(true, recovered)
    }

    private fun invokeShouldPersist(service: BackgroundTrackingService, point: TrackPoint, goodFixReady: Boolean): Boolean {
        val method = BackgroundTrackingService::class.java.getDeclaredMethod(
            "shouldPersistPoint",
            TrackPoint::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(service, point, goodFixReady) as Boolean
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        val field = BackgroundTrackingService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.BackgroundTrackingServiceSignalLossTest`

预期：FAIL，`signalLost` 字段和 `shouldPersistPoint(...)` 尚不存在。

- [ ] **步骤 3：实现最少代码**

在 `BackgroundTrackingService.kt` 中做以下最小接入：

1. 新增字段：

```kotlin
private val fixQualityGate = TrackFixQualityGate(
    requiredConsecutiveGoodFixes = 3,
    maxAcceptedAccuracyMeters = 25f,
    maxFixAgeMillis = 8_000L,
    minMeaningfulDistanceMeters = 8f,
)
private val motionConfidenceEngine = MotionConfidenceEngine()
private var lastLowPowerLocation: Location? = null
private var lastGoodFixCandidate: TrackPoint? = null
private var signalLost = false
private var activeSegmentStartAfterRecovery = false
```

2. 新增轻量传感器注册与注销：

```kotlin
private lateinit var sensorManager: SensorManager
private var accelerometer: Sensor? = null
private var stepCounter: Sensor? = null
```

在 `onCreate()` 初始化 `sensorManager`，在 `enableTracking()` 注册，在 `disableTracking()` / `onDestroy()` 注销。只接入：
- `TYPE_ACCELEROMETER`
- `TYPE_STEP_COUNTER`

在回调中调用：

```kotlin
motionConfidenceEngine.noteAccelerationVariance(variance, System.currentTimeMillis())
motionConfidenceEngine.noteStepCount(totalSteps, System.currentTimeMillis())
```

3. 在 `requestLocationUpdatesForPhase()` 中按阶段拆 provider：
- `IDLE`：仅 `PASSIVE_PROVIDER`
- `SUSPECT_MOVING`：`PASSIVE_PROVIDER` + `NETWORK_PROVIDER`
- `ACTIVE`：`GPS_PROVIDER` + `NETWORK_PROVIDER`
- `SUSPECT_STOPPING`：`PASSIVE_PROVIDER` + `NETWORK_PROVIDER`

4. 在 `handleLocationUpdate(location)` 中，把是否落点改成：

```kotlin
val nowMillis = System.currentTimeMillis()
val point = location.toTrackPoint()
val fixEvaluation = fixQualityGate.noteFix(
    point = point,
    nowMillis = nowMillis,
    previousCandidate = lastGoodFixCandidate,
)
if (fixEvaluation.acceptedAsGoodFix) {
    lastGoodFixCandidate = point
}
val lowPowerSignals = TrackingLowPowerSignals.fromLocations(
    previous = lastLowPowerLocation,
    current = location,
    nowMillis = nowMillis,
)
lastLowPowerLocation = location
val motionSnapshot = motionConfidenceEngine.evaluate(
    nowMillis = nowMillis,
    signals = MotionSignals(
        stepDelta = motionConfidenceEngine.currentStepDelta(),
        effectiveDistanceMeters = netDistanceMeters,
        reportedSpeedMetersPerSecond = speedMetersPerSecond ?: 0f,
        inferredSpeedMetersPerSecond = inferredSpeedMetersPerSecond,
        insideAnchor = false,
        sameAnchorWifi = false,
        poorAccuracy = (point.accuracyMeters ?: Float.MAX_VALUE) > 35f,
    ),
)
val shouldWake = lowPowerSignals.shouldEnterSuspectMoving || motionSnapshot.movingLikely
signalLost = currentPhase == TrackingPhase.ACTIVE && !fixEvaluation.acceptedAsGoodFix
```

5. 用新的 `nextPhase(...)` 调整相位，但 `signalLost` 不直接降成新相位；恢复到连续好点后清掉 `signalLost`，并标记下一次正式落点要新开段。

6. 新增：

```kotlin
private fun shouldPersistPoint(point: TrackPoint, goodFixReady: Boolean): Boolean {
    return when {
        signalLost -> goodFixReady
        currentPhase == TrackingPhase.ACTIVE -> (point.accuracyMeters ?: Float.MAX_VALUE) <= 25f
        else -> false
    }
}
```

7. 只有 `shouldPersistPoint(...)` 返回 `true` 时才 `appendRawPoint(...)`；从 `signalLost = true` 恢复时，把 `phaseAnchorPoint = latestPoint` 并重置连续好点计数，确保恢复后新开一段、不强接室内空档。

- [ ] **步骤 4：运行测试验证通过**

运行：`gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.BackgroundTrackingServiceSignalLossTest --tests com.wenhao.record.tracking.BackgroundTrackingServiceRollingAnalysisTest`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServiceSignalLossTest.kt
git commit -m "feat(tracking): stop recording when signal is lost indoors"
```

## 任务 5：放宽正式记录态的实时删点强度

**文件：**
- 修改：`app/src/test/java/com/wenhao/record/tracking/TrackNoiseFilterTest.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/TrackNoiseFilter.kt`

- [ ] **步骤 1：编写失败测试**

在 `TrackNoiseFilterTest.kt` 中新增一个正式记录态中等精度点不应过早被丢弃的用例：

```kotlin
@Test
fun `keeps moderate active fix for later path sanitizing`() {
    val result = TrackNoiseFilter.evaluate(
        previousPoint = null,
        lastPoint = TrackPoint(
            latitude = 30.0,
            longitude = 120.0,
            timestampMillis = 1_000L,
            accuracyMeters = 8f,
        ),
        sample = TrackNoiseSample(
            point = TrackPoint(
                latitude = 30.00018,
                longitude = 120.00018,
                timestampMillis = 6_000L,
                accuracyMeters = 32f,
            ),
            speedMetersPerSecond = 1.0f,
            locationAgeMs = 100L,
        ),
    )

    assertEquals(TrackNoiseAction.ACCEPT, result.action)
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.TrackNoiseFilterTest`

预期：FAIL，当前实现会返回 `DROP_DRIFT`。

- [ ] **步骤 3：实现最少代码**

在 `TrackNoiseFilter.kt` 中只放宽正式记录态里最早的严格精度拦截，不改跳点判断：

```kotlin
private const val MAX_POOR_ACCURACY_METERS = 40f
private const val MAX_POOR_ACCURACY_INITIAL_METERS = 35f
```

并把：

```kotlin
if (candidateAccuracy > MAX_POOR_ACCURACY_METERS) {
    return TrackNoiseResult(TrackNoiseAction.DROP_DRIFT)
}
```

改成：

```kotlin
if (candidateAccuracy > MAX_POOR_ACCURACY_METERS && lastPoint == null) {
    return TrackNoiseResult(TrackNoiseAction.DROP_DRIFT)
}
```

这样首点仍然严格，正式记录态的中等精度点则交给后续 `TrackPathSanitizer` 决定。

- [ ] **步骤 4：运行测试验证通过**

运行：`gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.TrackNoiseFilterTest`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/wenhao/record/tracking/TrackNoiseFilter.kt app/src/test/java/com/wenhao/record/tracking/TrackNoiseFilterTest.kt
git commit -m "fix(tracking): reduce eager point dropping in active recording"
```

## 任务 6：回归验证与人工检查

**文件：**
- 修改：本计划涉及的 Kotlin 实现与测试文件

- [ ] **步骤 1：运行目标单元测试**

运行：`gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.TrackFixQualityGateTest --tests com.wenhao.record.tracking.TrackingLowPowerSignalsTest --tests com.wenhao.record.tracking.MotionConfidenceEngineTest --tests com.wenhao.record.tracking.BackgroundTrackingServicePhasePolicyTest --tests com.wenhao.record.tracking.BackgroundTrackingServiceSignalLossTest --tests com.wenhao.record.tracking.TrackNoiseFilterTest`

预期：PASS。

- [ ] **步骤 2：运行关键 Robolectric 测试与 Kotlin 编译**

运行：`gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.BackgroundTrackingServiceRollingAnalysisTest --tests com.wenhao.record.tracking.BackgroundTrackingServiceCoordinateTest && gradle :app:compileDebugKotlin`

预期：全部 PASS，且出现 `BUILD SUCCESSFUL`。

- [ ] **步骤 3：检查 diff 范围**

运行：`git diff -- app/src/main/java/com/wenhao/record/tracking/TrackFixQualityGate.kt app/src/main/java/com/wenhao/record/tracking/TrackingLowPowerSignals.kt app/src/main/java/com/wenhao/record/tracking/MotionConfidenceEngine.kt app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingServicePhasePolicy.kt app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt app/src/main/java/com/wenhao/record/tracking/LocationSelectionUtils.kt app/src/main/java/com/wenhao/record/tracking/TrackNoiseFilter.kt app/src/test/java/com/wenhao/record/tracking/TrackFixQualityGateTest.kt app/src/test/java/com/wenhao/record/tracking/TrackingLowPowerSignalsTest.kt app/src/test/java/com/wenhao/record/tracking/MotionConfidenceEngineTest.kt app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServicePhasePolicyTest.kt app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServiceCoordinateTest.kt app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServiceSignalLossTest.kt app/src/test/java/com/wenhao/record/tracking/TrackNoiseFilterTest.kt docs/superpowers/specs/2026-04-26-efficient-accurate-tracking-design.md docs/superpowers/plans/2026-04-26-efficient-accurate-tracking-implementation.md`

预期：只包含本次低功耗唤醒、连续好点确认、室内断段相关改动。

- [ ] **步骤 4：人工验证 4 个场景**

在真机上逐项验证：

- 室外短走 3～5 分钟，确认开始移动后能较快进入记录。
- 室外 -> 室内 -> 室外，确认室内断开、出楼后恢复并新开一段。
- 手机静止 30 分钟以上，确认不频繁新增轨迹点。
- 室内来回走动但无好定位，确认不落正式轨迹。

- [ ] **步骤 5：如果恢复太慢或误触发，则停止并单独起补充计划**

如果完成后发现以下任一问题仍然明显：

- 室外起步进入记录需要明显超过 10 秒。
- 室内经常误触发进入正式记录。
- 出楼后长时间拿不到恢复。

不要继续在同一轮里直接叠加更多阈值微调。先记录日志样本、问题路径和诊断信息，再为“唤醒阈值与恢复窗口调参”单独补一份规格和计划，避免把实现和调参混成一轮。
