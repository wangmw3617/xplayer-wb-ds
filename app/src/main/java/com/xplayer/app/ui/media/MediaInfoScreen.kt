package com.xplayer.app.ui.media

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xplayer.app.R
import com.xplayer.app.data.local.HdrType
import com.xplayer.app.data.media.MediaInfo
import com.xplayer.app.data.media.MediaInfoProbe
import com.xplayer.app.data.media.MediaTrackInfo
import com.xplayer.app.data.scanner.SubtitleFinder
import com.xplayer.app.data.source.ContentUriSource
import com.xplayer.app.data.source.LocalFileSource
import com.xplayer.app.data.source.MediaSource
import com.xplayer.app.data.source.MediaUriResolver
import com.xplayer.app.data.source.NetworkUrlSource
import com.xplayer.app.ui.components.MicroBadge
import com.xplayer.app.ui.components.SettingGroup
import com.xplayer.app.ui.settings.SettingsPageScaffold
import com.xplayer.app.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 媒体信息页：在后台线程完成 Uri/目录/MediaExtractor 解析，展示容器、视频轨、音频轨与字幕轨真实参数。
 */
@Composable
fun MediaInfoScreen(
    sourceType: String,
    key: String,
    title: String,
    onBack: () -> Unit,
    onPlay: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var retryTick by remember { mutableIntStateOf(0) }
    var info by remember(sourceType, key, retryTick) { mutableStateOf<MediaInfo?>(null) }
    var failed by remember(sourceType, key, retryTick) { mutableStateOf(false) }

    LaunchedEffect(sourceType, key, retryTick) {
        failed = false
        val result = withContext(Dispatchers.IO) {
            val source = buildSource(context, sourceType, key, title)
            runCatching { MediaInfoProbe.probe(context, source) }.getOrNull()
        }
        if (result == null) failed = true else info = result
    }

    SettingsPageScaffold(
        title = stringResource(R.string.info_title),
        onBack = onBack,
    ) { padding ->
        val current = info
        when {
            failed -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.info_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { retryTick++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }

            current == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.info_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> MediaInfoContent(
                info = current,
                topPadding = padding.calculateTopPadding(),
                onPlay = onPlay,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaInfoContent(
    info: MediaInfo,
    topPadding: Dp,
    onPlay: (() -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = 32.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
            ) {
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    info.container?.let { MicroBadge(it, MaterialTheme.colorScheme.primary) }
                    info.videoTracks.firstOrNull()?.let { track ->
                        val label = Formatters.resolutionLabel(track.width ?: 0, track.height ?: 0)
                        if (label.isNotEmpty()) {
                            MicroBadge(label, MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    if (info.hdr != HdrType.NONE) {
                        MicroBadge(hdrLabel(info.hdr), MaterialTheme.colorScheme.secondary)
                    }
                    info.videoTracks.firstOrNull()?.codec?.let { codec ->
                        MicroBadge(codec, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        item {
            val fileRows = buildList {
                add(Triple(stringResource(R.string.info_file_name), info.displayName, false))
                add(
                    Triple(
                        stringResource(R.string.info_file_location),
                        if (info.isContentUri) readableUri(info.location) else info.location,
                        true,
                    ),
                )
                if (info.sizeBytes > 0) {
                    add(Triple(stringResource(R.string.info_file_size), Formatters.size(info.sizeBytes), false))
                }
                if (info.modifiedAt > 0) {
                    add(Triple(stringResource(R.string.info_file_modified), Formatters.dateTime(info.modifiedAt), false))
                }
            }
            SettingGroup(title = stringResource(R.string.info_group_file)) {
                fileRows.forEachIndexed { index, (label, value, wrap) ->
                    InfoRow(label, value, wrap = wrap, showDivider = index != fileRows.lastIndex)
                }
            }
        }

        item {
            val containerRows = buildList {
                add(stringResource(R.string.info_container) to (info.container ?: "-"))
                add(stringResource(R.string.info_duration) to Formatters.duration(info.durationMs))
                if (info.overallBitrate > 0) {
                    add(stringResource(R.string.info_bitrate) to bitrate(info.overallBitrate))
                }
                add(stringResource(R.string.info_track_count) to "${info.tracks.size}")
            }
            SettingGroup(title = stringResource(R.string.info_group_container)) {
                containerRows.forEachIndexed { index, (label, value) ->
                    InfoRow(label, value, showDivider = index != containerRows.lastIndex)
                }
            }
        }

        if (info.tracks.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.info_empty_tracks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                )
            }
        }

        info.videoTracks.forEachIndexed { position, track ->
            item(key = "video-$position") {
                TrackGroup(
                    title = stringResource(R.string.info_group_video) + trackSuffix(position, track),
                    track = track,
                    rows = videoRows(track),
                )
            }
        }

        info.audioTracks.forEachIndexed { position, track ->
            item(key = "audio-$position") {
                TrackGroup(
                    title = stringResource(R.string.info_group_audio) + trackSuffix(position, track),
                    track = track,
                    rows = audioRows(track),
                )
            }
        }

        info.subtitleTracks.forEachIndexed { position, track ->
            item(key = "subtitle-$position") {
                TrackGroup(
                    title = stringResource(R.string.info_group_subtitle) + trackSuffix(position, track),
                    track = track,
                    rows = subtitleRows(track),
                )
            }
        }

        if (info.sidecarSubtitles.isNotEmpty()) {
            item {
                SettingGroup(title = stringResource(R.string.info_group_sidecar)) {
                    info.sidecarSubtitles.forEachIndexed { index, subtitle ->
                        InfoRow(
                            title = subtitle.name,
                            value = File(subtitle.path).parentFile?.name.orEmpty(),
                            showDivider = index != info.sidecarSubtitles.lastIndex,
                        )
                    }
                }
            }
        }

        val lowerLocation = info.location.lowercase(Locale.US)
        if (info.tracks.isEmpty() &&
            (lowerLocation.startsWith("http://") || lowerLocation.startsWith("https://") ||
                lowerLocation.startsWith("rtsp://") || lowerLocation.startsWith("rtmp://"))
        ) {
            item {
                Text(
                    text = stringResource(R.string.info_network_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }
        }

        if (onPlay != null) {
            item {
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.info_play_with))
                }
            }
        }
    }
}

@Composable
private fun TrackGroup(
    title: String,
    track: MediaTrackInfo,
    rows: List<Pair<String, String?>>,
) {
    val allRows = buildList {
        if (track.isDefault) add(stringResource(R.string.info_default) to "✓")
        rows.forEach { (k, v) ->
            if (!v.isNullOrBlank()) add(k to v)
        }
    }
    SettingGroup(title = title) {
        allRows.forEachIndexed { index, row ->
            InfoRow(row.first, row.second, showDivider = index != allRows.lastIndex)
        }
    }
}

@Composable
private fun videoRows(track: MediaTrackInfo): List<Pair<String, String?>> {
    val resolution = track.width?.let { width ->
        track.height?.let { height ->
            val short = Formatters.resolutionLabel(width, height)
            if (short.isNotEmpty()) "$width×$height · $short" else "$width×$height"
        }
    }
    val rangeLabel = when (track.colorRange) {
        "Limited" -> stringResource(R.string.media_color_limited)
        "Full" -> stringResource(R.string.media_color_full)
        else -> track.colorRange
    }
    val color = listOfNotNull(track.colorStandard, track.colorTransfer, rangeLabel)
        .joinToString(" · ")
        .ifBlank { null }
    return listOf(
        stringResource(R.string.info_codec) to track.codec,
        stringResource(R.string.info_profile) to listOfNotNull(track.profile, track.level).joinToString(" ").ifBlank { null },
        stringResource(R.string.info_codec_string) to track.codecString,
        stringResource(R.string.info_resolution) to resolution,
        stringResource(R.string.info_frame_rate) to track.frameRate?.let { formatFrameRate(it) },
        stringResource(R.string.info_bitrate) to track.bitrate?.let { bitrate(it) },
        stringResource(R.string.info_color) to color,
        stringResource(R.string.info_hdr) to hdrLabel(track.hdr).takeIf { track.hdr != HdrType.NONE },
        stringResource(R.string.info_rotation) to track.rotation?.let { "$it°" },
    )
}

@Composable
private fun audioRows(track: MediaTrackInfo): List<Pair<String, String?>> = listOf(
    stringResource(R.string.info_codec) to track.codec,
    stringResource(R.string.info_profile) to track.profile,
    stringResource(R.string.info_channels) to track.channels?.let { channelLabel(it) },
    stringResource(R.string.info_sample_rate) to track.sampleRate?.let { Formatters.sampleRateLabel(it) },
    stringResource(R.string.info_bitrate) to track.bitrate?.let { bitrate(it) },
    stringResource(R.string.info_language) to track.language?.uppercase(Locale.US),
    stringResource(R.string.info_track_title) to track.title,
)

@Composable
private fun subtitleRows(track: MediaTrackInfo): List<Pair<String, String?>> = listOf(
    stringResource(R.string.info_codec) to track.codec,
    stringResource(R.string.info_language) to track.language?.uppercase(Locale.US),
    stringResource(R.string.info_track_title) to track.title,
)

@Composable
private fun InfoRow(
    title: String,
    value: String,
    wrap: Boolean = false,
    showDivider: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (wrap) Modifier.width(76.dp) else Modifier.weight(1f, fill = false),
            )
            if (!wrap) {
                Spacer(Modifier.size(12.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = if (wrap) TextAlign.Start else TextAlign.End,
                modifier = if (wrap) Modifier.weight(1f) else Modifier,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }
    }
}

private fun buildSource(context: android.content.Context, sourceType: String, key: String, title: String): MediaSource =
    when (sourceType) {
        "uri" -> {
            val uri = Uri.parse(key)
            ContentUriSource(
                uri = key,
                displayName = title.ifBlank { MediaUriResolver.displayName(context, uri) },
            )
        }

        "network" -> NetworkUrlSource(url = key, displayName = title.ifBlank { key })

        else -> {
            val file = File(key)
            LocalFileSource(
                path = file.path,
                displayName = title.ifBlank { file.nameWithoutExtension.ifBlank { file.name } },
                subtitles = SubtitleFinder.find(file.path),
                folderPath = file.parent ?: "",
            )
        }
    }

private fun readableUri(raw: String): String = runCatching {
    Uri.decode(raw)
}.getOrDefault(raw)

private fun trackSuffix(position: Int, track: MediaTrackInfo): String = buildString {
    append(" ")
    append(track.title ?: "#${position + 1}")
    track.language?.let { append(" · ${it.uppercase(Locale.US)}") }
}

private fun hdrLabel(hdr: HdrType): String = when (hdr) {
    HdrType.NONE -> "SDR"
    HdrType.HDR10 -> "HDR10"
    HdrType.HDR10_PLUS -> "HDR10+"
    HdrType.DOLBY_VISION -> "Dolby Vision"
    HdrType.HLG -> "HLG"
}

private fun bitrate(bitsPerSecond: Long): String = when {
    bitsPerSecond <= 0 -> "-"
    bitsPerSecond >= 1_000_000 -> String.format(Locale.US, "%.1f Mbps", bitsPerSecond / 1_000_000.0)
    else -> String.format(Locale.US, "%d kbps", bitsPerSecond / 1000)
}

private fun formatFrameRate(value: Float): String =
    if (value % 1f == 0f) {
        String.format(Locale.US, "%.0f fps", value)
    } else {
        String.format(Locale.US, "%.3f fps", value)
    }

@Composable
private fun channelLabel(channels: Int): String = when (channels) {
    1 -> stringResource(R.string.media_channel_mono)
    2 -> stringResource(R.string.media_channel_stereo)
    3 -> "2.1"
    6 -> "5.1"
    8 -> "7.1"
    else -> stringResource(R.string.media_channel_count, channels)
}
