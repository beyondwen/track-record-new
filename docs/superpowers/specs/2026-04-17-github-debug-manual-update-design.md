# 2026-04-17 GitHub Debug Release 手动更新设计

## 背景

当前项目已经具备 GitHub Actions 打包 debug APK 的基础能力。现在需要在 App 内增加一个**仅供单人自用**的更新能力：当 GitHub 上存在新的 debug 版本时，用户可以在 App 内**手动点击“检查更新”**，发现新版本后下载并安装。

该能力目标是最小可用，不追求面向公开分发场景的完备升级系统。

## 目标

- 在 App 中提供一个明确的“检查更新”入口。
- 仅在用户手动触发时检查 GitHub 上的最新 debug 版本。
- 当远端版本高于本地版本时，允许用户下载并安装新的 debug APK。
- 保持实现简单，适用于单人自用场景。

## 非目标

本次设计**不包含**以下能力：

- 自动后台轮询更新
- 静默安装
- 增量更新 / 差分包
- 多渠道发布管理
- 面向大量用户的升级策略、灰度策略或强更策略
- 复杂的下载管理（断点续传、后台常驻、多任务队列）

## 方案选择

围绕“GitHub 有新的 debug 版本后，App 可更新”这个需求，评估过以下三种路线：

### 方案 A：App 直接读取 GitHub Actions artifact

不采用。

原因：GitHub Actions artifact 下载通常依赖鉴权，天然更适合 CI 内部使用，不适合作为 App 端直接拉取的稳定更新源。

### 方案 B：GitHub Release 承载 debug APK，App 检查固定 Release

采用。

原因：

- Release 对 App 端更友好，可通过公开 API 获取元数据。
- GitHub Actions 可以直接更新 Release 附件。
- 对当前单人自用场景，维护成本最低。

### 方案 C：自建更新接口或静态元数据服务

不采用。

原因：需要额外维护服务或发布元数据站点，超出当前需求范围。

## 最终方案

采用 **“固定 GitHub debug release + App 手动检查更新”** 方案：

1. GitHub Actions 构建 debug APK。
2. 每次构建后更新同一个固定 Release。
3. Release 中包含：
   - `app-debug.apk`
   - `update.json`
4. App 在“关于 / 设置”页提供“检查更新”按钮。
5. 用户点击后，请求 GitHub Release API，读取 `update.json`。
6. 若远端 `versionCode` 大于本地版本，则提示用户下载并安装对应 APK。

## Release 设计

### 固定 Release 约定

固定使用一个长期存在的 debug Release：

- Tag：`debug-latest`
- Release Name：`Debug Latest`

该 Release 每次被新的 debug 构建覆盖更新，而不是每次都新建独立历史 Release。

这样可以保证 App 的更新检查逻辑稳定，只需要查询一个固定目标。

### Release 附件

每次更新 Release 时应包含两个核心附件：

1. `app-debug.apk`
2. `update.json`

其中 `app-debug.apk` 是供下载安装的目标文件；`update.json` 提供版本元数据，避免 App 通过文件名猜测版本。

### `update.json` 结构

`update.json` 采用最小必要结构：

```json
{
  "versionCode": 15,
  "versionName": "1.0.14",
  "apkName": "app-debug.apk"
}
```

字段定义：

- `versionCode`：远端 APK 的整型版本号，用于和本地版本比较
- `versionName`：用于界面展示
- `apkName`：APK 资源名，用于在 Release 附件列表中定位 APK 文件

如后续需要增加发布说明，可再补充字段，但本次不预留额外复杂结构。

## App 端交互设计

### 入口位置

在 App 内增加一个简单的“关于 / 设置”入口，并将“检查更新”按钮放在该页面中。

这样可以避免把维护型功能直接放入主业务界面，同时保持入口明确可见。

### 页面最小内容

“关于 / 设置”页本次只包含最小必要信息：

- 当前版本号
- “检查更新”按钮
- 检查中的状态反馈

不在本次范围内加入额外设置项。

### 检查更新流程

用户点击“检查更新”后：

1. 页面进入 checking 状态。
2. App 请求固定 Release 的 GitHub API。
3. 解析 Release 附件列表，找到 `update.json` 和 `app-debug.apk`。
4. 下载并解析 `update.json`。
5. 将远端 `versionCode` 与本地 `versionCode` 比较。
6. 根据结果给出反馈：
   - 若远端版本不高于本地：提示“当前已是最新版本”。
   - 若远端版本更高：弹出确认对话框，显示版本信息并询问是否下载安装。

