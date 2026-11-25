package com.xplayer.app.ui.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xplayer.app.R
import com.xplayer.app.data.local.PlaybackHistoryEntity
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.data.repository.PlaybackRepository
import com.xplayer.app.data.scanner.MediaScanner
import com.xplayer.app.ui.common.rememberAppVM
import com.xplayer.app.ui.components.ChevronTrailing
import com.xplayer.app.ui.components.EmptyState
import com.xplayer.app.ui.components.HdrBadge
import com.xplayer.app.ui.components.Thumbnail
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.ui.navigation.aboveBottomBar
import com.xplayer.app.ui.navigation.bottomBarListPadding
import com.xplayer.app.ui.videos.VideoDetailSheet
import com.xplayer.app.util.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    onPlay: (VideoEntity, Long) -> Unit,
    onOpenHiddenFolders: () -> Unit,
    onOpenInfo: (VideoEntity) -> Unit = {},
) {
    val viewModel = rememberAppVM { FoldersViewModel(it) }
    val folders by viewModel.folders.collectAsState()
    val hidden by viewModel.hiddenFolders.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val historyByPath by viewModel.historyByPath.collectAsState()

    var openedFolderPath by remember { mutableStateOf<String?>(null) }
    var pendingHideFolder by remember { mutableStateOf<FolderItem?>(null) }
    var detailVideoPath by remember { mutableStateOf<String?>(null) }
    var userTriggeredScan by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    LaunchedEffect(scanState.scanning, scanState.error) {
        if (!scanState.scanning && userTriggeredScan) {
            userTriggeredScan = false
            if (scanState.error == MediaScanner.ERROR_NO_PERMISSION) {
                snackbar.showSnackbar(context.getString(R.string.settings_storage_required))
            }
        }
    }

    val openedFolder = remember(openedFolderPath, folders) {
        openedFolderPath?.let { path -> folders.firstOrNull { it.path == path } }
    }
    val detailVideo = remember(detailVideoPath, openedFolder, folders) {
        detailVideoPath?.let { path ->
            openedFolder?.videos?.firstOrNull { it.path == path }
                ?: folders.asSequence().flatMap { it.videos.asSequence() }.firstOrNull { it.path == path }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.aboveBottomBar()) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.folders_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            if (scanState.scanning) {
                if (scanState.total > 0) {
                    LinearProgressIndicator(
                        progress = { (scanState.scanned.toFloat() / scanState.total).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = scanState.scanning,
                onRefresh = {
                    userTriggeredScan = true
                    viewModel.scan()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = bottomBarListPadding(horizontal = 16.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hidden.isNotEmpty()) {
                        item(key = "hidden_entry", contentType = "hiddenCard") {
                            HiddenFoldersCard(
                                count = hidden.size,
                                onClick = onOpenHiddenFolders,
                            )
                        }
                    }

                    if (folders.isEmpty()) {
                        item(key = "empty", contentType = "empty") {
                            EmptyState(
                                title = stringResource(R.string.folders_empty),
                                description = stringResource(R.string.folders_empty_desc),
                                icon = XPlayerIcons.Folder,
                                modifier = Modifier.fillParentMaxHeight(0.7f),
                            )
                        }
                    } else {
                        items(
                            items = folders,
                            key = { it.path },
                            contentType = { "folder" },
                        ) { folder ->
                            FolderRow(
                                folder = folder,
                                onClick = { openedFolderPath = folder.path },
                                onLongClick = { pendingHideFolder = folder },
                            )
                        }
                    }
                }
            }
        }
    }

    openedFolder?.let { folder ->
        FolderSheet(
            folder = folder,
            historyByPath = historyByPath,
            onDismiss = { openedFolderPath = null },
            onPlay = { video, position ->
                openedFolderPath = null
                onPlay(video, position)
            },
            onInspectVideo = { video ->
                detailVideoPath = video.path
            },
            onHide = {
                openedFolderPath = null
                pendingHideFolder = folder
            },
        )
    }

    detailVideo?.let { video ->
        val history = historyByPath[video.path]
        val positionMs = history?.lastPositionMs ?: 0L
        val progress = history?.progress ?: 0f
        val hasProgress = positionMs > 1_000L && progress < PlaybackRepository.COMPLETION_THRESHOLD
        VideoDetailSheet(
            video = video,
            hasProgress = hasProgress,
            onDismiss = { detailVideoPath = null },
            onPlay = {
                detailVideoPath = null
                openedFolderPath = null
                onPlay(video, positionMs)
            },
            onRename = { name ->
                viewModel.rename(video, name)
                detailVideoPath = null
            },
            onOpenInfo = {
                detailVideoPath = null
                openedFolderPath = null
                onOpenInfo(video)
            },
            onDelete = {
                viewModel.delete(video)
                detailVideoPath = null
            },
        )
    }

    pendingHideFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingHideFolder = null },
            title = { Text(stringResource(R.string.folders_hide)) },
            text = { Text(stringResource(R.string.folders_hide_confirm, folder.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingHideFolder = null
                        viewModel.hide(folder.path)
                        scope.launch {
                            val result = snackbar.showSnackbar(
                                message = context.getString(R.string.folders_hidden_toast, folder.name),
                                actionLabel = context.getString(R.string.common_undo),
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.unhide(folder.path)
                            }
                        }
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingHideFolder = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun HiddenFoldersCard(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.folders_hidden),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.folders_hidden_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChevronTrailing(count.toString())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(folder: FolderItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (folder.latestThumbnail != null) {
                Thumbnail(
                    path = folder.latestThumbnail,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = XPlayerIcons.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folder.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${stringResource(R.string.folders_videos, folder.videoCount)} · ${folder.formattedSize}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FolderSheet(
    folder: FolderItem,
    historyByPath: Map<String, PlaybackHistoryEntity>,
    onDismiss: () -> Unit,
    onPlay: (VideoEntity, Long) -> Unit,
    onInspectVideo: (VideoEntity) -> Unit,
    onHide: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.folders_videos, folder.videoCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = folder.videos,
                    key = { it.path },
                    contentType = { "folderVideo" },
                ) { video ->
                    val history = historyByPath[video.path]
                    val resumeMs = history?.lastPositionMs ?: 0L
                    val progress = history?.progress ?: 0f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { onPlay(video, resumeMs) },
                                onLongClick = { onInspectVideo(video) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.width(84.dp)) {
                            Thumbnail(
                                path = video.thumbnailPath,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                            )
                            if (progress > 0.01f) {
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(2.5.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "${Formatters.duration(video.durationMs)} · ${Formatters.size(video.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                HdrBadge(video.hdrType)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onHide, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.folders_hide))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
