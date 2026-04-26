# Today Session、本地历史与 D1 容灾实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 把当天轨迹显示、本地历史查询和开发阶段的卸载恢复整理成一条 local-first、D1 mirror 的完整链路。

**架构：** Android 端新增 `today_session` / `today_session_point` 本地模型，`BackgroundTrackingService` 在写 raw point 的同时维护当天会话元信息与展示点；历史页和详情页只读取本地 Room 历史表，不再把 D1 当成页面实时查询源。D1 侧第一阶段复用现有 `processed_histories` / `history-days` 作为历史镜像，并新增 today session 镜像表与接口；启动恢复 worker 负责把远端历史和未完成当天会话补回本地。

**技术栈：** Kotlin、Room、WorkManager、Robolectric、Cloudflare Workers、D1、TypeScript、Vitest。

---

## 当前进度与剩余工作（2026-04-26）

### 已完成任务

- **任务 1：建立 today session 本地模型** — 已完成。
- **任务 2：把采点链路接入 today session** — 已完成。
- **任务 3：整理本地历史查询结构** — 已完成。

### 任务 4：接入 today session 的 D1 mirror 上传链路 — 已完成

#### 已完成内容

**Android 端：**

- 已创建 `TodaySessionUploadService.kt`，负责上传 today session 元信息。
- 已创建 `TodaySessionPointUploadService.kt`，负责上传 today session 点位批次。
- 已创建 `TodaySessionSyncWorker.kt`，上传顺序为「session → points → 标记已同步」。
- 已创建 `TodaySessionSyncCoordinator.kt`，实现 `30 秒 / 20 个点 / force` 触发规则。
- 已创建 `TodaySessionRemoteReadService.kt`，支持读取远端 open today session 快照。
- 已修改 `TrackUploadScheduler.kt`，把 `TodaySessionSyncWorker` 纳入 one-time pipeline。
- 已在 `BackgroundTrackingService.kt` 接入 today session 同步触发逻辑，包括采点后触发、相位切换触发、停止时强制触发。

**Worker 端：**

- 已修改 `worker/src/schema.sql`，增加 `today_session` 与 `today_session_point` 表。
- 已修改 `worker/src/types.ts`，补齐 today session 请求 / 响应 / persistence 类型，并恢复 diagnostic 相关类型块一致性。
- 已修改 `worker/src/validation.ts`，增加 today session batch 校验。
- 已修改 `worker/src/d1.ts`，增加 today session D1 持久化与 open session 读取实现。
- 已修改 `worker/src/index.ts`，接入 `/today-sessions/batch`、`/today-session-points/batch`、`/today-sessions/open` 三条路由。
- 已补齐 `worker/src/today-session-index.test.ts` 与 `worker/src/d1.test.ts`。

#### 已完成验证

**Worker 端测试：**

```bash
rtk npm --prefix "/home/wenha/project/track-record-new/worker" test -- today-session-index d1
```

结果：`2` 个测试文件、`18` 条测试全部通过。

**Android 端测试：**

```bash
gradle :app:testDebugUnitTest --tests "com.wenhao.record.data.tracking.TodaySessionSyncWorkerTest"
```

结果：通过。

```bash
gradle :app:testDebugUnitTest --tests "com.wenhao.record.data.tracking.TodaySessionSyncCoordinatorTest" --tests "com.wenhao.record.data.tracking.TodaySessionRemoteReadServiceTest" --tests "com.wenhao.record.data.tracking.TrackUploadSchedulerTest"
```

结果：通过。

#### 本轮补充完成

- 已补齐 `worker/src/index.ts` 的 today session 专用错误日志分支。
- 已验证 `BackgroundTrackingServiceTodaySessionTest`、`BackgroundTrackingServiceSignalLossTest`、`TrackUploadSchedulerTest`。
- 已修复 `BackgroundTrackingService` 在 DB 被清空但 runtime snapshot 仍保留旧 `currentSessionId` 时复用失效 session 的问题。
- 已把采点后的 raw point / today session 本地写入调整为 local-first，同步调度仍异步触发。

### 任务 5：实现启动恢复链路 — 已完成

#### 目标

启动 App 后：

1. 先恢复本地历史所需的远端镜像数据。
2. 再恢复远端未完成 today session。
3. 恢复完成后刷新首页与历史页观察源。

#### 计划文件对应范围

- 创建：`app/src/main/java/com/wenhao/record/data/history/TrackMirrorRecoveryWorker.kt`
- 创建：`app/src/test/java/com/wenhao/record/data/history/TrackMirrorRecoveryWorkerTest.kt`
- 修改：`app/src/main/java/com/wenhao/record/RecordApplication.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TodaySessionStorage.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TrackUploadScheduler.kt`

#### 已完成内容

- 已新建 `TrackMirrorRecoveryWorker`。
- 恢复顺序固定为「history → today session」。
- `HistoryStorage` 已增加远端恢复写回入口。
- `TodaySessionStorage` 已增加 `replaceWithRemoteSession(...)` 远端覆盖恢复入口。
- `TrackUploadScheduler` 已增加 `kickMirrorRecovery(...)`。
- `RecordApplication` 启动时调度一次恢复 worker。
- 已补充 `TrackMirrorRecoveryWorkerTest`，并验证未破坏 `BootCompletedReceiver` 行为。

#### 建议验收命令

```bash
gradle :app:testDebugUnitTest --tests "com.wenhao.record.data.history.TrackMirrorRecoveryWorkerTest" --tests "com.wenhao.record.tracking.BootCompletedReceiverTest" --tests "com.wenhao.record.data.history.RemoteHistoryRepositoryTest"
```

### 任务 6：补齐完成态清理与最终验证 — 已完成

#### 目标

把 today session、本地 history、raw points、D1 mirror 的闭环收尾补齐，明确哪些数据保留、哪些数据在完成后可清理。

#### 计划文件对应范围

- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TodaySessionStorage.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/history/HistoryRetentionPolicy.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/RawPointRetentionPolicy.kt`
- 修改：`app/src/test/java/com/wenhao/record/data/tracking/TodaySessionStorageTest.kt`
- 修改：`app/src/test/java/com/wenhao/record/data/history/HistoryRetentionPolicyTest.kt`
- 修改：`app/src/test/java/com/wenhao/record/data/local/TrackDatabaseMigrationTest.kt`

#### 已完成内容

- `TodaySessionStorage.markCompleted(...)` 会把会话重新标记为待同步，确保完成态可镜像到 D1。
- `TodaySessionStorage.deleteCompletedSessionPoints(...)` 只清理已完成会话对应的 today session points。
- raw points 继续遵循 `RawPointRetentionPolicy` 的「同步成功 + 历史整理成功后再删」策略。
- `HistoryRetentionPolicy` 不再因为 mirror 成功删除本地 `history_record` / `history_point`，只清理过期 uploaded-id 记账。
- 已完成 Android 计划测试、`assembleDebug` 和 worker 全量 Vitest。

#### 建议最终验证命令

```bash
gradle :app:testDebugUnitTest --tests "com.wenhao.record.data.local.TrackDatabaseMigrationTest" --tests "com.wenhao.record.data.tracking.TodaySessionStorageTest" --tests "com.wenhao.record.data.tracking.TodaySessionSyncWorkerTest" --tests "com.wenhao.record.data.history.LocalHistoryRepositoryTest" --tests "com.wenhao.record.data.history.TrackMirrorRecoveryWorkerTest" --tests "com.wenhao.record.tracking.BackgroundTrackingServiceSignalLossTest" --tests "com.wenhao.record.tracking.BackgroundTrackingServiceTodaySessionTest"
```

```bash
gradle :app:assembleDebug
```

```bash
rtk npm --prefix "/home/wenha/project/track-record-new/worker" test
```

### 本轮最终验证结果

- `bash gradlew :app:testDebugUnitTest` — 通过，`261` 条测试全部通过。
- `bash gradlew :app:testDebugUnitTest --tests "com.wenhao.record.data.local.TrackDatabaseMigrationTest" --tests "com.wenhao.record.data.tracking.TodaySessionStorageTest" --tests "com.wenhao.record.data.tracking.TodaySessionSyncWorkerTest" --tests "com.wenhao.record.data.history.LocalHistoryRepositoryTest" --tests "com.wenhao.record.data.history.TrackMirrorRecoveryWorkerTest" --tests "com.wenhao.record.tracking.BackgroundTrackingServiceSignalLossTest" --tests "com.wenhao.record.tracking.BackgroundTrackingServiceTodaySessionTest"` — 通过。
- `bash gradlew :app:assembleDebug` — 通过。
- `npm --prefix worker test` — 通过，`10` 个测试文件、`45` 条测试全部通过。

---

## 文件结构

### Android 端新增文件

- `app/src/main/java/com/wenhao/record/data/local/stream/TodaySessionDao.kt` — today session 元信息与点位的 Room 查询入口。
- `app/src/main/java/com/wenhao/record/data/tracking/TodaySessionStorage.kt` — today session 的本地仓库，负责建会话、追加点、更新同步状态、恢复会话。
- `app/src/main/java/com/wenhao/record/data/history/LocalHistoryRepository.kt` — 历史列表摘要与详情读取的本地优先入口。
- `app/src/main/java/com/wenhao/record/data/tracking/TodaySessionUploadService.kt` — 上传 today session 元信息到 worker。
- `app/src/main/java/com/wenhao/record/data/tracking/TodaySessionPointUploadService.kt` — 上传 today session 点位批次到 worker。
- `app/src/main/java/com/wenhao/record/data/tracking/TodaySessionRemoteReadService.kt` — 读取 D1 中未完成 today session。
- `app/src/main/java/com/wenhao/record/data/tracking/TodaySessionSyncCoordinator.kt` — 30 秒 / 20 点 / 相位切换等同步触发节流器。
- `app/src/main/java/com/wenhao/record/data/tracking/TodaySessionSyncWorker.kt` — WorkManager one-shot 同步 today session。
- `app/src/main/java/com/wenhao/record/data/history/TrackMirrorRecoveryWorker.kt` — 启动时恢复历史与未完成 today session。
- `app/src/test/java/com/wenhao/record/data/tracking/TodaySessionStorageTest.kt` — today session 本地存取与清理测试。
- `app/src/test/java/com/wenhao/record/data/tracking/TodaySessionSyncWorkerTest.kt` — today session 上传顺序与状态流转测试。
- `app/src/test/java/com/wenhao/record/data/history/LocalHistoryRepositoryTest.kt` — 历史摘要 / 详情分层查询测试。
- `app/src/test/java/com/wenhao/record/data/history/TrackMirrorRecoveryWorkerTest.kt` — 启动恢复链路测试。

### Android 端修改文件

- `app/src/main/java/com/wenhao/record/data/local/TrackDatabase.kt` — 注册新实体、DAO 和 migration。
- `app/src/main/java/com/wenhao/record/data/local/stream/ContinuousTrackEntities.kt` — 增加 `TodaySessionEntity`、`TodaySessionPointEntity`。
- `app/src/main/java/com/wenhao/record/data/local/stream/ContinuousTrackDao.kt` — 增加 raw point 清理与按日统计辅助查询。
- `app/src/main/java/com/wenhao/record/data/local/history/HistoryEntities.kt` — 为本地历史记录补 `sourceSessionId`、`dateKey`、`syncState`、`version`。
- `app/src/main/java/com/wenhao/record/data/local/history/HistoryDao.kt` — 拆出摘要查询、详情查询、按日查询。
- `app/src/main/java/com/wenhao/record/data/tracking/TodayTrackDisplayCache.kt` — 改成 today session 的展示 façade，而不是独立存储源。
- `app/src/main/java/com/wenhao/record/data/tracking/TrackingRuntimeSnapshotStorage.kt` — 从 today session 恢复快照，而不是只靠内存缓存。
- `app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt` — 内部改成基于 summary / detail 查询，并支持恢复写回。
- `app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt` — 采点后写 today session、结束时落 history、触发 today session 同步。
- `app/src/main/java/com/wenhao/record/data/tracking/TrackUploadScheduler.kt` — 增加 today session sync 与 recovery worker 调度。
- `app/src/main/java/com/wenhao/record/RecordApplication.kt` — App 启动时拉起恢复 worker。
- `app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt` — record 页继续观察 today session façade；进入页面时清理过期 today session。
- `app/src/main/java/com/wenhao/record/ui/history/HistoryViewModel.kt` — 历史列表改走 `LocalHistoryRepository`，不再把 D1 当页面查询源。
- `app/src/main/java/com/wenhao/record/ui/map/MapActivity.kt` — 详情页改走本地历史详情。
- `app/src/test/java/com/wenhao/record/data/local/TrackDatabaseMigrationTest.kt` — 断言新 migration 和 today session 类型已暴露。
- `app/src/test/java/com/wenhao/record/tracking/BootCompletedReceiverTest.kt` — 启动恢复后不破坏原有开机恢复行为。

### Worker 端新增文件

- `worker/src/today-session-index.test.ts` — today session API 路由测试。

### Worker 端修改文件

- `worker/src/schema.sql` — 增加 `today_session`、`today_session_point` 表。
- `worker/src/types.ts` — 增加 today session 请求 / 响应 / persistence 类型。
- `worker/src/validation.ts` — 增加 today session 请求校验。
- `worker/src/d1.ts` — 增加 today session 持久化与读取实现。
- `worker/src/index.ts` — 增加 today session 批量 upsert 与恢复读取路由。
- `worker/src/d1.test.ts` — 增加 today session D1 持久化测试。

---

### 任务 1：建立 today session 本地模型

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/data/local/stream/TodaySessionDao.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/tracking/TodaySessionStorage.kt`
- 创建：`app/src/test/java/com/wenhao/record/data/tracking/TodaySessionStorageTest.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/local/stream/ContinuousTrackEntities.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/local/TrackDatabase.kt`
- 修改：`app/src/test/java/com/wenhao/record/data/local/TrackDatabaseMigrationTest.kt`

- [ ] **步骤 1：编写失败的 today session 测试**

```kotlin
package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.stream.TodaySessionDao
import com.wenhao.record.data.local.stream.TodaySessionEntity
import com.wenhao.record.data.local.stream.TodaySessionPointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodaySessionStorageTest {

    @Test
    fun `create or restore open session keeps same session within a day`() = runBlocking {
        val dao = FakeTodaySessionDao()
        val storage = TodaySessionStorage(dao)
        val first = storage.createOrRestoreOpenSession(nowMillis = 1_714_300_000_000L)
        val second = storage.createOrRestoreOpenSession(nowMillis = 1_714_300_600_000L)

        assertEquals(first.sessionId, second.sessionId)
        assertTrue(dao.sessions.single().status == TodaySessionStatus.ACTIVE.name)
    }

    @Test
    fun `append point marks point pending sync and updates latest timestamp`() = runBlocking {
        val dao = FakeTodaySessionDao()
        val storage = TodaySessionStorage(dao)
        val session = storage.createOrRestoreOpenSession(nowMillis = 1_714_300_000_000L)

        storage.appendPoint(
            sessionId = session.sessionId,
            pointId = 18L,
            rawPoint = rawPoint(timestampMillis = 1_714_300_010_000L),
            phase = "ACTIVE",
            nowMillis = 1_714_300_010_000L,
        )

        assertEquals(1, dao.points.size)
        assertEquals(TodaySessionSyncState.PENDING.name, dao.points.single().syncState)
        assertEquals(1_714_300_010_000L, dao.sessions.single().lastPointAt)
    }

    @Test
    fun `complete session keeps meta but clears active query`() = runBlocking {
        val dao = FakeTodaySessionDao()
        val storage = TodaySessionStorage(dao)
        val session = storage.createOrRestoreOpenSession(nowMillis = 1_714_300_000_000L)

        storage.markCompleted(
            sessionId = session.sessionId,
            endedAt = 1_714_303_000_000L,
            nowMillis = 1_714_303_000_000L,
        )

        assertFalse(storage.hasOpenSession(dayStartMillis = session.dayStartMillis))
        assertEquals(TodaySessionStatus.COMPLETED.name, dao.sessions.single().status)
    }

    private fun rawPoint(timestampMillis: Long): RawTrackPoint {
        return RawTrackPoint(
            pointId = 0,
            timestampMillis = timestampMillis,
            latitude = 30.0,
            longitude = 120.0,
            accuracyMeters = 8f,
            altitudeMeters = 12.0,
            speedMetersPerSecond = 1.1f,
            bearingDegrees = null,
            provider = "gps",
            sourceType = "LOCATION_MANAGER",
            isMock = false,
            wifiFingerprintDigest = null,
            activityType = "WALKING",
            activityConfidence = 0.9f,
            samplingTier = SamplingTier.ACTIVE,
        )
    }
}
```

并在 migration 测试里新增断言：

