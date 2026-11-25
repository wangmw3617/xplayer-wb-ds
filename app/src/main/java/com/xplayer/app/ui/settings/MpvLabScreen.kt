package com.xplayer.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xplayer.app.R
import com.xplayer.app.data.datastore.EnginePrefs
import com.xplayer.app.data.local.MpvShaderEntity
import com.xplayer.app.ui.components.SettingGroup
import com.xplayer.app.ui.components.SettingRow
import com.xplayer.app.ui.components.SettingSwitchRow
import com.xplayer.app.ui.icons.XPlayerIcons

@Composable
fun MpvLabScreen(onBack: () -> Unit, viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val shaders by viewModel.shaders.collectAsState()
    var conf by rememberSaveable { mutableStateOf(settings.engine.mpvConf) }
    var lastLoadedConf by rememberSaveable { mutableStateOf(settings.engine.mpvConf) }
    var resetConfirmVisible by remember { mutableStateOf(false) }
    var pendingDeleteShader by remember { mutableStateOf<MpvShaderEntity?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importShader) }

    LaunchedEffect(settings.engine.mpvConf) {
        if (settings.engine.mpvConf != lastLoadedConf) {
            lastLoadedConf = settings.engine.mpvConf
            conf = settings.engine.mpvConf
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_group_mpv),
        onBack = onBack,
        snackbar = snackbar,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "conf_group", contentType = "conf") {
                SettingGroup(title = stringResource(R.string.mpv_conf)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.mpv_conf_custom),
                        subtitle = stringResource(R.string.mpv_conf_custom_desc),
                        icon = Icons.Default.Build,
                        checked = settings.engine.customMpvConfEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(engine = it.engine.copy(customMpvConfEnabled = enabled)) }
                        },
                    )
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.mpv_conf_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = conf,
                            onValueChange = { conf = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            placeholder = { Text(stringResource(R.string.mpv_conf_hint)) },
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { conf = appendPreset(conf, PRESET_DEBAND) }) {
                                Text(
                                    text = stringResource(R.string.mpv_preset_band),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            TextButton(onClick = { conf = appendPreset(conf, PRESET_HDR) }) {
                                Text(
                                    text = stringResource(R.string.mpv_preset_hdr),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { resetConfirmVisible = true }) {
                                Text(stringResource(R.string.common_reset))
                            }
                            TextButton(onClick = { viewModel.saveMpvConf(conf) }) {
                                Text(stringResource(R.string.common_save))
                            }
                        }
                    }
                }
            }

            item(key = "shaders_group", contentType = "shaders") {
                SettingGroup(title = stringResource(R.string.mpv_shaders)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.mpv_shader_master),
                        subtitle = stringResource(
                            if (settings.engine.shaderPipelineEnabled) {
                                R.string.mpv_shader_master_desc_on
                            } else {
                                R.string.mpv_shader_master_desc_off
                            },
                        ),
                        icon = XPlayerIcons.Grid,
                        checked = settings.engine.shaderPipelineEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(engine = it.engine.copy(shaderPipelineEnabled = enabled)) }
                        },
                    )
                    SettingRow(
                        title = stringResource(R.string.mpv_shader_import),
                        subtitle = stringResource(R.string.mpv_shader_empty_desc),
                        icon = Icons.Default.Add,
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        showDivider = shaders.isNotEmpty(),
                    )
                    if (shaders.isEmpty()) {
                        Text(
                            text = stringResource(R.string.mpv_shader_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    } else {
                        shaders.forEachIndexed { idx, shader ->
                            ShaderRow(
                                shader = shader,
                                canMoveUp = idx > 0,
                                canMoveDown = idx < shaders.lastIndex,
                                onToggle = { viewModel.setShaderEnabled(shader.id, it) },
                                onMoveUp = { viewModel.moveShader(shader.id, -1) },
                                onMoveDown = { viewModel.moveShader(shader.id, 1) },
                                onDelete = { pendingDeleteShader = shader },
                            )
                            if (idx < shaders.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 18.dp),
                                    thickness = 0.6.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                )
                            }
                        }
                    }
                }
            }

            item(key = "note", contentType = "note") {
                Text(
                    text = stringResource(R.string.mpv_lab_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }

    if (resetConfirmVisible) {
        AlertDialog(
            onDismissRequest = { resetConfirmVisible = false },
            title = { Text(stringResource(R.string.common_reset)) },
            text = { Text(stringResource(R.string.mpv_conf_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetConfirmVisible = false
                        viewModel.resetMpvConf()
                        conf = EnginePrefs.DEFAULT_MPV_CONF
                        lastLoadedConf = EnginePrefs.DEFAULT_MPV_CONF
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirmVisible = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    pendingDeleteShader?.let { shader ->
        AlertDialog(
            onDismissRequest = { pendingDeleteShader = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.mpv_shader_delete_confirm, shader.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteShader = null
                        viewModel.deleteShader(shader)
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteShader = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ShaderRow(
    shader: MpvShaderEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shader.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = shader.filePath.substringAfterLast('/'),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.mpv_shader_move_up))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.mpv_shader_move_down))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
        }
        Switch(checked = shader.enabled, onCheckedChange = onToggle)
    }
}

private fun appendPreset(current: String, preset: String): String {
    val trimmed = current.trimEnd()
    return if (trimmed.isEmpty()) preset else "$trimmed\n$preset"
}

private const val PRESET_DEBAND = "deband=yes\ndeband-iterations=2\ndeband-grain=yes\n"
private const val PRESET_HDR = "tone-mapping=bt.2446a\nhdr-compute-peak=yes\n"
