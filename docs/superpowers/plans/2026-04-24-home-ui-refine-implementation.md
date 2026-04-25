# 首页 UI 轻量化重构实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 把记录页首页从厚重的多白卡叠层，重构为更接近花瓣地图的轻盈地图首页。

**架构：** 保持现有地图与业务逻辑不变，只重构首页 Compose 结构和设计系统组件。将数据摘要条、记录状态主卡、底部导航和浮动按钮统一到同一套浅雾面、低对比、低厚度的视觉语言，同时通过小型 helper 固定主卡内容裁剪策略，避免首页再次长成后台诊断面板。

**技术栈：** Kotlin、Jetpack Compose、Material 3、Robolectric 单测、ADB 真机截图验证

---

### 任务 1：首页主卡裁剪模型

**文件：**
- 创建：`/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/main/HomeRecordChromeModel.kt`
- 修改：`/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/main/RecordingHealthUiState.kt`
- 测试：`/Users/a555/StudioProjects/track-record-new/app/src/test/java/com/wenhao/record/ui/main/HomeRecordChromeModelTest.kt`

- [ ] **步骤 1：编写失败的测试**

```kotlin
@Test
fun `home chrome picks single blocking spotlight item before warnings`() {
    val state = buildRecordingHealthUiState(
        RecordingHealthInputs(
            hasLocationPermission = true,
            hasActivityRecognitionPermission = true,
            hasBackgroundLocationPermission = false,
            hasNotificationPermission = false,
            ignoresBatteryOptimizations = false,
            trackingEnabled = false,
            trackingActive = false,
            diagnosticsStatus = "后台待命中",
            diagnosticsEvent = "后台采点已停止",
            latestPointTimestampMillis = null,
        )
    )

    assertEquals(
        RecordingHealthItemKey.BACKGROUND_LOCATION,
        buildHomeRecordChromeModel(state).spotlightItem?.key,
    )
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`bash ./gradlew :app:testDebugUnitTest --tests com.wenhao.record.ui.main.HomeRecordChromeModelTest`
预期：FAIL，报错 `buildHomeRecordChromeModel` 未定义

- [ ] **步骤 3：编写最少实现代码**

```kotlin
data class HomeRecordChromeModel(
    val spotlightItem: RecordingHealthItemUiState?,
    val secondaryActionText: String,
)

fun buildHomeRecordChromeModel(state: RecordingHealthUiState): HomeRecordChromeModel {
    val spotlight = compactRecordingHealthHighlights(state, maxItems = 1).firstOrNull()
    return HomeRecordChromeModel(
        spotlightItem = spotlight,
        secondaryActionText = if (spotlight == null) "查看状态" else "查看详情",
    )
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`bash ./gradlew :app:testDebugUnitTest --tests com.wenhao.record.ui.main.HomeRecordChromeModelTest`
预期：PASS

### 任务 2：重构首页摘要条与状态主卡

**文件：**
- 修改：`/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt`
- 修改：`/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/main/RecordingHealthCard.kt`
- 修改：`/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/designsystem/TrackRecordComponents.kt`

- [ ] **步骤 1：把摘要条从厚卡改为轻量横条**

```kotlin
Column(
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(start = 16.dp, end = 16.dp, bottom = overlayBottomOffset)
        .widthIn(max = 420.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    DashboardCompactSummaryBar(dashboardState = dashboardState)
    RecordingHealthCard(...)
}
```

- [ ] **步骤 2：把记录状态卡收敛成单主卡**

```kotlin
val chromeModel = buildHomeRecordChromeModel(state)

chromeModel.spotlightItem?.let { item ->
    HomeSpotlightRow(item = item)
}

Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    TrackPrimaryButton(...)
    TrackSecondaryButton(text = chromeModel.secondaryActionText, ...)
}
```

- [ ] **步骤 3：统一容器厚度、圆角和按钮重量**

```kotlin
TrackLiquidPanel(
    tone = TrackLiquidTone.SUBTLE,
    shadowElevation = 8.dp,
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
)
```

- [ ] **步骤 4：运行编译验证**

运行：`bash ./gradlew :app:compileDebugKotlin`
预期：BUILD SUCCESSFUL

### 任务 3：重构底部导航与浮动定位按钮

**文件：**
- 修改：`/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/designsystem/TrackRecordComponents.kt`
- 修改：`/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/designsystem/TrackMapScaffold.kt`
- 修改：`/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt`

- [ ] **步骤 1：降低底栏高度与选中态重量**

```kotlin
Surface(
    shape = RoundedCornerShape(28.dp),
    tonalElevation = 1.dp,
    shadowElevation = 10.dp,
) {
    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
}
```

- [ ] **步骤 2：缩小选中态胶囊**

```kotlin
val containerColor = if (selected) {
    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f)
} else {
    Color.Transparent
}
```

- [ ] **步骤 3：把定位按钮改成更轻的地图浮层按钮**

```kotlin
FilledTonalIconButton(
    modifier = modifier.size(48.dp),
    colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ),
)
```

- [ ] **步骤 4：运行编译验证**

运行：`bash ./gradlew :app:compileDebugKotlin`
预期：BUILD SUCCESSFUL

### 任务 4：真机验收

**文件：**
- 无新增源码文件
- 验证截图：`/Users/a555/StudioProjects/track-record-new/.codex-phone/`

- [ ] **步骤 1：安装最新构建到真机**

运行：`bash ./gradlew :app:installDebug`
预期：Installed on 1 device.

- [ ] **步骤 2：启动首页并抓图**

运行：`adb shell am start -n com.wenhao.record/.ui.main.MainActivity`
运行：`adb exec-out screencap -p > /Users/a555/StudioProjects/track-record-new/.codex-phone/home-ui-refined.png`
预期：截图显示首页下半区明显变轻，地图更突出

- [ ] **步骤 3：运行首页相关测试与编译**

运行：`bash ./gradlew :app:testDebugUnitTest --tests com.wenhao.record.ui.main.HomeRecordChromeModelTest --tests com.wenhao.record.ui.main.RecordingHealthUiStateTest --tests com.wenhao.record.ui.main.MainUiRefreshPolicyTest`
运行：`bash ./gradlew :app:compileDebugKotlin`
预期：全部通过
