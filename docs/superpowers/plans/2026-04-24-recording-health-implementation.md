# 记录健康状态卡实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在记录页落地一张常驻的「记录健康状态卡」，聚合权限、系统限制与后台服务状态，并提供单按钮修复与轻量诊断入口。

**架构：** 采用「纯状态聚合 + Activity 动作分发 + Compose 独立卡片组件」方案。`MainActivity` 负责读取权限、快照与诊断信息，`RecordingHealthUiState` 负责将底层状态归并为可渲染的页面模型，`RecordingHealthCard` 负责展示状态项、主动作与诊断弹层。

**技术栈：** Kotlin, Jetpack Compose, Android Activity Result API, Robolectric, Material 3.

---

## 文件拆分与职责

- `app/src/main/java/com/wenhao/record/ui/main/RecordingHealthUiState.kt`
  - 新增记录健康状态聚合模型。
  - 定义总体状态、状态项、主动作与诊断摘要的数据结构。
  - 提供纯函数 `buildRecordingHealthUiState(inputs)`，便于单元测试。

- `app/src/test/java/com/wenhao/record/ui/main/RecordingHealthUiStateTest.kt`
  - 为状态聚合逻辑补充单元测试。
  - 覆盖阻塞、降级、可稳定记录、修复优先级与主动作切换场景。

- `app/src/main/java/com/wenhao/record/permissions/PermissionHelper.kt`
  - 暴露单项修复动作：定位、活动识别、通知权限、后台定位设置、电池优化设置。
  - 保留现有 `ensureSmartTrackingEnabled()`，但新增更细粒度 API 供状态卡使用。

- `app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt`
  - 读取权限、快照、诊断与电池优化状态。
  - 构建 `RecordingHealthUiState` 并传给 Compose。
  - 承接卡片点击动作与诊断弹层开关。

- `app/src/main/java/com/wenhao/record/ui/main/RecordingHealthCard.kt`
  - 新增状态卡与诊断弹层组件。
  - 负责渲染总体状态、6 个状态项、主动作按钮和诊断内容。

- `app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt`
  - 把状态卡挂到记录页底部信息层中。
  - 保持地图、底部导航与定位按钮结构不变。

- `app/src/main/java/com/wenhao/record/ui/main/AboutComposeScreen.kt`
  - 在设置页顶部增加「高级配置，仅在需要时调整」说明。

- `app/src/main/res/values/strings_compose_dashboard_ui.xml`
  - 新增状态卡标题、状态文案、状态项标签等文案。

- `app/src/main/res/values/strings_compose_dashboard_dialog.xml`
  - 新增诊断弹层标题、主动作和辅助说明文案。

- `app/src/main/res/values/strings.xml`
  - 补充通用修复提示、后台定位与电池优化风险说明文案。

---

### 任务 1：实现记录健康状态聚合模型

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/ui/main/RecordingHealthUiState.kt`
- 测试：`app/src/test/java/com/wenhao/record/ui/main/RecordingHealthUiStateTest.kt`

- [ ] **步骤 1：先写失败的状态聚合测试**

```kotlin
package com.wenhao.record.ui.main

