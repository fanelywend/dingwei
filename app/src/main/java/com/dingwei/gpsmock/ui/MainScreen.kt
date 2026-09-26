package com.dingwei.gpsmock.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dingwei.gpsmock.data.Favorite
import com.dingwei.gpsmock.data.LatLon
import com.dingwei.gpsmock.geo.CoordinateSystem
import com.dingwei.gpsmock.location.DiagnosticsSnapshot
import com.dingwei.gpsmock.location.GpsDiagnostics
import com.dingwei.gpsmock.location.InjectionVerdict
import com.dingwei.gpsmock.location.MockState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MockViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val running by MockState.running.collectAsStateWithLifecycle()
    val providers by MockState.providers.collectAsStateWithLifecycle()
    val error by MockState.error.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var permissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionGranted = hasLocationPermission(context) ||
            grants[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (permissionGranted) {
            vm.startMocking(true)
        } else {
            vm.notify("未获得定位权限，无法模拟定位")
        }
    }

    // 从系统设置返回时重新检查授权状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshAuthorization()
                permissionGranted = hasLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // 自检数据定期自动刷新，这样界面上的「读回值」始终是最新的
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshDiagnostics()
            kotlinx.coroutines.delay(2000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("定位模拟器") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomActionBar(
                running = running,
                onStart = {
                    if (permissionGranted) {
                        vm.startMocking(true)
                    } else {
                        permissionLauncher.launch(requiredRuntimePermissions())
                    }
                },
                onStop = { vm.stopMocking() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                authorized = vm.mockAuthorized,
                running = running,
                providers = providers,
                error = error,
                permissionGranted = permissionGranted,
                onOpenDeveloperOptions = { openDeveloperOptions(context) },
                onOpenAppSettings = { openAppSettings(context) },
                onRefresh = {
                    vm.refreshAuthorization()
                    permissionGranted = hasLocationPermission(context)
                }
            )

            DiagnosticsCard(
                snapshot = vm.diagnostics,
                onRefresh = { vm.refreshDiagnostics() },
                onCopy = {
                    val report = vm.diagnosticsReport()
                    clipboard.setText(AnnotatedString(report))
                    vm.notify("自检报告已复制到剪贴板")
                }
            )

            CitySearchCard(
                query = vm.query,
                results = vm.results,
                searching = vm.searching,
                resultsAreOnline = vm.resultsAreOnline,
                cityCount = vm.cityCount,
                onQueryChange = vm::onQueryChange,
                onClear = vm::clearSearch,
                onSelect = vm::selectPlace
            )

            MapCard(
                point = LatLon(vm.params.lat, vm.params.lon),
                onPick = { lat, lon -> vm.updatePosition(lat, lon, "地图选点") }
            )

            CoordinateCard(
                params = vm.params,
                coordSystem = vm.coordSystem,
                onCoordSystemChange = vm::selectCoordSystem,
                onApplyInput = { lat, lon -> vm.setFromCoordinateInput(lat, lon) },
                onCopy = { text ->
                    clipboard.setText(AnnotatedString(text))
                    vm.notify("已复制：$text")
                }
            )

            ParamsCard(
                params = vm.params,
                onAltitude = vm::updateAltitude,
                onAccuracy = vm::updateAccuracy,
                onSpeed = vm::updateSpeed,
                onBearing = vm::updateBearing
            )

            FavoritesCard(
                favorites = vm.favorites,
                defaultName = vm.params.placeName,
                onAdd = vm::addFavorite,
                onApply = vm::applyFavorite,
                onDelete = vm::removeFavorite
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

// ── 状态卡 ──────────────────────────────────────────────────────────────────

@Composable
private fun StatusCard(
    authorized: Boolean,
    running: Boolean,
    providers: Set<String>,
    error: String?,
    permissionGranted: Boolean,
    onOpenDeveloperOptions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (authorized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (authorized) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (authorized) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (authorized) "已授权为模拟位置应用" else "尚未授权模拟位置权限",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (authorized) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }

            if (!authorized) {
                Text(
                    text = "请依次打开：设置 → 关于手机 → 连点「版本号」7 次进入开发者模式，" +
                        "然后在「开发者选项 → 选择模拟位置信息应用」中选中「定位模拟器」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenDeveloperOptions) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("打开开发者选项")
                    }
                    OutlinedButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重新检测")
                    }
                }
            }

            if (authorized && !permissionGranted) {
                Text(
                    text = "还缺少定位权限，请点下方按钮授权。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                OutlinedButton(onClick = onOpenAppSettings) { Text("打开应用权限设置") }
            }

            if (running) {
                Text(
                    text = "正在模拟中 · provider: ${providers.joinToString(", ").ifEmpty { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (error != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ── 自检 / 诊断卡 ───────────────────────────────────────────────────────────

/**
 * 读回平台当前的位置，判断模拟到底有没有写进定位框架。
 *
 * 这是排查「开了模拟定位但其他 App 仍显示真实位置」的关键：
 * 若读回值 = 目标坐标，说明注入成功，问题在消费方 App；
 * 若读回值仍是真实位置，说明注入没生效。
 */
@Composable
private fun DiagnosticsCard(
    snapshot: DiagnosticsSnapshot?,
    onRefresh: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "自检 / 诊断",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRefresh) { Text("刷新") }
                TextButton(onClick = onCopy) { Text("复制报告") }
            }

            if (snapshot == null) {
                Text(
                    text = "正在采集…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val verdictColor = when (snapshot.verdict) {
                InjectionVerdict.INJECTION_OK -> MaterialTheme.colorScheme.primary
                InjectionVerdict.INJECTION_FAILED -> MaterialTheme.colorScheme.error
                InjectionVerdict.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = GpsDiagnostics.verdictText(snapshot.verdict),
                style = MaterialTheme.typography.bodySmall,
                color = verdictColor
            )

            HorizontalDivider(Modifier.padding(vertical = 2.dp))

            InfoRow("Android", "${snapshot.androidRelease} (API ${snapshot.sdkInt})")
            InfoRow(
                "模拟位置授权",
                if (snapshot.isMockAppSelected) "已授权" else "未授权 · ${snapshot.appOpsModeName}"
            )
            InfoRow("前台服务", if (snapshot.serviceRunning) "运行中" else "未运行")
            InfoRow(
                "最近写入",
                when {
                    snapshot.lastPushAgeMs == null -> "从未写入"
                    snapshot.pushedRecently -> "${GpsDiagnostics.formatAge(snapshot.lastPushAgeMs)}前（正常）"
                    else -> "${GpsDiagnostics.formatAge(snapshot.lastPushAgeMs)}前（偏旧）"
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Text(
                text = "平台读回值（应等于目标 ${"%.5f, %.5f".format(snapshot.targetLat, snapshot.targetLon)}）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            snapshot.probes.forEach { probe ->
                Column {
                    val distance = probe.distanceTo(snapshot.targetLat, snapshot.targetLon)
                    Text(
                        text = buildString {
                            append("[${probe.name}] ")
                            append(if (probe.registered) "已注册" else "未注册")
                            append(" · ")
                            append(if (probe.enabled) "启用" else "停用")
                            probe.isMock?.let {
                                append(" · isMock=")
                                append(it)
                            }
                            if (distance != null) {
                                append(" · 偏差 %.0fm".format(distance))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "   读回 ${GpsDiagnostics.formatCoord(probe.lat, probe.lon)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    probe.error?.let {
                        Text(
                            text = "   错误: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (snapshot.pushFailures.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                Text(
                    text = "写入失败详情",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                snapshot.pushFailures.forEach { (provider, err) ->
                    Text(
                        text = "[$provider] $err",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── 坐标卡 ──────────────────────────────────────────────────────────────────

@Composable
private fun CoordinateCard(
    params: com.dingwei.gpsmock.data.MockParams,
    coordSystem: CoordinateSystem,
    onCoordSystemChange: (CoordinateSystem) -> Unit,
    onApplyInput: (String, String) -> Boolean,
    onCopy: (String) -> Unit
) {
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }

    // 切换坐标系时，把当前坐标按新坐标系回填输入框
    LaunchedEffect(coordSystem, params.lat, params.lon) {
        val (lat, lon) = coordSystem.fromWgs84(params.lat, params.lon)
        latInput = "%.6f".format(lat)
        lonInput = "%.6f".format(lon)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("当前模拟坐标", style = MaterialTheme.typography.titleSmall)

            CoordinateSystem.entries.forEach { system ->
                val (lat, lon) = system.fromWgs84(params.lat, params.lon)
                val text = "%.6f, %.6f".format(lat, lon)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = system.short,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp)
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onCopy(text) }) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "复制 ${system.short}",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            Text("手动输入（按下面的坐标系解释）", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CoordinateSystem.entries.forEach { system ->
                    FilterChip(
                        selected = coordSystem == system,
                        onClick = { onCoordSystemChange(system) },
                        label = { Text(system.short) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latInput,
                    onValueChange = { latInput = it },
                    label = { Text("纬度") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lonInput,
                    onValueChange = { lonInput = it },
                    label = { Text("经度") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            FilledTonalButton(onClick = { onApplyInput(latInput, lonInput) }) {
                Text("应用坐标")
            }
        }
    }
}

// ── 参数卡 ──────────────────────────────────────────────────────────────────

@Composable
private fun ParamsCard(
    params: com.dingwei.gpsmock.data.MockParams,
    onAltitude: (Double) -> Unit,
    onAccuracy: (Float) -> Unit,
    onSpeed: (Float) -> Unit,
    onBearing: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("模拟参数", style = MaterialTheme.typography.titleSmall)

            SliderRow(
                label = "海拔",
                valueText = "%.0f m".format(params.altitude),
                value = params.altitude.toFloat(),
                range = 0f..8000f,
                onChange = { onAltitude(it.toDouble()) }
            )
            SliderRow(
                label = "精度",
                valueText = "%.0f m".format(params.accuracy),
                value = params.accuracy,
                range = 1f..100f,
                onChange = onAccuracy
            )
            SliderRow(
                label = "速度",
                valueText = "%.1f m/s".format(params.speed),
                value = params.speed,
                range = 0f..60f,
                onChange = onSpeed
            )
            SliderRow(
                label = "航向",
                valueText = "%.0f°".format(params.bearing),
                value = params.bearing,
                range = 0f..359f,
                onChange = onBearing
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range
        )
    }
}

// ── 收藏卡 ──────────────────────────────────────────────────────────────────

@Composable
private fun FavoritesCard(
    favorites: List<Favorite>,
    defaultName: String,
    onAdd: (String) -> Unit,
    onApply: (Favorite) -> Unit,
    onDelete: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("收藏地点", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("名称（可留空）") },
                    placeholder = { Text(defaultName) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = {
                        onAdd(nameInput)
                        nameInput = ""
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("收藏")
                }
            }

            if (favorites.isEmpty()) {
                Text(
                    text = "还没有收藏，收藏后可以一键切换地点。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                favorites.forEach { favorite ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(favorite.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "%.5f, %.5f".format(favorite.lat, favorite.lon),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onApply(favorite) }) { Text("定位") }
                        IconButton(onClick = { onDelete(favorite.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 底部启停按钮 ────────────────────────────────────────────────────────────

@Composable
private fun BottomActionBar(
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Button(
            onClick = { if (running) onStop() else onStart() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(52.dp),
            colors = if (running) {
                androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            } else {
                androidx.compose.material3.ButtonDefaults.buttonColors()
            }
        ) {
            Icon(
                imageVector = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (running) "停止模拟" else "开始模拟定位",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
