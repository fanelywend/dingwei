package com.dingwei.gpsmock.location

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * 模拟定位引擎——通过 Android 官方「测试 provider」机制写入伪造位置，**无需 root**。
 *
 * 原理：
 * 1. [LocationManager.addTestProvider] 注册一个测试用的 provider；
 * 2. [LocationManager.setTestProviderLocation] 把伪造的 [Location] 灌进去；
 * 3. 系统定位框架会把该位置分发给所有读取定位的 App（含 Google Play 服务融合定位）。
 *
 * 前提：用户在 **开发者选项 → 选择模拟位置信息应用** 中选中本 App。
 * 未选中时 [addTestProvider] 会抛 [SecurityException]，[isSelectedAsMockApp] 会返回 false。
 *
 * 同时模拟 gps / network / fused 三个 provider，兼容只读融合定位的 App。
 * 注：若设备上已存在真实的 fused provider（带 Play 服务的机型常见），
 * 注册会失败而被跳过，此时 gps + network 的伪造位置仍会进入融合定位。
 *
 * 已知限制：系统会在位置上打 `isMock` 标记，部分 App（银行、部分游戏反作弊）
 * 可据此检测到模拟定位——这不是本 App 能绕过的，绕过需要 root / Xposed。
 */
class MockLocationEngine(private val context: Context) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** 实际注册成功的 provider 名集合。 */
    private val registered = mutableSetOf<String>()

    /** 上一次注册失败的原因，供 UI 提示。 */
    @Volatile
    var lastError: String? = null
        private set

    val registeredProviders: Set<String> get() = registered.toSet()

    /** 本 App 是否已被选为「模拟位置信息应用」。 */
    fun isSelectedAsMockApp(): Boolean = try {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val uid = android.os.Process.myUid()
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, uid, context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, uid, context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (t: Throwable) {
        Log.w(TAG, "查询模拟位置授权失败", t)
        false
    }

    /**
     * 注册测试 provider。
     * @return 至少注册成功一个 provider 时返回 true。
     */
    // lint 的 WrongConstant 是针对 API 31+ 新签名（ProviderProperties.POWER_USAGE_*）的检查。
    // 这里刻意使用已废弃但**全 API 级别（24+）可用**的旧重载，其 powerRequirement 参数
    // 按文档应传 Criteria.POWER_*；且两套常量数值相同（POWER_LOW == POWER_USAGE_LOW == 1），
    // 不存在语义差异，故精确豁免该检查。
    @SuppressLint("WrongConstant")
    fun start(): Boolean {
        cleanup()
        lastError = null
        TARGETS.forEach { name ->
            try {
                @Suppress("DEPRECATION")
                lm.addTestProvider(
                    name,
                    false,  // requiresNetwork
                    true,   // requiresSatellite
                    false,  // requiresCell
                    false,  // hasMonetaryCost
                    true,   // supportsAltitude
                    true,   // supportsSpeed
                    true,   // supportsBearing
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                @Suppress("DEPRECATION")
                lm.setTestProviderEnabled(name, true)
                registered += name
            } catch (e: SecurityException) {
                // 未在开发者选项里把本 App 选为模拟位置应用
                lastError = "未获得模拟位置权限：请在开发者选项中选择本应用"
                Log.w(TAG, "注册 $name 失败（无权限）", e)
            } catch (e: IllegalArgumentException) {
                // provider 已存在（多为真实 fused provider）：跳过，不影响其余 provider
                Log.i(TAG, "provider $name 已存在，跳过：${e.message}")
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
                Log.w(TAG, "注册 $name 失败", t)
            }
        }
        return registered.isNotEmpty()
    }

    /**
     * 写入一次位置。返回是否至少有一个 provider 写入成功。
     *
     * 注意 [Location.elapsedRealtimeNanos] 必须设置且非 0，否则系统会拒绝该位置。
     */
    fun push(
        lat: Double,
        lon: Double,
        altitude: Double,
        accuracy: Float,
        speed: Float,
        bearing: Float
    ): Boolean {
        if (registered.isEmpty()) return false
        val nowMs = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtimeNanos()
        var anyOk = false
        registered.forEach { name ->
            try {
                val loc = Location(name).apply {
                    latitude = lat
                    longitude = lon
                    this.altitude = altitude
                    this.accuracy = accuracy.coerceAtLeast(1f)
                    this.speed = speed.coerceAtLeast(0f)
                    this.bearing = ((bearing % 360f) + 360f) % 360f
                    time = nowMs
                    elapsedRealtimeNanos = nowElapsed
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        verticalAccuracyMeters = accuracy.coerceAtLeast(1f)
                        speedAccuracyMetersPerSecond = 0.5f
                        bearingAccuracyDegrees = 1f
                    }
                }
                lm.setTestProviderLocation(name, loc)
                anyOk = true
            } catch (t: Throwable) {
                Log.w(TAG, "写入 $name 失败", t)
            }
        }
        return anyOk
    }

    /** 注销所有测试 provider，位置恢复为真实定位。 */
    fun stop() = cleanup()

    private fun cleanup() {
        registered.forEach { name ->
            try {
                @Suppress("DEPRECATION")
                lm.setTestProviderEnabled(name, false)
            } catch (_: Throwable) {
            }
            try {
                lm.removeTestProvider(name)
            } catch (_: Throwable) {
            }
        }
        registered.clear()
    }

    companion object {
        private const val TAG = "MockLocationEngine"

        const val GPS_PROVIDER = LocationManager.GPS_PROVIDER
        const val NETWORK_PROVIDER = LocationManager.NETWORK_PROVIDER
        const val FUSED_PROVIDER = "fused"

        val TARGETS = listOf(GPS_PROVIDER, NETWORK_PROVIDER, FUSED_PROVIDER)
    }
}