import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingHealthUiStateTest {

    @Test
    fun `missing location permission yields blocked status and repair action`() {
        val state = buildRecordingHealthUiState(
            RecordingHealthInputs(
                hasLocationPermission = false,
                hasActivityRecognitionPermission = true,
                hasBackgroundLocationPermission = true,
                hasNotificationPermission = true,
                ignoresBatteryOptimizations = true,
                trackingEnabled = false,
                trackingServiceRunning = false,
                diagnosticsStatus = "后台待命中",
                diagnosticsEvent = "后台采点已停止",
                latestPointTimestampMillis = null,
            )
        )

        assertEquals(RecordingHealthOverallStatus.BLOCKED, state.overallStatus)
        assertEquals(RecordingHealthAction.REQUEST_LOCATION_PERMISSION, state.primaryAction)
        assertEquals("未授权", state.items.first { it.key == RecordingHealthItemKey.LOCATION }.statusText)
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.ui.main.RecordingHealthUiStateTest"`

预期：FAIL，报错 `Unresolved reference: buildRecordingHealthUiState`

- [ ] **步骤 3：实现状态模型与纯函数聚合逻辑**

```kotlin
package com.wenhao.record.ui.main

enum class RecordingHealthOverallStatus { READY, DEGRADED, BLOCKED }
enum class RecordingHealthItemSeverity { NORMAL, WARNING, ERROR }
enum class RecordingHealthItemKey {
    LOCATION,
    BACKGROUND_LOCATION,
    ACTIVITY_RECOGNITION,
    NOTIFICATION,
    BATTERY_OPTIMIZATION,
    TRACKING_SERVICE,
}

enum class RecordingHealthAction {
    REQUEST_LOCATION_PERMISSION,
    REQUEST_ACTIVITY_RECOGNITION_PERMISSION,
    OPEN_APP_SETTINGS_FOR_BACKGROUND_LOCATION,
    REQUEST_NOTIFICATION_PERMISSION,
    OPEN_BATTERY_OPTIMIZATION_SETTINGS,
    START_BACKGROUND_TRACKING,
    SHOW_DIAGNOSTICS,
    NO_OP,
}

data class RecordingHealthItemUiState(
    val key: RecordingHealthItemKey,
    val title: String,
    val statusText: String,
    val riskText: String? = null,
    val severity: RecordingHealthItemSeverity,
    val action: RecordingHealthAction,
)

data class RecordingHealthUiState(
    val overallStatus: RecordingHealthOverallStatus,
    val title: String,
    val summaryText: String,
    val items: List<RecordingHealthItemUiState>,
    val primaryAction: RecordingHealthAction,
    val primaryActionText: String,
    val diagnosticSummary: RecordingHealthDiagnosticSummary,
) {
    companion object {
        val EMPTY = RecordingHealthUiState(
            overallStatus = RecordingHealthOverallStatus.BLOCKED,
            title = "记录状态",
            summaryText = "请先完成必要授权后再开始记录",
            items = emptyList(),
            primaryAction = RecordingHealthAction.NO_OP,
            primaryActionText = "去修复",
            diagnosticSummary = RecordingHealthDiagnosticSummary(
                phaseText = "待命",
                latestPointText = "暂无定位点",
                latestEventText = "正在整理诊断状态…",
                serviceText = "后台待命中",
            ),
        )
    }
}

data class RecordingHealthDiagnosticSummary(
    val phaseText: String,
    val latestPointText: String,
    val latestEventText: String,
    val serviceText: String,
)

data class RecordingHealthInputs(
    val hasLocationPermission: Boolean,
    val hasActivityRecognitionPermission: Boolean,
    val hasBackgroundLocationPermission: Boolean,
    val hasNotificationPermission: Boolean,
    val ignoresBatteryOptimizations: Boolean,
    val trackingEnabled: Boolean,
    val trackingServiceRunning: Boolean,
    val diagnosticsStatus: String,
    val diagnosticsEvent: String,
    val latestPointTimestampMillis: Long?,
)

fun buildRecordingHealthUiState(inputs: RecordingHealthInputs): RecordingHealthUiState {
    val baseReady = inputs.hasLocationPermission &&
        inputs.hasActivityRecognitionPermission &&
        inputs.hasBackgroundLocationPermission
    val items = buildList {
        add(
            RecordingHealthItemUiState(
                key = RecordingHealthItemKey.LOCATION,
                title = "定位权限",
                statusText = if (inputs.hasLocationPermission) "已授权" else "未授权",
                severity = if (inputs.hasLocationPermission) RecordingHealthItemSeverity.NORMAL else RecordingHealthItemSeverity.ERROR,
                action = if (inputs.hasLocationPermission) RecordingHealthAction.NO_OP else RecordingHealthAction.REQUEST_LOCATION_PERMISSION,
            )
        )
        add(
            RecordingHealthItemUiState(
                key = RecordingHealthItemKey.ACTIVITY_RECOGNITION,
                title = "活动识别",
                statusText = if (inputs.hasActivityRecognitionPermission) "已授权" else "未授权",
                severity = if (inputs.hasActivityRecognitionPermission) RecordingHealthItemSeverity.NORMAL else RecordingHealthItemSeverity.ERROR,
                action = if (inputs.hasActivityRecognitionPermission) RecordingHealthAction.NO_OP else RecordingHealthAction.REQUEST_ACTIVITY_RECOGNITION_PERMISSION,
            )
        )
        add(
            RecordingHealthItemUiState(
                key = RecordingHealthItemKey.BACKGROUND_LOCATION,
                title = "后台定位",
                statusText = if (inputs.hasBackgroundLocationPermission) "已允许后台" else "需到系统设置开启",
                riskText = if (inputs.hasBackgroundLocationPermission) null else "锁屏后可能断记录",
                severity = if (inputs.hasBackgroundLocationPermission) RecordingHealthItemSeverity.NORMAL else RecordingHealthItemSeverity.ERROR,
                action = if (inputs.hasBackgroundLocationPermission) RecordingHealthAction.NO_OP else RecordingHealthAction.OPEN_APP_SETTINGS_FOR_BACKGROUND_LOCATION,
            )
        )
        add(
            RecordingHealthItemUiState(
                key = RecordingHealthItemKey.NOTIFICATION,
                title = "通知权限",
                statusText = if (inputs.hasNotificationPermission) "已允许" else "建议开启",
                riskText = if (inputs.hasNotificationPermission) null else "后台运行提示可能不可见",
                severity = if (inputs.hasNotificationPermission) RecordingHealthItemSeverity.NORMAL else RecordingHealthItemSeverity.WARNING,
                action = if (inputs.hasNotificationPermission) RecordingHealthAction.NO_OP else RecordingHealthAction.REQUEST_NOTIFICATION_PERMISSION,
            )
        )
        add(
            RecordingHealthItemUiState(
                key = RecordingHealthItemKey.BATTERY_OPTIMIZATION,
                title = "电池优化",
                statusText = if (inputs.ignoresBatteryOptimizations) "已忽略优化" else "可能被系统限制",
                riskText = if (inputs.ignoresBatteryOptimizations) null else "系统可能杀掉记录服务",
                severity = if (inputs.ignoresBatteryOptimizations) RecordingHealthItemSeverity.NORMAL else RecordingHealthItemSeverity.WARNING,
                action = if (inputs.ignoresBatteryOptimizations) RecordingHealthAction.NO_OP else RecordingHealthAction.OPEN_BATTERY_OPTIMIZATION_SETTINGS,
            )
        )
        add(
            RecordingHealthItemUiState(
                key = RecordingHealthItemKey.TRACKING_SERVICE,
                title = "后台记录服务",
                statusText = when {
                    inputs.trackingServiceRunning -> "运行中"
                    baseReady -> "未启动"
                    else -> "条件不足"
                },
                severity = when {
                    inputs.trackingServiceRunning -> RecordingHealthItemSeverity.NORMAL
                    baseReady -> RecordingHealthItemSeverity.WARNING
                    else -> RecordingHealthItemSeverity.ERROR
                },
                action = when {
                    inputs.trackingServiceRunning -> RecordingHealthAction.SHOW_DIAGNOSTICS
                    baseReady -> RecordingHealthAction.START_BACKGROUND_TRACKING
                    else -> RecordingHealthAction.NO_OP
                },
            )
        )
    }

    val blocked = items.any { it.severity == RecordingHealthItemSeverity.ERROR }
    val degraded = !blocked && items.any { it.severity == RecordingHealthItemSeverity.WARNING }
    val overallStatus = when {
        blocked -> RecordingHealthOverallStatus.BLOCKED
        degraded -> RecordingHealthOverallStatus.DEGRADED
        else -> RecordingHealthOverallStatus.READY
    }
    val primaryAction = when {
        blocked -> firstRepairAction(items)
        inputs.trackingServiceRunning -> RecordingHealthAction.SHOW_DIAGNOSTICS
        degraded -> RecordingHealthAction.START_BACKGROUND_TRACKING
        else -> RecordingHealthAction.START_BACKGROUND_TRACKING
    }

    return RecordingHealthUiState(
        overallStatus = overallStatus,
        title = "记录状态",
        summaryText = when (overallStatus) {
            RecordingHealthOverallStatus.READY -> "当前适合长时间后台记录"
            RecordingHealthOverallStatus.DEGRADED -> "当前可记录，但后台稳定性可能受系统限制"
            RecordingHealthOverallStatus.BLOCKED -> "请先完成必要授权后再开始记录"
        },
        items = items,
        primaryAction = primaryAction,
        primaryActionText = when (primaryAction) {
            RecordingHealthAction.SHOW_DIAGNOSTICS -> "查看诊断"
            RecordingHealthAction.START_BACKGROUND_TRACKING -> if (overallStatus == RecordingHealthOverallStatus.DEGRADED) "继续记录" else "开始稳定记录"
            RecordingHealthAction.NO_OP -> "查看诊断"
            else -> "去修复"
        },
        diagnosticSummary = RecordingHealthDiagnosticSummary(
            phaseText = if (inputs.trackingEnabled) "记录中" else "待命",
            latestPointText = inputs.latestPointTimestampMillis?.toString() ?: "暂无定位点",
            latestEventText = inputs.diagnosticsEvent,
            serviceText = inputs.diagnosticsStatus,
        ),
    )
}

private fun firstRepairAction(items: List<RecordingHealthItemUiState>): RecordingHealthAction {
    val priority = listOf(
        RecordingHealthItemKey.LOCATION,
        RecordingHealthItemKey.ACTIVITY_RECOGNITION,
        RecordingHealthItemKey.BACKGROUND_LOCATION,
        RecordingHealthItemKey.NOTIFICATION,
        RecordingHealthItemKey.BATTERY_OPTIMIZATION,
        RecordingHealthItemKey.TRACKING_SERVICE,
    )
    return priority.firstNotNullOfOrNull { key ->
        items.firstOrNull { it.key == key && it.action != RecordingHealthAction.NO_OP }?.action
    } ?: RecordingHealthAction.NO_OP
}
```

- [ ] **步骤 4：补齐剩余测试场景**

```kotlin
@Test
fun `battery optimization risk yields degraded status`() {
    val state = buildRecordingHealthUiState(
        RecordingHealthInputs(
            hasLocationPermission = true,
            hasActivityRecognitionPermission = true,
            hasBackgroundLocationPermission = true,
            hasNotificationPermission = true,
            ignoresBatteryOptimizations = false,
            trackingEnabled = false,
            trackingServiceRunning = false,
            diagnosticsStatus = "后台待命中",
            diagnosticsEvent = "后台采点已停止",
            latestPointTimestampMillis = null,
        )
    )

    assertEquals(RecordingHealthOverallStatus.DEGRADED, state.overallStatus)
    assertEquals(RecordingHealthAction.START_BACKGROUND_TRACKING, state.primaryAction)
}

@Test
fun `tracking service running yields diagnostics primary action`() {
    val state = buildRecordingHealthUiState(
        RecordingHealthInputs(
            hasLocationPermission = true,
            hasActivityRecognitionPermission = true,
            hasBackgroundLocationPermission = true,
            hasNotificationPermission = true,
            ignoresBatteryOptimizations = true,
            trackingEnabled = true,
            trackingServiceRunning = true,
            diagnosticsStatus = "记录中",
            diagnosticsEvent = "后台采点服务已保持运行",
            latestPointTimestampMillis = 1713897600000,
        )
    )

    assertEquals(RecordingHealthOverallStatus.READY, state.overallStatus)
    assertEquals(RecordingHealthAction.SHOW_DIAGNOSTICS, state.primaryAction)
    assertEquals("查看诊断", state.primaryActionText)
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.ui.main.RecordingHealthUiStateTest"`

预期：PASS，`RecordingHealthUiStateTest` 全部通过

- [ ] **步骤 6：Commit 状态聚合实现**

```bash
git add app/src/main/java/com/wenhao/record/ui/main/RecordingHealthUiState.kt \
        app/src/test/java/com/wenhao/record/ui/main/RecordingHealthUiStateTest.kt
git commit -m "feat(record): add recording health state reducer"
```

---

### 任务 2：补齐首页修复动作分发与状态构建

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/permissions/PermissionHelper.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt`

- [ ] **步骤 1：先扩展 `PermissionHelper` 的细粒度动作接口**

```kotlin
fun requestLocationPermissionForRepair() {
    pendingPermissionAction = PendingPermissionAction.LOCATE
    locationPermissionLauncher.launch(
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    )
}

fun requestActivityRecognitionPermissionForRepair() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    smartTrackingPermissionLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
}

fun requestNotificationPermissionForRepair() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        openAppSettings()
        return
    }
    smartTrackingPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
}

