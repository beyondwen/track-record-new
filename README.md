# TrackRecord

TrackRecord 是一个 Android 轨迹记录应用，使用 Kotlin 开发，当前以 Jetpack Compose、Room 和 Mapbox 为核心技术栈。

## 项目概览

该项目聚焦于轨迹记录、历史回放、地图展示与后台追踪能力，代码已按业务领域拆分为 `data`、`tracking`、`map`、`ui`、`permissions`、`stability` 等模块目录。

当前项目信息：

- **应用 ID：** `com.wenhao.record`
- **最低支持版本：** Android 7.0（API 24）
- **目标版本：** Android 16（API 36）
- **JVM / Java 版本：** 17
- **主要 UI 技术：** Jetpack Compose
- **本地存储：** Room
- **地图能力：** Mapbox

## 目录结构

```text
TrackRecord/
├── app/
│   ├── src/main/java/com/wenhao/record/
│   │   ├── data/          # 历史记录、本地存储、轨迹数据处理
│   │   ├── map/           # 地图工具与坐标处理
│   │   ├── permissions/   # 权限相关逻辑
│   │   ├── stability/     # 崩溃日志等稳定性能力
│   │   ├── tracking/      # 后台追踪、决策与轨迹处理
│   │   ├── ui/            # Compose 页面与 UI 控制器
│   │   └── util/          # 通用工具
│   ├── schemas/           # Room schema 导出
│   └── build.gradle
├── docs/                  # 设计文档、迁移计划、会话记录
├── tools/                 # 辅助工具与实验性能力
├── gradle/
├── build.gradle
├── settings.gradle
└── gradlew / gradlew.bat
```

## 核心能力

- 轨迹记录与历史数据管理
- 后台定位与追踪服务
- 基于 Mapbox 的地图展示与轨迹渲染
- Room 本地数据库持久化
- Compose 界面与部分模块迁移演进
- 崩溃日志与稳定性支持能力

## 开发环境

建议使用以下环境：

- Android Studio 最新稳定版
- JDK 17
- Android SDK 36
- 可用的 Gradle 运行环境（推荐直接使用项目自带 Wrapper）

## 本地配置

项目支持从 `gradle.properties` 或 `local.properties` 读取以下配置：

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

说明：

- Mapbox Token 不再参与构建期注入。应用安装后请在“关于”页手动输入并保存到当前设备。
- 若未提供发布签名配置，`release` 构建会回退为 debug 签名，便于本地安装验证。
- `local.properties` 已被忽略，不应提交到仓库。

## 快速开始

### 使用 Android Studio

1. 打开 Android Studio。
2. 选择项目根目录 `TrackRecord`。
3. 等待 Gradle 同步完成。
4. 补充本地配置后运行应用。

### 使用命令行构建

```bash
./gradlew assembleDebug
```

如需运行测试：

```bash
./gradlew test
```

## 相关文档

- [Compose 迁移计划](./docs/compose-migration-plan.md)
- [Mapbox 迁移计划](./docs/mapbox-migration-plan.md)
- [UI 重设计计划](./docs/ui-redesign-plan.md)

## 说明

当前 README 以项目现状说明为主。若后续模块结构或构建配置发生变化，请同步更新本文档。
