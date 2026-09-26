package com.dingwei.gpsmock.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dingwei.gpsmock.data.CityRepository
import com.dingwei.gpsmock.data.Favorite
import com.dingwei.gpsmock.data.MockParams
import com.dingwei.gpsmock.data.OnlineGeocoder
import com.dingwei.gpsmock.data.PlaceResult
import com.dingwei.gpsmock.data.PrefsStore
import com.dingwei.gpsmock.geo.CoordinateSystem
import com.dingwei.gpsmock.location.MockLocationEngine
import com.dingwei.gpsmock.location.MockLocationService
import com.dingwei.gpsmock.location.MockState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 主界面状态与业务动作。
 *
 * 搜索策略：先查内置离线城市库（瞬时、免网络），未命中才走在线地理编码兜底。
 */
class MockViewModel(application: Application) : AndroidViewModel(application) {

    private val cityRepository = CityRepository(application)
    private val geocoder = OnlineGeocoder()
    private val prefs = PrefsStore(application)
    private val engine = MockLocationEngine(application)

    /** 搜索框内容 */
    var query by mutableStateOf("")
        private set

    /** 搜索结果 */
    var results by mutableStateOf<List<PlaceResult>>(emptyList())
        private set

    var searching by mutableStateOf(false)
        private set

    /** 本次结果是否来自在线兜底（UI 据此显示提示） */
    var resultsAreOnline by mutableStateOf(false)
        private set

    /** 当前模拟参数 */
    var params by mutableStateOf(prefs.loadParams())
        private set

    var favorites by mutableStateOf(prefs.loadFavorites())
        private set

    /** 手动输入框使用的坐标系 */
    var coordSystem by mutableStateOf(prefs.loadCoordSystem())
        private set

    /** 本 App 是否已被选为「模拟位置信息应用」 */
    var mockAuthorized by mutableStateOf(false)
        private set

    /** 内置城市库条目数（0 表示未加载成功） */
    var cityCount by mutableStateOf(0)
        private set

    /** 一次性提示消息（Snackbar） */
    var message by mutableStateOf<String?>(null)
        private set

    private var searchJob: Job? = null

    init {
        MockState.params.value = params
        viewModelScope.launch {
            cityCount = cityRepository.ensureLoaded()
            if (cityCount == 0) {
                message = "内置城市库加载失败，将只能使用在线搜索"
            }
        }
        refreshAuthorization()
    }

    fun consumeMessage() {
        message = null
    }

    /** 由 UI 主动抛出一条提示（如「已复制」）。 */
    fun notify(text: String) {
        message = text
    }

    /** 重新检查「模拟位置信息应用」授权（从设置页返回时调用）。 */
    fun refreshAuthorization() {
        mockAuthorized = engine.isSelectedAsMockApp()
    }

    // ── 搜索 ────────────────────────────────────────────────────────────────