fun openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", activity.packageName, null)
    )
    appSettingsLauncher.launch(intent)
}

fun openBatteryOptimizationSettings() {
    TrackingPermissionGate.buildIgnoreBatteryOptimizationsIntent(activity)?.let {
        batteryOptimizationLauncher.launch(it)
    }
}
```

- [ ] **步骤 2：在 `MainActivity` 中新增记录健康状态字段与诊断弹层开关**

```kotlin
private var recordingHealthState by mutableStateOf(RecordingHealthUiState.EMPTY)
private var showRecordingHealthDiagnostics by mutableStateOf(false)
```

同时在 `setContent` 中把状态和动作传给 `MainComposeScreen`：

```kotlin
MainComposeScreen(
    currentTab = currentTab,
    dashboardState = dashboardState,
    dashboardOverlayState = dashboardOverlayState,
    historyState = historyState,
    aboutState = aboutState,
    mapboxAccessToken = mapboxAccessToken,
    dashboardMapState = dashboardMapState,
    recordingHealthState = recordingHealthState,
    showRecordingHealthDiagnostics = showRecordingHealthDiagnostics,
    onRecordingHealthPrimaryAction = ::handleRecordingHealthPrimaryAction,
    onRecordingHealthItemAction = ::handleRecordingHealthAction,
    onRecordingHealthDiagnosticsDismiss = { showRecordingHealthDiagnostics = false },
    // 其余参数保持不变
)
```

- [ ] **步骤 3：实现状态构建与刷新入口**

```kotlin
private fun refreshRecordingHealthState(runtimeSnapshot: TrackingRuntimeSnapshot) {
    val diagnostics = AutoTrackDiagnosticsStorage.load(this)
    recordingHealthState = buildRecordingHealthUiState(
        RecordingHealthInputs(
            hasLocationPermission = permissionHelper.hasLocationPermission(),
            hasActivityRecognitionPermission = permissionHelper.hasActivityRecognitionPermission(),
            hasBackgroundLocationPermission = !permissionHelper.needsBackgroundLocationPermission(),
            hasNotificationPermission = !permissionHelper.needsNotificationPermission(),
            ignoresBatteryOptimizations = !permissionHelper.shouldRequestIgnoreBatteryOptimizations(),
            trackingEnabled = runtimeSnapshot.isEnabled,
            trackingServiceRunning = runtimeSnapshot.isEnabled,
            diagnosticsStatus = diagnostics.serviceStatus,
            diagnosticsEvent = diagnostics.lastEvent,
            latestPointTimestampMillis = runtimeSnapshot.latestPoint?.timestampMillis,
        )
    )
}
```

在 `refreshDashboardContent()`、`onResume()`、权限返回回调后的刷新入口里统一调用：

```kotlin
private fun refreshDashboardContent() {
    val runtimeSnapshot = TrackingRuntimeSnapshotStorage.peek(this)
    // 既有逻辑...
    refreshRecordingHealthState(runtimeSnapshot)
}
```

- [ ] **步骤 4：实现动作分发逻辑**

```kotlin
private fun handleRecordingHealthPrimaryAction() {
    handleRecordingHealthAction(recordingHealthState.primaryAction)
}

