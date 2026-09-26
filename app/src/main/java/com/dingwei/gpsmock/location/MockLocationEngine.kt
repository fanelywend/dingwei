package com.dingwei.gpsmock.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 模拟定位引擎——通过 Android 官方「测试 provider」机制写入伪造位置，**无需 root**。
 *
 * 原理：
 * 1. [LocationManager.addTestProvider] 注册一个测试用的 provider；
 * 2. [LocationManager.setTestProviderLocation] 把伪造的 [Location] 灌进去；
 * 3. 系统定位框架会把该位置分发给所有读取定位的 App（含融合定位）。
 *
 * 前提：用户在 **开发者选项 → 选择模拟位置信息应用** 中选中本 App。
 * 未选中时 [addTestProvider] 会抛 [SecurityException]。
 *
 * 同时模拟 gps / network / fused 三个 provider。注：若设备上已存在真实的 fused provider
 * （带 Google 服务的机型常见），注册会失败而被跳过，此时 gps + network 的伪造位置
 * 通常仍会进入融合定位。
 *
 * 已知限制：系统会在位置上打 mock 标记，部分 App 可据此检测到模拟定位；
 * 另有部分 App 根本不用系统定位（自带 WiFi/基站定位），这种情况无法通过本方式影响。
 * 用 [probe] 可以区分这两类情况。
 */
class MockLocationEngine(private val context: Context) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** 实际注册成功的 provider 名集合。 */
    private val registered = mutableSetOf<String>()

    /** 各 provider 注册失败的原因。 */
    private val registrationErrors = mutableMapOf<String, String>()

    /** 上一次注册的整体失败原因，供 UI 提示。 */
    @Volatile
    var lastError: String? = null
        private set

    /** 最近一次写入的失败详情（provider → 错误），空表示全部成功。 */
    @Volatile
    var lastPushFailures: Map<String, String> = emptyMap()
        private set

    val registeredProviders: Set<String> get() = registered.toSet()

    /** AppOps 的原始模式值（能区分「未授权」与其他异常）。 */
    fun appOpsMode(): Int = try {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val uid = android.os.Process.myUid()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, uid, context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, uid, context.packageName)
        }
    } catch (t: Throwable) {
        Log.w(TAG, "查询模拟位置授权失败", t)
        -1
    }

    /** 本 App 是否已被选为「模拟位置信息应用」。 */
    fun isSelectedAsMockApp(): Boolean = appOpsMode() == AppOpsManager.MODE_ALLOWED

    /**
     * 注册测试 provider。
     * @return 至少注册成功一个 provider 时返回 true。
     */
    // lint 的 WrongConstant 针对 API 31+ 新签名（ProviderProperties.POWER_USAGE_*）。
    // 这里刻意使用已废弃但**全 API 级别（24+）可用**的旧重载，其 powerRequirement 参数
    // 按文档应传 Criteria.POWER_*；两套常量数值相同（POWER_LOW == POWER_USAGE_LOW == 1）。
    @SuppressLint("WrongConstant")
    fun start(): Boolean {
        cleanup()
        lastError = null
        registrationErrors.clear()
        TARGETS.forEach { name ->
            try {
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
                registrationErrors[name] = "无模拟位置权限（未在开发者选项中选中本应用）"
                lastError = "未获得模拟位置权限：请在开发者选项中选择本应用"
                Log.w(TAG, "注册 $name 失败（无权限）", e)
            } catch (e: IllegalArgumentException) {
                // provider 已存在（多为真实 fused provider）：跳过，不影响其余 provider
                registrationErrors[name] = "provider 已存在（${e.message}）"
                Log.i(TAG, "provider $name 已存在，跳过：${e.message}")
            } catch (t: Throwable) {
                registrationErrors[name] = "${t.javaClass.simpleName}: ${t.message}"
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
        val failures = mutableMapOf<String, String>()
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
                failures[name] = "${t.javaClass.simpleName}: ${t.message}"
                Log.w(TAG, "写入 $name 失败", t)
            }
        }
        lastPushFailures = failures
        return anyOk
    }

    /**
     * 周期性重新启用测试 provider。
     *
     * 部分 ROM 会在系统定位开关切换、省电策略介入后把测试 provider 关掉，
     * 此时位置就不再被分发。重写位置前重新启用一次成本极低，可避免这种静默失效。
     */
    fun reassertEnabled() {
        registered.forEach { name ->
            try {
                @Suppress("DEPRECATION")
                lm.setTestProviderEnabled(name, true)
            } catch (t: Throwable) {
                Log.w(TAG, "重新启用 $name 失败", t)
            }
        }
    }

    /**
     * 读回平台当前各 provider 的位置，用于判断注入是否真的生效。
     * @param registeredNames 已注册的 provider 名（由服务写入 [MockState]，跨实例共享）
     */
    fun probe(registeredNames: Set<String>): List<ProviderProbe> {
        // 读取定位需要定位权限；没有权限时探测本身没有意义，直接说明原因，
        // 同时也满足 lint 对「显式检查权限」的要求。
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return TARGETS.map { name ->
            var error: String? = null
            var enabled = false
            var last: Location? = null

            try {
                enabled = lm.isProviderEnabled(name)
            } catch (t: Throwable) {
                error = "isProviderEnabled 失败: ${t.message}"
            }

            if (!hasPermission) {
                error = listOfNotNull(error, "缺少定位权限，无法读回位置").joinToString("; ")
            } else {
                try {
                    last = lm.getLastKnownLocation(name)
                } catch (t: Throwable) {
                    error = listOfNotNull(error, "getLastKnownLocation 失败: ${t.message}")
                        .joinToString("; ")
                }
            }

            ProviderProbe(
                name = name,
                registered = registeredNames.contains(name),
                enabled = enabled,
                error = error ?: registrationErrors[name],
                lat = last?.latitude,
                lon = last?.longitude,
                isMock = last?.let { location ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        location.isMock
                    } else {
                        @Suppress("DEPRECATION")
                        location.isFromMockProvider
                    }
                },
                ageMs = last?.let {
                    (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000
                }
            )
        }
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
        lastPushFailures = emptyMap()
    }

    companion object {
        private const val TAG = "MockLocationEngine"

        const val GPS_PROVIDER = LocationManager.GPS_PROVIDER
        const val NETWORK_PROVIDER = LocationManager.NETWORK_PROVIDER
        const val FUSED_PROVIDER = "fused"

        val TARGETS = listOf(GPS_PROVIDER, NETWORK_PROVIDER, FUSED_PROVIDER)
    }
}
