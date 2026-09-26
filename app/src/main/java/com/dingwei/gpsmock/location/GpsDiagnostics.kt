package com.dingwei.gpsmock.location

import android.location.Location

/**
 * 模拟注入的诊断结果。
 *
 * 用途：模拟定位「看起来开了但别的 App 还是真实位置」时，必须先分清是哪一类原因，
 * 否则无从下手：
 *
 * 1. **注入失败**：平台读回的仍是真实坐标 → 本 App 的问题（权限/注册/写入）。
 * 2. **注入成功但消费方没用系统定位**：平台读回已是模拟坐标，但目标 App 用的是
 *    自带网络定位（WiFi/基站）或缓存 → 不是本 App 能解决的。
 */
data class ProviderProbe(
    /** provider 名称：gps / network / fused */
    val name: String,
    /** 本 App 是否成功把它注册成了测试 provider */
    val registered: Boolean,
    /** 系统认为该 provider 是否启用 */
    val enabled: Boolean,
    /** 注册/探测时的失败原因 */
    val error: String?,
    /** 平台读回的坐标（null = 系统没有该 provider 的位置） */
    val lat: Double?,
    val lon: Double?,
    /** 平台是否把它标记为模拟位置（API 31+ 用 isMock） */
    val isMock: Boolean?,
    /** 该位置的年龄（毫秒），null 表示无数据 */
    val ageMs: Long?
) {
    /** 读回坐标与目标坐标的距离（米）；无数据时为 null。 */
    fun distanceTo(targetLat: Double, targetLon: Double): Double? {
        val la = lat ?: return null
        val lo = lon ?: return null
        return GpsDiagnostics.distanceMetres(targetLat, targetLon, la, lo)
    }

    /** 是否确认收到了我们的模拟位置（距离足够近即认为命中）。 */
    fun matches(targetLat: Double, targetLon: Double): Boolean {
        val d = distanceTo(targetLat, targetLon) ?: return false
        return d < MATCH_TOLERANCE_M
    }

    companion object {
        /** 判定「命中」的距离容差：远大于坐标舍入误差，远小于 GCJ-02 偏移（数百米）。 */
        const val MATCH_TOLERANCE_M = 100.0
    }
}

/** 注入链路的总体结论。 */
enum class InjectionVerdict {
    /** 平台读回的就是我们推的坐标 → 注入成功，问题在消费方 */
    INJECTION_OK,

    /** 平台读回的不是我们推的坐标 → 注入未生效 */
    INJECTION_FAILED,

    /** 没有可用的读回数据，无法判断 */
    NO_DATA
}

/** 一次完整的诊断快照。 */
data class DiagnosticsSnapshot(
    val sdkInt: Int,
    val androidRelease: String,
    val packageName: String,
    /** AppOps 的原始返回值，能区分「未授权(MODE_DEFAULT)」与其他异常 */
    val appOpsMode: Int,
    val appOpsModeName: String,
    val isMockAppSelected: Boolean,
    val targetLat: Double,
    val targetLon: Double,
    val pushedRecently: Boolean,
    val lastPushAgeMs: Long?,
    val serviceRunning: Boolean,
    val probes: List<ProviderProbe>,
    val pushFailures: Map<String, String>,
    /** 采集时的网络状态——排查「一联网就失效」时必须知道当时是否联网 */
    val wifiConnected: Boolean = false,
    val mobileConnected: Boolean = false
) {
    val verdict: InjectionVerdict
        get() {
            if (probes.none { it.registered }) return InjectionVerdict.NO_DATA
            val hit = probes.any { it.matches(targetLat, targetLon) }
            if (hit) return InjectionVerdict.INJECTION_OK
            if (probes.all { it.lat == null }) return InjectionVerdict.NO_DATA
            return InjectionVerdict.INJECTION_FAILED
        }
}

/** 诊断数据的取整与格式化（纯逻辑，便于单元测试）。 */
object GpsDiagnostics {

    fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6371008.8
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dp / 2) * Math.sin(dp / 2) +
            Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
        return 2 * radius * Math.asin(minOf(1.0, Math.sqrt(a)))
    }

    /** AppOps 模式值 → 可读名称。 */
    fun appOpsModeName(mode: Int): String = when (mode) {
        0 -> "MODE_ALLOWED(已允许)"
        1 -> "MODE_IGNORED(忽略)"
        2 -> "MODE_ERRORED(出错)"
        3 -> "MODE_DEFAULT(未授权)"
        else -> "未知($mode)"
    }

    fun formatCoord(lat: Double?, lon: Double?): String =
        if (lat == null || lon == null) "（无数据）" else "%.6f, %.6f".format(lat, lon)

    fun formatAge(ageMs: Long?): String = when {
        ageMs == null -> "—"
        ageMs < 1000 -> "${ageMs}ms"
        ageMs < 60_000 -> "%.1fs".format(ageMs / 1000.0)
        else -> "%.1f分钟".format(ageMs / 60_000.0)
    }

    fun verdictText(verdict: InjectionVerdict): String = when (verdict) {
        InjectionVerdict.INJECTION_OK ->
            "✅ 注入成功：系统读回的就是目标坐标。若其他 App 仍显示真实位置，" +
                "说明该 App 没有使用系统定位（多为自己实现的 WiFi/基站定位或缓存），不是本 App 的问题。"

        InjectionVerdict.INJECTION_FAILED ->
            "❌ 注入未生效：系统读回的仍是真实位置，说明模拟没有真正写进定位框架。"

        InjectionVerdict.NO_DATA ->
            "⚠️ 无法判断：没有读回到任何 provider 的位置数据。"
    }

    /** 生成可直接复制发走的纯文本诊断报告。 */
    fun buildReport(s: DiagnosticsSnapshot): String = buildString {
        appendLine("===== 定位模拟器 自检报告 =====")
        appendLine("Android: ${s.androidRelease} (API ${s.sdkInt})")
        appendLine("包名: ${s.packageName}")
        appendLine("模拟位置应用授权: ${if (s.isMockAppSelected) "是" else "否"} (AppOps=${s.appOpsModeName})")
        appendLine("前台服务: ${if (s.serviceRunning) "运行中" else "未运行"}")
        appendLine(
            "网络状态: WiFi=${if (s.wifiConnected) "已连接" else "未连接"} " +
                "移动数据=${if (s.mobileConnected) "已连接" else "未连接"}"
        )
        appendLine("最近一次推送: ${if (s.lastPushAgeMs == null) "从未" else "${formatAge(s.lastPushAgeMs)}前"}")
        appendLine("目标坐标: %.6f, %.6f".format(s.targetLat, s.targetLon))
        appendLine()
        appendLine("--- 各 provider 状态（读回 = 平台当前给的位置）---")
        s.probes.forEach { p ->
            val reg = if (p.registered) "已注册" else "未注册"
            val en = if (p.enabled) "启用" else "停用"
            val mock = when (p.isMock) {
                true -> "isMock=true"
                false -> "isMock=false"
                null -> "isMock=?"
            }
            val dist = p.distanceTo(s.targetLat, s.targetLon)
            appendLine(
                "[${p.name}] $reg/$en $mock 读回=${formatCoord(p.lat, p.lon)} " +
                    "偏差=${if (dist == null) "—" else "%.0fm".format(dist)} 年龄=${formatAge(p.ageMs)}"
            )
            p.error?.let { appendLine("      错误: $it") }
        }
        if (s.pushFailures.isNotEmpty()) {
            appendLine()
            appendLine("--- 写入失败详情 ---")
            s.pushFailures.forEach { (provider, err) -> appendLine("[$provider] $err") }
        }
        appendLine()
        appendLine("--- 结论 ---")
        appendLine(verdictText(s.verdict))
    }
}
