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


# Superpowers-ZH 中文增强版

本项目已安装 superpowers-zh 技能框架（20 个 skills）。

## 核心规则

1. **收到任务时，先检查是否有匹配的 skill** — 哪怕只有 1% 的可能性也要检查
2. **设计先于编码** — 收到功能需求时，先用 brainstorming skill 做需求分析
3. **测试先于实现** — 写代码前先写测试（TDD）
4. **验证先于完成** — 声称完成前必须运行验证命令

## 可用 Skills

Skills 位于 `.gemini/skills/` 目录，每个 skill 有独立的 `SKILL.md` 文件。

- **brainstorming**: 在任何创造性工作之前必须使用此技能——创建功能、构建组件、添加功能或修改行为。在实现之前先探索用户意图、需求和设计。
- **chinese-code-review**: 中文代码审查规范——在保持专业严谨的同时，用符合国内团队文化的方式给出有效反馈
- **chinese-commit-conventions**: 中文 Git 提交规范 — 适配国内团队的 commit message 规范和 changelog 自动化
- **chinese-documentation**: 中文技术文档写作规范——排版、术语、结构一步到位，告别机翻味
- **chinese-git-workflow**: 适配国内 Git 平台和团队习惯的工作流规范——Gitee、Coding、极狐 GitLab 全覆盖
- **dispatching-parallel-agents**: 当面对 2 个以上可以独立进行、无共享状态或顺序依赖的任务时使用
- **executing-plans**: 当你有一份书面实现计划需要在单独的会话中执行，并设有审查检查点时使用
- **finishing-a-development-branch**: 当实现完成、所有测试通过、需要决定如何集成工作时使用——通过提供合并、PR 或清理等结构化选项来引导开发工作的收尾
- **mcp-builder**: MCP 服务器构建方法论 — 系统化构建生产级 MCP 工具，让 AI 助手连接外部能力
- **receiving-code-review**: 收到代码审查反馈后、实施建议之前使用，尤其当反馈不明确或技术上有疑问时——需要技术严谨性和验证，而非敷衍附和或盲目执行
- **requesting-code-review**: 完成任务、实现重要功能或合并前使用，用于验证工作成果是否符合要求
- **subagent-driven-development**: 当在当前会话中执行包含独立任务的实现计划时使用
- **systematic-debugging**: 遇到任何 bug、测试失败或异常行为时使用，在提出修复方案之前执行
- **test-driven-development**: 在实现任何功能或修复 bug 时使用，在编写实现代码之前
- **using-git-worktrees**: 当需要开始与当前工作区隔离的功能开发或执行实现计划之前使用——创建具有智能目录选择和安全验证的隔离 git 工作树
- **using-superpowers**: 在开始任何对话时使用——确立如何查找和使用技能，要求在任何响应（包括澄清性问题）之前调用 Skill 工具
- **verification-before-completion**: 在宣称工作完成、已修复或测试通过之前使用，在提交或创建 PR 之前——必须运行验证命令并确认输出后才能声称成功；始终用证据支撑断言
- **workflow-runner**: 在 Claude Code / OpenClaw / Cursor 中直接运行 agency-orchestrator YAML 工作流——无需 API key，使用当前会话的 LLM 作为执行引擎。当用户提供 .yaml 工作流文件或要求多角色协作完成任务时触发。
- **writing-plans**: 当你有规格说明或需求用于多步骤任务时使用，在动手写代码之前
- **writing-skills**: 当创建新技能、编辑现有技能或在部署前验证技能是否有效时使用

## 如何使用

当任务匹配某个 skill 时，读取对应的 `.gemini/skills/<skill-name>/SKILL.md` 并严格遵循其流程。
