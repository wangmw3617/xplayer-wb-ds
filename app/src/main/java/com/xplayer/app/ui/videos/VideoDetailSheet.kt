package com.xplayer.app.ui.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xplayer.app.R
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.data.scanner.TrackDescription
import com.xplayer.app.data.scanner.TrackInspector
import com.xplayer.app.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailSheet(
    video: VideoEntity,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tracks by remember { mutableStateOf<List<TrackDescription>>(emptyList()) }
    var renameVisible by remember { mutableStateOf(false) }
    var deleteVisible by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(video.title) }

    LaunchedEffect(video.path) {
        tracks = withContext(Dispatchers.IO) { TrackInspector.inspect(video.path) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = video.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            InfoRow(stringResource(R.string.videos_resolution), "${video.width}×${video.height} · ${Formatters.resolutionLabel(video.width, video.height)}")
            InfoRow(stringResource(R.string.videos_codec), Formatters.videoCodecLabel(video.videoCodec) ?: (video.videoCodec ?: "-"))
            InfoRow(stringResource(R.string.videos_hdr), hdrLabel(video))
            InfoRow(stringResource(R.string.videos_size), Formatters.size(video.sizeBytes))
            InfoRow(stringResource(R.string.videos_duration), Formatters.duration(video.durationMs))
            InfoRow(stringResource(R.string.videos_modified), Formatters.dateTime(video.dateModified))

            val audio = tracks.filter { it.type == "audio" }
            val subtitles = tracks.filter { it.type != "audio" }
            if (audio.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.videos_audio_tracks),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                audio.forEach { track ->
                    InfoRow(track.title ?: track.mime?.substringAfter('/') ?: "-", TrackInspector.describe(track))
                }
            }
            if (subtitles.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.videos_subtitle_tracks),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                subtitles.forEach { track ->
                    InfoRow(track.language?.uppercase() ?: track.mime ?: "-", TrackInspector.describe(track))
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_resume))
                }
                OutlinedButton(onClick = { renameVisible = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.common_rename))
                }
                OutlinedButton(onClick = { deleteVisible = true }) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }

    if (renameVisible) {
        AlertDialog(
            onDismissRequest = { renameVisible = false },
            title = { Text(stringResource(R.string.videos_rename_title)) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.videos_rename_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameVisible = false
                        onRename(nameInput)
                    },
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameVisible = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (deleteVisible) {
        AlertDialog(
            onDismissRequest = { deleteVisible = false },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.videos_delete_confirm, video.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteVisible = false
                        onDelete()
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteVisible = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 7.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }
}

private fun hdrLabel(video: VideoEntity): String = when (video.hdrType) {
    com.xplayer.app.data.local.HdrType.NONE -> "SDR"
    com.xplayer.app.data.local.HdrType.HDR10 -> "HDR10"
    com.xplayer.app.data.local.HdrType.HDR10_PLUS -> "HDR10+"
    com.xplayer.app.data.local.HdrType.DOLBY_VISION -> "Dolby Vision"
    com.xplayer.app.data.local.HdrType.HLG -> "HLG"
}
