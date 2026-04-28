package com.wenhao.record.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wenhao.record.R
import com.wenhao.record.ui.designsystem.TrackInsetPanel
import com.wenhao.record.ui.designsystem.TrackLiquidPanel
import com.wenhao.record.ui.designsystem.TrackLiquidTone
import com.wenhao.record.ui.designsystem.TrackPrimaryButton
import com.wenhao.record.ui.designsystem.TrackSecondaryButton
import com.wenhao.record.ui.designsystem.TrackStatChip

@Composable
fun AboutComposeScreen(
    state: AboutUiState,
    onCheckUpdateClick: () -> Unit,
    onMapboxTokenChange: (String) -> Unit,
    onMapboxTokenSaveClick: () -> Unit,
    onMapboxTokenClearClick: () -> Unit,
    onWorkerBaseUrlChange: (String) -> Unit,
    onUploadTokenChange: (String) -> Unit,
    onSampleUploadConfigSaveClick: () -> Unit,
    onSampleUploadConfigClearClick: () -> Unit,
    onWorkerConnectivityTestClick: () -> Unit,
    onSyncDiagnosticsRefreshClick: () -> Unit,
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
            var activeSettingsPage by remember { mutableStateOf<AboutSettingsPage>(AboutSettingsPage.OVERVIEW) }

            when (activeSettingsPage) {
                AboutSettingsPage.OVERVIEW -> {
                    SettingsHeroSection(state = state)
                    SettingsListSection(
                        rows = buildAboutSettingsRows(state),
                        onWorkerConfigClick = { activeSettingsPage = AboutSettingsPage.WORKER_CONFIG },
                        onSyncDiagnosticsClick = {
                            activeSettingsPage = AboutSettingsPage.SYNC_DIAGNOSTICS
                            onSyncDiagnosticsRefreshClick()
                        },
                    )
                    MapboxSettingsSection(
                        state = state,
                        onMapboxTokenChange = onMapboxTokenChange,
                        onMapboxTokenSaveClick = onMapboxTokenSaveClick,
                        onMapboxTokenClearClick = onMapboxTokenClearClick,
                    )
                    AppMaintenanceSection(
                        state = state,
                        onCheckUpdateClick = onCheckUpdateClick,
                    )
                }

                AboutSettingsPage.WORKER_CONFIG -> {
                    WorkerConfigSection(
                        state = state,
                        onBackClick = { activeSettingsPage = AboutSettingsPage.OVERVIEW },
                        onWorkerBaseUrlChange = onWorkerBaseUrlChange,
                        onUploadTokenChange = onUploadTokenChange,
                        onSampleUploadConfigSaveClick = onSampleUploadConfigSaveClick,
                        onSampleUploadConfigClearClick = onSampleUploadConfigClearClick,
                        onWorkerConnectivityTestClick = onWorkerConnectivityTestClick,
                    )
                }

                AboutSettingsPage.SYNC_DIAGNOSTICS -> {
                    LaunchedEffect(Unit) {
                        onSyncDiagnosticsRefreshClick()
                    }
                    SyncDiagnosticsSection(
                        state = state,
                        onBackClick = { activeSettingsPage = AboutSettingsPage.OVERVIEW },
                        onRefreshClick = onSyncDiagnosticsRefreshClick,
                    )
                }
            }

            if (state.isCheckingUpdate || state.isTestingWorkerConnectivity || state.statusMessage != null) {
                AboutStatusPanel(state = state)
            }
        }
    }
}

private enum class AboutSettingsPage {
    OVERVIEW,
    WORKER_CONFIG,
    SYNC_DIAGNOSTICS,
}

