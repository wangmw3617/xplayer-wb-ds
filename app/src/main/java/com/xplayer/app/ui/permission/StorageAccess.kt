package com.xplayer.app.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect

object StorageAccess {

    fun isGranted(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun needsLegacyRuntimePermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    fun settingsIntent(context: Context): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    } else {
        null
    }
}

data class StoragePermissionState(
    val granted: Boolean,
    val request: () -> Unit,
)

/**
 * 首次启动的存储授权引导：Android 11+ 走「所有文件访问权限」，
 * Android 10 退回 READ_EXTERNAL_STORAGE 运行时权限；返回应用后自动复查。
 */
@Composable
fun rememberStoragePermission(): StoragePermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StorageAccess.isGranted(context)) }

    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result -> granted = result || StorageAccess.isGranted(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = StorageAccess.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return StoragePermissionState(
        granted = granted,
        request = {
            if (StorageAccess.needsLegacyRuntimePermission()) {
                legacyLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                StorageAccess.settingsIntent(context)?.let { intent ->
                    runCatching { context.startActivity(intent) }
                }
            }
        },
    )
}
