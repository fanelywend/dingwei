package com.dingwei.gpsmock.data

import android.content.Context
import com.dingwei.gpsmock.geo.CoordinateSystem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 轻量持久化：收藏列表、上次参数、坐标系选择。 */
class PrefsStore(context: Context) {

    private val sp = context.getSharedPreferences("dingwei_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 显式指定序列化器，避免 encodeToString 的重载歧义。 */
    private val favoritesSerializer = ListSerializer(Favorite.serializer())

    fun loadFavorites(): List<Favorite> = runCatching {
        sp.getString(KEY_FAVORITES, null)
            ?.let { json.decodeFromString(favoritesSerializer, it) }
            ?: emptyList()
    }.getOrElse { emptyList() }

    fun saveFavorites(list: List<Favorite>) {
        sp.edit().putString(KEY_FAVORITES, json.encodeToString(favoritesSerializer, list)).apply()
    }

    fun loadParams(): MockParams {
        val lat = sp.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return MockParams()
        val lon = sp.getString(KEY_LON, null)?.toDoubleOrNull() ?: return MockParams()
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return MockParams()
        return MockParams(
            lat = lat,
            lon = lon,
            placeName = sp.getString(KEY_PLACE, null) ?: "",
            altitude = sp.getString(KEY_ALT, null)?.toDoubleOrNull() ?: 50.0,
            accuracy = sp.getString(KEY_ACC, null)?.toFloatOrNull() ?: 5f,
            speed = sp.getString(KEY_SPEED, null)?.toFloatOrNull() ?: 0f,
            bearing = sp.getString(KEY_BEARING, null)?.toFloatOrNull() ?: 0f
        )
    }

    fun saveParams(p: MockParams) {
        sp.edit()
            .putString(KEY_LAT, p.lat.toString())
            .putString(KEY_LON, p.lon.toString())
            .putString(KEY_PLACE, p.placeName)
            .putString(KEY_ALT, p.altitude.toString())
            .putString(KEY_ACC, p.accuracy.toString())
            .putString(KEY_SPEED, p.speed.toString())
            .putString(KEY_BEARING, p.bearing.toString())
            .apply()
    }

    fun loadCoordSystem(): CoordinateSystem {
        val name = sp.getString(KEY_COORD_SYS, null) ?: return CoordinateSystem.WGS84
        return runCatching { CoordinateSystem.valueOf(name) }.getOrElse { CoordinateSystem.WGS84 }
    }

    fun saveCoordSystem(system: CoordinateSystem) {
        sp.edit().putString(KEY_COORD_SYS, system.name).apply()
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_PLACE = "place"
        const val KEY_ALT = "alt"
        const val KEY_ACC = "acc"
        const val KEY_SPEED = "speed"
        const val KEY_BEARING = "bearing"
        const val KEY_COORD_SYS = "coord_sys"
    }
}
