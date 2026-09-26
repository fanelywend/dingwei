package com.dingwei.gpsmock.location

import com.dingwei.gpsmock.data.MockParams
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 模拟定位的进程内共享状态。
 *
 * UI 与前台服务跑在同一个进程里，直接用 StateFlow 通信，
 * 比 Intent extra / Binder 更简单，也不会出现参数不同步的问题。
 */
object MockState {

    /** 前台服务是否正在模拟定位。 */
    val running = MutableStateFlow(false)

    /** 当前模拟参数（坐标一律 WGS-84）。服务每秒读取一次并写入系统。 */
    val params = MutableStateFlow(MockParams())

    /** 已注册成功的 provider 名称集合。 */
    val providers = MutableStateFlow<Set<String>>(emptySet())

    /** 最近一次成功写入系统的时间戳（毫秒），0 表示尚未成功。 */
    val lastPushAt = MutableStateFlow(0L)

    /** 最近一次写入的失败详情（provider → 错误），空表示全部成功。 */
    val pushFailures = MutableStateFlow<Map<String, String>>(emptyMap())

    /** 错误信息，供 UI 提示。 */
    val error = MutableStateFlow<String?>(null)
}
