package com.wenhao.record.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GithubReleaseUpdateService(
    private val owner: String,
    private val repo: String,
    private val authToken: String = "",
    private val releaseJsonFetcher: (() -> String)? = null,
    private val updateJsonFetcher: ((String) -> String)? = null,
) {
    fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult {
        return try {
            val releaseJsonText = releaseJsonFetcher?.invoke()
                ?: httpGet("https://api.github.com/repos/$owner/$repo/releases/tags/debug-latest")
            val releaseJson = JSONObject(releaseJsonText)
            val assets = releaseJson.optJSONArray("assets")
                ?: return UpdateCheckResult.Failure("更新信息不完整")

            var updateJsonUrl: String? = null
            val apkUrlsByName = linkedMapOf<String, String>()
            val assetUrlsByName = linkedMapOf<String, String>()
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val downloadUrl = asset.optString("browser_download_url")
                val assetUrl = asset.optString("url")
                if (name == "update.json") {
                    updateJsonUrl = resolveAssetUrl(
                        assetUrl = assetUrl,
                        browserDownloadUrl = downloadUrl,
                    )
                }
                if (name.isNotBlank() && downloadUrl.isNotBlank()) {
                    apkUrlsByName[name] = downloadUrl
                }
                if (name.isNotBlank() && assetUrl.isNotBlank()) {
                    assetUrlsByName[name] = assetUrl
                }
            }

            val metadataUrl = updateJsonUrl ?: return UpdateCheckResult.Failure("更新信息不完整")
            val metadataText = updateJsonFetcher?.invoke(metadataUrl)
                ?: httpGet(metadataUrl, downloadAsset = authToken.isNotBlank())
            val metadataJson = JSONObject(metadataText)
            val versionCode = metadataJson.optInt("versionCode", -1)
            val versionName = metadataJson.optString("versionName")
            val apkName = metadataJson.optString("apkName")
            val apkUrl = resolveAssetUrl(
                assetUrl = assetUrlsByName[apkName],
                browserDownloadUrl = apkUrlsByName[apkName],
            )

            if (versionCode < 0 || versionName.isBlank() || apkName.isBlank() || apkUrl.isNullOrBlank()) {
                return UpdateCheckResult.Failure("更新信息异常")
            }

            if (versionCode > currentVersionCode) {
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
        } catch (_: Exception) {
            UpdateCheckResult.Failure("检查更新失败")
        }
    }

    private fun resolveAssetUrl(
        assetUrl: String?,
        browserDownloadUrl: String?,
    ): String? {
        return if (authToken.isNotBlank()) {
            assetUrl?.takeIf { it.isNotBlank() } ?: browserDownloadUrl
        } else {
            browserDownloadUrl?.takeIf { it.isNotBlank() } ?: assetUrl
        }
    }

    private fun httpGet(url: String, downloadAsset: Boolean = false): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty(
            "Accept",
            if (downloadAsset) "application/octet-stream" else "application/vnd.github+json"
        )
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (authToken.isNotBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $authToken")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}
