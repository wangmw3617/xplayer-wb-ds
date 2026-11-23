package com.xplayer.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.xplayer.app.data.local.MpvShaderDao
import com.xplayer.app.data.local.MpvShaderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * GLSL 着色器管理：文件统一拷贝进应用私有目录，避免外部 Uri 授权失效。
 */
class ShaderRepository(
    private val context: Context,
    private val dao: MpvShaderDao,
) {

    val shaders: Flow<List<MpvShaderEntity>> = dao.observeAll()

    private val shaderDir: File
        get() = File(context.filesDir, "shaders").apply { mkdirs() }

    suspend fun import(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri) ?: "shader_${System.currentTimeMillis()}.glsl"
            val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val target = File(shaderDir, uniqueName(safeName))
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("cannot open stream")

            val existing = dao.observeAll().first()
            dao.upsert(
                MpvShaderEntity(
                    name = target.nameWithoutExtension,
                    filePath = target.absolutePath,
                    enabled = true,
                    orderIndex = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1,
                ),
            )
            target.name
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun delete(entity: MpvShaderEntity) {
        runCatching { File(entity.filePath).delete() }
        dao.delete(entity.id)
    }

    /** 与相邻项交换顺序，实现拖拽排序的简化版（上移 / 下移） */
    suspend fun move(id: Long, delta: Int) = withContext(Dispatchers.IO) {
        val list = dao.observeAll().first()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return@withContext
        val target = index + delta
        if (target !in list.indices) return@withContext
        val current = list[index]
        val neighbour = list[target]
        dao.setOrder(current.id, neighbour.orderIndex)
        dao.setOrder(neighbour.id, current.orderIndex)
    }

    suspend fun enabledPaths(): List<String> =
        dao.observeAll().first().filter { it.enabled }.sortedBy { it.orderIndex }.map { it.filePath }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: uri.lastPathSegment
    }

    private fun uniqueName(name: String): String {
        var candidate = File(shaderDir, name)
        var counter = 1
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "glsl")
        while (candidate.exists()) {
            candidate = File(shaderDir, "${base}_$counter.$ext")
            counter++
        }
        return candidate.name
    }
}
