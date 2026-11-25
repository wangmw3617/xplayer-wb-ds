package com.xplayer.app.data.repository

import com.xplayer.app.data.local.StreamUrlDao
import com.xplayer.app.data.local.StreamUrlEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject

class StreamRepository(private val dao: StreamUrlDao) {

    val items: Flow<List<StreamUrlEntity>> = dao.observeAll()

    suspend fun remember(
        url: String,
        title: String? = null,
        headersJson: String? = null,
        overwriteMeta: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return@withContext
        val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() }
        val cleanHeaders = headersJson?.trim()?.takeIf { it.isNotEmpty() }
        val existing = dao.findByUrl(cleanUrl)
        dao.upsert(
            StreamUrlEntity(
                id = existing?.id ?: 0L,
                url = cleanUrl,
                customTitle = if (overwriteMeta) cleanTitle else (cleanTitle ?: existing?.customTitle),
                headersJson = if (overwriteMeta) cleanHeaders else (cleanHeaders ?: existing?.headersJson),
                lastPlayedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clear()
    }

    suspend fun find(url: String): StreamUrlEntity? = withContext(Dispatchers.IO) {
        dao.findByUrl(url.trim())
    }

    companion object {
        /** 将多行 `"Key: Value"` 文本编码为 JSON 字符串，忽略空行与无效行 */
        fun encodeHeadersText(raw: String): String? {
            val map = linkedMapOf<String, String>()
            raw.lineSequence().forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        map[key] = value
                    }
                }
            }
            if (map.isEmpty()) return null
            val obj = JSONObject()
            map.forEach { (k, v) -> obj.put(k, v) }
            return obj.toString()
        }

        fun parseHeaders(json: String?): Map<String, String> {
            if (json.isNullOrBlank()) return emptyMap()
            return runCatching {
                val obj = JSONObject(json)
                buildMap {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = obj.optString(k, "").trim()
                        if (k.isNotBlank() && v.isNotEmpty()) put(k, v)
                    }
                }
            }.getOrDefault(emptyMap())
        }

        fun formatHeadersText(json: String?): String =
            parseHeaders(json).entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }
}
