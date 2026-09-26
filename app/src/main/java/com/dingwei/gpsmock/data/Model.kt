package com.dingwei.gpsmock.data

import kotlinx.serialization.Serializable

/**
 * 离线城市库条目。
 * [lat]/[lon] 一律为 **WGS-84**（与 Android 定位框架、OSM 地图一致）。
 */
@Serializable
data class City(
    val name: String,
    val province: String = "",
    val lat: Double,
    val lon: Double,
    val pinyin: String = "",
    val py: String = "",
    val level: String = "city"
)

/** WGS-84 经纬度点。 */
data class LatLon(val lat: Double, val lon: Double)

/** 一条地点搜索结果（离线城市库或在线地理编码）。 */
data class PlaceResult(
    val name: String,
    val detail: String,
    val lat: Double,
    val lon: Double,
    val source: Source
) {
    enum class Source {
        /** 内置离线城市库 */
        OFFLINE,

        /** 在线地理编码（OSM/Photon） */
        ONLINE
    }
}

/** 模拟参数。坐标一律 WGS-84。 */
data class MockParams(
    val lat: Double = DEFAULT_LAT,
    val lon: Double = DEFAULT_LON,
    val placeName: String = "北京市",
    val altitude: Double = 50.0,
    val accuracy: Float = 5f,
    val speed: Float = 0f,
    val bearing: Float = 0f
) {
    companion object {
        /**
         * 默认点：北京市中心，WGS-84，与内置城市库（cities.json）中的北京市一致。
         *
         * 注意：中文网站常见的「北京 39.9042, 116.4074」是 **GCJ-02 火星坐标**，
         * 不是 WGS-84；按 WGS-84 用会偏约 550 米。
         */
        const val DEFAULT_LAT = 39.9028
        const val DEFAULT_LON = 116.4011
    }
}

/** 收藏地点。 */
@Serializable
data class Favorite(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val altitude: Double = 50.0,
    val accuracy: Float = 5f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val savedAt: Long = 0L
)