@Composable
private fun SettingsListSection(
    rows: List<AboutSettingsRowModel>,
    onWorkerConfigClick: () -> Unit,
    onSyncDiagnosticsClick: () -> Unit,
) {
    AboutSection(
        title = "设置列表",
        statusLabel = "${rows.size} 项",
    ) {
        rows.forEachIndexed { index, row ->
            SettingsListRow(
                row = row,
                onClick = when (row.id) {
                    AboutSettingsRowId.WORKER_CONFIG -> onWorkerConfigClick
                    AboutSettingsRowId.SYNC_DIAGNOSTICS -> onSyncDiagnosticsClick
                },
            )
            if (index != rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
            }
        }
    }
}

@Composable
private fun SettingsListRow(
    row: AboutSettingsRowModel,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackInsetPanel(
            modifier = Modifier.size(44.dp),
            accented = true,
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = row.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TrackStatChip(text = row.statusLabel)
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorkerConfigSection(
    state: AboutUiState,
    onBackClick: () -> Unit,
    onWorkerBaseUrlChange: (String) -> Unit,
    onUploadTokenChange: (String) -> Unit,
    onSampleUploadConfigSaveClick: () -> Unit,
    onSampleUploadConfigClearClick: () -> Unit,
    onWorkerConnectivityTestClick: () -> Unit,
) {
    AboutSection(
        title = "Worker 配置",
        statusLabel = if (state.hasConfiguredSampleUpload) "已连接" else "未连接",
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBackClick),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "返回设置列表",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        OutlinedTextField(
            value = state.workerBaseUrlInput,
            onValueChange = onWorkerBaseUrlChange,
            label = { Text("Worker URL") },
            placeholder = { Text("例如：https://your-worker.workers.dev") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.uploadTokenInput,
            onValueChange = onUploadTokenChange,
            label = { Text("Token") },
            placeholder = { Text("请输入上传鉴权 Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsHintCard(
            title = if (state.hasConfiguredSampleUpload) "Worker 已保存" else "配置 Worker 后启用同步",
            body = if (state.hasConfiguredSampleUpload) {
                "原始点位、今日会话和历史摘要都会走这组 Worker 配置。"
            } else {
                "这里集中配置 URL 和 Token，保存后可测试连通性。"
            },
        )
        SettingsActionRow(
            primaryText = "保存配置",
            onPrimaryClick = onSampleUploadConfigSaveClick,
            primaryEnabled = state.workerBaseUrlInput.isNotBlank() && state.uploadTokenInput.isNotBlank(),
            secondaryText = "清空",
            onSecondaryClick = onSampleUploadConfigClearClick,
            secondaryEnabled = !state.isTestingWorkerConnectivity &&
                (
                    state.hasConfiguredSampleUpload ||
                        state.workerBaseUrlInput.isNotBlank() ||
                        state.uploadTokenInput.isNotBlank()
                ),
        )
        TrackSecondaryButton(
            text = if (state.isTestingWorkerConnectivity) "测试中..." else "测试 Worker",
            onClick = onWorkerConnectivityTestClick,
            enabled = !state.isCheckingUpdate &&
                !state.isTestingWorkerConnectivity &&
                state.workerBaseUrlInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SyncDiagnosticsSection(
    state: AboutUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    AboutSection(
        title = "同步诊断",
        statusLabel = when {
            state.syncDiagnostics.isRefreshing -> "刷新中"
            state.syncDiagnostics.outboxFailedCount > 0 -> "有失败"
            else -> "正常"
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBackClick),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "返回设置列表",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        SettingsHintCard(
            title = "本机同步链路快照",
            body = "这里展示采集队列、今日展示缓存、清洗结果和上传队列状态，方便判断数据是否卡在本机。",
        )

        val diagnosticsRows = buildSyncDiagnosticsRows(state.syncDiagnostics)
        diagnosticsRows.forEachIndexed { index, row ->
            SyncDiagnosticsRow(row = row)
            if (index != diagnosticsRows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
            }
        }

        TrackSecondaryButton(
            text = if (state.syncDiagnostics.isRefreshing) "刷新中..." else "刷新状态",
            onClick = onRefreshClick,
            enabled = !state.syncDiagnostics.isRefreshing,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SyncDiagnosticsRow(row: SyncDiagnosticsRowModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = row.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TrackStatChip(text = row.value)
    }
}

@Composable
private fun MapboxSettingsSection(
    state: AboutUiState,
    onMapboxTokenChange: (String) -> Unit,
    onMapboxTokenSaveClick: () -> Unit,
    onMapboxTokenClearClick: () -> Unit,
) {
    AboutSection(
        title = "地图服务",
        statusLabel = if (state.hasConfiguredMapboxToken) "已就绪" else "待配置",
    ) {
        OutlinedTextField(
            value = state.mapboxTokenInput,
            onValueChange = onMapboxTokenChange,
            label = { Text("地图访问令牌") },
            placeholder = { Text("请输入 Mapbox Token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsHintCard(
            title = if (state.hasConfiguredMapboxToken) "地图可用" else "地图未启用",
            body = if (state.hasConfiguredMapboxToken) {
                "当前设备已保存地图令牌，记录页会直接使用这份配置。"
            } else {
                "未配置时地图会保持禁用，记录页只显示占位提示。"
            },
        )
        SettingsActionRow(
            primaryText = "保存令牌",
            onPrimaryClick = onMapboxTokenSaveClick,
            primaryEnabled = state.mapboxTokenInput.isNotBlank(),
            secondaryText = "清空",
            onSecondaryClick = onMapboxTokenClearClick,
            secondaryEnabled = state.hasConfiguredMapboxToken || state.mapboxTokenInput.isNotBlank(),
        )
    }
}

@Composable
private fun AppMaintenanceSection(
    state: AboutUiState,
    onCheckUpdateClick: () -> Unit,
) {
    AboutSection(
        title = "应用维护",
        statusLabel = if (state.isCheckingUpdate) "检查中" else "稳定",
    ) {
        TrackPrimaryButton(
            text = if (state.isCheckingUpdate) "检查中..." else "检查更新",
            onClick = onCheckUpdateClick,
            enabled = !state.isCheckingUpdate && !state.isTestingWorkerConnectivity,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AboutSection(
    title: String,
    statusLabel: String? = null,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    statusLabel?.let { label ->
                        TrackStatChip(text = label)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                content()
            },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TrackStatChip(text = "设置")
            Text(
                text = "地图与同步",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "让记录页、历史页和云端同步使用同一套配置，减少重复切换时的阻塞感。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.compose_dashboard_health_advanced_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrackStatChip(text = if (state.hasConfiguredMapboxToken) "地图已接通" else "地图待配置")
                TrackStatChip(text = if (state.hasConfiguredSampleUpload) "同步已接通" else "同步待配置")
            }
            Text(
                text = state.appVersionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    primaryText: String,
    onPrimaryClick: () -> Unit,
    primaryEnabled: Boolean,
    secondaryText: String,
    onSecondaryClick: () -> Unit,
    secondaryEnabled: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TrackPrimaryButton(
            text = primaryText,
            onClick = onPrimaryClick,
            enabled = primaryEnabled,
            modifier = Modifier.weight(1f),
        )
        TrackSecondaryButton(
            text = secondaryText,
            onClick = onSecondaryClick,
            enabled = secondaryEnabled,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsHintCard(
    title: String,
    body: String,
) {
    TrackInsetPanel(
        modifier = Modifier.fillMaxWidth(),
        accented = true,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutStatusPanel(
    state: AboutUiState,
) {
    TrackLiquidPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tone = TrackLiquidTone.SUBTLE,
        shadowElevation = 10.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isCheckingUpdate || state.isTestingWorkerConnectivity) {
                CircularProgressIndicator(
                    modifier = Modifier.width(20.dp),
                    strokeWidth = 2.2.dp,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = when {
                        state.isCheckingUpdate -> "正在检查更新"
                        state.isTestingWorkerConnectivity -> "正在测试 Worker"
                        else -> "当前状态"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.statusMessage ?: "配置已保存到本地设备。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
