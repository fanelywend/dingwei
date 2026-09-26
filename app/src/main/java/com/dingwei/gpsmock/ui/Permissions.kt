package com.dingwei.gpsmock.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** 是否已授予定位权限（精确或粗略其一即可）。 */
fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

/**
 * 需要申请的全部运行时权限。
 *
 * 通知权限（Android 13+）是为了让前台服务的通知可见——没有它服务仍在跑，
 * 但通知栏看不到，用户会以为失效了。
 */
fun requiredRuntimePermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

/**
 * 跳转「开发者选项」。
 *
 * 不用 resolveActivity 预判：Android 11+ 的包可见性限制会让它误报 null，
 * 直接 startActivity 失败再退化到应用详情页更可靠。
 */
fun openDeveloperOptions(context: Context) {
    val opened = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!opened) openAppSettings(context)
}

/** 跳转本应用详情页（用户可在此授予权限）。 */
fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
