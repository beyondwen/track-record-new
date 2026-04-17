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
