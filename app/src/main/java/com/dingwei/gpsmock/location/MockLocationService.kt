package com.dingwei.gpsmock.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.dingwei.gpsmock.MainActivity
import com.dingwei.gpsmock.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 模拟定位前台服务：维持 [MockLocationEngine] 的运行，并**每秒重写一次位置**。
 *
 * 为什么要每秒重写：定位是一个「新鲜度」敏感的数据，只写一次的话，
 * 稍后读取定位的 App 会拿到过期位置（甚至被系统判为无定位）。
 *
 * 用前台服务而非普通后台服务，是为了在切到别的 App、锁屏后仍然持续生效
 * ——这恰恰是模拟定位最常用的场景。
 */
class MockLocationService : LifecycleService() {

    private lateinit var engine: MockLocationEngine
    private var tickJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        engine = MockLocationEngine(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopMocking()
            else -> startMocking()
        }
        // START_STICKY：进程被系统回收后自动重建，避免模拟定位悄悄失效
        return START_STICKY
    }

    private fun startMocking() {
        if (tickJob?.isActive == true) return

        // 先注册 provider，再发前台通知。
        // 顺序很重要：反过来的话，注册失败时通知已经发出去了，
        // 用户会看到「正在模拟定位」却什么都没发生（Android 12+ 的
        // addTestProvider 在 AppOps 未通过时是静默 return，失败毫无提示）。
        if (!engine.start()) {
            MockState.error.value = engine.lastError
                ?: "无法注册模拟位置 provider，请检查开发者选项设置"
            MockState.running.value = false
            stopSelf()
            return
        }

        if (!startForegroundCompat()) {
            MockState.error.value = "前台服务启动失败：请确认已授予定位权限"
            MockState.running.value = false
            engine.stop()
            stopSelf()
            return
        }

        MockState.providers.value = engine.registeredProviders
        MockState.error.value = null
        MockState.running.value = true
        Log.i(TAG, "开始模拟定位，providers=${engine.registeredProviders}")

        tickJob = lifecycleScope.launch {
            var tick = 0
            while (isActive) {
                pushOnce()
                // 每 10 秒重新启用一次测试 provider：部分 ROM 会在系统定位开关或
                // 省电策略介入后把它静默关掉，导致位置不再分发。
                if (tick % 10 == 0) engine.reassertEnabled()
                // 每 5 秒读回校验一次：确认系统拿到的确实是我们的坐标。
                // 这是唯一能发现「静默失效」的手段。
                if (tick % 5 == 0 && tick > 0) verifyInjection()
                // 通知栏每 5 秒刷新一次即可，避免过于频繁
                if (tick % 5 == 0) updateNotification()
                tick++
                delay(TICK_MS)
            }
        }
    }

    private fun pushOnce() {
        val p = MockState.params.value
        val ok = engine.push(
            lat = p.lat,
            lon = p.lon,
            altitude = p.altitude,
            accuracy = p.accuracy,
            speed = p.speed,
            bearing = p.bearing
        )
        if (ok) {
            MockState.lastPushAt.value = System.currentTimeMillis()
        } else {
            MockState.error.value = "位置写入失败，模拟定位可能已失效"
        }
        MockState.pushFailures.value = engine.lastPushFailures
    }

    /**
     * 读回校验：若平台给的位置始终不是我们的目标坐标，说明注入并未真正生效。
     * 这种情况下把错误显示出来，而不是让用户以为一切正常。
     */
    private fun verifyInjection() {
        val p = MockState.params.value
        val probes = engine.probe(engine.registeredProviders)
        val hit = probes.any { it.matches(p.lat, p.lon) }
        MockState.error.value = when {
            hit -> null
            probes.all { it.lat == null } -> "注入状态未知：读不到任何 provider 的位置数据"
            else -> "注入未生效：系统读回的不是目标坐标，详情见「自检 / 诊断」"
        }
    }

    private fun stopMocking() {
        tickJob?.cancel()
        tickJob = null
        engine.stop()
        MockState.running.value = false
        MockState.providers.value = emptySet()
        MockState.lastPushAt.value = 0L
        MockState.pushFailures.value = emptyMap()
        Log.i(TAG, "已停止模拟定位")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        tickJob?.cancel()
        engine.stop()
        MockState.running.value = false
        MockState.providers.value = emptySet()
        MockState.pushFailures.value = emptyMap()
        // 任何退出路径都要撤掉通知：否则注册失败时残留的「正在模拟定位」
        // 会让用户以为模拟还在生效。
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    // ── 通知 ────────────────────────────────────────────────────────────────

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "模拟定位",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示当前模拟的位置"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(): Notification {
        val p = MockState.params.value
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = p.placeName.ifBlank { "模拟定位中" }
        val text = "%.5f, %.5f".format(p.lat, p.lon)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    private fun startForegroundCompat(): Boolean = try {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (t: Throwable) {
        // Android 14+ 起，location 类型前台服务要求已授予定位权限，否则抛 SecurityException
        Log.e(TAG, "startForeground 失败", t)
        false
    }

    private fun updateNotification() {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    companion object {
        private const val TAG = "MockLocationService"
        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 0x1001
        private const val TICK_MS = 1000L

        const val ACTION_START = "com.dingwei.gpsmock.action.START"
        const val ACTION_STOP = "com.dingwei.gpsmock.action.STOP"

        /** 启动模拟定位（需已获得定位权限且已被选为模拟位置应用）。 */
        fun start(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        /** 停止模拟定位。 */
        fun stop(context: Context) {
            context.stopService(Intent(context, MockLocationService::class.java))
        }
    }
}
