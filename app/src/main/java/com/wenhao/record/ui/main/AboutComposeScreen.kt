package com.wenhao.record.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
    onMapboxTokenChange: (String) -> Unit,
    onMapboxTokenSaveClick: () -> Unit,
    onMapboxTokenClearClick: () -> Unit,
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
