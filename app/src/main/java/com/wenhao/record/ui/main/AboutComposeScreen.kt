package com.wenhao.record.ui.main

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wenhao.record.ui.designsystem.TrackPrimaryButton

@Composable
fun AboutComposeScreen(
    state: AboutUiState,
    onBackClick: () -> Unit,
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(text = "关于", style = MaterialTheme.typography.headlineMedium)
        Text(text = state.appVersionLabel)
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
        TrackPrimaryButton(
            text = "返回",
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AboutSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                content()
            },
        )
    }
}
