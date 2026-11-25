package com.xplayer.app.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

object StorageAccess {

    fun hasFullStorageAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    } else {
        hasLegacyReadPermission(context)
    }

    fun hasScopedVideoPermission(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO,
            ) == PackageManager.PERMISSION_GRANTED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> false
        else -> hasLegacyReadPermission(context)
    }

    fun isGranted(context: Context): Boolean =
        hasFullStorageAccess(context) || hasScopedVideoPermission(context)

    fun runtimePermission(): String? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> Manifest.permission.READ_EXTERNAL_STORAGE
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_VIDEO
        else -> null
    }

    fun settingsIntent(context: Context): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    } else {
        appDetailsIntent(context)
    }

    fun fallbackManageAllFilesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    private fun hasLegacyReadPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

    internal fun Context.findActivity(): Activity? {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}

data class StoragePermissionState(
    val granted: Boolean,
    val fullAccess: Boolean,
    val request: () -> Unit,
)

/**
 * 存储授权引导：
 * - Android 11+ 优先跳转「所有文件访问权限」，TV/定制 ROM 异常时逐级回退；Android 13+ 同时兼容 READ_MEDIA_VIDEO 降级；
 * - Android 10 走 READ_EXTERNAL_STORAGE；若被永久拒绝（不再询问）则直接打开应用详情页。
 */
@Composable
fun rememberStoragePermission(): StoragePermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StorageAccess.isGranted(context)) }
    var fullAccess by remember { mutableStateOf(StorageAccess.hasFullStorageAccess(context)) }
    var askedOnce by remember { mutableStateOf(false) }

    fun refreshState() {
        granted = StorageAccess.isGranted(context)
        fullAccess = StorageAccess.hasFullStorageAccess(context)
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        askedOnce = true
        refreshState()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val latestRequest by rememberUpdatedState {
        fun openSettingsWithFallback() {
            val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                listOf(
                    StorageAccess.settingsIntent(context),
                    StorageAccess.fallbackManageAllFilesIntent(),
                    StorageAccess.appDetailsIntent(context),
                )
            } else {
                listOf(StorageAccess.appDetailsIntent(context))
            }
            for (intent in candidates) {
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(intent) }.isSuccess) break
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val perm = Manifest.permission.READ_EXTERNAL_STORAGE
            val activity = with(StorageAccess) { context.findActivity() }
            val permanentlyDenied = askedOnce &&
                activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
            if (permanentlyDenied) {
                openSettingsWithFallback()
            } else {
                runtimeLauncher.launch(perm)
            }
        } else {
            openSettingsWithFallback()
        }
    }

    return remember(granted, fullAccess) {
        StoragePermissionState(
            granted = granted,
            fullAccess = fullAccess,
            request = { latestRequest() },
        )
    }
}
