package com.dingwei.gpsmock.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 城市搜索测试——直接用**真实的** `assets/cities.json`，
 * 验证「输入城市名/拼音/首字母即可定位」这条核心链路。
 */
class CityMatcherTest {

    private val cities: List<City> = loadRealCities()

    private fun loadRealCities(): List<City> {
        // 单元测试的工作目录是模块目录（app/）
        val candidates = listOf(
            File("src/main/assets/cities.json"),
            File("app/src/main/assets/cities.json")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("找不到 cities.json，已尝试：${candidates.map { it.absolutePath }}")
        return Json { ignoreUnknownKeys = true }.decodeFromString(file.readText())
    }

    private fun firstHit(query: String): String? = CityMatcher.search(cities, query).firstOrNull()?.name

    @Test
    fun `城市库规模与字段完整性`() {
        assertTrue("城市库应有至少 300 条，实际 ${cities.size}", cities.size >= 300)
        val missingPinyin = cities.count { it.pinyin.isBlank() || it.py.isBlank() }
        assertEquals("不应有拼音缺失的记录", 0, missingPinyin)
        assertTrue("应包含省级记录", cities.any { it.level == "province" })
        assertTrue("应包含地级市记录", cities.count { it.level == "city" } >= 300)
    }

    @Test
    fun `城市库坐标全部为中国境内合理值`() {
        // 同时也是一道「坐标系没被搞错」的护栏：若把 GCJ-02 当 WGS-84 或反之，
        // 数值仍在范围内，因此这里只做范围校验；坐标系由 verify_cities.py 与
        // CoordinateConverterTest 的量级断言把关。
        val bad = cities.filter { it.lat !in 3.0..54.0 || it.lon !in 73.0..136.0 }
        assertEquals("越界记录：${bad.take(3).map { it.name }}", 0, bad.size)
    }

    @Test
    fun `中文城市名可以搜到`() {
        assertEquals("北京市", firstHit("北京"))
        assertEquals("深圳市", firstHit("深圳"))
        assertEquals("杭州市", firstHit("杭州"))
        assertEquals("三亚市", firstHit("三亚"))
    }

    @Test
    fun `拼音全拼可以搜到`() {
        assertEquals("深圳市", firstHit("shenzhen"))
        assertEquals("杭州市", firstHit("hangzhou"))
        assertEquals("乌鲁木齐市", firstHit("wulumuqi"))
        // 库里拼音是全名音译（beijingshi），输入不带「市」也应命中
        assertEquals("北京市", firstHit("beijing"))
    }

    @Test
    fun `拼音首字母可以搜到`() {
        assertEquals("北京市", firstHit("bj"))
        assertEquals("乌鲁木齐市", firstHit("wlmq"))
        assertEquals("杭州市", firstHit("hz"))
    }

    @Test
    fun `多音字城市按正确读音匹配`() {
        assertEquals("重庆市", firstHit("chongqing"))
        assertEquals("厦门市", firstHit("xiamen"))
        assertEquals("六安市", firstHit("luan"))
        assertEquals("蚌埠市", firstHit("bengbu"))
    }

    @Test
    fun `省份名可以列出该省城市`() {
        val results = CityMatcher.search(cities, "广东省", limit = 50)
        assertTrue("搜索省份应返回多个城市，实际 ${results.size}", results.size > 5)
        assertTrue(results.all { it.detail.contains("广东省") || it.name == "广东省" })
    }

    @Test
    fun `无匹配时返回空列表而不是全部数据`() {
        assertTrue(CityMatcher.search(cities, "zzzzzzzzzz").isEmpty())
        assertTrue(CityMatcher.search(cities, "").isEmpty())
        assertTrue(CityMatcher.search(cities, "   ").isEmpty())
    }

    @Test
    fun `精确匹配优先于模糊匹配`() {
        // 「杭州市」应当排在所有仅「包含杭州」的结果之前
        val results = CityMatcher.search(cities, "杭州市")
        assertEquals("杭州市", results.first().name)
    }

    @Test
    fun `搜索结果携带可用的坐标与来源`() {
        val results = CityMatcher.search(cities, "深圳", limit = 1)
        assertEquals(1, results.size)
        val hit = results.first()
        assertEquals(PlaceResult.Source.OFFLINE, hit.source)
        assertTrue(hit.lat in 22.0..23.0)
        assertTrue(hit.lon in 113.0..115.0)
        assertTrue(hit.detail.isNotBlank())
    }
}
