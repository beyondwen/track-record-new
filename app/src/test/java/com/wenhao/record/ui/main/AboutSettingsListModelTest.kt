package com.wenhao.record.ui.main

import kotlin.test.Test
import kotlin.test.assertEquals

class AboutSettingsListModelTest {

    @Test
    fun `worker row shows unconfigured status`() {
        val rows = buildAboutSettingsRows(
            AboutUiState(appVersionLabel = "1.0"),
        )

        val workerRow = rows.single { it.id == AboutSettingsRowId.WORKER_CONFIG }
        assertEquals("Worker 配置", workerRow.title)
        assertEquals("配置 URL 和 Token", workerRow.subtitle)
        assertEquals("未连接", workerRow.statusLabel)
    }

    @Test
    fun `worker row shows configured status`() {
        val rows = buildAboutSettingsRows(
            AboutUiState(
                appVersionLabel = "1.0",
                workerBaseUrlInput = "https://example.workers.dev",
                uploadTokenInput = "token",
                hasConfiguredSampleUpload = true,
            ),
        )

        val workerRow = rows.single { it.id == AboutSettingsRowId.WORKER_CONFIG }
        assertEquals("已连接", workerRow.statusLabel)
    }
    @Test
    fun `settings rows include sync diagnostics entry`() {
        val rows = buildAboutSettingsRows(
            AboutUiState(appVersionLabel = "1.0"),
        )

        val diagnosticsRow = rows.single { it.id == AboutSettingsRowId.SYNC_DIAGNOSTICS }
        assertEquals("同步诊断", diagnosticsRow.title)
        assertEquals("查看队列、缓存和上传状态", diagnosticsRow.subtitle)
        assertEquals("可查看", diagnosticsRow.statusLabel)
    }

}