private fun handleRecordingHealthAction(action: RecordingHealthAction) {
    when (action) {
        RecordingHealthAction.REQUEST_LOCATION_PERMISSION -> permissionHelper.requestLocationPermissionForRepair()
        RecordingHealthAction.REQUEST_ACTIVITY_RECOGNITION_PERMISSION -> permissionHelper.requestActivityRecognitionPermissionForRepair()
        RecordingHealthAction.OPEN_APP_SETTINGS_FOR_BACKGROUND_LOCATION -> permissionHelper.openAppSettings()
        RecordingHealthAction.REQUEST_NOTIFICATION_PERMISSION -> permissionHelper.requestNotificationPermissionForRepair()
        RecordingHealthAction.OPEN_BATTERY_OPTIMIZATION_SETTINGS -> permissionHelper.openBatteryOptimizationSettings()
        RecordingHealthAction.START_BACKGROUND_TRACKING -> permissionHelper.ensureSmartTrackingEnabled()
        RecordingHealthAction.SHOW_DIAGNOSTICS -> showRecordingHealthDiagnostics = true
        RecordingHealthAction.NO_OP -> Unit
    }
}
```

- [ ] **步骤 5：运行聚合测试回归**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.ui.main.RecordingHealthUiStateTest"`

预期：PASS，状态构建接线后不影响纯聚合测试

