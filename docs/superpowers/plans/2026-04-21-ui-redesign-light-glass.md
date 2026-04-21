# TrackRecord UI 重构实现计划 (Light Premium Glass)

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现 Dribbble 风格的高级感浅色毛玻璃 UI，包括浮动模块化布局和动态进度光轮。

**架构：** 基于 Compose `GraphicsLayer` 实现实时模糊，利用 `Canvas` 绘制自定义光效，并使用 `AnimateVisibility` 与 `Damping` 动画提升沉浸感。

**技术栈：** Kotlin, Jetpack Compose, Material 3, GraphicsLayer API.

---

### 任务 1：实现毛玻璃材质基础 (TrackGlassmorphism)

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/ui/designsystem/TrackGlassmorphism.kt`

- [ ] **步骤 1：创建 `Modifier.glassBackground` 修饰符**
实现一个通用的毛玻璃背景修饰符，处理模糊效果、半透明填充和细腻边框。

```kotlin
fun Modifier.glassBackground(
    blur: Dp = 25.dp,
    color: Color = Color.White.copy(alpha = 0.6f),
    borderColor: Color = Color.White.copy(alpha = 0.8f),
    shape: Shape = RoundedCornerShape(24.dp)
): Modifier = this.then(
    Modifier
        .graphicsLayer {
            // 在 Android 12+ 上使用 RenderEffect 实现模糊
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                renderEffect = RenderEffect.createBlurEffect(
                    blur.toPx(), blur.toPx(), Shader.TileMode.CLAMP
                )
            }
            clip = true
            this.shape = shape
        }
        .background(color)
        .border(1.dp, borderColor, shape)
)
```

- [ ] **步骤 2：封装 `TrackGlassCard` 容器**
提供一个标准的玻璃卡片容器。

```kotlin
@Composable
fun TrackGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.glassBackground(shape = shape),
        color = Color.Transparent,
        shadowElevation = 8.dp, // 软阴影
        content = content
    )
}
```

- [ ] **步骤 3：Commit 基础材质实现**

```bash
git add app/src/main/java/com/wenhao/record/ui/designsystem/TrackGlassmorphism.kt
git commit -m "feat(ui): add glassmorphism material support"
```

---

### 任务 2：实现动态进度光轮组件 (TrackGlowRing)

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/ui/designsystem/TrackGlowRing.kt`

- [ ] **步骤 1：编写 `TrackGlowRing` 自定义绘图逻辑**
使用 `Canvas` 绘制带有外发光的进度圆环。

```kotlin
@Composable
fun TrackGlowRing(
    progress: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    glowColor: Color = ringColor.copy(alpha = 0.4f)
) {
    Canvas(modifier = modifier) {
        // 绘制发光层
        drawArc(
            color = glowColor,
            startAngle = -225f,
            sweepAngle = 270f * progress,
            useCenter = false,
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        )
        // 绘制主环
        drawArc(
            color = ringColor,
            startAngle = -225f,
            sweepAngle = 270f * progress,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
```

- [ ] **步骤 2：添加呼吸动效**
让光效更灵动。

```kotlin
val infiniteTransition = rememberInfiniteTransition()
val glowAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f, targetValue = 0.8f,
    animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse)
)
```

- [ ] **步骤 3：Commit 组件实现**

```bash
git add app/src/main/java/com/wenhao/record/ui/designsystem/TrackGlowRing.kt
git commit -m "feat(ui): add GlowRing custom drawing component"
```

---

### 任务 3：重构 Dashboard 布局为浮动模块化

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/ui/dashboard/DashboardComposeScreen.kt`

- [ ] **步骤 1：移除旧的 `TrackLiquidPanel` 底部面板**
将布局逻辑从底部抽屉模式改为全屏地图背景 + 浮动卡片。

- [ ] **步骤 2：实现 `HeroMetric` (进度光轮里程卡)**
将其放置在屏幕中央。

```kotlin
Box(modifier = Modifier.align(Alignment.Center)) {
    TrackGlassCard(modifier = Modifier.size(200.dp)) {
        Box(contentAlignment = Alignment.Center) {
            TrackGlowRing(progress = currentProgress)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = distance, style = MaterialTheme.typography.displayLarge)
                Text(text = "KM", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
```

- [ ] **步骤 3：实现次要指标浮动卡片**
使用 `Row` 和 `weight` 放置在底部上方。

- [ ] **步骤 4：Commit 页面重构**

```bash
git add app/src/main/java/com/wenhao/record/ui/dashboard/DashboardComposeScreen.kt
git commit -m "refactor(ui): update dashboard to floating modular layout"
```

---

### 任务 5：重构 History 页面为毛玻璃风格

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/ui/history/HistoryComposeScreen.kt`

- [ ] **步骤 1：将 `HistoryOverviewMetricCard` 迁移至 `TrackGlassCard`**
替换掉旧的 `TrackLiquidPanel`。

- [ ] **步骤 2：将 `HistoryDayCard` 迁移至 `TrackGlassCard`**
确保列表中的每一项都具有通透的玻璃质感。

- [ ] **步骤 3：优化入场动画**
为 `LazyColumn` 的每一项添加交错的入场效果。

- [ ] **步骤 4：Commit History 重构**

```bash
git add app/src/main/java/com/wenhao/record/ui/history/HistoryComposeScreen.kt
git commit -m "refactor(ui): update history screen with glassmorphism (Task 5)"
```

---

### 任务 6：重构 Map Detail 页面为浮动卡片风格

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/ui/map/MapComposeScreen.kt`

- [ ] **步骤 1：重构 `BottomSheet` 为浮动玻璃卡片**
修改 `TrackBottomSurface` 的实现，使其不再贴合屏幕底部，而是以卡片形式浮动。

- [ ] **步骤 2：应用 `TrackGlowRing` 作为装饰**
在里程显示旁添加一个静态或缓慢旋转的虹彩光轮。

- [ ] **步骤 3：Commit Map Detail 重构**

```bash
git add app/src/main/java/com/wenhao/record/ui/map/MapComposeScreen.kt
git commit -m "refactor(ui): update map detail with floating glass layout (Task 6)"
```