### 下载与安装流程

当用户确认更新后：

1. App 下载 `app-debug.apk` 到本地缓存目录或应用私有文件目录。
2. 下载完成后，通过 `FileProvider` 暴露安装 URI。
3. 使用系统安装 Intent 拉起系统安装器。
4. 用户在系统安装界面完成覆盖安装。

### 失败场景反馈

需要覆盖以下最小失败反馈：

- 网络或 GitHub API 请求失败：提示“检查更新失败”
- Release 中缺少 `update.json` 或 `app-debug.apk`：提示“更新信息不完整”
- `update.json` 解析失败：提示“更新信息异常”
- APK 下载失败：提示“下载失败”
- 未允许安装未知应用：提示需要前往系统设置授权

本次只要求有明确反馈，不要求复杂错误分级体系。

## 版本比较策略

### 版本来源

当前版本来源以 Gradle 配置为准：

- `app/build.gradle` 中的 `versionCode`
- `app/build.gradle` 中的 `versionName`

当前已知值为：

- `versionCode = 15`
- `versionName = "1.0.14"`

### 比较规则

仅使用 `versionCode` 作为更新判定依据：

- 远端 `versionCode` > 本地 `versionCode`：可更新
- 否则视为无更新

`versionName` 只用于展示，不参与更新判定。

## 代码结构设计

### App 端模块划分

建议新增 `update` 目录，集中承载更新逻辑，例如：

- `app/src/main/java/com/wenhao/record/update/`

该目录内的职责应包括：

- GitHub Release API 请求
- `update.json` 解析
- 版本比较
- APK 下载
- 安装 Intent 触发

这样可以将更新逻辑与现有轨迹记录、历史记录、地图等业务模块隔离。

### UI 结构

在现有 Compose UI 中增加简单的“关于 / 设置”页面及对应入口。

页面状态建议只包含：

- idle
- checking
- update available
- no update
- error

不引入复杂状态机或事件总线。

### Android 配置改动

为支持安装下载后的 APK，需要增加：

1. `FileProvider` Manifest 声明
2. `res/xml/` 下的 provider 路径配置

这部分仅服务于本地 APK 安装，不扩展到其他文件分享场景。

## GitHub Actions 设计

在现有 `.github/workflows/android-build.yml` 基础上扩展：

1. 继续构建 `assembleDebug`
2. 保留现有 artifact 上传，便于在 Actions 页面下载
3. 构建后生成 `update.json`
4. 更新固定 Release：`debug-latest`
5. 覆盖旧附件，确保 Release 中始终只保留当前最新的一份 debug APK 和对应元数据

这样既保留了 CI artifact，又让 App 端有稳定更新源。

## 测试设计

本次实现后至少验证以下场景：

### 1. 无更新场景

- 本地版本与 Release 中版本一致
- 点击“检查更新”后提示“当前已是最新版本”

### 2. 有更新场景

- 提高 `versionCode`
- 通过 GitHub Actions 发布新的 debug Release
- App 可检测到新版本并弹出更新确认

### 3. 下载与安装场景

- APK 可成功下载到本地
- 可成功拉起系统安装器

### 4. 异常场景

- GitHub API 请求失败
- `update.json` 缺失或格式错误
- APK 下载失败

## 风险与约束

### GitHub API 限流

存在 GitHub API 访问频率限制，但当前方案为单人使用且仅手动触发，风险可接受。

### Debug 包覆盖安装约束

只有当新旧 APK 签名一致时，系统才允许覆盖安装。因此 GitHub Actions 产出的 debug 包必须持续保持同一签名链路。

### 未知来源安装授权

Android 8+ 设备上，系统可能要求用户手动授予“安装未知应用”权限。本次实现仅负责检测并提示，不负责绕过该限制。

### 固定 Release 的覆盖一致性

GitHub Actions 必须保证固定 Release 在每次构建后被正确覆盖，避免旧附件残留，防止 App 读取到不匹配的 `update.json` 与 APK。

## 成功标准

满足以下条件即视为完成：

- App 中存在可见的“检查更新”入口
- 用户手动点击后可请求 GitHub Release 并判断是否有新版本
- 有新版本时可下载并拉起 APK 安装
- 无新版本和失败场景都有清晰反馈
- GitHub Actions 能持续更新固定 debug Release 及其元数据