```kotlin
@Test
fun `track database declares migration 14 to 15 and exposes today session entities`() {
    val companionClass = Class.forName("com.wenhao.record.data.local.TrackDatabase\$Companion")

    assertTrue(
        companionClass.declaredFields.any { it.name == "MIGRATION_14_15" } ||
            companionClass.declaredMethods.any { it.name == "getMIGRATION_14_15" }
    )

    assertTrue(classExists("com.wenhao.record.data.local.stream.TodaySessionEntity"))
    assertTrue(classExists("com.wenhao.record.data.local.stream.TodaySessionPointEntity"))
    assertTrue(classExists("com.wenhao.record.data.local.stream.TodaySessionDao"))
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
gradle :app:testDebugUnitTest --tests com.wenhao.record.data.tracking.TodaySessionStorageTest --tests com.wenhao.record.data.local.TrackDatabaseMigrationTest
```

预期：FAIL，提示 `TodaySessionStorage`、`TodaySessionDao`、`TodaySessionEntity` 或 `MIGRATION_14_15` 不存在。

- [ ] **步骤 3：实现 Room 实体、DAO 和 storage**

在 `ContinuousTrackEntities.kt` 中新增实体：

```kotlin
@Entity(
    tableName = "today_session",
    indices = [
        Index(value = ["dayStartMillis", "status"]),
        Index(value = ["updatedAt"]),
    ],
)
data class TodaySessionEntity(
    @PrimaryKey
    val sessionId: String,
    val dayStartMillis: Long,
    val status: String,
    val startedAt: Long,
    val lastPointAt: Long?,
    val endedAt: Long?,
    val lastSyncedAt: Long?,
    val syncState: String,
    val phase: String,
    val anchorLatitude: Double?,
    val anchorLongitude: Double?,
    val anchorTimestampMillis: Long?,
    val latestLatitude: Double?,
    val latestLongitude: Double?,
    val latestAccuracyMeters: Float?,
    val latestAltitudeMeters: Double?,
    val lastAnalysisAt: Long?,
    val recoveredFromRemote: Boolean,
    val updatedAt: Long,
)

@Entity(
    tableName = "today_session_point",
    primaryKeys = ["sessionId", "pointId"],
    indices = [
        Index(value = ["dayStartMillis", "timestampMillis"]),
        Index(value = ["syncState", "timestampMillis"]),
    ],
)
data class TodaySessionPointEntity(
    val sessionId: String,
    val pointId: Long,
    val dayStartMillis: Long,
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Float?,
    val provider: String,
    val samplingTier: String,
    val syncState: String,
)
```

创建 `TodaySessionDao.kt`：

```kotlin
@Dao
interface TodaySessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(entity: TodaySessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPoint(entity: TodaySessionPointEntity)

    @Query(
        """
        SELECT * FROM today_session
        WHERE dayStartMillis = :dayStartMillis AND status IN (:openStates)
        ORDER BY startedAt DESC
        LIMIT 1
        """
    )
    suspend fun loadOpenSession(dayStartMillis: Long, openStates: List<String>): TodaySessionEntity?

    @Query(
        """
        SELECT * FROM today_session_point
        WHERE sessionId = :sessionId
        ORDER BY timestampMillis ASC
        """
    )
    suspend fun loadPoints(sessionId: String): List<TodaySessionPointEntity>

    @Query(
        """
        SELECT * FROM today_session_point
        WHERE sessionId = :sessionId AND syncState = :syncState
        ORDER BY timestampMillis ASC
        LIMIT :limit
        """
    )
    suspend fun loadPointsBySyncState(sessionId: String, syncState: String, limit: Int): List<TodaySessionPointEntity>

    @Query(
        """
        UPDATE today_session_point
        SET syncState = :syncState
        WHERE sessionId = :sessionId AND pointId IN (:pointIds)
        """
    )
    suspend fun updatePointSyncState(sessionId: String, pointIds: List<Long>, syncState: String)

    @Query(
        """
        DELETE FROM today_session_point
        WHERE sessionId = :sessionId
        """
    )
    suspend fun deletePointsForSession(sessionId: String)

    @Query(
        """
        DELETE FROM today_session
        WHERE status = :status AND dayStartMillis < :minDayStartMillis
        """
    )
    suspend fun deleteSessionsByStatusBefore(status: String, minDayStartMillis: Long)
}
```

创建 `TodaySessionStorage.kt`，定义状态并实现核心方法：

```kotlin
enum class TodaySessionStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    RECOVERED,
}

enum class TodaySessionSyncState {
    PENDING,
    SYNCED,
    FAILED,
}

class TodaySessionStorage(
    private val dao: TodaySessionDao,
) {
    suspend fun createOrRestoreOpenSession(nowMillis: Long): TodaySessionEntity {
        val dayStartMillis = dayStartMillis(nowMillis)
        val existing = dao.loadOpenSession(
            dayStartMillis = dayStartMillis,
            openStates = listOf(
                TodaySessionStatus.ACTIVE.name,
                TodaySessionStatus.PAUSED.name,
                TodaySessionStatus.RECOVERED.name,
            ),
        )
        if (existing != null) return existing

        val created = TodaySessionEntity(
            sessionId = "session_$dayStartMillis\_$nowMillis",
            dayStartMillis = dayStartMillis,
            status = TodaySessionStatus.ACTIVE.name,
            startedAt = nowMillis,
            lastPointAt = null,
            endedAt = null,
            lastSyncedAt = null,
            syncState = TodaySessionSyncState.PENDING.name,
            phase = "IDLE",
            anchorLatitude = null,
            anchorLongitude = null,
            anchorTimestampMillis = null,
            latestLatitude = null,
            latestLongitude = null,
            latestAccuracyMeters = null,
            latestAltitudeMeters = null,
            lastAnalysisAt = null,
            recoveredFromRemote = false,
            updatedAt = nowMillis,
        )
        dao.upsertSession(created)
        return created
    }

    suspend fun appendPoint(
        sessionId: String,
        pointId: Long,
        rawPoint: RawTrackPoint,
        phase: String,
        nowMillis: Long,
    ) {
        val dayStartMillis = dayStartMillis(rawPoint.timestampMillis)
        dao.upsertPoint(
            TodaySessionPointEntity(
                sessionId = sessionId,
                pointId = pointId,
                dayStartMillis = dayStartMillis,
                timestampMillis = rawPoint.timestampMillis,
                latitude = rawPoint.latitude,
                longitude = rawPoint.longitude,
                accuracyMeters = rawPoint.accuracyMeters,
                altitudeMeters = rawPoint.altitudeMeters,
                speedMetersPerSecond = rawPoint.speedMetersPerSecond,
                provider = rawPoint.provider,
                samplingTier = rawPoint.samplingTier.name,
                syncState = TodaySessionSyncState.PENDING.name,
            )
        )
        val current = dao.loadOpenSession(
            dayStartMillis,
            listOf(
                TodaySessionStatus.ACTIVE.name,
                TodaySessionStatus.PAUSED.name,
                TodaySessionStatus.RECOVERED.name,
            ),
        ) ?: return
        dao.upsertSession(
            current.copy(
                lastPointAt = rawPoint.timestampMillis,
                phase = phase,
                latestLatitude = rawPoint.latitude,
                latestLongitude = rawPoint.longitude,
                latestAccuracyMeters = rawPoint.accuracyMeters,
                latestAltitudeMeters = rawPoint.altitudeMeters,
                syncState = TodaySessionSyncState.PENDING.name,
                updatedAt = nowMillis,
            )
        )
    }

    suspend fun markCompleted(sessionId: String, endedAt: Long, nowMillis: Long) {
        val current = dao.loadPoints(sessionId)
        val first = current.firstOrNull()
        val open = first?.dayStartMillis?.let { dayStart ->
            dao.loadOpenSession(
                dayStart,
                listOf(
                    TodaySessionStatus.ACTIVE.name,
                    TodaySessionStatus.PAUSED.name,
                    TodaySessionStatus.RECOVERED.name,
                ),
            )
        } ?: return
        dao.upsertSession(
            open.copy(
                status = TodaySessionStatus.COMPLETED.name,
                endedAt = endedAt,
                syncState = TodaySessionSyncState.PENDING.name,
                updatedAt = nowMillis,
            )
        )
    }

    suspend fun hasOpenSession(dayStartMillis: Long): Boolean {
        return dao.loadOpenSession(
            dayStartMillis,
            listOf(
                TodaySessionStatus.ACTIVE.name,
                TodaySessionStatus.PAUSED.name,
                TodaySessionStatus.RECOVERED.name,
            ),
        ) != null
    }
}
```

