# GitHub Debug 手动更新功能实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为 TrackRecord 增加一个仅在用户手动点击时触发的 GitHub debug Release 更新能力，并让现有 GitHub Actions 持续发布可供 App 检查与安装的 debug APK。

**架构：** GitHub Actions 在构建 debug APK 后生成 `update.json` 并覆盖固定的 `debug-latest` Release。App 端新增一个 `update` 目录集中处理 Release 查询、元数据解析、版本比较、APK 下载与安装，并在新的关于页提供“检查更新”入口。安装通过 `FileProvider` 和系统安装 Intent 完成，不引入后台轮询或复杂下载管理。

**技术栈：** Android / Kotlin / Jetpack Compose / GitHub Actions / GitHub Releases API / FileProvider

---

## 文件结构

### 将修改的现有文件

- `.github/workflows/android-build.yml` — 在现有 debug 构建流程后追加 `update.json` 生成、固定 Release 更新和附件覆盖逻辑。
- `app/src/main/AndroidManifest.xml` — 注册 APK 安装所需的 `FileProvider`。
- `app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt` — 挂接关于页入口、调度检查更新与安装流程、处理安装未知应用权限引导。
- `app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt` — 在当前 Compose 主界面中增加关于页入口与页面切换。
- `app/build.gradle` — 补充更新功能所需依赖（仅在缺失时添加），并保持现有版本号作为更新判定来源。

### 将新增的文件

- `app/src/main/java/com/wenhao/record/update/AppUpdateInfo.kt` — 定义远端更新元数据与检查结果模型。
- `app/src/main/java/com/wenhao/record/update/GithubReleaseUpdateService.kt` — 请求 GitHub Release API、解析 `update.json`、比较版本并返回检查结果。
- `app/src/main/java/com/wenhao/record/update/ApkDownloadInstaller.kt` — 下载 APK 到缓存目录并生成安装 Intent 所需的 `content://` URI。
- `app/src/main/java/com/wenhao/record/ui/main/AboutUiState.kt` — 定义关于页展示状态。
- `app/src/main/java/com/wenhao/record/ui/main/AboutComposeScreen.kt` — 提供当前版本与“检查更新”按钮 UI。
- `app/src/main/res/xml/update_apk_paths.xml` — 配置 `FileProvider` 可暴露的 APK 文件目录。
- `app/src/test/java/com/wenhao/record/update/GithubReleaseUpdateServiceTest.kt` — 覆盖 `update.json` 解析、版本比较和异常场景。

### 可能需要查阅的文件

- `app/src/main/java/com/wenhao/record/ui/dashboard/DashboardComposeScreen.kt` — 参考当前 Compose 页面风格，确保关于页入口与现有设计一致。
- `app/src/main/res/values/strings.xml` — 如需新增更新相关文案，统一放入字符串资源。

---

### 任务 1：先补 GitHub Release 发布链路

**文件：**
- 修改：`.github/workflows/android-build.yml:1-49`
- 测试：GitHub Actions workflow 语法检查（本地只做静态审阅）

- [ ] **步骤 1：编写 workflow 变更目标清单**

将当前 workflow 末尾补成以下目标：

```yml
      - name: Read app version
        id: app_version
        run: |
          VERSION_CODE=$(grep -oP 'versionCode\s*=\s*\K\d+' app/build.gradle | head -1)
          VERSION_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle | head -1)
          echo "version_code=$VERSION_CODE" >> "$GITHUB_OUTPUT"
          echo "version_name=$VERSION_NAME" >> "$GITHUB_OUTPUT"

      - name: Write update metadata
        run: |
          cat > update.json <<EOF
          {
            "versionCode": ${{ steps.app_version.outputs.version_code }},
            "versionName": "${{ steps.app_version.outputs.version_name }}",
            "apkName": "app-debug.apk"
          }
          EOF
```

- [ ] **步骤 2：追加固定 Release 发布步骤**

把 workflow 继续补成如下结构：

