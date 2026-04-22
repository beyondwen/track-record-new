package com.wenhao.record.ui.main

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wenhao.record.ui.designsystem.TrackBottomNavigationBar
import com.wenhao.record.ui.designsystem.TrackBottomTab
import com.wenhao.record.ui.designsystem.TrackLiquidPanel
import com.wenhao.record.ui.designsystem.TrackLiquidTone
import com.wenhao.record.ui.designsystem.TrackPrimaryButton
import com.wenhao.record.ui.designsystem.TrackStatChip

@Composable
fun AboutComposeScreen(
    state: AboutUiState,
    onRecordClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    onMapboxTokenChange: (String) -> Unit,
    onMapboxTokenSaveClick: () -> Unit,
    onMapboxTokenClearClick: () -> Unit,
    onWorkerBaseUrlChange: (String) -> Unit,
    onUploadTokenChange: (String) -> Unit,
    onSampleUploadConfigSaveClick: () -> Unit,
    onSampleUploadConfigClearClick: () -> Unit,
    onWorkerConnectivityTestClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .padding(bottom = 118.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsHeroSection(state = state)
            AboutSection(title = "地图配置") {
                OutlinedTextField(
                    value = state.mapboxTokenInput,
                    onValueChange = onMapboxTokenChange,
                    label = { Text("Mapbox Token") },
                    placeholder = { Text("请输入你自己的 Mapbox Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (state.hasConfiguredMapboxToken) {
                        "已保存到当前设备，后续构建不会再把 Mapbox Token 打进 APK。"
                    } else {
                        "当前设备未配置 Mapbox Token，地图页会保持禁用。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TrackPrimaryButton(
                    text = "保存 Mapbox Token",
                    onClick = onMapboxTokenSaveClick,
                    enabled = state.mapboxTokenInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TrackPrimaryButton(
                    text = "清空 Mapbox Token",
                    onClick = onMapboxTokenClearClick,
                    enabled = state.hasConfiguredMapboxToken || state.mapboxTokenInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AboutSection(title = "Worker 上传") {
                OutlinedTextField(
                    value = state.workerBaseUrlInput,
                    onValueChange = onWorkerBaseUrlChange,
                    label = { Text("Worker 地址") },
                    placeholder = { Text("例如：https://your-worker.workers.dev") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.uploadTokenInput,
                    onValueChange = onUploadTokenChange,
                    label = { Text("上传 Token") },
                    placeholder = { Text("请输入上传鉴权 Token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (state.hasConfiguredSampleUpload) {
                        "点位、分析和历史同步都使用这组 Worker 配置。"
                    } else {
                        "当前设备未配置 Worker 上传信息。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TrackPrimaryButton(
                    text = "保存上传配置",
                    onClick = onSampleUploadConfigSaveClick,
                    enabled = state.workerBaseUrlInput.isNotBlank() &&
                        state.uploadTokenInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TrackPrimaryButton(
                    text = "清空上传配置",
                    onClick = onSampleUploadConfigClearClick,
                    enabled = !state.isTestingWorkerConnectivity &&
                        (
                            state.hasConfiguredSampleUpload ||
                                state.workerBaseUrlInput.isNotBlank() ||
                                state.uploadTokenInput.isNotBlank()
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                TrackPrimaryButton(
                    text = if (state.isTestingWorkerConnectivity) "测试中..." else "测试 Worker",
                    onClick = onWorkerConnectivityTestClick,
                    enabled = !state.isCheckingUpdate &&
                        !state.isTestingWorkerConnectivity &&
                        state.workerBaseUrlInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AboutSection(title = "应用") {
                TrackPrimaryButton(
                    text = if (state.isCheckingUpdate) "检查中..." else "检查更新",
                    onClick = onCheckUpdateClick,
                    enabled = !state.isCheckingUpdate &&
                        !state.isTestingWorkerConnectivity,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.isCheckingUpdate || state.isTestingWorkerConnectivity) {
                CircularProgressIndicator()
            }
            state.statusMessage?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        TrackBottomNavigationBar(
            selectedTab = TrackBottomTab.SETTINGS,
            onRecordClick = onRecordClick,
            onHistoryClick = onHistoryClick,
            onSettingsClick = onSettingsClick,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            recordLabel = "记录",
            historyLabel = "历史",
            settingsLabel = "设置",
            settingsEnabled = false,
        )
    }
}

@Composable
private fun AboutSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    TrackLiquidPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tone = TrackLiquidTone.SUBTLE,
        shadowElevation = 10.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                content()
            },
        )
    }
}

@Composable
private fun SettingsHeroSection(
    state: AboutUiState,
) {
    TrackLiquidPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tone = TrackLiquidTone.STRONG,
        shadowElevation = 14.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 18.dp,
            vertical = 18.dp,
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TrackStatChip(text = "设置")
            Text(
                text = "同步与地图",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.appVersionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
