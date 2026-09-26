package com.dingwei.gpsmock.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * 在线地理编码兜底：Photon（基于 OpenStreetMap，免 API Key）。
 *
 * 只在离线城市库搜不到时调用（例如搜索「东京」「某条街道」）。
 * Photon 返回的坐标是 **WGS-84**，与本 App 内部坐标系一致。
 *
 * 说明：这是公共免费服务，请勿高频调用；已设置 User-Agent 与超时。
 */
class OnlineGeocoder {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun search(query: String, limit: Int = 10): List<PlaceResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        runCatching {
            // 注意：Photon 的 lang 参数只支持 default/de/en/fr，传 lang=zh 会返回 HTTP 400；
            // 不传 lang 时它本身就返回中文本地化名称（"杭州市"/"浙江省"），故不能加 lang 参数。
            val url = URL("$ENDPOINT?q=${URLEncoder.encode(q, "UTF-8")}&limit=$limit")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8000
                readTimeout = 8000
            }
            try {
                if (conn.responseCode !in 200..299) {
                    Log.w(TAG, "在线搜索返回 HTTP ${conn.responseCode}")
                    return@runCatching emptyList()
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            } finally {
                conn.disconnect()
            }
        }.onFailure {
            Log.w(TAG, "在线搜索失败", it)
        }.getOrElse { emptyList() }
    }

    private fun parse(body: String): List<PlaceResult> {
        val root = json.parseToJsonElement(body).jsonObject
        val features = root["features"] as? JsonArray ?: return emptyList()
        return features.mapNotNull { element ->
            val feature = element.jsonObject
            val coords = (feature["geometry"]?.jsonObject?.get("coordinates") as? JsonArray) ?: return@mapNotNull null
            val lon = (coords.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val lat = (coords.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null

            val props = feature["properties"]?.jsonObject ?: return@mapNotNull null
            val name = (props["name"] as? JsonPrimitive)?.contentOrNullSafe()
                ?: (props["city"] as? JsonPrimitive)?.contentOrNullSafe()
                ?: return@mapNotNull null

            val detail = listOfNotNull(
                (props["city"] as? JsonPrimitive)?.contentOrNullSafe(),
                (props["state"] as? JsonPrimitive)?.contentOrNullSafe(),
                (props["country"] as? JsonPrimitive)?.contentOrNullSafe()
            ).filter { it.isNotEmpty() && it != name }.distinct().joinToString(" · ")

            PlaceResult(name, detail, lat, lon, PlaceResult.Source.ONLINE)
        }
    }

    /** JsonPrimitive.content 在 JsonNull 上会抛异常，这里统一安全取值。 */
    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null
        else content.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "OnlineGeocoder"
        const val ENDPOINT = "https://photon.komoot.io/api/"
        const val USER_AGENT = "DingweiGpsMock/1.0 (Android; personal use)"
    }
}