在 `TrackDatabase.kt` 中注册实体、DAO 和 migration：

```kotlin
@Database(
    entities = [
        HistoryRecordEntity::class,
        HistoryPointEntity::class,
        RawLocationPointEntity::class,
        AnalysisSegmentEntity::class,
        StayClusterEntity::class,
        AnalysisCursorEntity::class,
        UploadCursorEntity::class,
        TodayDisplayPointEntity::class,
        SyncOutboxEntity::class,
        TodaySessionEntity::class,
        TodaySessionPointEntity::class,
    ],
    version = 15,
    exportSchema = true,
)
abstract class TrackDatabase : RoomDatabase() {
    abstract fun todaySessionDao(): TodaySessionDao
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `today_session` (
                `sessionId` TEXT NOT NULL,
                `dayStartMillis` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `lastPointAt` INTEGER,
                `endedAt` INTEGER,
                `lastSyncedAt` INTEGER,
                `syncState` TEXT NOT NULL,
                `phase` TEXT NOT NULL,
                `anchorLatitude` REAL,
                `anchorLongitude` REAL,
                `anchorTimestampMillis` INTEGER,
                `latestLatitude` REAL,
                `latestLongitude` REAL,
                `latestAccuracyMeters` REAL,
                `latestAltitudeMeters` REAL,
                `lastAnalysisAt` INTEGER,
                `recoveredFromRemote` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`sessionId`)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `today_session_point` (
                `sessionId` TEXT NOT NULL,
                `pointId` INTEGER NOT NULL,
                `dayStartMillis` INTEGER NOT NULL,
                `timestampMillis` INTEGER NOT NULL,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `accuracyMeters` REAL,
                `altitudeMeters` REAL,
                `speedMetersPerSecond` REAL,
                `provider` TEXT NOT NULL,
                `samplingTier` TEXT NOT NULL,
                `syncState` TEXT NOT NULL,
                PRIMARY KEY(`sessionId`, `pointId`)
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_today_session_dayStartMillis_status` ON `today_session` (`dayStartMillis`, `status`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_today_session_updatedAt` ON `today_session` (`updatedAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_today_session_point_dayStartMillis_timestampMillis` ON `today_session_point` (`dayStartMillis`, `timestampMillis`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_today_session_point_syncState_timestampMillis` ON `today_session_point` (`syncState`, `timestampMillis`)")
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：

```bash
gradle :app:testDebugUnitTest --tests com.wenhao.record.data.tracking.TodaySessionStorageTest --tests com.wenhao.record.data.local.TrackDatabaseMigrationTest
```

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/wenhao/record/data/local/TrackDatabase.kt app/src/main/java/com/wenhao/record/data/local/stream/ContinuousTrackEntities.kt app/src/main/java/com/wenhao/record/data/local/stream/TodaySessionDao.kt app/src/main/java/com/wenhao/record/data/tracking/TodaySessionStorage.kt app/src/test/java/com/wenhao/record/data/tracking/TodaySessionStorageTest.kt app/src/test/java/com/wenhao/record/data/local/TrackDatabaseMigrationTest.kt
git commit -m "feat(记录): 增加 today session 本地模型"
```

---

### 任务 2：把采点链路接入 today session

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TodayTrackDisplayCache.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TrackingRuntimeSnapshotStorage.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt`
- 创建：`app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServiceTodaySessionTest.kt`

- [ ] **步骤 1：编写失败测试，锁定采点后 today session 与展示层同时更新**

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundTrackingServiceTodaySessionTest {

    @Test
    fun `accepted raw point is mirrored into today session storage`() {
        val service = Robolectric.buildService(BackgroundTrackingService::class.java).create().get()
        setField(service, "enabled", true)
        setField(service, "currentPhase", TrackingPhase.ACTIVE)

        invokeHandleLocationUpdate(
            service,
            location(
                provider = LocationManager.GPS_PROVIDER,
                timestampMillis = System.currentTimeMillis(),
                accuracyMeters = 8f,
            ),
        )

        val storage = TodaySessionStorage(
            TrackDatabase.getInstance(service).todaySessionDao(),
        )
        val dayStart = HistoryDayAggregator.startOfDay(System.currentTimeMillis())
        assertTrue(runBlocking { storage.hasOpenSession(dayStart) })
        assertTrue(runBlocking { TodayTrackDisplayCache.loadToday(service).isNotEmpty() })
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.BackgroundTrackingServiceTodaySessionTest
```

预期：FAIL，today session 仍未被 service 写入。

- [ ] **步骤 3：实现 today session 写入、快照恢复与展示 façade**

在 `TodayTrackDisplayCache.kt` 中把数据源改成 `TodaySessionStorage`：

```kotlin
object TodayTrackDisplayCache {
    const val MAX_DISPLAY_POINTS = 2_048

    suspend fun append(
        context: Context,
        sessionId: String,
        pointId: Long,
        rawPoint: RawTrackPoint,
        phase: String,
        nowMillis: Long = rawPoint.timestampMillis,
    ) {
        storage(context).appendPoint(
            sessionId = sessionId,
            pointId = pointId,
            rawPoint = rawPoint,
            phase = phase,
            nowMillis = nowMillis,
        )
    }

    suspend fun loadToday(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<TrackPoint> {
        return storage(context).loadDisplayPoints(nowMillis)
    }

    fun observeToday(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Flow<List<TrackPoint>> {
        return storage(context).observeDisplayPoints(nowMillis)
    }

    private fun storage(context: Context): TodaySessionStorage {
        return TodaySessionStorage(
            TrackDatabase.getInstance(context.applicationContext).todaySessionDao(),
        )
    }
}
```

在 `TrackingRuntimeSnapshotStorage.kt` 中让 `peek()` 可以从 open session 生成快照：

```kotlin
data class TrackingRuntimeSnapshot(
    val isEnabled: Boolean,
    val phase: TrackingPhase,
    val samplingTier: SamplingTier,
    val latestPoint: TrackPoint?,
    val lastAnalysisAt: Long?,
    val sessionId: String? = null,
    val dayStartMillis: Long? = null,
)

fun peek(context: Context): TrackingRuntimeSnapshot {
    warmUp(context)
    val enabled = prefs(context).getBoolean(KEY_ENABLED, false)
    val todaySession = runBlocking {
        TodaySessionStorage(
            TrackDatabase.getInstance(context.applicationContext).todaySessionDao(),
        ).loadOpenSession(System.currentTimeMillis())
    }
    return if (todaySession == null) {
        cached ?: TrackingRuntimeSnapshot(
            isEnabled = enabled,
            phase = TrackingPhase.IDLE,
            samplingTier = SamplingTier.IDLE,
            latestPoint = null,
            lastAnalysisAt = null,
        )
    } else {
        TrackingRuntimeSnapshot(
            isEnabled = enabled,
            phase = runCatching { TrackingPhase.valueOf(todaySession.phase) }.getOrDefault(TrackingPhase.IDLE),
            samplingTier = SamplingTier.ACTIVE,
            latestPoint = todaySession.toLatestTrackPoint(),
            lastAnalysisAt = todaySession.lastAnalysisAt,
            sessionId = todaySession.sessionId,
            dayStartMillis = todaySession.dayStartMillis,
        )
    }.also { cached = it }
}
```

在 `BackgroundTrackingService.kt` 中把 raw point insert 的返回 `pointId` 继续写入 today session：

```kotlin
private lateinit var todaySessionStorage: TodaySessionStorage
private var currentSessionId: String? = null

override fun onCreate() {
    super.onCreate()
    val database = TrackDatabase.getInstance(this)
    pointStorage = ContinuousPointStorage(database.continuousTrackDao())
    todaySessionStorage = TodaySessionStorage(database.todaySessionDao())
}

private suspend fun ensureCurrentSession(nowMillis: Long): String {
    val existing = currentSessionId
    if (existing != null) return existing
    return todaySessionStorage.createOrRestoreOpenSession(nowMillis).sessionId.also {
        currentSessionId = it
    }
}

private fun persistAcceptedPoint(rawPoint: RawTrackPoint) {
    runOnTrackingThread {
        val pointId = pointStorage.appendRawPoint(rawPoint)
        val sessionId = ensureCurrentSession(rawPoint.timestampMillis)
        TodayTrackDisplayCache.append(
            context = applicationContext,
            sessionId = sessionId,
            pointId = pointId,
            rawPoint = rawPoint.copy(pointId = pointId),
            phase = currentPhase.name,
            nowMillis = rawPoint.timestampMillis,
        )
        saveSnapshot()
    }
}
```

在会话停止或完成时更新 today session 状态：

```kotlin
private fun disableTracking() {
    val sessionId = currentSessionId
    if (sessionId != null) {
        runOnTrackingThread {
            todaySessionStorage.markPaused(
                sessionId = sessionId,
                phase = currentPhase.name,
                nowMillis = System.currentTimeMillis(),
            )
        }
    }
    currentSessionId = null
}
```

在 `MainActivity.refreshDashboardContent()` 开头继续清理过期 today session：

```kotlin
private fun refreshDashboardContent() {
    lifecycleScope.launch(Dispatchers.IO) {
        TodayTrackDisplayCache.clearIfExpired(this@MainActivity)
    }
    val runtimeSnapshot = TrackingRuntimeSnapshotStorage.peek(this)
    ...
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：

```bash
gradle :app:testDebugUnitTest --tests com.wenhao.record.tracking.BackgroundTrackingServiceTodaySessionTest --tests com.wenhao.record.tracking.BackgroundTrackingServiceSignalLossTest --tests com.wenhao.record.data.tracking.TodayTrackDisplayCacheTest
```

预期：PASS，且已有 signal-loss / today display 回归不被破坏。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/wenhao/record/data/tracking/TodayTrackDisplayCache.kt app/src/main/java/com/wenhao/record/data/tracking/TrackingRuntimeSnapshotStorage.kt app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt app/src/test/java/com/wenhao/record/tracking/BackgroundTrackingServiceTodaySessionTest.kt
git commit -m "feat(记录): 采点链路接入 today session"
```

---

### 任务 3：整理本地历史查询结构

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/data/history/LocalHistoryRepository.kt`
- 创建：`app/src/test/java/com/wenhao/record/data/history/LocalHistoryRepositoryTest.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/local/history/HistoryEntities.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/local/history/HistoryDao.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/history/HistoryViewModel.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/map/MapActivity.kt`

- [ ] **步骤 1：编写失败测试，锁定摘要查询与详情查询分层**

```kotlin
class LocalHistoryRepositoryTest {

    @Test
    fun `loadDailySummaries only needs history records`() = runBlocking {
        val dao = FakeHistoryDao(
            records = listOf(
                HistoryRecordEntity(
                    historyId = 7L,
                    sourceSessionId = "session_1",
                    dateKey = 1_714_220_800_000L,
                    timestamp = 1_714_223_400_000L,
                    distanceKm = 3.2,
                    durationSeconds = 900,
                    averageSpeedKmh = 12.8,
                    title = "下班回家",
                    syncState = "SYNCED",
                    version = 1L,
                    startSource = "AUTO",
                    stopSource = "AUTO",
                    manualStartAt = null,
                    manualStopAt = null,
                )
            ),
            points = emptyMap(),
        )
        val repository = LocalHistoryRepository(dao)

        val items = repository.loadDailySummaries()

        assertEquals(1, items.size)
        assertEquals("下班回家", items.single().routeTitle)
    }

    @Test
    fun `loadDayDetail fetches points lazily for selected day`() = runBlocking {
        val dayStart = 1_714_220_800_000L
        val dao = FakeHistoryDao(
            records = listOf(
                HistoryRecordEntity(
                    historyId = 8L,
                    sourceSessionId = "session_2",
                    dateKey = dayStart,
                    timestamp = dayStart + 6_000L,
                    distanceKm = 1.1,
                    durationSeconds = 300,
                    averageSpeedKmh = 13.2,
                    title = "夜跑",
                    syncState = "PENDING",
                    version = 2L,
                    startSource = "AUTO",
                    stopSource = "AUTO",
                    manualStartAt = null,
                    manualStopAt = null,
                )
            ),
            points = mapOf(
                8L to listOf(
                    HistoryPointEntity(8L, 0, 30.0, 120.0, dayStart + 1_000L, 8f, 10.0),
                    HistoryPointEntity(8L, 1, 30.1, 120.1, dayStart + 6_000L, 8f, 10.0),
                )
            ),
        )
        val repository = LocalHistoryRepository(dao)

        val item = repository.loadDayDetail(dayStart)

        assertEquals(1, item?.segments?.size)
        assertEquals(2, item?.pointCount)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
gradle :app:testDebugUnitTest --tests com.wenhao.record.data.history.LocalHistoryRepositoryTest
```

预期：FAIL，`LocalHistoryRepository` 和新字段 / 新 DAO 查询不存在。

- [ ] **步骤 3：实现本地摘要 / 详情仓库，并把页面改成本地优先**

在 `HistoryEntities.kt` 中补字段：

```kotlin
@Entity(tableName = "history_record")
data class HistoryRecordEntity(
    @PrimaryKey
    val historyId: Long,
    val sourceSessionId: String?,
    val dateKey: Long,
    val timestamp: Long,
    val distanceKm: Double,
    val durationSeconds: Int,
    val averageSpeedKmh: Double,
    val title: String?,
    val syncState: String,
    val version: Long,
    val startSource: String,
    val stopSource: String,
    val manualStartAt: Long?,
    val manualStopAt: Long?,
)
```

在 `HistoryDao.kt` 中新增分层查询：

```kotlin
@Query(
    """
    SELECT * FROM history_record
    ORDER BY timestamp DESC, historyId DESC
    """
)
suspend fun getHistoryRecords(): List<HistoryRecordEntity>

@Query(
    """
    SELECT * FROM history_record
    WHERE dateKey = :dayStartMillis
    ORDER BY timestamp ASC, historyId ASC
    """
)
suspend fun getHistoryRecordsByDay(dayStartMillis: Long): List<HistoryRecordEntity>

@Query(
    """
    SELECT * FROM history_point
    WHERE historyId = :historyId
    ORDER BY pointOrder ASC
    """
)
suspend fun getHistoryPoints(historyId: Long): List<HistoryPointEntity>
```

创建 `LocalHistoryRepository.kt`：

```kotlin
class LocalHistoryRepository(
    private val dao: HistoryDao,
) {
    suspend fun loadDailySummaries(): List<HistoryDaySummaryItem> {
        val items = dao.getHistoryRecords().map { record ->
            HistoryItem(
                id = record.historyId,
                timestamp = record.timestamp,
                distanceKm = record.distanceKm,
                durationSeconds = record.durationSeconds,
                averageSpeedKmh = record.averageSpeedKmh,
                title = record.title,
                points = emptyList(),
                startSource = TrackRecordSource.fromStorage(record.startSource),
                stopSource = TrackRecordSource.fromStorage(record.stopSource),
                manualStartAt = record.manualStartAt,
                manualStopAt = record.manualStopAt,
            )
        }
        return HistoryDayAggregator.aggregate(items).map { it.toSummaryItem() }
    }

    suspend fun loadDayDetail(dayStartMillis: Long): HistoryDayItem? {
        val records = dao.getHistoryRecordsByDay(dayStartMillis)
        if (records.isEmpty()) return null
        val items = records.map { record ->
            HistoryItem(
                id = record.historyId,
                timestamp = record.timestamp,
                distanceKm = record.distanceKm,
                durationSeconds = record.durationSeconds,
                averageSpeedKmh = record.averageSpeedKmh,
                title = record.title,
                points = dao.getHistoryPoints(record.historyId).map { point ->
                    TrackPoint(
                        latitude = point.latitude,
                        longitude = point.longitude,
                        timestampMillis = point.timestampMillis,
                        accuracyMeters = point.accuracyMeters,
                        altitudeMeters = point.altitudeMeters,
                        wgs84Latitude = point.wgs84Latitude,
                        wgs84Longitude = point.wgs84Longitude,
                    )
                },
                startSource = TrackRecordSource.fromStorage(record.startSource),
                stopSource = TrackRecordSource.fromStorage(record.stopSource),
                manualStartAt = record.manualStartAt,
                manualStopAt = record.manualStopAt,
            )
        }
        return HistoryDayAggregator.aggregate(items).firstOrNull()
    }
}
```

在 `HistoryViewModel.kt` 中改成本地仓库：

```kotlin
private val localHistoryRepository: LocalHistoryRepository = LocalHistoryRepository(
    TrackDatabase.getInstance(application).historyDao(),
)

fun reload() {
    val generation = nextReloadGeneration()
    viewModelScope.launch(Dispatchers.IO) {
        val localItems = localHistoryRepository.loadDailySummaries()
        withContext(Dispatchers.Main) {
            if (generation != reloadGeneration) return@withContext
            historyItems = localItems
            recalculateTotals()
            updateContent()
        }
    }
}
```

在 `MapActivity.kt` 中改成本地详情读取：

```kotlin
private val localHistoryRepository by lazy {
    LocalHistoryRepository(TrackDatabase.getInstance(applicationContext).historyDao())
}

private fun renderHistory() {
    val dayStartMillis = intent.getLongExtra(EXTRA_DAY_START, -1L).takeIf { it > 0L } ?: return
    AppTaskExecutor.runOnIo {
        val item = runBlocking { localHistoryRepository.loadDayDetail(dayStartMillis) }
        AppTaskExecutor.runOnMain {
            if (item == null || item.segments.none { segment -> segment.isNotEmpty() }) {
                Toast.makeText(this, R.string.dashboard_history_no_route, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                renderHistoryItem(item)
            }
        }
    }
}
```

在 `HistoryStorage.kt` 中继续保留 cache，但内部 `loadDaily` / `loadDailyByStart` 改为委托 `LocalHistoryRepository`，并在 `add()` / `save()` 时写入 `sourceSessionId`、`dateKey`、`syncState`、`version`。

- [ ] **步骤 4：运行测试验证通过**

运行：

```bash
gradle :app:testDebugUnitTest --tests com.wenhao.record.data.history.LocalHistoryRepositoryTest --tests com.wenhao.record.data.history.HistoryProjectionRecoveryTest --tests com.wenhao.record.data.history.RemoteHistoryRepositoryTest
```

预期：PASS，且旧的远端历史聚合测试没有被误删。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/wenhao/record/data/local/history/HistoryEntities.kt app/src/main/java/com/wenhao/record/data/local/history/HistoryDao.kt app/src/main/java/com/wenhao/record/data/history/LocalHistoryRepository.kt app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt app/src/main/java/com/wenhao/record/ui/history/HistoryViewModel.kt app/src/main/java/com/wenhao/record/ui/map/MapActivity.kt app/src/test/java/com/wenhao/record/data/history/LocalHistoryRepositoryTest.kt
git commit -m "refactor(历史): 拆分本地摘要与详情查询"
```

---

### 任务 4：接入 today session 的 D1 mirror 上传链路

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/data/tracking/TodaySessionUploadService.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/tracking/TodaySessionPointUploadService.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/tracking/TodaySessionSyncCoordinator.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/tracking/TodaySessionSyncWorker.kt`
- 创建：`app/src/main/java/com/wenhao/record/data/tracking/TodaySessionRemoteReadService.kt`
- 创建：`app/src/test/java/com/wenhao/record/data/tracking/TodaySessionSyncWorkerTest.kt`
- 创建：`worker/src/today-session-index.test.ts`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TrackUploadScheduler.kt`
- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt`
- 修改：`worker/src/schema.sql`
- 修改：`worker/src/types.ts`
- 修改：`worker/src/validation.ts`
- 修改：`worker/src/d1.ts`
- 修改：`worker/src/index.ts`
- 修改：`worker/src/d1.test.ts`

- [ ] **步骤 1：先写失败测试，锁定上传顺序与 worker 路由**

Android 端 worker 测试：

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class TodaySessionSyncWorkerTest {

    @Test
    fun `doWork uploads session meta before session points and marks them synced`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = TrackDatabase.getInstance(context)
        val storage = TodaySessionStorage(database.todaySessionDao())
        val session = storage.createOrRestoreOpenSession(nowMillis = 1_714_300_000_000L)
        storage.appendPoint(
            sessionId = session.sessionId,
            pointId = 18L,
            rawPoint = rawPoint(timestampMillis = 1_714_300_010_000L),
            phase = "ACTIVE",
            nowMillis = 1_714_300_010_000L,
        )

        val callOrder = mutableListOf<String>()
        val worker = buildWorker(
            context = context,
            sessionUploader = { _, _, _ -> callOrder += "session"; UploadHttpResponse(200, "{\"ok\":true,\"message\":\"ok\"}") },
            pointUploader = { _, _, _ -> callOrder += "points"; UploadHttpResponse(200, "{\"ok\":true,\"message\":\"ok\"}") },
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(listOf("session", "points"), callOrder)
    }
}
```

Worker 端路由测试：

```ts
import { describe, expect, it } from "vitest";
import { createApp } from "./index";

describe("today session routes", () => {
  it("accepts today session batch uploads", async () => {
    const app = createApp({
      todaySessionPersistence: {
        persistSessions: async () => ({ insertedCount: 1, dedupedCount: 0 }),
        persistSessionPoints: async () => ({ insertedCount: 0, dedupedCount: 0 }),
        readLatestOpenSession: async () => null,
      }
    });

    const response = await app.fetch(
      new Request("https://worker.example.com/today-sessions/batch", {
        method: "POST",
        headers: {
          Authorization: "Bearer token",
          "content-type": "application/json"
        },
        body: JSON.stringify({
          deviceId: "device-1",
          appVersion: "1.0.0",
          sessions: [
            {
              sessionId: "session_1",
              dayStartMillis: 1714300800000,
              status: "ACTIVE",
              startedAt: 1714300900000,
              lastPointAt: 1714300910000,
              endedAt: null,
              phase: "ACTIVE",
              updatedAt: 1714300910000
            }
          ]
        })
      }),
      { UPLOAD_TOKEN: "token", MAPBOX_PUBLIC_TOKEN: "pk", DB: {} as D1Database }
    );

    expect(response.status).toBe(200);
  });
});
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
gradle :app:testDebugUnitTest --tests com.wenhao.record.data.tracking.TodaySessionSyncWorkerTest
npm --prefix worker test -- today-session-index d1
```

预期：FAIL，today session 上传类型、worker route、D1 persistence 都不存在。

- [ ] **步骤 3：实现 today session 上传服务、节流器、worker 路由和 D1 表**

在 `worker/src/schema.sql` 中增加表：

```sql
CREATE TABLE IF NOT EXISTS today_session (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  session_id TEXT NOT NULL,
  day_start_millis INTEGER NOT NULL,
  status TEXT NOT NULL,
  started_at INTEGER NOT NULL,
  last_point_at INTEGER,
  ended_at INTEGER,
  phase TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_today_session_device_session
  ON today_session (device_id, session_id);

CREATE INDEX IF NOT EXISTS idx_today_session_device_day_status
  ON today_session (device_id, day_start_millis, status);

CREATE TABLE IF NOT EXISTS today_session_point (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  session_id TEXT NOT NULL,
  point_id INTEGER NOT NULL,
  day_start_millis INTEGER NOT NULL,
  timestamp_millis INTEGER NOT NULL,
  latitude REAL NOT NULL,
  longitude REAL NOT NULL,
  accuracy_meters REAL,
  altitude_meters REAL,
  speed_meters_per_second REAL,
  provider TEXT NOT NULL,
  sampling_tier TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_today_session_point_device_session_point
  ON today_session_point (device_id, session_id, point_id);

CREATE INDEX IF NOT EXISTS idx_today_session_point_device_session_time
  ON today_session_point (device_id, session_id, timestamp_millis);
```

在 `worker/src/types.ts` 中增加 today session 类型：

```ts
export interface TodaySessionRecord {
  sessionId: string;
  dayStartMillis: number;
  status: string;
  startedAt: number;
  lastPointAt: number | null;
  endedAt: number | null;
  phase: string;
  updatedAt: number;
}

export interface TodaySessionPoint {
  sessionId: string;
  pointId: number;
  dayStartMillis: number;
  timestampMillis: number;
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
  altitudeMeters: number | null;
  speedMetersPerSecond: number | null;
  provider: string;
  samplingTier: string;
  updatedAt: number;
}
```

在 `worker/src/d1.ts` 中增加 persistence：

```ts
export interface TodaySessionPersistence {
  persistSessions(
    deviceId: string,
    appVersion: string,
    sessions: TodaySessionRecord[],
    env: Env
  ): Promise<{ insertedCount: number; dedupedCount: number }>;

  persistSessionPoints(
    deviceId: string,
    appVersion: string,
    points: TodaySessionPoint[],
    env: Env
  ): Promise<{ insertedCount: number; dedupedCount: number }>;

  readLatestOpenSession(
    deviceId: string,
    env: Env
  ): Promise<{ session: TodaySessionRecord; points: TodaySessionPoint[] } | null>;
}
```

在 `worker/src/index.ts` 中注册路由：

```ts
if (
  url.pathname !== "/today-sessions/batch" &&
  url.pathname !== "/today-session-points/batch" &&
  url.pathname !== "/today-sessions/open" &&
  ...
) {
  return jsonResponse(404, { ok: false, message: "Not found" });
}

if (url.pathname === "/today-sessions/batch") {
  if (request.method !== "POST") {
    return jsonResponse(405, { ok: false, message: "Method not allowed" });
  }
  const payload = validateTodaySessionBatchRequest(await request.json());
  const result = await todaySessionPersistence.persistSessions(
    payload.deviceId,
    payload.appVersion,
    payload.sessions,
    env,
  );
  return jsonResponse(200, { ok: true, ...result });
}

if (url.pathname === "/today-session-points/batch") {
  if (request.method !== "POST") {
    return jsonResponse(405, { ok: false, message: "Method not allowed" });
  }
  const payload = validateTodaySessionPointBatchRequest(await request.json());
  const result = await todaySessionPersistence.persistSessionPoints(
    payload.deviceId,
    payload.appVersion,
    payload.points,
    env,
  );
  return jsonResponse(200, { ok: true, ...result });
}

if (url.pathname === "/today-sessions/open") {
  if (request.method !== "GET") {
    return jsonResponse(405, { ok: false, message: "Method not allowed" });
  }
  const deviceId = parseRequiredQueryString(url.searchParams.get("deviceId"), "`deviceId`");
  const result = await todaySessionPersistence.readLatestOpenSession(deviceId, env);
  return jsonResponse(200, { ok: true, session: result?.session ?? null, points: result?.points ?? [] });
}
```

Android 端上传 worker：

```kotlin
class TodaySessionSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val sessionStorage: TodaySessionStorage = TodaySessionStorage(
        TrackDatabase.getInstance(appContext).todaySessionDao(),
    ),
    private val sessionUploadService: TodaySessionUploadService = TodaySessionUploadService(),
    private val pointUploadService: TodaySessionPointUploadService = TodaySessionPointUploadService(),
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val config = configLoader(applicationContext)
        if (!config.isConfigured()) return Result.success()

        val session = sessionStorage.loadOpenSession(System.currentTimeMillis()) ?: return Result.success()
        sessionUploadService.upload(config, BuildConfig.VERSION_NAME, deviceIdProvider(applicationContext), session)
        val pendingPoints = sessionStorage.loadPendingPoints(session.sessionId, limit = 200)
        if (pendingPoints.isNotEmpty()) {
            pointUploadService.upload(
                config,
                BuildConfig.VERSION_NAME,
                deviceIdProvider(applicationContext),
                pendingPoints,
            )
            sessionStorage.markPointsSynced(session.sessionId, pendingPoints.map { it.pointId }, System.currentTimeMillis())
        }
        sessionStorage.markSessionSynced(session.sessionId, System.currentTimeMillis())
        return Result.success()
    }
}
```

调度器增加 one-shot work：

```kotlin
private const val TODAY_SESSION_SYNC_ONE_TIME_WORK = "today-session-sync-once"

fun kickTodaySessionSync(context: Context) {
    workManager(context.applicationContext).enqueueUniqueWork(
        TODAY_SESSION_SYNC_ONE_TIME_WORK,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<TodaySessionSyncWorker>().build(),
    )
}
```

协调器根据 30 秒 / 20 点 / 相位切换触发：

```kotlin
class TodaySessionSyncCoordinator(
    private val prefs: SharedPreferences,
) {
    fun shouldSync(nowMillis: Long, pendingPointCount: Int, force: Boolean): Boolean {
        if (force) return true
        val lastSyncedAt = prefs.getLong("last_today_session_sync_at", 0L)
        return pendingPointCount >= 20 || nowMillis - lastSyncedAt >= 30_000L
    }

    fun markTriggered(nowMillis: Long) {
        prefs.edit { putLong("last_today_session_sync_at", nowMillis) }
    }
}
```

并在 `BackgroundTrackingService` 的采点、相位切换、后台切换处调用：

```kotlin
if (todaySessionSyncCoordinator.shouldSync(nowMillis, pendingPointCount, force = false)) {
    TrackUploadScheduler.kickTodaySessionSync(applicationContext)
    todaySessionSyncCoordinator.markTriggered(nowMillis)
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：

```bash
gradle :app:testDebugUnitTest --tests com.wenhao.record.data.tracking.TodaySessionSyncWorkerTest --tests com.wenhao.record.data.tracking.TrackUploadSchedulerTest
npm --prefix worker test -- today-session-index d1
```

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/wenhao/record/data/tracking/TodaySessionUploadService.kt app/src/main/java/com/wenhao/record/data/tracking/TodaySessionPointUploadService.kt app/src/main/java/com/wenhao/record/data/tracking/TodaySessionSyncCoordinator.kt app/src/main/java/com/wenhao/record/data/tracking/TodaySessionSyncWorker.kt app/src/main/java/com/wenhao/record/data/tracking/TodaySessionRemoteReadService.kt app/src/main/java/com/wenhao/record/data/tracking/TrackUploadScheduler.kt app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt app/src/test/java/com/wenhao/record/data/tracking/TodaySessionSyncWorkerTest.kt worker/src/schema.sql worker/src/types.ts worker/src/validation.ts worker/src/d1.ts worker/src/index.ts worker/src/d1.test.ts worker/src/today-session-index.test.ts
git commit -m "feat(同步): 增加 today session D1 mirror 链路"
```

---

### 任务 5：实现启动恢复链路

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/data/history/TrackMirrorRecoveryWorker.kt`
- 创建：`app/src/test/java/com/wenhao/record/data/history/TrackMirrorRecoveryWorkerTest.kt`
- 修改：`app/src/main/java/com/wenhao/record/RecordApplication.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TodaySessionStorage.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TrackUploadScheduler.kt`

- [ ] **步骤 1：编写失败测试，锁定“先恢复历史，再恢复未完成 today session”**

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class TrackMirrorRecoveryWorkerTest {

    @Test
    fun `doWork restores remote histories before open today session`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val restoreOrder = mutableListOf<String>()
        val worker = buildWorker(
            context = context,
            historyLoader = { _, _ ->
                restoreOrder += "history"
                RemoteHistoryReadResult.Success(listOf(remoteHistory(8L)))
            },
            sessionLoader = { _, _ ->
                restoreOrder += "today"
                RemoteTodaySessionReadResult.Success(remoteSession())
            },
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(listOf("history", "today"), restoreOrder)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
gradle :app:testDebugUnitTest --tests com.wenhao.record.data.history.TrackMirrorRecoveryWorkerTest
```

预期：FAIL，恢复 worker 不存在。

- [ ] **步骤 3：实现恢复 worker，并在应用启动时调度**

创建 `TrackMirrorRecoveryWorker.kt`：

```kotlin
class TrackMirrorRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val historyLoader: (TrainingSampleUploadConfig, String) -> RemoteHistoryReadResult = { config, deviceId ->
        RemoteHistoryReadService().loadAll(config, deviceId)
    },
    private val sessionLoader: (TrainingSampleUploadConfig, String) -> RemoteTodaySessionReadResult = { config, deviceId ->
        TodaySessionRemoteReadService().loadLatestOpenSession(config, deviceId)
    },
    private val configLoader: (Context) -> TrainingSampleUploadConfig = TrainingSampleUploadConfigStorage::load,
    private val deviceIdProvider: (Context) -> String = ::uploadDeviceId,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val config = configLoader(applicationContext)
        if (!config.isConfigured()) return Result.success()
        val deviceId = deviceIdProvider(applicationContext)

        when (val historyResult = historyLoader(config, deviceId)) {
            is RemoteHistoryReadResult.Success -> {
                if (historyResult.histories.isNotEmpty()) {
                    HistoryStorage.upsertRecoveredItems(applicationContext, historyResult.histories)
                }
            }
            is RemoteHistoryReadResult.Failure -> return Result.retry()
        }

        when (val sessionResult = sessionLoader(config, deviceId)) {
            is RemoteTodaySessionReadResult.Success -> {
                sessionResult.session?.let { payload ->
                    TodaySessionStorage(
                        TrackDatabase.getInstance(applicationContext).todaySessionDao(),
                    ).replaceWithRemoteSession(payload.session, payload.points)
                }
            }
            is RemoteTodaySessionReadResult.Failure -> return Result.retry()
        }

        TrackDataChangeNotifier.notifyDashboardChanged()
        TrackDataChangeNotifier.notifyHistoryChanged()
        return Result.success()
    }
}
```

在 `HistoryStorage.kt` 中增加恢复写回入口：

```kotlin
suspend fun upsertRecoveredItems(context: Context, items: List<HistoryItem>) {
    upsertProjectedItems(context, items)
}
```

在 `TodaySessionStorage.kt` 中增加远端恢复入口：

```kotlin
suspend fun replaceWithRemoteSession(
    session: RemoteTodaySession,
    points: List<RemoteTodaySessionPoint>,
) {
    dao.upsertSession(session.toEntity(recoveredFromRemote = true))
    dao.deletePointsForSession(session.sessionId)
    points.forEach { point ->
        dao.upsertPoint(point.toEntity(syncState = TodaySessionSyncState.SYNCED.name))
    }
}
```

在 `TrackUploadScheduler.kt` 中增加恢复调度：

```kotlin
private const val MIRROR_RECOVERY_ONE_TIME_WORK = "track-mirror-recovery-once"

fun kickMirrorRecovery(context: Context) {
    workManager(context.applicationContext).enqueueUniqueWork(
        MIRROR_RECOVERY_ONE_TIME_WORK,
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<TrackMirrorRecoveryWorker>().build(),
    )
}
```

在 `RecordApplication.kt` 中启动时调用：

```kotlin
override fun onCreate() {
    super.onCreate()
    CrashLogStore.install(this)
    TrackingRuntimeSnapshotStorage.warmUp(this)
    HistoryStorage.warmUp(this)
    TrackUploadScheduler.ensureScheduled(this)
    TrackUploadScheduler.kickMirrorRecovery(this)
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：

```bash
gradle :app:testDebugUnitTest --tests com.wenhao.record.data.history.TrackMirrorRecoveryWorkerTest --tests com.wenhao.record.tracking.BootCompletedReceiverTest --tests com.wenhao.record.data.history.RemoteHistoryRepositoryTest
```

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/wenhao/record/data/history/TrackMirrorRecoveryWorker.kt app/src/main/java/com/wenhao/record/RecordApplication.kt app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt app/src/main/java/com/wenhao/record/data/tracking/TodaySessionStorage.kt app/src/main/java/com/wenhao/record/data/tracking/TrackUploadScheduler.kt app/src/test/java/com/wenhao/record/data/history/TrackMirrorRecoveryWorkerTest.kt
git commit -m "feat(恢复): 启动时恢复历史与未完成会话"
```

---

### 任务 6：补齐完成态清理与最终验证

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/TodaySessionStorage.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/history/HistoryRetentionPolicy.kt`
- 修改：`app/src/main/java/com/wenhao/record/data/tracking/RawPointRetentionPolicy.kt`
- 修改：`app/src/test/java/com/wenhao/record/data/tracking/TodaySessionStorageTest.kt`
- 修改：`app/src/test/java/com/wenhao/record/data/history/HistoryRetentionPolicyTest.kt`
- 修改：`app/src/test/java/com/wenhao/record/data/local/TrackDatabaseMigrationTest.kt`

- [ ] **步骤 1：编写失败测试，锁定“历史完成后 today session 点可清，历史数据保留，raw point 只按策略清理”**

```kotlin
@Test
fun `clearCompletedSessionPoints removes only completed today session points`() = runBlocking {
    val dao = FakeTodaySessionDao()
    val storage = TodaySessionStorage(dao)
    val session = storage.createOrRestoreOpenSession(nowMillis = 1_714_300_000_000L)
    storage.appendPoint(session.sessionId, 18L, rawPoint(1_714_300_001_000L), "ACTIVE", 1_714_300_001_000L)
    storage.markCompleted(session.sessionId, 1_714_300_010_000L, 1_714_300_010_000L)

    storage.clearCompletedSessionPointsBefore(dayStartMillis = session.dayStartMillis + 1)

    assertTrue(dao.points.isEmpty())
    assertEquals(TodaySessionStatus.COMPLETED.name, dao.sessions.single().status)
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
gradle :app:testDebugUnitTest --tests com.wenhao.record.data.tracking.TodaySessionStorageTest --tests com.wenhao.record.data.history.HistoryRetentionPolicyTest
```

预期：FAIL，today session 清理策略尚未实现。

- [ ] **步骤 3：实现完成态清理逻辑**

在 `BackgroundTrackingService.kt` 中，当本地 history 成功写入后清理 today session 点并触发现有 history 上传：

```kotlin
private suspend fun persistProjectedHistoryAndFinalizeSession(
    sessionId: String,
    historyItems: List<HistoryItem>,
    nowMillis: Long,
) {
    if (historyItems.isNotEmpty()) {
        HistoryStorage.upsertProjectedItems(applicationContext, historyItems)
        todaySessionStorage.markCompleted(sessionId, endedAt = nowMillis, nowMillis = nowMillis)
        todaySessionStorage.clearCompletedSessionPointsBefore(
            dayStartMillis = HistoryDayAggregator.startOfDay(nowMillis),
        )
        TrackUploadScheduler.kickLocalResultSyncPipeline(applicationContext)
    }
}
```

在 `TodaySessionStorage.kt` 中增加清理方法：

```kotlin
suspend fun clearCompletedSessionPointsBefore(dayStartMillis: Long) {
    dao.deleteCompletedSessionPointsBefore(dayStartMillis, TodaySessionStatus.COMPLETED.name)
}
```

在 `HistoryRetentionPolicy.kt` 中只清理已远端镜像完成的本地上传标记，不删除 `history_record` / `history_point`；在 `RawPointRetentionPolicy.kt` 中继续保留“同步成功 + 历史整理成功后再删 raw point”的规则，不让 today session 清理误删 raw 数据。

- [ ] **步骤 4：运行完整验证**

运行：

```bash
gradle :app:testDebugUnitTest --tests com.wenhao.record.data.local.TrackDatabaseMigrationTest --tests com.wenhao.record.data.tracking.TodaySessionStorageTest --tests com.wenhao.record.data.tracking.TodaySessionSyncWorkerTest --tests com.wenhao.record.data.history.LocalHistoryRepositoryTest --tests com.wenhao.record.data.history.TrackMirrorRecoveryWorkerTest --tests com.wenhao.record.tracking.BackgroundTrackingServiceSignalLossTest --tests com.wenhao.record.tracking.BackgroundTrackingServiceTodaySessionTest
```

```bash
gradle :app:assembleDebug
```

```bash
npm --prefix worker test
```

预期：全部 PASS；`assembleDebug` 成功；worker 全量 Vitest 通过。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt app/src/main/java/com/wenhao/record/data/tracking/TodaySessionStorage.kt app/src/main/java/com/wenhao/record/data/history/HistoryStorage.kt app/src/main/java/com/wenhao/record/data/history/HistoryRetentionPolicy.kt app/src/main/java/com/wenhao/record/data/tracking/RawPointRetentionPolicy.kt app/src/test/java/com/wenhao/record/data/tracking/TodaySessionStorageTest.kt app/src/test/java/com/wenhao/record/data/history/HistoryRetentionPolicyTest.kt app/src/test/java/com/wenhao/record/data/local/TrackDatabaseMigrationTest.kt
git commit -m "feat(记录): 完成 today history D1 容灾闭环"
```

---

## 自检

### 规格覆盖度

- **Today Session Layer：** 任务 1、2、4 覆盖本地模型、展示读取、近实时同步。
- **Local History Layer：** 任务 3 覆盖本地 summary / detail 分层、历史页与详情页切换到本地查询。
- **D1 Mirror Layer：** 任务 4 覆盖 today session 远端镜像；任务 5 复用现有 `processed_histories` / `history-days` 做历史恢复读取。
- **恢复链路：** 任务 5 覆盖历史恢复和 today session 恢复。
- **清理与保留策略：** 任务 6 覆盖 today session 点清理、raw point 延后清理、history 长期保留。

### 占位符扫描

- 已移除 `TODO`、`后续实现`、`适当处理` 之类占位符。
- 每个任务都包含明确文件、测试、命令和主要代码块。

### 类型一致性

- today session 统一使用 `TodaySessionEntity` / `TodaySessionPointEntity` / `TodaySessionStatus` / `TodaySessionSyncState`。
- 本地历史统一使用 `sourceSessionId`、`dateKey`、`syncState`、`version`。
- 远端 today session 统一使用 `TodaySessionRecord` / `TodaySessionPoint`。

---

计划已完成并保存到 `docs/superpowers/plans/2026-04-26-today-history-d1-implementation.md`。两种执行方式：

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

**选哪种方式？**
