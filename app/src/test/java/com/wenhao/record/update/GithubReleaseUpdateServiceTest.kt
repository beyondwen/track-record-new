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
            releaseJsonFetcher = { "{\"assets\": []}" },
            updateJsonFetcher = { error("should not fetch metadata") },
        )

        val result = service.checkForUpdate(currentVersionCode = 15)

        assertTrue(result is UpdateCheckResult.Failure)
        assertEquals("更新信息不完整", result.message)
    }

    @Test
    fun `uses public browser download urls when asset api url is also present`() {
        val service = GithubReleaseUpdateService(
            owner = "wenhao",
            repo = "track-record-new",
            releaseJsonFetcher = {
                """
                {
                  "assets": [
                    {"name": "update.json", "url": "https://api.github.com/assets/update", "browser_download_url": "https://example.com/update.json"},
                    {"name": "app-debug.apk", "url": "https://api.github.com/assets/apk", "browser_download_url": "https://example.com/app-debug.apk"}
                  ]
                }
                """.trimIndent()
            },
            updateJsonFetcher = { url ->
                assertEquals("https://example.com/update.json", url)
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
        assertEquals("https://example.com/app-debug.apk", result.info.apkUrl)
    }
}
