package com.dingwei.gpsmock.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标转换的正确性测试。
 *
 * 这些数学如果错了，定位会偏几百米且很难察觉，所以用**外部公开参考向量**
 * 而不是「自己算自己」来验证。
 */
class CoordinateConverterTest {

    private val tol = 1e-6

    @Test
    fun `WGS84 转 GCJ02 与公开参考向量一致`() {
        // eviltransform（广泛使用的火星坐标转换库）公开测试向量：
        //   wgs2gcj(31.1774276, 121.5272106) = (31.17530398364597, 121.531541859215)
        val (lat, lon) = CoordinateConverter.wgs84ToGcj02(31.1774276, 121.5272106)
        assertEquals(31.17530398364597, lat, tol)
        assertEquals(121.531541859215, lon, tol)
    }

    @Test
    fun `GCJ02 转 WGS84 是正转换的逆运算`() {
        val samples = listOf(
            39.9028 to 116.4011, // 北京
            31.2325 to 121.4691, // 上海
            23.1327 to 113.2592, // 广州
            22.5463 to 114.0528, // 深圳
            43.8242 to 87.6140,  // 乌鲁木齐
            18.2546 to 109.5076  // 三亚
        )
        samples.forEach { (lat, lon) ->
            val (gLat, gLon) = CoordinateConverter.wgs84ToGcj02(lat, lon)
            val (wLat, wLon) = CoordinateConverter.gcj02ToWgs84(gLat, gLon)
            assertEquals("纬度往返 $lat", lat, wLat, tol)
            assertEquals("经度往返 $lon", lon, wLon, tol)
        }
    }

    @Test
    fun `境外坐标不做偏移`() {
        // 东京、纽约：GCJ-02 只在中国大陆生效
        val overseas = listOf(35.6762 to 139.6503, 40.7128 to -74.0060)
        overseas.forEach { (lat, lon) ->
            val (gcjLat, gcjLon) = CoordinateConverter.wgs84ToGcj02(lat, lon)
            assertEquals(lat, gcjLat, 1e-12)
            assertEquals(lon, gcjLon, 1e-12)
            assertTrue(CoordinateConverter.outOfChina(lat, lon))
        }
        assertTrue(!CoordinateConverter.outOfChina(39.9028, 116.4011))
    }

    @Test
    fun `国内偏移量在 300 至 700 米之间`() {
        // 这个量级正是「必须做坐标系转换」的原因：不转换就会偏几百米。
        val samples = listOf(
            "北京" to (39.9028 to 116.4011),
            "上海" to (31.2325 to 121.4691),
            "广州" to (23.1327 to 113.2592),
            "乌鲁木齐" to (43.8242 to 87.6140)
        )
        samples.forEach { (name, point) ->
            val (gLat, gLon) = CoordinateConverter.wgs84ToGcj02(point.first, point.second)
            val metres = distanceMetres(point.first, point.second, gLat, gLon)
            assertTrue("$name 偏移 %.0f 米，超出预期区间".format(metres), metres in 250.0..700.0)
        }
    }

    @Test
    fun `百度坐标往返转换一致`() {
        val samples = listOf(39.9028 to 116.4011, 31.2325 to 121.4691)
        samples.forEach { (lat, lon) ->
            val (bLat, bLon) = CoordinateConverter.wgs84ToBd09(lat, lon)
            val (wLat, wLon) = CoordinateConverter.bd09ToWgs84(bLat, bLon)
            assertEquals(lat, wLat, tol)
            assertEquals(lon, wLon, tol)
        }
    }

    @Test
    fun `CoordinateSystem 枚举的转换互为逆运算`() {
        val lat = 30.5728
        val lon = 104.0638 // 成都
        CoordinateSystem.entries.forEach { system ->
            val (sLat, sLon) = system.fromWgs84(lat, lon)
            if (system == CoordinateSystem.WGS84) {
                assertEquals(lat, sLat, 1e-12)
                assertEquals(lon, sLon, 1e-12)
            } else {
                assertTrue("${system.short} 应与 WGS-84 有差异", distanceMetres(lat, lon, sLat, sLon) > 50)
            }
            val (backLat, backLon) = system.toWgs84(sLat, sLon)
            assertEquals("${system.short} 纬度往返", lat, backLat, tol)
            assertEquals("${system.short} 经度往返", lon, backLon, tol)
        }
    }

    private fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6371008.8
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * radius * asin(min(1.0, sqrt(a)))
    }
}
