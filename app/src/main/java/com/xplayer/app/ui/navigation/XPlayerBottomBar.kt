package com.xplayer.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xplayer.app.R
import com.xplayer.app.data.datastore.AnimationLevel
import com.xplayer.app.data.datastore.BottomBarStyle
import com.xplayer.app.ui.components.FrostedGlassSurface
import com.xplayer.app.ui.components.LocalAppBackdropState
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.ui.theme.LocalAnimationLevel
import com.xplayer.app.ui.theme.scaleDuration

private data class TabItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val tabs = listOf(
    TabItem(Routes.HOME, R.string.nav_home, Icons.Default.Home),
    TabItem(Routes.VIDEOS, R.string.nav_videos, XPlayerIcons.Video),
    TabItem(Routes.FOLDERS, R.string.nav_folders, XPlayerIcons.Folder),
    TabItem(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
)

/** 主 Tab 滚动列表底部留白（自动叠加上系统三键/手势导航栏高度） */
@Composable
fun bottomBarListPadding(
    horizontal: Dp = 0.dp,
    top: Dp = 0.dp,
    extraBottom: Dp = 96.dp,
): PaddingValues = PaddingValues(
    start = horizontal,
    end = horizontal,
    top = top,
    bottom = extraBottom + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
)

/** 避免 Snackbar 等底部浮层被底栏遮挡 */
fun Modifier.aboveBottomBar(): Modifier =
    this.navigationBarsPadding().padding(bottom = 86.dp)

private fun <T> tabAnimSpec(level: AnimationLevel, baseMs: Int = 220): AnimationSpec<T> =
    if (level == AnimationLevel.OFF) snap() else tween(level.scaleDuration(baseMs))

/**
 * 底部导航栏：支持 FlClash 液态玻璃悬浮胶囊、实色悬浮胶囊与经典贴边底栏三种样式。
 */
@Composable
fun XPlayerBottomBar(
    currentRoute: String?,
    style: BottomBarStyle = BottomBarStyle.LIQUID_GLASS,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (style) {
        BottomBarStyle.LIQUID_GLASS -> {
            val backdrop = LocalAppBackdropState.current
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                if (backdrop != null) {
                    FrostedGlassSurface(
                        backdropState = backdrop,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FloatingTabRow(currentRoute = currentRoute, onSelect = onSelect)
                    }
                } else {
                    FloatingPillContainer(currentRoute = currentRoute, onSelect = onSelect)
                }
            }
        }

        BottomBarStyle.FLOATING_PILL -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                FloatingPillContainer(currentRoute = currentRoute, onSelect = onSelect)
            }
        }

        BottomBarStyle.CLASSIC -> {
            Surface(
                modifier = modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(66.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEach { tab ->
                            ClassicTabBarItem(
                                item = tab,
                                selected = currentRoute == tab.route,
                                onClick = { onSelect(tab.route) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingPillContainer(
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        shadowElevation = 8.dp,
    ) {
        FloatingTabRow(currentRoute = currentRoute, onSelect = onSelect)
    }
}

/** 复刻 FlClash 截图底栏：高圆角药丸容器内嵌高亮选中胶囊，图标与文字同为主色 */
@Composable
private fun FloatingTabRow(
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    val animLevel = LocalAnimationLevel.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val pillBg = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.20f else 0.11f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            val interaction = remember { MutableInteractionSource() }
            val containerColor by animateColorAsState(
                targetValue = if (selected) pillBg else Color.Transparent,
                animationSpec = tabAnimSpec(animLevel),
                label = "tabPillColor",
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tabAnimSpec(animLevel),
                label = "tabContentColor",
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(containerColor)
                    .clickable(interactionSource = interaction, indication = null) {
                        onSelect(tab.route)
                    }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(tab.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ClassicTabBarItem(
    item: TabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animLevel = LocalAnimationLevel.current
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 56.dp else 0.dp,
        animationSpec = tabAnimSpec(animLevel),
        label = "pillWidth",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.62f,
        animationSpec = tabAnimSpec(animLevel),
        label = "iconAlpha",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.6f,
        animationSpec = tabAnimSpec(animLevel),
        label = "labelAlpha",
    )
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .width(pillWidth)
                    .height(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    ),
            )
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = iconAlpha)
                },
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = labelAlpha)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
