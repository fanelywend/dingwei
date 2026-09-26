package com.dingwei.gpsmock.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dingwei.gpsmock.data.LatLon
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * OSM 地图卡片（osmdroid，免 API Key）。
 *
 * 地图使用 WGS-84 的 OSM 瓦片，**轻点/长按地图即选点**，坐标直接作为模拟位置。
 * 由于 OSM 是 WGS-84，与 App 内部坐标系一致，不需要做偏移补偿。
 */
@Composable
fun MapCard(
    point: LatLon,
    onPick: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val markerHolder = remember { mutableStateOf<Marker?>(null) }
    val mapHolder = remember { mutableStateOf<MapView?>(null) }
    val currentPick by rememberUpdatedState(onPick)

    DisposableEffect(Unit) {
        onDispose {
            // 释放瓦片缓存与网络监听，避免退出界面后泄漏
            mapHolder.value?.onDetach()
            mapHolder.value = null
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = "轻点或长按地图选择位置（OSM / WGS-84）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp)
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            factory = { ctx ->
                ensureOsmConfiguration(ctx)
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setUseDataConnection(true)
                    controller.setZoom(11.0)

                    // 拖动地图时不让外层可滚动 Column 抢走手势
                    setOnTouchListener { view, _ ->
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }

                    overlays.add(
                        MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                currentPick(p.latitude, p.longitude)
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint): Boolean {
                                currentPick(p.latitude, p.longitude)
                                return true
                            }
                        })
                    )

                    val marker = Marker(this).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "模拟位置"
                        position = GeoPoint(point.lat, point.lon)
                    }
                    overlays.add(marker)
                    markerHolder.value = marker
                    mapHolder.value = this

                    controller.setCenter(GeoPoint(point.lat, point.lon))
                    onResume()
                }
            },
            update = { mapView ->
                val marker = markerHolder.value
                val target = GeoPoint(point.lat, point.lon)
                val moved = marker?.position?.let {
                    it.latitude != target.latitude || it.longitude != target.longitude
                } ?: true
                if (marker != null && moved) {
                    marker.position = target
                    mapView.controller.animateTo(target)
                }
                mapView.invalidate()
            }
        )
    }
}

/** osmdroid 全局配置：瓦片缓存目录、User-Agent（OSM 瓦片服务要求）。 */
internal fun ensureOsmConfiguration(context: Context) {
    val config = Configuration.getInstance()
    if (config.userAgentValue.isNullOrBlank()) {
        config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = context.packageName
    }
}
