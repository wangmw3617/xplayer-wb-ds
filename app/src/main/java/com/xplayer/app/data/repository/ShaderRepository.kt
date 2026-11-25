package com.xplayer.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.xplayer.app.data.local.MpvShaderDao
import com.xplayer.app.data.local.MpvShaderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * GLSL 着色器管理：校验格式与体积后拷贝进应用私有目录，
 * 并通过互斥锁与 Room 事务保障排序、删除与文件一致性。
 */
class ShaderRepository(
    private val context: Context,
    private val dao: MpvShaderDao,
) {

    val shaders: Flow<List<MpvShaderEntity>> = dao.observeAll()

    private val mutex = Mutex()

    private fun shaderDir(): File = File(context.filesDir, "shaders").apply { mkdirs() }

    suspend fun import(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            var target: File? = null
            runCatching {
                val resolver = context.contentResolver
                var displayName: String? = null
                var declaredSize = -1L
                runCatching {
                    resolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) declaredSize = cursor.getLong(sizeIdx)
                        }
                    }
                }
                if (declaredSize > MAX_SHADER_BYTES) error("INVALID_SHADER_SIZE")

                val rawName = displayName?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: "shader_${System.currentTimeMillis()}.glsl"
                val ext = rawName.substringAfterLast('.', "").lowercase(Locale.US)
                if (ext.isNotEmpty() && ext !in ALLOWED_EXTENSIONS) {
                    error("INVALID_SHADER_EXT")
                }

                val safeName = sanitizeFileName(rawName)
                val dir = shaderDir()
                val outFile = File(dir, uniqueName(dir, safeName))
                target = outFile

                resolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_SHADER_BYTES) error("INVALID_SHADER_SIZE")
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: error("CANNOT_OPEN_STREAM")

                if (outFile.length() <= 0L) error("EMPTY_SHADER")

                val existing = dao.observeAll().first()
                dao.upsert(
                    MpvShaderEntity(
                        name = outFile.nameWithoutExtension.ifBlank { outFile.name },
                        filePath = outFile.absolutePath,
                        enabled = true,
                        orderIndex = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1,
                    ),
                )
                pruneOrphans(dir, existing.mapTo(HashSet()) { it.filePath } + outFile.absolutePath)
                outFile.name
            }.onFailure {
                target?.let { f -> runCatching { f.delete() } }
            }
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.setEnabled(id, enabled)
    }

    suspend fun delete(entity: MpvShaderEntity) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { File(entity.filePath).delete() }
            dao.delete(entity.id)
        }
    }

    /** 与相邻项事务交换顺序；若历史数据出现相同 orderIndex，则先按当前顺序重排后再交换 */
    suspend fun move(id: Long, delta: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = dao.observeAll().first()
            val index = list.indexOfFirst { it.id == id }
            if (index < 0) return@withLock
            val target = index + delta
            if (target !in list.indices) return@withLock
            val current = list[index]
            val neighbour = list[target]
            if (current.orderIndex == neighbour.orderIndex) {
                val reordered = list.toMutableList().apply {
                    set(index, neighbour)
                    set(target, current)
                }
                reordered.forEachIndexed { idx, item ->
                    dao.setOrder(item.id, idx)
                }
            } else {
                dao.swapOrder(current.id, neighbour.orderIndex, neighbour.id, current.orderIndex)
            }
        }
    }

    suspend fun enabledPaths(): List<String> = withContext(Dispatchers.IO) {
        dao.observeAll().first()
            .filter { it.enabled && File(it.filePath).isFile }
            .sortedBy { it.orderIndex }
            .map { it.filePath }
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimStart('.')
        val withExt = if ('.' in cleaned) cleaned else "$cleaned.glsl"
        return withExt.ifBlank { "shader_${System.currentTimeMillis()}.glsl" }
    }

    private fun uniqueName(dir: File, name: String): String {
        var candidate = File(dir, name)
        var counter = 1
        val base = name.substringBeforeLast('.', name).ifBlank { "shader" }
        val ext = name.substringAfterLast('.', "glsl").ifBlank { "glsl" }
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$counter.$ext")
            counter++
        }
        return candidate.name
    }

    private fun pruneOrphans(dir: File, activePaths: Set<String>) {
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath !in activePaths) {
                runCatching { file.delete() }
            }
        }
    }

    companion object {
        private const val MAX_SHADER_BYTES = 4L * 1024 * 1024
        private val ALLOWED_EXTENSIONS = setOf("glsl", "hook", "frag", "fsh", "txt")
    }
}
