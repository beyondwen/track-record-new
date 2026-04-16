# TrackRecord 项目 AI 助手规范 (GEMINI.md)

作为本项目的 AI 助手，请在所有的交互、代码生成和分析中严格遵守以下规范：

## 1. 技术栈与语言要求
*   **首选语言**：始终使用 **Kotlin**。禁止使用 Java，除非是与旧系统集成或处理极其底层的遗留 API。
*   **UI 框架**：优先使用 **Jetpack Compose**。目前项目正在进行 Compose 迁移（参考 `docs/compose-migration-plan.md`），新功能或重构应默认使用 Compose 构建。
*   **地图与定位**：项目深度依赖位置服务和地图功能。目前正在向 Mapbox 迁移（参考 `docs/mapbox-migration-plan.md`）。在涉及地图功能的代码时，请先确认当前的迁移状态。
*   **本地存储**：使用 Room 数据库（参考 `app/schemas` 目录）。

## 2. 架构与目录约定
*   **分层清晰**：遵守现有的包结构约定（`data`, `map`, `permissions`, `tracking`, `ui`, `util`）。
*   **服务与后台任务**：追踪功能依赖于后台服务（如 `BackgroundTrackingService`），修改此类核心逻辑时必须考虑电池优化、Android 后台限制和权限状态。
*   **资源管理**：
    *   绝不在代码中硬编码字符串，必须提取到 `res/values/strings*.xml` 中（尤其是针对 Compose 的 `strings_compose_*.xml`）。
    *   图标和背景等资源应优先使用 `res/drawable` 下的 XML 矢量图。

## 3. 编码风格与质量
*   **类型安全**：充分利用 Kotlin 的空安全（Null Safety）和强类型特性。避免使用 `!!`（非空断言），推荐使用安全调用 `?.` 或 `let`。
*   **异步编程**：统一使用 Kotlin Coroutines（协程）和 Flow 处理异步数据流，不要混用其他异步回调方案。
*   **测试驱动**：在修改核心逻辑（如 `tracking`, `data` 包下的代码）后，务必检查或添加相应的单元测试（位于 `app/src/test` 或 `androidTest`）。

## 4. 特殊业务逻辑提示
*   在处理轨迹、坐标等敏感数据时，注意数据的清洗和隐私保护（如 `TrackPathSanitizer`, `TrackingTextSanitizer`）。
*   此项目包含稳定性和崩溃日志收集机制（`CrashLogStore`），处理异常时请考虑是否需要记录到本地。

> **注意**：在执行任何涉及重大重构、架构变更或跨越多个文件的复杂任务前，请先查阅 `docs/` 目录下的相关设计文档。
