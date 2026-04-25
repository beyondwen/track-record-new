# 首页地图上拉面板重构实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将首页改为默认轻摘要、上拉展开记录面板的地图优先交互。

**架构：** 保留现有 `MainComposeScreen` 作为首页布局入口，把底部面板拆成收起摘要与展开详情两种状态。地图点击和拖动事件统一驱动面板收起，底部导航与浮动定位按钮根据实际面板高度避让。

**技术栈：** Android Kotlin、Jetpack Compose、Material 3、Mapbox Compose。

---

## 文件结构

- 修改：`app/src/main/java/com/wenhao/record/ui/main/DashboardOverviewSheetState.kt`，扩展上拉面板状态解析逻辑。
- 修改：`app/src/test/java/com/wenhao/record/ui/main/DashboardOverviewSheetStateTest.kt`，覆盖地图交互收起与阈值拖拽。
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt`，实现收起摘要、展开面板、地图点击收起、动态避让。
- 修改：`app/src/main/java/com/wenhao/record/ui/map/TrackMapboxCanvas.kt`，增加地图空白点击回调。
- 修改：`app/src/main/java/com/wenhao/record/ui/designsystem/TrackRecordComponents.kt`，调薄底部导航视觉。
- 修改：`app/src/main/java/com/wenhao/record/ui/designsystem/TrackMapScaffold.kt`，轻量化浮动地图按钮。

## 任务 1：面板状态逻辑

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/ui/main/DashboardOverviewSheetState.kt`
- 测试：`app/src/test/java/com/wenhao/record/ui/main/DashboardOverviewSheetStateTest.kt`

- [ ] **步骤 1：编写失败测试**

新增测试：地图交互会把展开面板收起；拖拽阈值仍保持原语义。

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :app:testDebugUnitTest --tests com.wenhao.record.ui.main.DashboardOverviewSheetStateTest`
预期：FAIL，原因是新函数尚未定义。

- [ ] **步骤 3：实现状态函数**

新增 `resolveDashboardOverviewSheetAfterMapInteraction()`，返回 `false`。

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :app:testDebugUnitTest --tests com.wenhao.record.ui.main.DashboardOverviewSheetStateTest`
预期：PASS。

## 任务 2：首页上拉面板

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt`

- [ ] **步骤 1：实现收起摘要**

把 `DashboardOverviewSheet` 改为始终渲染一个容器，收起时显示把手、指标摘要和状态胶囊。

- [ ] **步骤 2：实现展开详情**

展开时复用现有 `DashboardOverviewPanel` 内容，保持最多一条风险与两个操作按钮。

- [ ] **步骤 3：接入地图交互收起**

地图拖动和空白点击调用状态函数收起面板，同时保留用户位置点点击弹窗。

- [ ] **步骤 4：调整动态避让**

继续使用 `onSizeChanged` 获取面板高度，定位按钮与地图 viewport bottom padding 跟随面板高度。

## 任务 3：地图点击回调

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/ui/map/TrackMapboxCanvas.kt`

- [ ] **步骤 1：添加参数**

新增 `onMapClick: (() -> Unit)? = null` 参数。

- [ ] **步骤 2：更新点击处理**

点击用户位置点时只触发用户位置弹窗；点击空白地图时触发 `onMapClick` 并返回 `false`。

## 任务 4：导航与浮动按钮视觉

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/ui/designsystem/TrackRecordComponents.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/designsystem/TrackMapScaffold.kt`

- [ ] **步骤 1：调薄底部导航**

降低容器圆角、padding、item 最小高度和阴影。

- [ ] **步骤 2：轻量化定位按钮**

视觉尺寸保持不低于 44dp，降低描边和阴影，常态更接近地图工具按钮。

## 任务 5：验证

**文件：**
- 修改：本计划涉及的 Kotlin 文件

- [ ] **步骤 1：运行单元测试**

运行：`./gradlew :app:testDebugUnitTest --tests com.wenhao.record.ui.main.DashboardOverviewSheetStateTest`
预期：PASS。

- [ ] **步骤 2：运行 Kotlin 编译**

运行：`./gradlew :app:compileDebugKotlin`
预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：检查 diff**

运行：`git diff -- app/src/main/java/com/wenhao/record/ui/main app/src/main/java/com/wenhao/record/ui/map app/src/main/java/com/wenhao/record/ui/designsystem app/src/test/java/com/wenhao/record/ui/main docs/superpowers`
预期：只包含首页上拉面板相关改动。
