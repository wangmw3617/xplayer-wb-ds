package com.xplayer.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xplayer.app.R
import com.xplayer.app.player.codec.CodecCapabilityProbe
import com.xplayer.app.player.codec.CodecReport
import com.xplayer.app.ui.components.MicroBadge
import com.xplayer.app.ui.components.SettingGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodecScreen(onBack: () -> Unit) {
    var report by remember { mutableStateOf<CodecReport?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        report = withContext(Dispatchers.IO) { runCatching { CodecCapabilityProbe.probe() }.getOrNull() }
        loading = false
    }

    SettingsPageScaffold(
        title = stringResource(R.string.codec_title),
        onBack = onBack,
        actionIcon = Icons.Default.Refresh,
        actionDescription = stringResource(R.string.codec_refresh),
        onAction = { if (!loading) reload++ },
    ) { padding ->
        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.common_loading), style = MaterialTheme.typography.bodySmall)
            }
            return@SettingsPageScaffold
        }

        val data = report
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (data == null) {
                item(key = "error", contentType = "error") {
                    Text(
                        text = stringResource(R.string.videos_operation_failed),
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                item(key = "chip", contentType = "chip") {
                    SettingGroup(title = stringResource(R.string.codec_chip)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = data.chipset,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = data.socModel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { if (!loading) reload++ },
                                enabled = !loading,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.codec_refresh))
                            }
                        }
                    }
                }

                item(key = "codecs", contentType = "codecs") {
                    SettingGroup(title = stringResource(R.string.codec_title)) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            data.codecs.forEach { codec ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (codec.supported) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.Warning
                                        },
                                        contentDescription = null,
                                        tint = if (codec.supported) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.size(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = codec.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        if (codec.decoder != null) {
                                            Text(
                                                text = codec.decoder,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    MicroBadge(
                                        text = stringResource(
                                            if (codec.supported) R.string.codec_supported else R.string.codec_unsupported,
                                        ),
                                        container = if (codec.supported) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (data.hdrFormats.isNotEmpty() || data.dolbyVisionProfiles.isNotEmpty()) {
                    item(key = "hdr", contentType = "hdr") {
                        SettingGroup(title = stringResource(R.string.codec_hdr_title)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    data.hdrFormats.forEach { format ->
                                        MicroBadge(
                                            text = format,
                                            container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                                        )
                                    }
                                }
                                if (data.dolbyVisionProfiles.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    data.dolbyVisionProfiles.forEach { profile ->
                                        Text(
                                            text = stringResource(R.string.codec_dolby_vision_profile, profile),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "decoders", contentType = "decoders") {
                    SettingGroup(title = stringResource(R.string.codec_decoder)) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            if (data.decoders.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.codec_hardware_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = "${data.decoders.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(6.dp))
                                data.decoders.forEachIndexed { idx, decoder ->
                                    Text(
                                        text = decoder,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                    if (idx < data.decoders.lastIndex) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
