package com.xplayer.app.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.xplayer.app.data.datastore.ScreenshotFormat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 当前帧截图落盘：优先写入公共目录 Pictures/XPlayer（MediaStore，免权限），
 * 失败时回退到应用外部私有目录，保证截图不会丢。
 */
object ScreenshotSaver {

    fun save(context: Context, bitmap: Bitmap, format: ScreenshotFormat = ScreenshotFormat.PNG): Uri? {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val extension = if (format == ScreenshotFormat.PNG) "png" else "jpg"
        val fileName = "XPlayer_$stamp.$extension"
        val mime = if (format == ScreenshotFormat.PNG) "image/png" else "image/jpeg"
        val compressFormat = if (format == ScreenshotFormat.PNG) {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }

        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/XPlayer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    bitmap.compress(compressFormat, 95, output)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri
            }
        }

        return runCatching {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
                "XPlayer",
            ).apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { bitmap.compress(compressFormat, 95, it) }
            Uri.fromFile(file)
        }.getOrNull()
    }
}
