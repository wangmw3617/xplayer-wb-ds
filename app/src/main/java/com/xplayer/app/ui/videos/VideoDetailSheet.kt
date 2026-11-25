package com.xplayer.app.ui.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xplayer.app.R
import com.xplayer.app.data.local.HdrType
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.data.scanner.TrackDescription
import com.xplayer.app.data.scanner.TrackInspector
import com.xplayer.app.ui.components.InfoRow
import com.xplayer.app.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val INVALID_FILENAME_REGEX = Regex("[/\\\\:*?\"<>|\\x00-\\x1F]")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailSheet(
    video: VideoEntity,
    hasProgress: Boolean = false,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenInfo: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tracks by remember(video.path) { mutableStateOf<List<TrackDescription>?>(null) }
    var renameVisible by remember(video.path) { mutableStateOf(false) }
    var deleteVisible by remember(video.path) { mutableStateOf(false) }
    var nameInput by remember(video.path) { mutableStateOf(video.title) }
    var renameInvalid by remember(video.path) { mutableStateOf(false) }

    LaunchedEffect(video.path) {
        tracks = null
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenInfo) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.action_media_info),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = video.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            val resShort = Formatters.resolutionLabel(video.width, video.height)
            val resText = if (resShort.isNotEmpty() && video.width > 0 && video.height > 0) {
                "${video.width}×${video.height} · $resShort"
            } else if (video.width > 0 && video.height > 0) {
                "${video.width}×${video.height}"
            } else {
                "-"
            }
            InfoRow(stringResource(R.string.videos_resolution), resText)
            InfoRow(
                stringResource(R.string.videos_codec),
                Formatters.videoCodecLabel(video.videoCodec) ?: (video.videoCodec ?: "-"),
            )
            InfoRow(stringResource(R.string.videos_hdr), hdrLabel(video.hdrType))
            if (video.bitrate > 0L) {
                InfoRow(stringResource(R.string.videos_bitrate), formatBitrate(video.bitrate))
            }
            if (video.frameRate > 0f) {
                InfoRow(stringResource(R.string.videos_framerate), formatFrameRate(video.frameRate))
            }
            InfoRow(stringResource(R.string.videos_size), Formatters.size(video.sizeBytes))
            InfoRow(stringResource(R.string.videos_duration), Formatters.duration(video.durationMs))
            InfoRow(stringResource(R.string.videos_modified), Formatters.dateTime(video.dateModified), showDivider = false)

            val loadedTracks = tracks
            if (loadedTracks == null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.common_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val audio = loadedTracks.filter { it.type == "audio" }
                val subtitles = loadedTracks.filter { it.type != "audio" }
                if (audio.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.videos_audio_tracks),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    audio.forEachIndexed { idx, track ->
                        InfoRow(
                            label = track.title ?: track.mime?.substringAfter('/') ?: "-",
                            value = TrackInspector.describe(track),
                            showDivider = idx < audio.lastIndex,
                        )
                    }
                }
                if (subtitles.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.videos_subtitle_tracks),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    subtitles.forEachIndexed { idx, track ->
                        InfoRow(
                            label = track.language?.uppercase(Locale.US) ?: track.mime ?: "-",
                            value = TrackInspector.describe(track),
                            showDivider = idx < subtitles.lastIndex,
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (hasProgress) R.string.home_resume else R.string.videos_play))
                }
                OutlinedButton(
                    onClick = {
                        nameInput = video.title
                        renameInvalid = false
                        renameVisible = true
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.common_rename))
                }
                OutlinedButton(
                    onClick = { deleteVisible = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
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
                Column {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            renameInvalid = false
                        },
                        isError = renameInvalid,
                        singleLine = true,
                        label = { Text(stringResource(R.string.videos_rename_hint)) },
                    )
                    if (renameInvalid) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.videos_rename_invalid),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = nameInput.trim()
                        if (trimmed.isEmpty() || trimmed == "." || trimmed == ".." || INVALID_FILENAME_REGEX.containsMatchIn(trimmed)) {
                            renameInvalid = true
                        } else {
                            renameVisible = false
                            onRename(trimmed)
                        }
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
                ) {
                    Text(
                        text = stringResource(R.string.common_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteVisible = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private fun hdrLabel(hdr: HdrType): String = when (hdr) {
    HdrType.NONE -> "SDR"
    HdrType.HDR10 -> "HDR10"
    HdrType.HDR10_PLUS -> "HDR10+"
    HdrType.DOLBY_VISION -> "Dolby Vision"
    HdrType.HLG -> "HLG"
}

private fun formatBitrate(bps: Long): String = when {
    bps >= 1_000_000L -> String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
    else -> "${(bps / 1_000L).coerceAtLeast(1L)} kbps"
}

private fun formatFrameRate(fps: Float): String {
    val raw = String.format(Locale.US, "%.2f", fps).trimEnd('0').trimEnd('.')
    return "$raw fps"
}
