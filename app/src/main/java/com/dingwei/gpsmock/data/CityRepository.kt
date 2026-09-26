package com.dingwei.gpsmock.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 离线城市库：读取 `assets/cities.json`。
 *
 * 命中时瞬时返回、不依赖网络；未命中由 [OnlineGeocoder] 兜底。
 * 匹配与排序逻辑在 [CityMatcher]（纯函数，可单元测试）。
 *
 * 坐标说明：`cities.json` 中的经纬度**已统一为 WGS-84**
 * （原始数据源为高德 GCJ-02 火星坐标，构建时已反解，详见 README）。
 */
class CityRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    private var cities: List<City> = emptyList()

    @Volatile
    private var loaded = false

    /** 已加载的城市条目数（未加载时为 0）。 */
    val size: Int get() = cities.size

    /** 加载城市库（幂等）。返回条目数。 */
    suspend fun ensureLoaded(): Int = withContext(Dispatchers.IO) {
        if (loaded) return@withContext cities.size
        cities = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.mapCatching { text ->
            json.decodeFromString<List<City>>(text)
        }.onFailure {
            Log.e(TAG, "城市库加载失败", it)
        }.getOrElse { emptyList() }

        // 同名同省去重（直辖市的省级/地级两条记录内容相同）
        cities = cities.distinctBy { "${it.province}|${it.name}" }
        loaded = true
        Log.i(TAG, "城市库已加载：${cities.size} 条")
        cities.size
    }

    /** 离线搜索。 */
    suspend fun search(query: String, limit: Int = 20): List<PlaceResult> {
        if (query.isBlank()) return emptyList()
        ensureLoaded()
        return CityMatcher.search(cities, query, limit)
    }

    private companion object {
        const val TAG = "CityRepository"
        const val ASSET_NAME = "cities.json"
    }
}