- [ ] **步骤 6：Commit 动作分发接线**

```bash
git add app/src/main/java/com/wenhao/record/permissions/PermissionHelper.kt \
        app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt
git commit -m "feat(record): wire recording health actions on main screen"
```

---

### 任务 3：实现状态卡与诊断弹层 UI

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/ui/main/RecordingHealthCard.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt`
- 修改：`app/src/main/res/values/strings_compose_dashboard_ui.xml`
- 修改：`app/src/main/res/values/strings_compose_dashboard_dialog.xml`
- 修改：`app/src/main/res/values/strings.xml`

- [ ] **步骤 1：新增状态卡组件**

```kotlin
@Composable
fun RecordingHealthCard(
    state: RecordingHealthUiState,
    onPrimaryActionClick: () -> Unit,
    onItemClick: (RecordingHealthAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    TrackLiquidPanel(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tone = TrackLiquidTone.STRONG,
        shadowElevation = 18.dp,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = state.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TrackStatChip(text = state.summaryText)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.items.forEach { item ->
                    RecordingHealthItemChip(item = item, onClick = { onItemClick(item.action) })
                }
            }

            Text(
                text = state.summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TrackPrimaryButton(
                text = state.primaryActionText,
                onClick = onPrimaryActionClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

- [ ] **步骤 2：新增诊断弹层组件**

```kotlin
@Composable
fun RecordingHealthDiagnosticsDialog(
    state: RecordingHealthUiState,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        TrackLiquidPanel(
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tone = TrackLiquidTone.STRONG,
            shadowElevation = 22.dp,
            contentPadding = PaddingValues(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(text = "记录诊断", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TrackInsetPanel {
                    Text("当前阶段：${state.diagnosticSummary.phaseText}")
                    Text("最近定位：${state.diagnosticSummary.latestPointText}")
                    Text("最近事件：${state.diagnosticSummary.latestEventText}")
                    Text("服务状态：${state.diagnosticSummary.serviceText}")
                }
                TrackPrimaryButton(
                    text = "我知道了",
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
```

- [ ] **步骤 3：把状态卡挂到 `MainComposeScreen` 的记录页信息层**

```kotlin
@Composable
private fun DashboardRoot(
    dashboardState: DashboardScreenUiState,
    overlayState: DashboardOverlayUiState,
    mapState: TrackMapSceneState,
    mapboxAccessToken: String,
    recordingHealthState: RecordingHealthUiState,
    showRecordingHealthDiagnostics: Boolean,
    onRecordingHealthPrimaryAction: () -> Unit,
    onRecordingHealthItemAction: (RecordingHealthAction) -> Unit,
    onRecordingHealthDiagnosticsDismiss: () -> Unit,
    // 其余参数保持不变
) {
    // 地图层不变

    if (isRecordVisible) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(2f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 96.dp)
                .widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecordingHealthCard(
                state = recordingHealthState,
                onPrimaryActionClick = onRecordingHealthPrimaryAction,
                onItemClick = onRecordingHealthItemAction,
            )
            DashboardMetricsStrip(dashboardState = dashboardState)
        }
    }

    if (isRecordVisible && showRecordingHealthDiagnostics) {
        RecordingHealthDiagnosticsDialog(
            state = recordingHealthState,
            onDismissRequest = onRecordingHealthDiagnosticsDismiss,
        )
    }
}
```

- [ ] **步骤 4：补充字符串资源**

```xml
<string name="compose_dashboard_health_title">记录状态</string>
<string name="compose_dashboard_health_ready">可稳定记录</string>
<string name="compose_dashboard_health_degraded">可记录但有风险</string>
<string name="compose_dashboard_health_blocked">当前不可稳定记录</string>
<string name="compose_dashboard_health_advanced_note">高级配置，仅在需要时调整</string>
<string name="compose_dashboard_health_action_fix">去修复</string>
<string name="compose_dashboard_health_action_start">开始稳定记录</string>
<string name="compose_dashboard_health_action_continue">继续记录</string>
<string name="compose_dashboard_health_action_diagnostics">查看诊断</string>
```

- [ ] **步骤 5：运行目标测试与编译校验**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.ui.main.RecordingHealthUiStateTest" --tests "com.wenhao.record.ui.dashboard.DashboardUiControllerTest"`

预期：PASS，字符串与 UI 接线不会破坏现有 Dashboard 单元测试

- [ ] **步骤 6：Commit UI 组件与资源**

```bash
git add app/src/main/java/com/wenhao/record/ui/main/RecordingHealthCard.kt \
        app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt \
        app/src/main/res/values/strings_compose_dashboard_ui.xml \
        app/src/main/res/values/strings_compose_dashboard_dialog.xml \
        app/src/main/res/values/strings.xml
git commit -m "feat(record): add recording health card and diagnostics dialog"
```

---

### 任务 4：精简设置页提示并完成回归验证

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/ui/main/AboutComposeScreen.kt`
- 修改：`app/src/main/res/values/strings_compose_dashboard_ui.xml`
- 测试：`app/src/test/java/com/wenhao/record/ui/main/RecordingHealthUiStateTest.kt`

- [ ] **步骤 1：在设置页顶部加入高级配置说明**

```kotlin
@Composable
private fun SettingsHeroSection(
    state: AboutUiState,
) {
    TrackLiquidPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tone = TrackLiquidTone.STRONG,
        shadowElevation = 14.dp,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrackStatChip(text = "设置")
            Text(
                text = stringResource(R.string.compose_dashboard_health_advanced_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 保留原有版本号与状态块
        }
    }
}
```

- [ ] **步骤 2：为修复优先级补一条回归测试**

```kotlin
@Test
fun `repair action prefers background location before notification and battery`() {
    val state = buildRecordingHealthUiState(
        RecordingHealthInputs(
            hasLocationPermission = true,
            hasActivityRecognitionPermission = true,
            hasBackgroundLocationPermission = false,
            hasNotificationPermission = false,
            ignoresBatteryOptimizations = false,
            trackingEnabled = false,
            trackingServiceRunning = false,
            diagnosticsStatus = "后台待命中",
            diagnosticsEvent = "后台采点已停止",
            latestPointTimestampMillis = null,
        )
    )

    assertEquals(
        RecordingHealthAction.OPEN_APP_SETTINGS_FOR_BACKGROUND_LOCATION,
        state.primaryAction,
    )
}
```

- [ ] **步骤 3：运行回归测试集合**

运行：`./gradlew testDebugUnitTest --tests "com.wenhao.record.ui.main.RecordingHealthUiStateTest" --tests "com.wenhao.record.ui.main.MainUiRefreshPolicyTest" --tests "com.wenhao.record.ui.dashboard.DashboardUiControllerTest"`

预期：PASS，记录健康状态逻辑与主界面刷新策略共存

- [ ] **步骤 4：做一次手动验证清单**

在真机或模拟器上逐项验证：

```text
1. 未授权定位时首页显示“当前不可稳定记录”，主按钮为“去修复”
2. 仅缺后台定位时，状态项显示“需到系统设置开启”
3. 所有权限齐全、服务未启动时，主按钮为“开始稳定记录”
4. 记录中时，主按钮为“查看诊断”
5. 设置页顶部出现“高级配置，仅在需要时调整”
```

- [ ] **步骤 5：Commit 设置页说明与验证收尾**

```bash
git add app/src/main/java/com/wenhao/record/ui/main/AboutComposeScreen.kt \
        app/src/test/java/com/wenhao/record/ui/main/RecordingHealthUiStateTest.kt \
        app/src/main/res/values/strings_compose_dashboard_ui.xml
git commit -m "feat(record): polish recording health entry messaging"
```

---

## 自检结果

### 规格覆盖度

- 记录页常驻状态卡：由任务 3 落地。
- 6 个关键状态项：由任务 1 的状态模型和任务 3 的 UI 渲染共同覆盖。
- 单按钮修复：由任务 1 的 `primaryAction` 与任务 2 的动作分发覆盖。
- 轻量诊断弹层：由任务 3 覆盖。
- 设置页高级配置说明：由任务 4 覆盖。
- 测试策略：由任务 1 与任务 4 的单元测试和回归验证覆盖。

### 占位符扫描

- 计划中没有 `TODO`、`待定`、`后续实现` 等占位词。
- 每个代码步骤都给出了明确文件与示例代码。
- 每个测试步骤都给出了命令与预期结果。

### 类型一致性

- 统一使用 `RecordingHealthUiState`、`RecordingHealthInputs`、`RecordingHealthAction` 作为命名。
- `primaryAction` 与状态项点击动作都使用同一枚举，避免二套动作系统并存。
- `MainActivity` 仅负责构建与分发，不在 Compose 层重新推导健康状态。
