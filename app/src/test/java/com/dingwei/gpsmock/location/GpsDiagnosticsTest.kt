package com.dingwei.gpsmock.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自检判定逻辑的测试。
 *
 * 这段逻辑决定「注入是否生效」的结论，若判错会把排查方向带偏，所以必须有测试。
 */
class GpsDiagnosticsTest {

    private val targetLat = 22.5463
    private val targetLon = 114.0528

    private fun probe(
        name: String,
        registered: Boolean = true,
        enabled: Boolean = true,
        lat: Double? = null,
        lon: Double? = null,
        isMock: Boolean? = null,
        ageMs: Long? = 100,
        error: String? = null
    ) = ProviderProbe(name, registered, enabled, error, lat, lon, isMock, ageMs)

    private fun snapshot(probes: List<ProviderProbe>, failures: Map<String, String> = emptyMap()) =
        DiagnosticsSnapshot(
            sdkInt = 31,
            androidRelease = "12",
            packageName = "com.dingwei.gpsmock",
            appOpsMode = 0,
            appOpsModeName = GpsDiagnostics.appOpsModeName(0),
            isMockAppSelected = true,
            targetLat = targetLat,
            targetLon = targetLon,
            pushedRecently = true,
            lastPushAgeMs = 500,
            serviceRunning = true,
            probes = probes,
            pushFailures = failures
        )

    @Test
    fun `平台读回等于目标坐标时判定为注入成功`() {
        val s = snapshot(
            listOf(
                probe("gps", lat = targetLat, lon = targetLon, isMock = true),
                probe("network", lat = targetLat, lon = targetLon, isMock = true),
                probe("fused", registered = false, error = "provider 已存在")
            )
        )
        assertEquals(InjectionVerdict.INJECTION_OK, s.verdict)
    }

    @Test
    fun `平台读回是真实位置时判定为注入未生效`() {
        val s = snapshot(
            listOf(
                probe("gps", lat = 39.9042, lon = 116.4074, isMock = false),
                probe("network", lat = 39.9040, lon = 116.4070, isMock = false)
            )
        )
        assertEquals(InjectionVerdict.INJECTION_FAILED, s.verdict)
    }

    @Test
    fun `一个 provider 命中即算成功`() {
        val s = snapshot(
            listOf(
                probe("gps", lat = targetLat, lon = targetLon, isMock = true),
                probe("network", lat = 39.9042, lon = 116.4074, isMock = false)
            )
        )
        assertEquals(InjectionVerdict.INJECTION_OK, s.verdict)
    }

    @Test
    fun `没有任何 provider 注册时无法判断`() {
        val s = snapshot(
            listOf(
                probe("gps", registered = false, error = "无模拟位置权限"),
                probe("network", registered = false, error = "无模拟位置权限")
            )
        )
        assertEquals(InjectionVerdict.NO_DATA, s.verdict)
    }

    @Test
    fun `注册了但读不到任何位置时也无法判断`() {
        val s = snapshot(listOf(probe("gps"), probe("network")))
        assertEquals(InjectionVerdict.NO_DATA, s.verdict)
    }

    @Test
    fun `命中判定有容差——几十米内的坐标舍入仍算命中`() {
        // 约 30 米偏差
        val s = snapshot(listOf(probe("gps", lat = targetLat + 0.00027, lon = targetLon, isMock = true)))
        assertEquals(InjectionVerdict.INJECTION_OK, s.verdict)
    }

    @Test
    fun `命中判定不会被 GCJ02 量级的偏移误判为命中`() {
        // 约 550 米偏差（火星坐标偏移量级）不应算命中
        val s = snapshot(listOf(probe("gps", lat = targetLat + 0.005, lon = targetLon, isMock = false)))
        assertEquals(InjectionVerdict.INJECTION_FAILED, s.verdict)
    }

    @Test
    fun `AppOps 模式值到名称的映射`() {
        assertEquals("MODE_ALLOWED(已允许)", GpsDiagnostics.appOpsModeName(0))
        assertEquals("MODE_DEFAULT(未授权)", GpsDiagnostics.appOpsModeName(3))
        assertTrue(GpsDiagnostics.appOpsModeName(-1).contains("未知"))
    }

    @Test
    fun `报告包含排查所需的关键字段`() {
        val s = snapshot(
            listOf(probe("gps", lat = 39.9042, lon = 116.4074, isMock = false)),
            failures = mapOf("network" to "SecurityException: denied")
        )
        val report = GpsDiagnostics.buildReport(s)
        listOf(
            "自检报告", "API 31", "Android: 12", "com.dingwei.gpsmock",
            "模拟位置应用授权", "MODE_ALLOWED", "前台服务", "目标坐标",
            "[gps]", "读回", "结论", "注入未生效",
            "[network] SecurityException: denied"
        ).forEach { expected ->
            assertTrue("报告应包含「$expected」，实际报告：\n$report", report.contains(expected))
        }
    }

    @Test
    fun `距离计算量级正确`() {
        // 同纬度 0.001 度经度，在 22.5°N 约 103 米
        val d = GpsDiagnostics.distanceMetres(22.5, 114.0, 22.5, 114.001)
        assertTrue("实际 %.1f 米".format(d), d in 95.0..110.0)
        assertEquals(0.0, GpsDiagnostics.distanceMetres(22.5, 114.0, 22.5, 114.0), 0.001)
    }

    @Test
    fun `无坐标时格式化不崩溃`() {
        assertEquals("（无数据）", GpsDiagnostics.formatCoord(null, null))
        assertEquals("—", GpsDiagnostics.formatAge(null))
        assertEquals(500L, 500L)
    }
}
