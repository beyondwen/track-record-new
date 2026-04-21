# TrackRecord UI 重构规格：Dribbble Premium Glass (浅色版)

## 1. 视觉语言 (Visual Language)

本项目将采用 **Light Glassmorphism (浅色毛玻璃)** 风格，旨在将 TrackRecord 从一个实用的工具面板提升为一个具有“呼吸感”和“现代艺术感”的移动日志应用。

### 1.1 色彩系统
*   **Surface (面板)**: `rgba(255, 255, 255, 0.6)`，配合 `backdrop-filter: blur(25dp)`。
*   **Border (边框)**: `rgba(255, 255, 255, 0.8)`，粗细为 `1dp`，模拟玻璃边缘的高光。
*   **Typography (排版)**: 
    *   主标题: `Ink (0xFF1F2247)`，加粗，负字间距。
    *   辅助文字: `MutedInk (0xFF6C7099)`，中等字重。
*   **Accent (强调色)**: 
    *   虹彩光轮: 从 `LakePrimary (0xFF5A66FF)` 到柔和蓝色的渐变。
    *   状态指示: `SuccessColor (0xFF52D5B4)` 带有外发光。

### 1.2 材质与投影
*   **实时模糊**: 使用 Compose 的 `RenderEffect` 或 `GraphicsLayer` 在地图图层之上实现。
*   **软阴影**: 使用极大的扩散半径和极低的透明度（如 `color = rgba(90, 102, 255, 0.12), blur = 20dp`），营造浮动感而非沉重感。

## 2. 布局架构 (Layout Architecture)

采用 **Floating Modules (浮动模块化)** 结构。

### 2.1 Dashboard (主页)
*   **Map Base**: 全屏背景，色调调整为浅色/高对比度。
*   **Status Chip (顶部)**: 左上角浮动的胶囊形指示器，显示 GPS 状态。
*   **Hero Metric (核心卡片)**: 位于屏幕中上部，包含“进度光轮”和超大里程数字。
*   **Mini Metrics (次要卡片)**: 两块对称的浮动玻璃卡片，显示时间与速度。
*   **Action Dock (底部导航)**: 圆角矩形流体面板，包含主操作按钮（开始/停止）和历史记录入口。

### 2.2 History (历史列表)
*   **Hero Section**: 总里程与时长卡片采用 `TrackGlassCard`。
*   **Log Cards**: 每一天的记录卡片采用毛玻璃材质。
*   **Interactive**: 点击卡片时有微弱的视觉反馈（光晕涟漪）。

### 2.3 Map Detail (地图轨迹)
*   **Sheet to Float**: 将原有的贴边底部面板改为“浮动卡片 (Floating Card)”。
*   **Translucency**: 底部卡片背景为全透明毛玻璃，透过它可以看到轨迹的末端。
*   **Hierarchy**: 强化标题与里程的层级，使用 `TrackGlowRing`（静态或进度）作为装饰。

## 3. 交互与动效 (Interactions & Motion)

等级：**Deeply Immersive (深度沉浸)**。

### 3.1 核心动效
*   **进度光轮 (Glow Ring)**: 
    *   光轮带有 2s 周期的微弱呼吸效果（透明度 0.6 -> 1.0）。
    *   位移增加时，光轮沿顺时针平滑生长。
*   **数字滚动 (Number Rolling)**: 里程数字切换时使用 `animateIntAsState` 配合自定义转换逻辑，实现类似机械滚轮的动态效果。
*   **入场动画**: 页面加载时，各卡片按顺序从屏幕外以 `Damping`（阻尼）效果滑入。

### 3.2 状态反馈
*   点击按钮时，毛玻璃面板会有微弱的收缩（Scale 0.98）并伴随触感反馈。

## 4. 技术实现要点 (Implementation Notes)

*   **性能优化**: 为了在 Compose 中实现流畅的实时模糊，必须在 `GraphicsLayer` 中应用 `RenderEffect`，并避免在每一帧中重新创建大的 Bitmap。
*   **自定义绘图**: 进度光轮需在 `Canvas` 中使用 `drawArc` 配合 `BlurMaskFilter` 实现发光边缘。
