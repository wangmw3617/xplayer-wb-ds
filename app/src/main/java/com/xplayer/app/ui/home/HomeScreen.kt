package com.xplayer.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xplayer.app.R
import com.xplayer.app.data.local.PlaybackHistoryEntity
import com.xplayer.app.data.local.StreamUrlEntity
import com.xplayer.app.data.repository.StreamRepository
import com.xplayer.app.ui.common.rememberAppVM
import com.xplayer.app.ui.components.ChevronTrailing
import com.xplayer.app.ui.components.MicroBadge
import com.xplayer.app.ui.components.Thumbnail
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.ui.navigation.bottomBarListPadding
import com.xplayer.app.ui.permission.PermissionBanner
import com.xplayer.app.util.Formatters

private val STREAM_URL_REGEX = Regex("^(https?|rtsps?|rtmp|mms|udp|rtp)://\\S+$", RegexOption.IGNORE_CASE)

@Composable
fun HomeScreen(
    onResumeHistory: (PlaybackHistoryEntity) -> Unit,
    onPlayNetwork: (url: String, title: String?) -> Unit,
    onOpenCodec: () -> Unit,
    onOpenSettings: () -> Unit,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
) {
    val viewModel = rememberAppVM { HomeViewModel(it) }
    val videoCount by viewModel.videoCount.collectAsState()
    val recentThumbnails by viewModel.recentThumbnails.collectAsState()
    val streams by viewModel.streams.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val recent by viewModel.recent.collectAsState()

    var pendingRemoveHistory by remember { mutableStateOf<PlaybackHistoryEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = bottomBarListPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header", contentType = "header") {
            HomeHeader(
                videoCount = videoCount,
                scanning = scanState.scanning,
                scanned = scanState.scanned,
                permissionGranted = permissionGranted,
                onScan = viewModel::scan,
                onRequestPermission = onRequestPermission,
            )
        }

        if (!permissionGranted) {
            item(key = "permission", contentType = "permission") {
                PermissionBanner(
                    onRequest = onRequestPermission,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        continueWatching?.let { entry ->
            item(key = "continue", contentType = "continue") {
                SectionTitle(stringResource(R.string.home_continue))
                Spacer(Modifier.height(8.dp))
                ContinueWatchingCard(
                    entry = entry,
                    thumbnail = entry.thumbnailPath ?: recentThumbnails[entry.mediaId],
                    onClick = { onResumeHistory(entry) },
                )
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recent_header", contentType = "sectionTitle") {
                SectionTitle(stringResource(R.string.home_recent))
            }
            item(key = "recent_list", contentType = "recentList") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = recent,
                        key = { "${it.sourceType.name}:${it.id}" },
                        contentType = { "recentCard" },
                    ) { entry ->
                        RecentCard(
                            entry = entry,
                            thumbnail = entry.thumbnailPath ?: recentThumbnails[entry.mediaId],
                            onClick = { onResumeHistory(entry) },
                            onRemove = { pendingRemoveHistory = entry },
                        )
                    }
                }
            }
        }

        item(key = "network", contentType = "network") {
            SectionTitle(stringResource(R.string.home_network))
            Spacer(Modifier.height(8.dp))
            NetworkCard(
                streams = streams,
                onPlay = { url, title ->
                    viewModel.rememberStream(url, title)
                    onPlayNetwork(url, title)
                },
                onSaveStream = { url, title, rawHeaders ->
                    viewModel.rememberStream(url, title, rawHeaders, overwriteMeta = true)
                },
                onDelete = viewModel::removeStream,
            )
        }

        item(key = "codec", contentType = "codec") {
            CodecPanelCard(onClick = onOpenCodec)
        }

        if (videoCount == 0 && !scanState.scanning) {
            item(key = "empty", contentType = "empty") {
                EmptyLibraryCard(
                    permissionGranted = permissionGranted,
                    onScan = viewModel::scan,
                    onRequestPermission = onRequestPermission,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }

    pendingRemoveHistory?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRemoveHistory = null },
            title = { Text(stringResource(R.string.home_remove_record)) },
            text = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeHistory(entry)
                        pendingRemoveHistory = null
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveHistory = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun HomeHeader(
    videoCount: Int,
    scanning: Boolean,
    scanned: Int,
    permissionGranted: Boolean,
    onScan: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "XPlayer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (scanning) {
                    stringResource(R.string.settings_scanning, scanned)
                } else {
                    stringResource(R.string.home_video_count, videoCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (scanning) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        IconButton(
            onClick = if (permissionGranted) onScan else onRequestPermission,
            enabled = !scanning,
        ) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.home_scan))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun ContinueWatchingCard(
    entry: PlaybackHistoryEntity,
    thumbnail: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Thumbnail(
                path = thumbnail,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MicroBadge(
                        text = entry.preferredEngine ?: "Media3",
                        container = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.home_remaining,
                            Formatters.duration((entry.durationMs - entry.lastPositionMs).coerceAtLeast(0)),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { entry.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentCard(
    entry: PlaybackHistoryEntity,
    thumbnail: String?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onRemove)
            .padding(4.dp),
    ) {
        Box {
            Thumbnail(
                path = thumbnail,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            ) {
                Text(
                    text = Formatters.duration(entry.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            if (entry.progress > 0f) {
                LinearProgressIndicator(
                    progress = { entry.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NetworkCard(
    streams: List<StreamUrlEntity>,
    onPlay: (String, String?) -> Unit,
    onSaveStream: (url: String, title: String?, rawHeaders: String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var url by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var editingStream by remember { mutableStateOf<StreamUrlEntity?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<StreamUrlEntity?>(null) }

    val submitQuickUrl: () -> Unit = {
        val trimmed = url.trim()
        if (STREAM_URL_REGEX.matches(trimmed)) {
            focusManager.clearFocus()
            onPlay(trimmed, null)
            url = ""
        } else {
            error = true
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.home_network_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    error = false
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.home_network_hint)) },
                isError = error,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { submitQuickUrl() }),
                trailingIcon = {
                    IconButton(onClick = submitQuickUrl) {
                        Icon(
                            imageVector = XPlayerIcons.Video,
                            contentDescription = stringResource(R.string.home_network_play),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
            if (error) {
                Text(
                    text = stringResource(R.string.home_network_invalid),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }

            if (streams.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                streams.take(4).forEach { stream ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { onPlay(stream.url, stream.customTitle) },
                                onLongClick = {
                                    editingStream = stream
                                    editorVisible = true
                                },
                            )
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = XPlayerIcons.Video,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stream.customTitle ?: stream.url,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stream.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { pendingDelete = stream }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        editingStream = null
                        editorVisible = true
                    }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.home_network_more),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (editorVisible) {
        StreamEditorDialog(
            initial = editingStream,
            onDismiss = {
                editorVisible = false
                editingStream = null
            },
            onSave = { savedUrl, savedTitle, rawHeaders ->
                onSaveStream(savedUrl, savedTitle, rawHeaders)
                editorVisible = false
                editingStream = null
            },
        )
    }

    pendingDelete?.let { stream ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.home_network_delete_confirm, stream.customTitle ?: stream.url)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(stream.id)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun StreamEditorDialog(
    initial: StreamUrlEntity?,
    onDismiss: () -> Unit,
    onSave: (url: String, title: String?, rawHeaders: String) -> Unit,
) {
    var urlInput by remember(initial) { mutableStateOf(initial?.url.orEmpty()) }
    var titleInput by remember(initial) { mutableStateOf(initial?.customTitle.orEmpty()) }
    var headersInput by remember(initial) {
        mutableStateOf(StreamRepository.formatHeadersText(initial?.headersJson))
    }
    var urlError by remember(initial) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_network_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        urlError = false
                    },
                    label = { Text(stringResource(R.string.network_url)) },
                    placeholder = { Text(stringResource(R.string.home_network_hint)) },
                    isError = urlError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                if (urlError) {
                    Text(
                        text = stringResource(R.string.home_network_invalid),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text(stringResource(R.string.network_name)) },
                    placeholder = { Text(stringResource(R.string.network_name_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = headersInput,
                    onValueChange = { headersInput = it },
                    label = { Text(stringResource(R.string.network_headers)) },
                    placeholder = { Text(stringResource(R.string.network_headers_hint)) },
                    minLines = 2,
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = urlInput.trim()
                    if (!STREAM_URL_REGEX.matches(trimmed)) {
                        urlError = true
                    } else {
                        onSave(trimmed, titleInput.trim().takeIf { it.isNotEmpty() }, headersInput)
                    }
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun CodecPanelCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = XPlayerIcons.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_codec),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.home_codec_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChevronTrailing()
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    permissionGranted: Boolean,
    onScan: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = XPlayerIcons.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.home_empty),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            if (permissionGranted) {
                Button(onClick = onScan) {
                    Text(stringResource(R.string.home_scan))
                }
            } else {
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.perm_grant))
                }
            }
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.settings_title))
            }
        }
    }
}