    fun onQueryChange(text: String) {
        query = text
        searchJob?.cancel()
        if (text.isBlank()) {
            results = emptyList()
            resultsAreOnline = false
            searching = false
            return
        }
        searchJob = viewModelScope.launch {
            searching = true
            delay(DEBOUNCE_MS) // 输入防抖
            val offline = cityRepository.search(text)
            if (offline.isNotEmpty()) {
                results = offline
                resultsAreOnline = false
            } else if (text.trim().length >= 2) {
                val online = geocoder.search(text)
                results = online
                resultsAreOnline = online.isNotEmpty()
            } else {
                results = emptyList()
                resultsAreOnline = false
            }
            searching = false
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        query = ""
        results = emptyList()
        resultsAreOnline = false
        searching = false
    }

    /** 选中一条搜索结果：把坐标（WGS-84）设为当前模拟位置。 */
    fun selectPlace(place: PlaceResult) {
        updatePosition(place.lat, place.lon, place.name)
        query = place.name
        results = emptyList()
        resultsAreOnline = false
    }

    // ── 位置与参数 ──────────────────────────────────────────────────────────

    /** 更新模拟位置（入参必须是 WGS-84）。 */
    fun updatePosition(lat: Double, lon: Double, name: String) {
        params = params.copy(lat = lat, lon = lon, placeName = name)
        MockState.params.value = params
        prefs.saveParams(params)
    }

    /**
     * 从手动输入框读取坐标。输入按 [system] 解释，内部统一存 WGS-84。
     * @return 输入是否合法
     */
    fun setFromCoordinateInput(latText: String, lonText: String): Boolean {
        val lat = latText.trim().toDoubleOrNull()
        val lon = lonText.trim().toDoubleOrNull()
        if (lat == null || lon == null) {
            message = "经纬度格式不正确，请输入数字"
            return false
        }
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            message = "经纬度超出有效范围"
            return false
        }
        val (wgsLat, wgsLon) = coordSystem.toWgs84(lat, lon)
        updatePosition(wgsLat, wgsLon, "手动输入")
        return true
    }

    fun updateAltitude(value: Double) = updateParams { it.copy(altitude = value) }

    fun updateAccuracy(value: Float) = updateParams { it.copy(accuracy = value) }

    fun updateSpeed(value: Float) = updateParams { it.copy(speed = value) }

    fun updateBearing(value: Float) = updateParams { it.copy(bearing = value) }

    private inline fun updateParams(block: (MockParams) -> MockParams) {
        params = block(params)
        MockState.params.value = params
        prefs.saveParams(params)
    }

    /** 切换手动输入使用的坐标系（属性 setter 与同名方法会在 JVM 上签名冲突，故用 select 前缀）。 */
    fun selectCoordSystem(system: CoordinateSystem) {
        coordSystem = system
        prefs.saveCoordSystem(system)
    }

    // ── 启停模拟 ────────────────────────────────────────────────────────────

    /**
     * 开始模拟定位。
     * @return 是否成功发起（失败原因通过 [message] 提示）
     */
    fun startMocking(hasLocationPermission: Boolean): Boolean {
        refreshAuthorization()
        if (!mockAuthorized) {
            message = "请先在「开发者选项 → 选择模拟位置信息应用」中选中本应用"
            return false
        }
        if (!hasLocationPermission) {
            message = "请先授予定位权限"
            return false
        }
        MockState.error.value = null
        MockState.params.value = params
        MockLocationService.start(getApplication())
        return true
    }

    fun stopMocking() {
        MockLocationService.stop(getApplication())
    }

    // ── 收藏 ────────────────────────────────────────────────────────────────

    fun addFavorite(name: String) {
        val favorite = Favorite(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { params.placeName.ifBlank { "未命名地点" } },
            lat = params.lat,
            lon = params.lon,
            altitude = params.altitude,
            accuracy = params.accuracy,
            speed = params.speed,
            bearing = params.bearing,
            savedAt = System.currentTimeMillis()
        )
        // 同一位置只保留最新一条
        favorites = (listOf(favorite) + favorites)
            .distinctBy { "%.5f,%.5f".format(it.lat, it.lon) }
        prefs.saveFavorites(favorites)
        message = "已收藏「${favorite.name}」"
    }

    fun removeFavorite(id: String) {
        favorites = favorites.filterNot { it.id == id }
        prefs.saveFavorites(favorites)
    }

    fun applyFavorite(favorite: Favorite) {
        params = params.copy(
            lat = favorite.lat,
            lon = favorite.lon,
            placeName = favorite.name,
            altitude = favorite.altitude,
            accuracy = favorite.accuracy,
            speed = favorite.speed,
            bearing = favorite.bearing
        )
        MockState.params.value = params
        prefs.saveParams(params)
        message = "已定位到「${favorite.name}」"
    }

    private companion object {
        const val DEBOUNCE_MS = 200L
    }
}