```yml
      - name: Rename debug APK
        run: cp app/build/outputs/apk/debug/app-debug.apk ./app-debug.apk

      - name: Publish debug release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: debug-latest
          name: Debug Latest
          prerelease: true
          make_latest: false
          files: |
            app-debug.apk
            update.json
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```
```

- [ ] **步骤 3：人工检查 YAML 是否满足规格**

检查要点：
- 仍然保留 `assembleDebug`
- 仍然保留 artifact 上传
- 新增 `update.json`
- 使用固定 tag `debug-latest`
- Release 同时上传 `app-debug.apk` 与 `update.json`

预期：文本审查通过，没有遗漏规格里的 Release 结构。

- [ ] **步骤 4：Commit**

```bash
git add .github/workflows/android-build.yml
git commit -m "ci: publish debug release metadata"
```

### 任务 2：先用测试锁定更新检查逻辑

**文件：**
- 创建：`app/src/test/java/com/wenhao/record/update/GithubReleaseUpdateServiceTest.kt`
- 创建：`app/src/main/java/com/wenhao/record/update/AppUpdateInfo.kt`
- 创建：`app/src/main/java/com/wenhao/record/update/GithubReleaseUpdateService.kt`
- 测试：`app/src/test/java/com/wenhao/record/update/GithubReleaseUpdateServiceTest.kt`

- [ ] **步骤 1：编写失败的测试，覆盖有更新场景**

```kotlin
package com.wenhao.record.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GithubReleaseUpdateServiceTest {
    @Test
    fun `returns available update when remote version is newer`() {
        val service = GithubReleaseUpdateService(
            owner = "wenhao",
            repo = "track-record-new",
            releaseJsonFetcher = {
                """
                {
                  "assets": [
                    {"name": "update.json", "browser_download_url": "https://example.com/update.json"},
                    {"name": "app-debug.apk", "browser_download_url": "https://example.com/app-debug.apk"}
                  ]
                }
                """.trimIndent()
            },
            updateJsonFetcher = {
                """
                {
                  "versionCode": 16,
                  "versionName": "1.0.15",
                  "apkName": "app-debug.apk"
                }
                """.trimIndent()
            },
        )

        val result = service.checkForUpdate(currentVersionCode = 15)

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertEquals(16, result.info.versionCode)
        assertEquals("https://example.com/app-debug.apk", result.info.apkUrl)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
./gradlew testDebugUnitTest --tests com.wenhao.record.update.GithubReleaseUpdateServiceTest
```

预期：FAIL，报错 `Unresolved reference: GithubReleaseUpdateService` 或 `UpdateCheckResult` 未定义。

- [ ] **步骤 3：补充无更新和异常场景测试**

把同一测试文件继续补到至少包含以下两个用例：

```kotlin
    @Test
    fun `returns no update when remote version is not newer`() {
        val service = GithubReleaseUpdateService(
            owner = "wenhao",
            repo = "track-record-new",
            releaseJsonFetcher = {
                """
                {
                  "assets": [
                    {"name": "update.json", "browser_download_url": "https://example.com/update.json"},
                    {"name": "app-debug.apk", "browser_download_url": "https://example.com/app-debug.apk"}
                  ]
                }
                """.trimIndent()
            },
            updateJsonFetcher = {
                """
                {
                  "versionCode": 15,
                  "versionName": "1.0.14",
                  "apkName": "app-debug.apk"
                }
                """.trimIndent()
            },
        )

        val result = service.checkForUpdate(currentVersionCode = 15)

        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun `returns failure when update asset is missing`() {
        val service = GithubReleaseUpdateService(
            owner = "wenhao",
            repo = "track-record-new",
            releaseJsonFetcher = { "{"assets": []}" },
            updateJsonFetcher = { error("should not fetch metadata") },
        )

        val result = service.checkForUpdate(currentVersionCode = 15)

        assertTrue(result is UpdateCheckResult.Failure)
        assertEquals("更新信息不完整", result.message)
    }
```

- [ ] **步骤 4：实现最少模型代码让测试可编译**

先创建 `AppUpdateInfo.kt`：

```kotlin
package com.wenhao.record.update

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkName: String,
    val apkUrl: String,
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failure(val message: String) : UpdateCheckResult
}
```

- [ ] **步骤 5：实现最少服务代码让测试通过**

创建 `GithubReleaseUpdateService.kt`，先按下面实现：

```kotlin
package com.wenhao.record.update

import org.json.JSONObject

class GithubReleaseUpdateService(
    private val owner: String,
    private val repo: String,
    private val releaseJsonFetcher: () -> String,
    private val updateJsonFetcher: (String) -> String,
) {
    fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult {
        val releaseJson = JSONObject(releaseJsonFetcher())
        val assets = releaseJson.optJSONArray("assets") ?: return UpdateCheckResult.Failure("更新信息不完整")

        var updateJsonUrl: String? = null
        val apkUrlsByName = linkedMapOf<String, String>()
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val downloadUrl = asset.optString("browser_download_url")
            if (name == "update.json") updateJsonUrl = downloadUrl
            if (name.isNotBlank() && downloadUrl.isNotBlank()) apkUrlsByName[name] = downloadUrl
        }

        val metadataUrl = updateJsonUrl ?: return UpdateCheckResult.Failure("更新信息不完整")
        val metadataJson = JSONObject(updateJsonFetcher(metadataUrl))
        val versionCode = metadataJson.optInt("versionCode", -1)
        val versionName = metadataJson.optString("versionName")
        val apkName = metadataJson.optString("apkName")
        val apkUrl = apkUrlsByName[apkName]

        if (versionCode < 0 || versionName.isBlank() || apkName.isBlank() || apkUrl.isNullOrBlank()) {
            return UpdateCheckResult.Failure("更新信息异常")
        }

        return if (versionCode > currentVersionCode) {
            UpdateCheckResult.UpdateAvailable(
                AppUpdateInfo(
                    versionCode = versionCode,
                    versionName = versionName,
                    apkName = apkName,
                    apkUrl = apkUrl,
                )
            )
        } else {
            UpdateCheckResult.UpToDate
        }
    }
}
```

- [ ] **步骤 6：运行测试验证通过**

运行：
```bash
./gradlew testDebugUnitTest --tests com.wenhao.record.update.GithubReleaseUpdateServiceTest
```

预期：PASS，三个用例全部通过。

- [ ] **步骤 7：Commit**

```bash
git add app/src/main/java/com/wenhao/record/update/AppUpdateInfo.kt \
        app/src/main/java/com/wenhao/record/update/GithubReleaseUpdateService.kt \
        app/src/test/java/com/wenhao/record/update/GithubReleaseUpdateServiceTest.kt
git commit -m "feat: add github release update checker"
```

### 任务 3：接入真实网络请求与 APK 下载安装

**文件：**
- 修改：`app/build.gradle:131-163`
- 创建：`app/src/main/java/com/wenhao/record/update/ApkDownloadInstaller.kt`
- 修改：`app/src/main/java/com/wenhao/record/update/GithubReleaseUpdateService.kt`
- 测试：`app/src/test/java/com/wenhao/record/update/GithubReleaseUpdateServiceTest.kt`

- [ ] **步骤 1：先写失败的下载器测试接口约束（如项目测试环境允许）**

如果当前项目已有可方便 mock 输入输出的 JVM 单测环境，则先为下载器写一个最小接口约束：

```kotlin
interface ApkDownloader {
    fun download(apkUrl: String, fileName: String): File
}
```

如果当前项目没有现成的下载类测试模式，则跳过单测，直接实现最小生产代码，不再额外引入测试框架。

- [ ] **步骤 2：补充所需依赖（仅缺什么补什么）**

在 `app/build.gradle` 的 `dependencies` 中补充：

```groovy
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2'
```

如果已经存在这行，则保持不变，不重复添加。

- [ ] **步骤 3：把更新服务改为支持真实 HTTP 请求**

将 `GithubReleaseUpdateService` 调整为以下结构：

```kotlin
class GithubReleaseUpdateService(
    private val owner: String,
    private val repo: String,
    private val releaseJsonFetcher: (() -> String)? = null,
    private val updateJsonFetcher: ((String) -> String)? = null,
) {
    fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult {
        return try {
            val releaseJsonText = releaseJsonFetcher?.invoke()
                ?: httpGet("https://api.github.com/repos/$owner/$repo/releases/tags/debug-latest")
            val releaseJson = JSONObject(releaseJsonText)
            ...
            val metadataText = updateJsonFetcher?.invoke(metadataUrl) ?: httpGet(metadataUrl)
            ...
        } catch (_: Exception) {
            UpdateCheckResult.Failure("检查更新失败")
        }
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}
```

要求：
- 保持已有测试注入入口不变
- 真实运行时默认请求 GitHub Release API
- 异常统一返回 `UpdateCheckResult.Failure("检查更新失败")`

- [ ] **步骤 4：实现 APK 下载与安装工具**

创建 `ApkDownloadInstaller.kt`：

```kotlin
package com.wenhao.record.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ApkDownloadInstaller(
    private val context: Context,
) {
    fun download(apkUrl: String, fileName: String = "app-debug.apk"): File {
        val targetFile = File(context.cacheDir, fileName)
        val connection = URL(apkUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return targetFile
    }

    fun createInstallIntent(apkFile: File): Intent {
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
```

- [ ] **步骤 5：运行更新检查相关单测**

运行：
```bash
./gradlew testDebugUnitTest --tests com.wenhao.record.update.GithubReleaseUpdateServiceTest
```

预期：PASS。

- [ ] **步骤 6：Commit**

```bash
git add app/build.gradle \
        app/src/main/java/com/wenhao/record/update/GithubReleaseUpdateService.kt \
        app/src/main/java/com/wenhao/record/update/ApkDownloadInstaller.kt
git commit -m "feat: add apk download and github update fetch"
```

### 任务 4：增加关于页与“检查更新”入口

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/ui/main/AboutUiState.kt`
- 创建：`app/src/main/java/com/wenhao/record/ui/main/AboutComposeScreen.kt`
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt:86-292`
- 测试：手动 UI 验证

- [ ] **步骤 1：定义关于页状态模型**

创建 `AboutUiState.kt`：

```kotlin
package com.wenhao.record.ui.main

data class AboutUiState(
    val appVersionLabel: String,
    val isCheckingUpdate: Boolean = false,
    val statusMessage: String? = null,
)
```

- [ ] **步骤 2：实现最小关于页 Compose 界面**

创建 `AboutComposeScreen.kt`：

```kotlin
package com.wenhao.record.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wenhao.record.ui.designsystem.TrackPrimaryButton

@Composable
fun AboutComposeScreen(
    state: AboutUiState,
    onBackClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "关于", style = MaterialTheme.typography.headlineMedium)
        Text(text = state.appVersionLabel)
        TrackPrimaryButton(
            text = if (state.isCheckingUpdate) "检查中..." else "检查更新",
            onClick = onCheckUpdateClick,
            enabled = !state.isCheckingUpdate,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isCheckingUpdate) {
            CircularProgressIndicator()
        }
        state.statusMessage?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
        TrackPrimaryButton(
            text = "返回",
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

- [ ] **步骤 3：把 About 页接入 `MainComposeScreen`**

将 `MainTab` 扩展为：

```kotlin
enum class MainTab {
    RECORD,
    HISTORY,
    BAROMETER,
    ABOUT,
}
```

并把 `MainComposeScreen` 签名补成：

```kotlin
fun MainComposeScreen(
    currentTab: MainTab,
    dashboardState: DashboardScreenUiState,
    dashboardOverlayState: DashboardOverlayUiState,
    historyState: HistoryScreenUiState,
    barometerState: BarometerUiState,
    aboutState: AboutUiState,
    dashboardMapState: TrackMapSceneState,
    onRecordTabClick: () -> Unit,
    onHistoryTabClick: () -> Unit,
    onBarometerTabClick: () -> Unit,
    onAboutTabClick: () -> Unit,
    onAboutBackClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    ...
)
```

并新增一个 `when (currentTab)` 分支：

```kotlin
            MainTab.ABOUT -> {
                AboutComposeScreen(
                    state = aboutState,
                    onBackClick = onAboutBackClick,
                    onCheckUpdateClick = onCheckUpdateClick,
                )
            }
```

- [ ] **步骤 4：给现有界面增加进入 About 页的入口**

在当前最小改动前提下，优先在 `DashboardRoot` 顶部区域增加一个轻量入口按钮；如果复用现有顶部区域不方便，则在 `DashboardComposeScreen` 可进入的明显位置加一个“关于”文本按钮。

代码要求：
- 不新增复杂导航框架
- 仅通过现有 `currentTab` 切换页面
- RECORD / HISTORY / BAROMETER 三个主功能保持可返回

- [ ] **步骤 5：手动检查编译期调用点**

检查 `MainActivity` 中 `MainComposeScreen(...)` 的所有新参数都已准备好，否则下一任务实现时会编译失败。

预期：当前阶段可能尚未完全可编译，但必须明确下一任务要补哪些参数。

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/java/com/wenhao/record/ui/main/AboutUiState.kt \
        app/src/main/java/com/wenhao/record/ui/main/AboutComposeScreen.kt \
        app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt
git commit -m "feat: add about screen update entry"
```

### 任务 5：在 Activity 中接通检查更新与安装流程

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt:1-250`
- 修改：`app/src/main/java/com/wenhao/record/RecordApplication.kt:1-16`（仅在需要全局单例时）
- 测试：手动功能验证

- [ ] **步骤 1：先定义 Activity 内所需状态**

在 `MainActivity` 中新增以下属性：

```kotlin
    private val updateService by lazy {
        GithubReleaseUpdateService(
            owner = "wenhao",
            repo = "track-record-new",
        )
    }
    private val apkDownloadInstaller by lazy { ApkDownloadInstaller(this) }
    private var aboutState by mutableStateOf(
        AboutUiState(appVersionLabel = buildVersionLabel())
    )
```

并新增辅助方法：

```kotlin
    private fun buildVersionLabel(): String {
        return "当前版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }
```

- [ ] **步骤 2：把 About 页事件接到 `MainComposeScreen`**

将 `setContent { MainComposeScreen(...) }` 调整为包含：

```kotlin
                    aboutState = aboutState,
                    onAboutTabClick = { showTab(MainTab.ABOUT) },
                    onAboutBackClick = { showTab(MainTab.RECORD) },
                    onCheckUpdateClick = ::checkForAppUpdate,
```

并在 RECORD 页面能触发 `onAboutTabClick`。

- [ ] **步骤 3：实现手动检查更新方法**

在 `MainActivity` 中新增：

```kotlin
    private fun checkForAppUpdate() {
        aboutState = aboutState.copy(isCheckingUpdate = true, statusMessage = null)
        lifecycleScope.launch(Dispatchers.IO) {
            val result = updateService.checkForUpdate(currentVersionCode = BuildConfig.VERSION_CODE)
            launch(Dispatchers.Main) {
                aboutState = aboutState.copy(isCheckingUpdate = false)
                when (result) {
                    is UpdateCheckResult.UpToDate -> {
                        aboutState = aboutState.copy(statusMessage = "当前已是最新版本")
                    }
                    is UpdateCheckResult.Failure -> {
                        aboutState = aboutState.copy(statusMessage = result.message)
                    }
                    is UpdateCheckResult.UpdateAvailable -> {
                        aboutState = aboutState.copy(
                            statusMessage = "发现新版本：${result.info.versionName}"
                        )
                        showUpdateConfirmDialog(result.info)
                    }
                }
            }
        }
    }
```

- [ ] **步骤 4：实现确认下载与安装逻辑**

新增：

```kotlin
    private fun showUpdateConfirmDialog(info: AppUpdateInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle("发现新版本")
            .setMessage("检测到新版本 ${info.versionName}，是否下载并安装？")
            .setNegativeButton("取消", null)
            .setPositiveButton("更新") { _, _
                downloadAndInstallUpdate(info)
            }
            .show()
    }

    private fun downloadAndInstallUpdate(info: AppUpdateInfo) {
        aboutState = aboutState.copy(statusMessage = "正在下载更新…")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apkFile = apkDownloadInstaller.download(info.apkUrl, info.apkName)
                val intent = apkDownloadInstaller.createInstallIntent(apkFile)
                launch(Dispatchers.Main) {
                    aboutState = aboutState.copy(statusMessage = "下载完成，正在打开安装器")
                    startActivity(intent)
                }
            } catch (_: Exception) {
                launch(Dispatchers.Main) {
                    aboutState = aboutState.copy(statusMessage = "下载失败")
                }
            }
        }
    }
```

- [ ] **步骤 5：处理安装未知应用权限引导**

在 `downloadAndInstallUpdate` 的安装前加上检查：

```kotlin
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
    !packageManager.canRequestPackageInstalls()
) {
    aboutState = aboutState.copy(statusMessage = "请先允许安装未知应用")
    val intent = Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:$packageName")
    )
    startActivity(intent)
    return@launch
}
```

- [ ] **步骤 6：运行手动验证**

验证项：
- 能进入 About 页
- 点击“检查更新”时按钮进入 checking 状态
- 无更新时显示“当前已是最新版本”
- 有更新时能弹出确认框
- 下载完成后能拉起安装器

预期：上述流程在真机或可安装环境下成立。

- [ ] **步骤 7：Commit**

```bash
git add app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt \
        app/src/main/java/com/wenhao/record/RecordApplication.kt
git commit -m "feat: wire manual app update flow"
```

### 任务 6：补齐 FileProvider 配置并完成最终验证

**文件：**
- 修改：`app/src/main/AndroidManifest.xml:17-56`
- 创建：`app/src/main/res/xml/update_apk_paths.xml`
- 测试：`./gradlew testDebugUnitTest`、`./gradlew assembleDebug`

- [ ] **步骤 1：先写 Manifest 目标配置**

在 `<application>` 中加入：

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/update_apk_paths" />
        </provider>
```

- [ ] **步骤 2：新增 provider 路径文件**

创建 `app/src/main/res/xml/update_apk_paths.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path
        name="apk_cache"
        path="." />
</paths>
```

- [ ] **步骤 3：运行更新模块单测**

运行：
```bash
./gradlew testDebugUnitTest --tests com.wenhao.record.update.GithubReleaseUpdateServiceTest
```

预期：PASS。

- [ ] **步骤 4：运行完整单测**

运行：
```bash
./gradlew testDebugUnitTest
```

预期：PASS。

- [ ] **步骤 5：运行 Debug 构建验证**

运行：
```bash
./gradlew assembleDebug
```

预期：PASS，生成 debug APK。

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/AndroidManifest.xml \
        app/src/main/res/xml/update_apk_paths.xml
git commit -m "feat: enable apk installation for app update"
```

## 规格覆盖自检

- 固定 Release：任务 1 覆盖 `debug-latest`、`app-debug.apk`、`update.json`
- 手动检查入口：任务 4、任务 5 覆盖 About 页与“检查更新”按钮
- 版本比较：任务 2 覆盖 `versionCode` 判定
- 下载与安装：任务 3、任务 5、任务 6 覆盖 APK 下载、安装 Intent 与 `FileProvider`
- 无更新 / 有更新 / 失败反馈：任务 2、任务 5 覆盖
- 最终验证：任务 6 覆盖 `testDebugUnitTest` 与 `assembleDebug`

已检查：
- 无 `TODO`、`待定`、`后续实现` 占位符
- 类型名在计划中保持一致：`AppUpdateInfo`、`UpdateCheckResult`、`GithubReleaseUpdateService`
- 任务范围聚焦于一个可独立交付的手动更新能力，没有额外扩展自动更新或多渠道能力
