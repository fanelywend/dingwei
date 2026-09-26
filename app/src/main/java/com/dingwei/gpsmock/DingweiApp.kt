package com.dingwei.gpsmock

import android.app.Application
import com.dingwei.gpsmock.ui.ensureOsmConfiguration

class DingweiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // osmdroid 全局配置：瓦片缓存目录与 User-Agent（OSM 瓦片服务要求）
        ensureOsmConfiguration(this)
    }
}
