package com.dingwei.gpsmock.geo

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标系定义与互转。
 *
 * | 坐标系 | 使用者 | 说明 |
 * |---|---|---|
 * | WGS-84 | GPS 硬件、OSM 地图、Android 系统定位框架 | GPS 原始坐标 |
 * | GCJ-02 | 高德/腾讯/微信/滴滴等几乎全部国内 App | 「火星坐标」，国测局加密偏移 |
 * | BD-09 | 百度地图 | 在 GCJ-02 上再偏移一次 |
 *
 * 本 App 内部一律以 **WGS-84** 存储和推送：Android 定位框架给 App 的是 WGS-84，
 * 国内 App 自己会转成 GCJ-02 显示。若推送时先转成 GCJ-02，国内 App 会二次偏移，
 * 导致定位偏 300~700 米。
 */
enum class CoordinateSystem(val label: String, val short: String) {
    WGS84("WGS-84（GPS 原始，OSM）", "WGS84"),
    GCJ02("GCJ-02（火星坐标，高德/腾讯）", "GCJ02"),
    BD09("BD-09（百度）", "BD09");

    /** 把本坐标系的坐标转成 WGS-84。 */
    fun toWgs84(lat: Double, lon: Double): Pair<Double, Double> = when (this) {
        WGS84 -> lat to lon
        GCJ02 -> CoordinateConverter.gcj02ToWgs84(lat, lon)
        BD09 -> CoordinateConverter.bd09ToWgs84(lat, lon)
    }

    /** 把 WGS-84 坐标转成本坐标系。 */
    fun fromWgs84(lat: Double, lon: Double): Pair<Double, Double> = when (this) {
        WGS84 -> lat to lon
        GCJ02 -> CoordinateConverter.wgs84ToGcj02(lat, lon)
        BD09 -> CoordinateConverter.wgs84ToBd09(lat, lon)
    }
}

object CoordinateConverter {

    private const val PI = Math.PI
    /** 克拉索夫斯基椭球长半轴 */
    private const val A = 6378245.0
    /** 偏心率平方 */
    private const val EE = 0.00669342162296594323

    /** 中国大陆粗略矩形范围；境外不偏移（国际坐标系本就一致）。 */
    fun outOfChina(lat: Double, lon: Double): Boolean =
        lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

    fun wgs84ToGcj02(lat: Double, lon: Double): Pair<Double, Double> {
        if (outOfChina(lat, lon)) return lat to lon
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return (lat + dLat) to (lon + dLon)
    }

    /**
     * GCJ-02 → WGS-84。
     * 偏移量随位置变化，没有解析反函数，用迭代逼近：每轮用正算的残差修正，
     * 3 轮即可收敛到 1e-9 度（约 0.1 毫米）量级。
     */
    fun gcj02ToWgs84(lat: Double, lon: Double): Pair<Double, Double> {
        if (outOfChina(lat, lon)) return lat to lon
        var wgsLat = lat
        var wgsLon = lon
        repeat(3) {
            val (gLat, gLon) = wgs84ToGcj02(wgsLat, wgsLon)
            wgsLat += lat - gLat
            wgsLon += lon - gLon
        }
        return wgsLat to wgsLon
    }

    fun gcj02ToBd09(lat: Double, lon: Double): Pair<Double, Double> {
        val z = sqrt(lon * lon + lat * lat) + 0.00002 * sin(lat * PI * 3000.0 / 180.0)
        val theta = atan2(lat, lon) + 0.000003 * cos(lon * PI * 3000.0 / 180.0)
        return (z * sin(theta) + 0.006) to (z * cos(theta) + 0.0065)
    }

    fun bd09ToGcj02(lat: Double, lon: Double): Pair<Double, Double> {
        val x = lon - 0.0065
        val y = lat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * PI * 3000.0 / 180.0)
        val theta = atan2(y, x) - 0.000003 * cos(x * PI * 3000.0 / 180.0)
        return (z * sin(theta)) to (z * cos(theta))
    }

    fun wgs84ToBd09(lat: Double, lon: Double): Pair<Double, Double> {
        val (gLat, gLon) = wgs84ToGcj02(lat, lon)
        return gcj02ToBd09(gLat, gLon)
    }

    fun bd09ToWgs84(lat: Double, lon: Double): Pair<Double, Double> {
        val (gLat, gLon) = bd09ToGcj02(lat, lon)
        return gcj02ToWgs84(gLat, gLon)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
