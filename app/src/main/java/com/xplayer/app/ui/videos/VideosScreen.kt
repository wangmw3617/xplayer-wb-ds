package com.xplayer.app.ui.videos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xplayer.app.R
import com.xplayer.app.data.local.HdrType
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.ui.common.rememberAppVM
import com.xplayer.app.ui.components.MicroBadge
import com.xplayer.app.ui.components.Thumbnail
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.util.Formatters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideosScreen(
    onPlay: (VideoEntity, Long) -> Unit,
) {
    val viewModel = rememberAppVM { VideosViewModel(it) }
    val items by viewModel.items.collectAsState()
    val query by viewModel.query.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val grid by viewModel.grid.collectAsState()
    val context = LocalContext.current

    var searchActive by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<VideoEntity?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val message = viewModel.messages

    LaunchedEffect(message) {
        message.collect { resId ->
            snackbar.showSnackbar(context.getString(resId))
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
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
                    IconButton(onClick = { searchActive = !searchActive }) {
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
                            imageVector = if (grid) Icons.Default.List else XPlayerIcons.Grid,
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
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(16.dp),
                )
            }

            if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = XPlayerIcons.Video,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (query.isBlank()) {
                            stringResource(R.string.videos_empty)
                        } else {
                            stringResource(R.string.videos_no_match, query)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (query.isBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.videos_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 110.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(items, key = { it.video.id }) { item ->
                        GridVideoCard(
                            item = item,
                            onClick = { onPlay(item.video, item.positionMs) },
                            onLongClick = { selected = item.video },
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 110.dp, top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items, key = { it.video.id }) { item ->
                        ListVideoRow(
                            item = item,
                            onClick = { onPlay(item.video, item.positionMs) },
                            onLongClick = { selected = item.video },
                        )
                    }
                }
            }
        }
    }

    selected?.let { video ->
        VideoDetailSheet(
            video = video,
            onDismiss = { selected = null },
            onPlay = {
                selected = null
                onPlay(video, 0L)
            },
            onRename = { name ->
                viewModel.rename(video, name)
                selected = null
            },
            onDelete = {
                viewModel.delete(video)
                selected = null
            },
        )
    }
}

@Composable
private fun BadgeRow(video: VideoEntity) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val resolution = Formatters.resolutionLabel(video.width, video.height)
        if (resolution == "4K" || resolution == "2K") {
            MicroBadge(resolution, MaterialTheme.colorScheme.primary)
        }
        when (video.hdrType) {
            HdrType.DOLBY_VISION -> MicroBadge("DV", MaterialTheme.colorScheme.tertiary)
            HdrType.HDR10, HdrType.HDR10_PLUS -> MicroBadge("HDR", MaterialTheme.colorScheme.tertiary)
            HdrType.HLG -> MicroBadge("HLG", MaterialTheme.colorScheme.tertiary)
            HdrType.NONE -> Unit
        }
        Formatters.videoCodecLabel(video.videoCodec)?.let { codec ->
            if (codec == "HEVC" || codec == "AV1") {
                MicroBadge(codec, MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f))
            }
        }
        if (video.hasAssSubtitle) {
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
                    text = Formatters.duration(item.video.durationMs),
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
        BadgeRow(item.video)
        Spacer(Modifier.height(2.dp))
        Text(
            text = Formatters.size(item.video.sizeBytes),
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
                    text = Formatters.duration(item.video.durationMs),
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
            BadgeRow(item.video)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${Formatters.size(item.video.sizeBytes)} · ${Formatters.resolutionLabel(item.video.width, item.video.height)}",
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
