package com.xplayer.app.ui.videos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xplayer.app.R
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.data.repository.PlaybackRepository
import com.xplayer.app.data.scanner.MediaScanner
import com.xplayer.app.ui.common.rememberAppVM
import com.xplayer.app.ui.components.EmptyState
import com.xplayer.app.ui.components.HdrBadge
import com.xplayer.app.ui.components.MicroBadge
import com.xplayer.app.ui.components.Thumbnail
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.ui.navigation.aboveBottomBar
import com.xplayer.app.ui.navigation.bottomBarListPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(
    onPlay: (VideoEntity, Long) -> Unit,
    onOpenInfo: (VideoEntity) -> Unit = {},
) {
    val viewModel = rememberAppVM { VideosViewModel(it) }
    val items by viewModel.items.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val query by viewModel.query.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val grid by viewModel.grid.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var searchActive by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    var userTriggeredScan by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        viewModel.messages.collect { resId ->
            snackbar.showSnackbar(context.getString(resId))
        }
    }

    LaunchedEffect(scanState.scanning, scanState.error) {
        if (!scanState.scanning && userTriggeredScan) {
            userTriggeredScan = false
            if (scanState.error == MediaScanner.ERROR_NO_PERMISSION) {
                snackbar.showSnackbar(context.getString(R.string.settings_storage_required))
            }
        }
    }

    val selectedItem = remember(selectedPath, items) {
        selectedPath?.let { path -> items.firstOrNull { it.video.path == path } }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.aboveBottomBar()) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.videos_title),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.videos_count, items.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            viewModel.setQuery("")
                            focusManager.clearFocus()
                        } else {
                            searchActive = true
                        }
                    }) {
                        Icon(
                            imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(R.string.videos_search),
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.videos_sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                VideoSort.DATE to R.string.videos_sort_date,
                                VideoSort.NAME to R.string.videos_sort_name,
                                VideoSort.SIZE to R.string.videos_sort_size,
                                VideoSort.DURATION to R.string.videos_sort_duration,
                            ).forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    onClick = {
                                        viewModel.setSort(mode)
                                        sortMenu = false
                                    },
                                    trailingIcon = if (sort == mode) {
                                        { Text("•", color = MaterialTheme.colorScheme.primary) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = viewModel::toggleView) {
                        Icon(
                            imageVector = if (grid) Icons.AutoMirrored.Filled.List else XPlayerIcons.Grid,
                            contentDescription = if (grid) {
                                stringResource(R.string.videos_view_list)
                            } else {
                                stringResource(R.string.videos_view_grid)
                            },
                        )
                    }
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

            AnimatedVisibility(visible = searchActive) {
                TextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.videos_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_close),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(16.dp),
                )
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
                if (items.isEmpty()) {
                    EmptyState(
                        title = if (query.isBlank()) {
                            stringResource(R.string.videos_empty)
                        } else {
                            stringResource(R.string.videos_no_match, query)
                        },
                        description = if (query.isBlank()) stringResource(R.string.videos_empty_desc) else null,
                        icon = XPlayerIcons.Video,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    )
                } else if (grid) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        contentPadding = bottomBarListPadding(horizontal = 14.dp, top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(
                            items = items,
                            key = { it.video.id },
                            contentType = { "videoGrid" },
                        ) { item ->
                            GridVideoCard(
                                item = item,
                                onClick = { onPlay(item.video, item.positionMs) },
                                onLongClick = { selectedPath = item.video.path },
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = bottomBarListPadding(horizontal = 14.dp, top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            items = items,
                            key = { it.video.id },
                            contentType = { "videoList" },
                        ) { item ->
                            ListVideoRow(
                                item = item,
                                onClick = { onPlay(item.video, item.positionMs) },
                                onLongClick = { selectedPath = item.video.path },
                            )
                        }
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        val video = item.video
        val hasProgress = item.positionMs > 1_000L && item.progress < PlaybackRepository.COMPLETION_THRESHOLD
        VideoDetailSheet(
            video = video,
            hasProgress = hasProgress,
            onDismiss = { selectedPath = null },
            onPlay = {
                selectedPath = null
                onPlay(video, item.positionMs)
            },
            onRename = { name ->
                viewModel.rename(video, name)
                selectedPath = null
            },
            onOpenInfo = {
                selectedPath = null
                onOpenInfo(video)
            },
            onDelete = {
                viewModel.delete(video)
                selectedPath = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgeRow(item: VideoListItem) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (item.resolutionLabel == "4K" || item.resolutionLabel == "2K") {
            MicroBadge(item.resolutionLabel, MaterialTheme.colorScheme.primary)
        }
        HdrBadge(item.video.hdrType)
        item.codecBadge?.let { codec ->
            MicroBadge(codec, MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f))
        }
        if (item.video.hasAssSubtitle) {
            MicroBadge("ASS", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridVideoCard(
    item: VideoListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            Thumbnail(
                path = item.video.thumbnailPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            ) {
                Text(
                    text = item.durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            if (item.progress > 0.01f) {
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.22f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.video.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        BadgeRow(item)
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.sizeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListVideoRow(
    item: VideoListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(132.dp)) {
            Thumbnail(
                path = item.video.thumbnailPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            ) {
                Text(
                    text = item.durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.video.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            BadgeRow(item)
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    item.sizeLabel,
                    item.resolutionLabel.takeIf { it.isNotEmpty() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.progress > 0.01f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}
