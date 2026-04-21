# TrackRecord UI 重构规格：Quiet Atlas（雾白湖绿地图版）

## 文档定位

- 文档类型：移动端 UI 重构设计
- 适用范围：`Dashboard`、`History`、`Map Detail`、`About` 及公共 `designsystem`
- 设计目标：将当前偏展示型的浅色玻璃风格，重构为更克制、更现代的地图工具体验
- 设计关键词：`Quiet Atlas`、城市地图、雾白湖绿、极简、浮动信息层、低情绪噪声

## 相关参考

- 官方设计参考：
  - [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
  - [Compose MotionScheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)
  - [Compose Adaptive List Detail](https://developer.android.com/develop/ui/compose/layouts/adaptive/list-detail)
  - [Compose Accessibility Defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- 视觉参考：
  - [Dribbble Popular Mobile](https://dribbble.com/shots/popular/mobile)
  - [Smart Home Control App](https://dribbble.com/shots/27295104-Smart-Home-Control-App)
  - [Solar panel monitoring mobile app](https://dribbble.com/shots/27294739-Solar-panel-monitoring-mobile-app)
  - [Movie Discovery Streaming Mobile App UI UX Design](https://dribbble.com/shots/27152729-Movie-Discovery-Streaming-Mobile-App-UI-UX-Design)
  - [Healthcare App UI — Doctor Booking & Telemedicine Mobile Design](https://dribbble.com/shots/27294815-Healthcare-App-UI-Doctor-Booking-Telemedicine-Mobile-Design)

说明：

- 上述 Dribbble 页面用于提炼卡片层级、留白节奏、浮动控件和浅色地图覆盖层语言。
- 本设计不直接复刻外部稿件，而是将其视觉规律落到当前 app 的功能结构中。

## 1. 摘要

本次重构不改变 app 的核心功能结构，也不重写导航模型。重构重点是统一视觉系统、重新建立页面层级，并让地图重新成为首页和详情页的主画布。

最终目标如下：

- 从「偏展示稿的玻璃拟态」转向「安静、现代、克制的地图工具」。
- 用 `雾白 + 湖绿 + 石板灰` 重建浅色主题，去掉偏紫蓝的既有气质。
- 收敛首页视觉重心，移除与地图竞争注意力的夸张 Hero 组件。
- 让 `History` 更适合扫描和回看轨迹，而不是情绪化内容流。
- 让 `About` 从开发配置堆栈转为产品化的设置与服务页面。
- 通过统一的 `designsystem` 组件和 token，避免每个页面各自生长一套样式。

## 2. 设计目标与非目标

### 2.1 设计目标

- 建立一套适用于地图工具型 app 的浅色 Compose 视觉系统。
- 保留当前页面和数据流的主体结构，降低重构风险。
- 强化地图与轨迹的主视觉地位，让信息层更像覆盖在地图上的面板。
- 在保证信息清晰的前提下，提高整体完成度、品牌感和一致性。
- 将控件尺寸、动效节奏、可点击区域统一到 Material 3 和 Compose 官方建议范围。

### 2.2 非目标

- 不在本阶段调整业务逻辑、数据库模型或导航架构。
- 不在本阶段引入照片、地点标签、回忆卡片等新产品能力。
- 不做以大屏优先为目标的信息架构重写，只保证未来可扩展。
- 不追求复杂拟物、强发光、重模糊或大面积高饱和渐变效果。

## 3. 核心设计原则

### 3.1 地图是主画布，面板是配角

首页和地图详情页都必须保持「先看到地图，再理解信息」的阅读顺序。所有卡片和按钮都服务于地图，不与地图争夺主视觉中心。

### 3.2 视觉克制优先于炫技

当前界面存在明显的展示稿倾向，例如发光光环、较强的玻璃边缘和偏厚的浮层。新方案会保留轻微半透明和柔和高光，但整体更薄、更静、更像真实信息覆盖层。

### 3.3 层级靠排版和间距建立，不靠装饰堆砌

核心数字、标题和次要信息之间的关系，应主要通过字重、字号、留白和布局建立，而不是依赖额外的阴影、发光或多层容器。

### 3.4 页面共享一套组件语法

状态胶囊、指标卡、底部工具条、浮动按钮、轨迹摘要卡需要在不同页面中共享统一的造型和交互反馈，不再每页单独生成视觉变体。

## 4. 视觉系统

### 4.1 色彩系统

新主题将替换现有偏紫蓝色板，改为以下方向：

- 页面背景：雾白、淡灰绿、极浅石板灰渐层
- 主强调色：湖水绿，用于轨迹线、主按钮、活动状态和关键高亮
- 中性色：石板灰，用于正文、次级标题、图标和弱边框
- 辅助色：非常轻的鼠尾草绿或冷灰绿，仅用于表面层次区分
- 错误与警告：沿用 Material 3 语义色，但饱和度控制在整体体系内

设计要求：

- 背景始终比当前版本更白、更雾感，减少蓝紫色偏色。
- 强调色只在关键交互点出现，不能泛滥到整页。
- 地图覆盖层需要和底图颜色兼容，避免出现一块明显突兀的 UI 贴片。

### 4.2 材质与表面

新方案不再强调「厚玻璃」，改为「轻雾面板」：

- 表面 alpha 更低
- 边框更细
- 阴影更浅
- 高光更集中且面积更小
- 模糊只作为辅助，不成为视觉主角

目标效果：

- 卡片像漂浮在地图上的信息纸片
- 工具条像地图控件而非展示型 CTA 容器
- 面板与背景之间有分层，但没有强烈的漂浮炫耀感

### 4.3 排版

排版策略如下：

- 收敛 `display` 级标题的使用范围
- 提高 `title` 与 `body` 的可读性
- 数字类信息使用更稳定的字重和字宽风格
- 页面标题更像地图产品，而不是品牌海报

具体要求：

- 首页不再出现占据中心的大号展示数字
- 列表页和设置页优先使用中等字号和稳定字重
- 核心读数使用清晰、直接的数字排布，减少装饰性字距处理

### 4.4 圆角、边框与阴影

- 主卡片圆角统一提升到中大圆角，但不过度夸张
- 工具按钮使用统一的圆形或胶囊形语法
- 边框颜色改为轻雾边框，不再使用明显发亮的玻璃描边
- 阴影以轻柔分层为主，不制造厚重浮空感

## 5. 页面架构

### 5.1 Dashboard

#### 目标

将首页重构为「全屏地图 + 顶部状态胶囊 + 底部浮动工具面板」结构，移除当前居中的大型 Hero 光环。

#### 布局

- 全屏地图保留，作为首页第一视觉层
- 顶部左上角保留一个轻量状态胶囊，显示 GPS / 记录状态
- 页面中央不再放置大号里程组件
- 核心数据下沉到底部浮动面板
- 底部保留轻量导航与主要操作入口

#### 信息层级

默认态只展示：

- 距离
- 时长
- 速度

展开态可展示：

- 记录状态说明
- 自动追踪相关文案
- 次级引导信息

#### 视觉要求

- 地图始终是视觉中心
- 面板高度控制在不压迫地图的范围
- 底部条更薄、更整洁，像地图工具层而不是展示卡片

### 5.2 History

#### 目标

将历史页重构为高可扫描性的轨迹摘要列表。

#### 布局

- 页面顶部保留简洁概览区
- 下方为按时间组织的轨迹摘要卡列表
- 每张卡片包含日期、距离、时长、质量信息和微型轨迹预览

#### 信息层级

顶部概览区只放：

- 累计距离
- 累计时长
- 记录总天数

列表卡片优先展示：

- 日期或时间标签
- 距离与时长
- 质量等级
- 微型轨迹预览

#### 视觉要求

- 分组标题减弱装饰性
- 列表扫描效率优先
- 历史卡片与首页面板保持同一套表面语言

### 5.3 Map Detail

#### 目标

保留当前「全屏地图 + 底部详情卡」的正确结构，但统一其样式和信息层级。

#### 布局

- 顶部返回按钮保留
- 右下重定位按钮保留
- 底部详情卡保留浮动形式
- 详情卡默认折叠，展示最关键的 2 到 3 项信息
- 展开后再显示海拔图例、点位数、摘要等次级信息

#### 视觉要求

- 返回按钮和重定位按钮统一为地图控件语言
- 详情卡更薄、更轻
- 详情卡不能遮挡过多轨迹末端信息

### 5.4 About

#### 目标

将当前偏开发后台式的配置页，重构为产品化的设置与服务页。

#### 信息架构

页面按以下顺序组织：

1. 应用信息
2. 地图服务
3. 上传服务
4. 应用管理

#### 视觉要求

- 每个 section 有明确标题和说明
- 输入框、按钮、状态文案之间建立更清晰的层级
- 页面整体仍然遵守 `Quiet Atlas` 的克制气质，不引入与地图页冲突的独立视觉系统

## 6. 设计系统拆分

### 6.1 Theme 与 Token

需要重写 [`TrackRecordTheme.kt`](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/designsystem/TrackRecordTheme.kt)，完成以下工作：

- 替换现有主色和表面色
- 收敛 typography 层级
- 统一 spacing token
- 统一圆角 token
- 定义 surface alpha、弱边框、浅阴影等表面 token

建议新增或重组以下概念：

- 页面背景 token
- 地图表面 token
- 弱边框 token
- 主要强调色 token
- 次级文本 token
- 地图控件尺寸 token

### 6.2 Surface 组件

需要弱化当前 [`TrackGlassmorphism.kt`](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/designsystem/TrackGlassmorphism.kt) 的高模糊和发亮边框表现，重新抽象为更明确的 surface 组件族。

建议组件：

- `TrackMapSurfaceCard`
- `TrackInsetPanel`
- `TrackToolbarPill`
- `TrackControlButton`

这些组件的职责应清晰区分：

- 地图覆盖信息卡
- 页面内嵌容器
- 顶部或底部工具条
- 浮动控件按钮

### 6.3 数据组件

需要统一指标和摘要组件，避免重复造型：

- 指标卡
- 状态胶囊
- 轨迹摘要卡
- 微型轨迹预览卡
- 设置页 section 卡

设计要求：

- 同类数据在不同页面应使用同一造型逻辑
- 同一控件在不同页面可调整密度，但不改变组件语法

### 6.4 导航与控件

底部导航和浮动地图按钮需要统一风格，满足以下要求：

- 命中区不低于 `48 dp`
- 选中态明显但不高调
- 按压反馈轻量
- 与页面表面风格统一

## 7. 动效与交互

### 7.1 总体原则

动效风格从当前的展示型入场过渡，改为更轻、更短、更像工具型产品的状态反馈。

### 7.2 动效要求

- 减少 `scaleIn` 和强烈发光式进场
- 优先使用轻位移和淡入
- 页面首次入场允许存在层级化出现，但总时长需明显缩短
- 点击反馈以表面压感和轻微色彩变化为主

### 7.3 页面级交互

- 首页底部面板支持折叠与展开
- 地图详情卡支持折叠与展开
- 历史卡片保持现有点击与长按语义
- 设置页按钮与输入框反馈更明确，但不增加复杂动画

## 8. 技术落地边界

### 8.1 保留结构

以下结构默认保留，不做推倒重写：

- 当前页面入口与导航模型
- `Mapbox` 画布和地图渲染通道
- 历史数据来源和点击行为
- `About` 页的功能项和保存逻辑

### 8.2 重点改造文件

- [`TrackRecordTheme.kt`](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/designsystem/TrackRecordTheme.kt)
- [`TrackGlassmorphism.kt`](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/designsystem/TrackGlassmorphism.kt)
- [`TrackRecordComponents.kt`](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/designsystem/TrackRecordComponents.kt)
- [`DashboardComposeScreen.kt`](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/dashboard/DashboardComposeScreen.kt)
- [`HistoryComposeScreen.kt`](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/history/HistoryComposeScreen.kt)
- [`MapComposeScreen.kt`](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/map/MapComposeScreen.kt)
- [`AboutComposeScreen.kt`](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/main/AboutComposeScreen.kt)

### 8.3 实施顺序

推荐的实现顺序如下：

1. 重写主题和 surface token
2. 重构基础面板、按钮、状态组件
3. 重构 `Dashboard`
4. 重构 `History`
5. 重构 `Map Detail`
6. 重构 `About`

这样可以保证页面改造始终基于统一组件，而不是先在页面里临时拼出新样式。

## 9. 风险与约束

### 9.1 主要风险

- 如果保留过多既有玻璃风格细节，最终成品会停留在旧方案的变体，而不是新的视觉系统。
- 如果首页继续保留中心大型 Hero 读数，地图主画布目标会失败。
- 如果 `History` 继续强调装饰性而不是扫描效率，会与 `Quiet Atlas` 的定位冲突。

### 9.2 落地约束

- 需要控制 Compose 中模糊和阴影的使用，避免在地图页叠加过重绘制成本。
- 自定义 surface 组件应尽量通过现有 Material 3 能力和轻量 `drawWithCache` 实现。
- 组件重构应优先复用当前文件边界，避免顺手做大范围无关重构。

## 10. 验收标准

完成重构后，应满足以下标准：

- 首页打开后，第一视觉中心是地图，而不是中心读数或装饰组件。
- 全局配色已从偏紫蓝切换为雾白湖绿体系。
- `Dashboard`、`History`、`Map Detail`、`About` 共享同一套 surface 与控件语言。
- 主要点击控件满足基本触控尺寸和明显状态反馈。
- 页面视觉完成度提升，但不过度依赖重模糊、强发光或展示型动画。
- 所有功能行为与当前版本保持一致，UI 重构不引入功能回归。

