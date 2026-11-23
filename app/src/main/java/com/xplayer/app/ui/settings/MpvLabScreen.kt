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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xplayer.app.R
import com.xplayer.app.data.datastore.EnginePrefs
import com.xplayer.app.data.local.MpvShaderEntity
import com.xplayer.app.ui.common.rememberAppVM
import com.xplayer.app.ui.components.SettingGroup
import com.xplayer.app.ui.components.SettingRow
import com.xplayer.app.ui.components.SettingSwitchRow

@Composable
fun MpvLabScreen(onBack: () -> Unit, viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val shaders by viewModel.shaders.collectAsState()
    var conf by remember { mutableStateOf(settings.engine.mpvConf) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importShader) }

    LaunchedEffect(settings.engine.mpvConf) {
        conf = settings.engine.mpvConf
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
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 40.dp),
        ) {
            item {
                SettingGroup(title = stringResource(R.string.mpv_conf)) {
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
                            placeholder = { Text("vo=gpu-next") },
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                conf = conf.trimEnd() + "\n" + PRESET_DEBAND
                            }) {
                                Text(
                                    text = stringResource(R.string.mpv_preset_band),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            TextButton(onClick = {
                                conf = conf.trimEnd() + "\n" + PRESET_HDR
                            }) {
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
                            TextButton(onClick = {
                                viewModel.resetMpvConf()
                                conf = EnginePrefs.DEFAULT_MPV_CONF
                            }) {
                                Text(stringResource(R.string.common_reset))
                            }
                            TextButton(onClick = { viewModel.saveMpvConf(conf) }) {
                                Text(stringResource(R.string.common_save))
                            }
                        }
                    }
                }
            }

            item {
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
                        icon = com.xplayer.app.ui.icons.XPlayerIcons.Grid,
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
                }
            }

            if (shaders.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.mpv_shader_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                items(shaders, key = { it.id }) { shader ->
                    ShaderRow(
                        shader = shader,
                        onToggle = { viewModel.setShaderEnabled(shader.id, it) },
                        onMoveUp = { viewModel.moveShader(shader.id, -1) },
                        onMoveDown = { viewModel.moveShader(shader.id, 1) },
                        onDelete = { viewModel.deleteShader(shader) },
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.mpv_lab_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ShaderRow(
    shader: MpvShaderEntity,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shader.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = shader.filePath.substringAfterLast('/'),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.mpv_shader_move_up))
        }
        IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.mpv_shader_move_down))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
        }
        Switch(checked = shader.enabled, onCheckedChange = onToggle)
    }
}

private const val PRESET_DEBAND = "deband=yes\ndeband-iterations=2\ndeband-grain=yes\n"
private const val PRESET_HDR = "tone-mapping=bt.2446a\nhdr-compute-peak=yes\n"
