package com.wheelcharm.telemetry.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wheelcharm.telemetry.data.local.DriveEntity
import com.wheelcharm.telemetry.data.local.GarageVehicleEntity
import com.wheelcharm.telemetry.data.local.TelemetryTypeConverters
import com.wheelcharm.telemetry.domain.model.*
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val MapDarkBg = Color(0xFF07090C)
private val SheetGlassBg = Color(0xD90B0E13)
private val CardDarkSurface = Color(0xFF151820)
private val PillUnselectedBg = Color(0xFF1A1E28)
private val AccentElectricBlue = Color(0xFF0A84FF)
private val AccentNeonCyan = Color(0xFF00E5FF)
private val AccentEmerald = Color(0xFF34C759)
private val AccentWarningOrange = Color(0xFFFF9500)
private val AccentDangerRed = Color(0xFFFF3B30)
private val AccentGoldCrown = Color(0xFFE5B842)
private val AccentSilver = Color(0xFF9BA3B0)
private val AccentBronze = Color(0xFFC47A45)
private val AccentPurpleRing = Color(0xFF7B61FF)
private val TextPrimaryWhite = Color(0xFFF5F7FA)
private val TextSecondaryGray = Color(0xFF8E95A2)

enum class BottomNavTab(val title: String) {
    DRIVES("Drives"),
    NAVIGATION("Navigation"),
    OBD_GARAGE("OBD-II & Garage"),
    STATISTICS("Statistics"),
    LEADERBOARD("Leaderboard")
}

@Composable
fun WheelCharmTelemetryApp(
    drives: List<DriveEntity>,
    vehicles: List<GarageVehicleEntity>,
    selectedVehicleId: String?,
    selectedSort: DriveSortFilter,
    placementMode: DevicePlacementMode,
    isAutopilotEnabled: Boolean,
    selectedRecapTimeframe: RecapTimeframe,
    stats: StatisticsSummary,
    leaderboard: List<LeaderboardEntry>,
    selectedPeriod: LeaderboardPeriod,
    selectedScope: LeaderboardScope,
    selectedMetric: LeaderboardMetric,
    liveTelemetry: LiveTelemetryState,
    pairedObdDevices: List<ObdBluetoothDeviceItem> = emptyList(),
    presetWaypoints: List<NavigationWaypoint> = emptyList(),
    navigationState: ActiveNavigationState = ActiveNavigationState(),
    onSelectVehicleFilter: (String?) -> Unit,
    onSelectSort: (DriveSortFilter) -> Unit,
    onSelectPlacementMode: (DevicePlacementMode) -> Unit,
    onToggleAutopilot: (Boolean) -> Unit,
    onSelectRecapTimeframe: (RecapTimeframe) -> Unit,
    onSetActiveGarageVehicle: (String) -> Unit,
    onAddGarageVehicle: (VehicleCategory, String, String, String, String, Int, Int) -> Unit,
    onSelectPeriod: (LeaderboardPeriod) -> Unit,
    onSelectScope: (LeaderboardScope) -> Unit,
    onSelectMetric: (LeaderboardMetric) -> Unit,
    onToggleRecording: () -> Unit,
    onRefreshPairedObdDevices: () -> Unit = {},
    onConnectObdBluetooth: (String) -> Unit = {},
    onConnectObdWifi: (String, Int) -> Unit = { _, _ -> },
    onDisconnectObd: () -> Unit = {},
    onStartNavigation: (NavigationWaypoint) -> Unit = {},
    onStopNavigation: () -> Unit = {},
    onToggleHudMirror: () -> Unit = {},
    onLaunchExternalTurnByTurn: (NavigationWaypoint) -> Unit = {},
    onShareTelemetrySummary: (String) -> Unit = {}
) {
    var currentTab by remember { mutableStateOf(BottomNavTab.STATISTICS) }
    var activeWrappedStoryReport by remember { mutableStateOf<WrappedRecapReport?>(null) }
    var showObdQuickDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MapDarkBg)
    ) {
        // Arka Plan Karanlık Harita & Canlı Navigasyon Vektör Katmanı
        DarkMapBackgroundCanvas(
            liveTelemetry = liveTelemetry,
            navigationState = navigationState,
            presetWaypoints = presetWaypoints
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        if (currentTab == BottomNavTab.NAVIGATION) {
                            Color(0x99080A0F)
                        } else {
                            SheetGlassBg
                        }
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val forward = targetState.ordinal >= initialState.ordinal
                        val slideDir = if (forward) 1 else -1
                        (fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { (it / 5) * slideDir })
                            .togetherWith(
                                fadeOut(animationSpec = tween(200)) +
                                    slideOutHorizontally(animationSpec = tween(240)) { -(it / 6) * slideDir }
                            )
                    },
                    label = "main_tab_transition"
                ) { tab ->
                    when (tab) {
                        BottomNavTab.DRIVES -> DrivesScreen(
                            drives = drives,
                            vehicles = vehicles,
                            selectedVehicleId = selectedVehicleId,
                            selectedSort = selectedSort,
                            liveState = liveTelemetry,
                            onSelectVehicle = onSelectVehicleFilter,
                            onSelectSort = onSelectSort,
                            onToggleRecording = onToggleRecording,
                            onOpenObdModal = {
                                onRefreshPairedObdDevices()
                                showObdQuickDialog = true
                            },
                            onOpenNavigationTab = { currentTab = BottomNavTab.NAVIGATION }
                        )

                        BottomNavTab.NAVIGATION -> NavigationAndCockpitScreen(
                            liveState = liveTelemetry,
                            navigationState = navigationState,
                            presetWaypoints = presetWaypoints,
                            onStartNavigation = onStartNavigation,
                            onStopNavigation = onStopNavigation,
                            onToggleHudMirror = onToggleHudMirror,
                            onLaunchExternalTurnByTurn = onLaunchExternalTurnByTurn,
                            onToggleRecording = onToggleRecording,
                            onOpenObdModal = {
                                onRefreshPairedObdDevices()
                                showObdQuickDialog = true
                            }
                        )

                        BottomNavTab.OBD_GARAGE -> GarageAndObdScreen(
                            vehicles = vehicles,
                            placementMode = placementMode,
                            isAutopilotEnabled = isAutopilotEnabled,
                            liveState = liveTelemetry,
                            pairedObdDevices = pairedObdDevices,
                            onSelectPlacementMode = onSelectPlacementMode,
                            onToggleAutopilot = onToggleAutopilot,
                            onSetActiveVehicle = onSetActiveGarageVehicle,
                            onAddVehicle = onAddGarageVehicle,
                            onRefreshPairedObdDevices = onRefreshPairedObdDevices,
                            onConnectObdBluetooth = onConnectObdBluetooth,
                            onConnectObdWifi = onConnectObdWifi,
                            onDisconnectObd = onDisconnectObd
                        )

                        BottomNavTab.STATISTICS -> StatisticsScreen(
                            stats = stats,
                            selectedTimeframe = selectedRecapTimeframe,
                            onSelectTimeframe = onSelectRecapTimeframe,
                            onOpenWrappedStory = { report -> activeWrappedStoryReport = report },
                            onShareStats = {
                                val shareMsg = String.format(
                                    Locale.US,
                                    "⚡ ApexPulse Telemetri Raporum:\n" +
                                        "🛣️ Toplam Mesafe: %.1f mi (%.1f sa)\n" +
                                        "🚀 Zirve Hız: %.0f mph\n" +
                                        "⚙️ Sürücü Profili: %s (Ort. %d RPM, %d. Vites, Sağlık: %%%d)\n" +
                                        "#ApexPulse #Telemetry",
                                    stats.totalDistanceMiles,
                                    stats.totalTimeHours,
                                    stats.rocketModeTopSpeedMph,
                                    stats.mechanicalSummary.driverPersona.badgeTitle,
                                    stats.mechanicalSummary.avgRpm,
                                    stats.mechanicalSummary.mostUsedGear.coerceAtLeast(1),
                                    stats.mechanicalSummary.mechanicalHealthScore
                                )
                                onShareTelemetrySummary(shareMsg)
                            }
                        )

                        BottomNavTab.LEADERBOARD -> LeaderboardScreen(
                            entries = leaderboard,
                            selectedPeriod = selectedPeriod,
                            selectedScope = selectedScope,
                            selectedMetric = selectedMetric,
                            onSelectPeriod = onSelectPeriod,
                            onSelectScope = onSelectScope,
                            onSelectMetric = onSelectMetric
                        )
                    }
                }
            }

            WheelCharmBottomNavigation(
                selectedTab = currentTab,
                isObdConnected = liveTelemetry.obdData.isConnected,
                isNavigating = navigationState.isNavigating,
                onSelectTab = { currentTab = it }
            )
        }

        if (showObdQuickDialog) {
            ObdConnectionDialog(
                obdData = liveTelemetry.obdData,
                pairedDevices = pairedObdDevices,
                onRefreshPaired = onRefreshPairedObdDevices,
                onConnectBluetooth = { mac ->
                    onConnectObdBluetooth(mac)
                },
                onConnectWifi = { host, port ->
                    onConnectObdWifi(host, port)
                },
                onDisconnect = onDisconnectObd,
                onDismiss = { showObdQuickDialog = false }
            )
        }

        activeWrappedStoryReport?.let { report ->
            SpotifyWrappedStoryModal(
                report = report,
                onDismiss = { activeWrappedStoryReport = null }
            )
        }
    }
}

/**
 * CANLI HARİTA NAVİGASYONU, TURN-BY-TURN REHBER, G-G SÜRTÜNME DAİRESİ,
 * 0-100 KM/H DRAG KRONOMETRESİ VE ÖN CAM HUD (HEAD-UP DISPLAY) KOKPİTİ.
 */
@Composable
fun NavigationAndCockpitScreen(
    liveState: LiveTelemetryState,
    navigationState: ActiveNavigationState,
    presetWaypoints: List<NavigationWaypoint>,
    onStartNavigation: (NavigationWaypoint) -> Unit,
    onStopNavigation: () -> Unit,
    onToggleHudMirror: () -> Unit,
    onLaunchExternalTurnByTurn: (NavigationWaypoint) -> Unit,
    onToggleRecording: () -> Unit,
    onOpenObdModal: () -> Unit
) {
    val animatedSpeed by animateFloatAsState(
        targetValue = liveState.currentSpeedKmh,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cockpit_speed"
    )
    val animatedRpm by animateIntAsState(
        targetValue = liveState.obdData.rpm,
        animationSpec = tween(180),
        label = "cockpit_rpm"
    )

    val hudModifier = if (navigationState.isHudMirrored) {
        Modifier.scale(scaleX = -1f, scaleY = 1f)
    } else {
        Modifier
    }

    LazyColumn(
        modifier = hudModifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (navigationState.isHudMirrored) "HUD ÖN CAM MODU" else "Navigation & Cockpit",
                        color = if (navigationState.isHudMirrored) AccentNeonCyan else TextPrimaryWhite,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (liveState.hasGpsFix) {
                            String.format(Locale.US, "GPS Aktif • %.4f°N, %.4f°E", liveState.currentLatitude, liveState.currentLongitude)
                        } else {
                            "GPS uydusu aranıyor • Sensör füzyonu hazır"
                        },
                        color = TextSecondaryGray,
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = if (navigationState.isHudMirrored) AccentNeonCyan.copy(alpha = 0.25f) else CardDarkSurface,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, if (navigationState.isHudMirrored) AccentNeonCyan else Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.clickable { onToggleHudMirror() }
                    ) {
                        Text(
                            text = if (navigationState.isHudMirrored) "HUD Kapat" else "🪞 HUD Yansıt",
                            color = if (navigationState.isHudMirrored) AccentNeonCyan else TextPrimaryWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Surface(
                        color = if (liveState.obdData.isConnected) AccentEmerald.copy(alpha = 0.2f) else CardDarkSurface,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, if (liveState.obdData.isConnected) AccentEmerald else Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.clickable { onOpenObdModal() }
                    ) {
                        Text(
                            text = if (liveState.obdData.isConnected) "🔌 OBD Bağlı" else "🔌 OBD-II Seç",
                            color = if (liveState.obdData.isConnected) AccentEmerald else TextPrimaryWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Aktif Turn-by-Turn Navigasyon Yönlendirme Banner'ı
        item {
            if (navigationState.isNavigating && navigationState.destination != null) {
                val dest = navigationState.destination
                val relativeBearing = ((navigationState.bearingToTargetDegrees - liveState.currentHeadingDeg + 360f) % 360f)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF063970), Color(0xFF0A84FF))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.28f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "Yön",
                                tint = AccentNeonCyan,
                                modifier = Modifier
                                    .size(30.dp)
                                    .rotate(relativeBearing)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = navigationState.nextManeuverText,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Hedef: %s • Kalan: %.2f km • Tahmini: ~%d dk",
                                    dest.title,
                                    navigationState.distanceRemainingMeters / 1000.0,
                                    navigationState.etaMinutes
                                ),
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onLaunchExternalTurnByTurn(dest) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sesli Adım Adım Rota", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onStopNavigation,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Rotayı Bitir", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Hızlı Navigasyon Hedef Seçici
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(CardDarkSurface.copy(alpha = 0.9f))
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧭 Hızlı Rota & Navigasyon Hedefi Seç",
                            color = TextPrimaryWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Dokun & Başlat",
                            color = AccentElectricBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(presetWaypoints, key = { it.id }) { wp ->
                            Column(
                                modifier = Modifier
                                    .width(175.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PillUnselectedBg)
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                    .clickable { onStartNavigation(wp) }
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = wp.categoryEmoji, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = wp.title,
                                        color = TextPrimaryWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = wp.subtitle,
                                    color = TextSecondaryGray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Çift Dijital Sürüş Kokpiti: Hız + Devir Takometresi + Anlık Vites + G-G Sürtünme Dairesi
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sol Kart: Canlı Hız, Devir Kemeri ve Vites Göstergesi
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardDarkSurface.copy(alpha = 0.92f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TachometerAndSpeedGauge(
                        speedKmh = animatedSpeed,
                        rpm = animatedRpm,
                        gear = liveState.obdData.estimatedGear,
                        isClutchSlipping = liveState.obdData.isClutchSlipping
                    )
                }

                // Sağ Kart: Canlı G-Kuvveti Sürtünme Dairesi (Friction Circle / G-Bowl)
                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardDarkSurface.copy(alpha = 0.92f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "G-FORCE RADAR",
                        color = TextSecondaryGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GForceFrictionCircleCanvas(
                        longitudinalG = liveState.longitudinalG,
                        lateralG = liveState.lateralG,
                        modifier = Modifier.size(125.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format(
                            Locale.US,
                            "Boyuna: %+.2fG • Yanal: %.2fG",
                            liveState.longitudinalG,
                            abs(liveState.lateralG)
                        ),
                        color = TextPrimaryWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Performans Kronometresi (Dragy 0-60 km/h, 0-100 km/h ve Zirve G)
        item {
            val perf = liveState.performanceMetrics
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardDarkSurface.copy(alpha = 0.92f))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⏱️ Performans Telemetrisi (Otomatik Dragy)",
                        color = TextPrimaryWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "GPS + IMU Füzyon",
                        color = AccentNeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PerformanceMetricChip(
                        title = "0 - 60 km/h",
                        value = perf.bestZeroToSixtySec?.let { String.format(Locale.US, "%.2f sn", it) } ?: "Hazır",
                        accent = AccentNeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PerformanceMetricChip(
                        title = "0 - 100 km/h",
                        value = perf.bestZeroToHundredSec?.let { String.format(Locale.US, "%.2f sn", it) } ?: "Hazır",
                        accent = AccentGoldCrown,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PerformanceMetricChip(
                        title = "Zirve G",
                        value = String.format(
                            Locale.US,
                            "%.2f G",
                            maxOf(perf.peakLongitudinalAccelG, perf.peakLongitudinalBrakeG, perf.peakLateralG)
                        ),
                        accent = AccentEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Canlı Kayıt Başlat / Durdur Kokpit Kontrolü
        item {
            LiveTelemetryBanner(
                liveState = liveState,
                onToggleRecording = onToggleRecording
            )
        }
    }
}

@Composable
private fun PerformanceMetricChip(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PillUnselectedBg)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, color = TextSecondaryGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun TachometerAndSpeedGauge(
    speedKmh: Float,
    rpm: Int,
    gear: Int,
    isClutchSlipping: Boolean
) {
    val rpmFraction = (rpm / 6000f).coerceIn(0f, 1f)
    val gearText = if (gear in 1..6) "$gear" else "N"

    Box(
        modifier = Modifier.size(165.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 14.dp.toPx()
            val arcSize = Size(size.width - strokeW, size.height - strokeW)
            val topLeft = Offset(strokeW / 2f, strokeW / 2f)

            // Arka plan takometre yayı
            drawArc(
                color = Color.White.copy(alpha = 0.1f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Redline bölgesi (4500 - 6000 RPM)
            drawArc(
                color = AccentDangerRed.copy(alpha = 0.35f),
                startAngle = 135f + (270f * 0.75f),
                sweepAngle = 270f * 0.25f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
            )

            // Aktif devir yayı
            val activeSweep = 270f * rpmFraction
            val gaugeColor = when {
                isClutchSlipping -> AccentDangerRed
                rpm >= 4400 -> AccentDangerRed
                rpm >= 3000 -> AccentWarningOrange
                else -> AccentNeonCyan
            }
            if (activeSweep > 1f) {
                drawArc(
                    color = gaugeColor,
                    startAngle = 135f,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = if (isClutchSlipping) AccentDangerRed else AccentElectricBlue.copy(alpha = 0.22f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isClutchSlipping) AccentDangerRed else AccentElectricBlue)
            ) {
                Text(
                    text = "VİTES $gearText",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format(Locale.US, "%.0f", speedKmh),
                color = TextPrimaryWhite,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "km/h • $rpm RPM",
                color = TextSecondaryGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GForceFrictionCircleCanvas(
    longitudinalG: Float,
    lateralG: Float,
    modifier: Modifier = Modifier
) {
    val animLat by animateFloatAsState(targetValue = lateralG, animationSpec = tween(120), label = "g_lat")
    val animLon by animateFloatAsState(targetValue = longitudinalG, animationSpec = tween(120), label = "g_lon")

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f - 6f

        // 0.5G ve 1.0G Eşanjör Halkaları
        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = maxRadius,
            center = center,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.06f),
            radius = maxRadius * 0.5f,
            center = center,
            style = Stroke(width = 1.5f)
        )

        // X ve Y eksen çizgileri
        drawLine(
            color = Color.White.copy(alpha = 0.1f),
            start = Offset(center.x - maxRadius, center.y),
            end = Offset(center.x + maxRadius, center.y),
            strokeWidth = 1.5f
        )
        drawLine(
            color = Color.White.copy(alpha = 0.1f),
            start = Offset(center.x, center.y - maxRadius),
            end = Offset(center.x, center.y + maxRadius),
            strokeWidth = 1.5f
        )

        // G-Kuvveti Vektör Noktası (Tam ölçek = 1.0G)
        val normX = (animLat / 1.0f).coerceIn(-1f, 1f)
        val normY = (-animLon / 1.0f).coerceIn(-1f, 1f)
        val dotPos = Offset(
            x = center.x + normX * maxRadius,
            y = center.y + normY * maxRadius
        )

        val totalG = kotlin.math.sqrt(animLat * animLat + animLon * animLon)
        val dotColor = when {
            totalG >= 0.45f -> AccentDangerRed
            totalG >= 0.25f -> AccentWarningOrange
            else -> AccentEmerald
        }

        drawLine(
            color = dotColor.copy(alpha = 0.5f),
            start = center,
            end = dotPos,
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = dotColor.copy(alpha = 0.28f),
            radius = 18f,
            center = dotPos
        )
        drawCircle(
            color = dotColor,
            radius = 9f,
            center = dotPos
        )
    }
}

/**
 * OBD-II (ELM327) BLUETOOTH & WI-FI BAĞLANTI SEÇİM MODALI.
 */
@Composable
fun ObdConnectionDialog(
    obdData: ObdLiveData,
    pairedDevices: List<ObdBluetoothDeviceItem>,
    onRefreshPaired: () -> Unit,
    onConnectBluetooth: (String) -> Unit,
    onConnectWifi: (String, Int) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTransport by remember { mutableStateOf(obdData.transportType) }
    var customMac by remember { mutableStateOf("00:1D:A5:68:98:8B") }
    var wifiHost by remember { mutableStateOf("192.168.0.10") }
    var wifiPortText by remember { mutableStateOf("35000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDarkSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔌 OBD-II (ELM327) Bağlantısı",
                    color = TextPrimaryWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                IconButton(onClick = onRefreshPaired) {
                    Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = AccentElectricBlue)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Canlı Durum Banner'ı
                val statusText = when (val st = obdData.connectionState) {
                    is ObdConnectionState.Connected -> "BAĞLI • ${obdData.connectedTargetLabel} (${obdData.rpm} RPM)"
                    is ObdConnectionState.Connecting -> "BAĞLANIYOR • ${obdData.connectedTargetLabel}..."
                    is ObdConnectionState.Reconnecting -> "YENİDEN BAĞLANIYOR (#${st.attempt}) • ${st.reason}"
                    is ObdConnectionState.Disconnected -> "BAĞLI DEĞİL • Bir ELM327 adaptörü seçin"
                }
                val statusColor = when (obdData.connectionState) {
                    is ObdConnectionState.Connected -> AccentEmerald
                    is ObdConnectionState.Connecting, is ObdConnectionState.Reconnecting -> AccentWarningOrange
                    is ObdConnectionState.Disconnected -> TextSecondaryGray
                }

                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusText,
                            color = TextPrimaryWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (obdData.connectionState !is ObdConnectionState.Disconnected) {
                            TextButton(onClick = onDisconnect) {
                                Text("Kes", color = AccentDangerRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Bağlantı Türü Seçimi (Bluetooth SPP vs Wi-Fi TCP)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ObdTransportType.entries.forEach { type ->
                        FilterChipButton(
                            text = type.displayName,
                            leadingEmoji = if (type == ObdTransportType.BLUETOOTH_SPP) "📶" else "🌐",
                            isSelected = selectedTransport == type,
                            onClick = { selectedTransport = type }
                        )
                    }
                }

                if (selectedTransport == ObdTransportType.BLUETOOTH_SPP) {
                    Text(
                        text = "Eşleşmiş Bluetooth Cihazları (${pairedDevices.size})",
                        color = TextPrimaryWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (pairedDevices.isEmpty()) {
                        Text(
                            text = "Eşleşmiş Bluetooth cihazı bulunamadı. Telefonun Bluetooth ayarlarından 'OBDII' veya 'ELM327' adaptörünü eşleştirip yenileyin ya da aşağıdan MAC adresi girin.",
                            color = TextSecondaryGray,
                            fontSize = 12.sp
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            pairedDevices.forEach { dev ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(PillUnselectedBg)
                                        .border(
                                            1.dp,
                                            if (dev.isLikelyObdAdapter) AccentElectricBlue else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onConnectBluetooth(dev.macAddress) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (dev.isLikelyObdAdapter) "⚡ ${dev.name} (OBD-II)" else dev.name,
                                            color = TextPrimaryWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(text = dev.macAddress, color = TextSecondaryGray, fontSize = 11.sp)
                                    }
                                    Text(
                                        text = "Bağlan →",
                                        color = AccentElectricBlue,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Text("Manuel Bluetooth MAC Adresi", color = TextSecondaryGray, fontSize = 12.sp)
                    OutlinedTextField(
                        value = customMac,
                        onValueChange = { customMac = it },
                        label = { Text("Örn: 00:1D:A5:68:98:8B") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onConnectBluetooth(customMac) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentElectricBlue),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Bluetooth ELM327'ye Bağlan", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "Wi-Fi ELM327 Adaptör Ayarları (Yerel Ağ)",
                        color = TextPrimaryWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChipButton(
                            text = "192.168.0.10:35000",
                            isSelected = wifiHost == "192.168.0.10",
                            onClick = {
                                wifiHost = "192.168.0.10"
                                wifiPortText = "35000"
                            }
                        )
                        FilterChipButton(
                            text = "10.0.0.1:35000",
                            isSelected = wifiHost == "10.0.0.1",
                            onClick = {
                                wifiHost = "10.0.0.1"
                                wifiPortText = "35000"
                            }
                        )
                    }
                    OutlinedTextField(
                        value = wifiHost,
                        onValueChange = { wifiHost = it },
                        label = { Text("ELM327 IP Adresi (192.168.x.x)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = wifiPortText,
                        onValueChange = { wifiPortText = it },
                        label = { Text("TCP Port (Varsayılan: 35000)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onConnectWifi(wifiHost, wifiPortText.toIntOrNull() ?: 35000)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentElectricBlue),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Wi-Fi ELM327'ye Bağlan", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = TextPrimaryWhite, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * SPOTIFY WRAPPED / STORY TARZI 6 SLAYTLI ANİMASYONLU RAPORLAMA.
 */
@Composable
fun SpotifyWrappedStoryModal(
    report: WrappedRecapReport,
    onDismiss: () -> Unit
) {
    val totalSlides = 6
    var currentSlide by remember { mutableIntStateOf(0) }
    var slideProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentSlide) {
        slideProgress = 0f
        val steps = 110
        for (i in 1..steps) {
            delay(50L)
            slideProgress = i / steps.toFloat()
        }
        if (currentSlide < totalSlides - 1) {
            currentSlide++
        } else {
            onDismiss()
        }
    }

    val slideGradients = listOf(
        listOf(Color(0xFF0A1931), Color(0xFF1565C0), Color(0xFF07090C)),
        listOf(Color(0xFF1F0A33), Color(0xFF6A1B9A), Color(0xFF07090C)),
        listOf(Color(0xFF0B2B26), Color(0xFF00897B), Color(0xFF07090C)),
        listOf(Color(0xFF2D1206), Color(0xFFE65100), Color(0xFF07090C)),
        listOf(Color(0xFF33150A), Color(0xFFD84315), Color(0xFF07090C)),
        listOf(Color(0xFF1A103C), Color(0xFF5E35B1), Color(0xFF07090C))
    )

    val infiniteTransition = rememberInfiniteTransition(label = "wrapped_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(slideGradients[currentSlide % slideGradients.size]))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (offset.x < size.width * 0.35f) {
                            currentSlide = (currentSlide - 1).coerceAtLeast(0)
                        } else {
                            if (currentSlide < totalSlides - 1) currentSlide++ else onDismiss()
                        }
                    }
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (idx in 0 until totalSlides) {
                        val fillFraction = when {
                            idx < currentSlide -> 1f
                            idx == currentSlide -> slideProgress
                            else -> 0f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.25f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fillFraction)
                                    .fillMaxHeight()
                                    .background(Color.White)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡ ${report.periodTitle} • ${report.timeframe.label.uppercase()}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 1.2.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                AnimatedContent(
                    targetState = currentSlide,
                    transitionSpec = {
                        (fadeIn(tween(350)) + slideInHorizontally { it / 3 })
                            .togetherWith(fadeOut(tween(250)) + slideOutHorizontally { -it / 3 })
                    },
                    modifier = Modifier.weight(1f),
                    label = "wrapped_slide"
                ) { slideIndex ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (slideIndex) {
                            0 -> {
                                Text(text = "🛣️", fontSize = 72.sp, modifier = Modifier.scale(pulseScale))
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = "ASFALTTA GEÇEN ZAMAN",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f km", report.totalDistanceKm),
                                    color = Color.White,
                                    fontSize = 54.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "(%.1f mi • %.1f Saat Direksiyon Başında)",
                                        report.totalDistanceMiles,
                                        report.totalDurationHours
                                    ),
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 16.sp
                                )
                            }

                            1 -> {
                                Text(text = "🚀", fontSize = 72.sp, modifier = Modifier.scale(pulseScale))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "ZİRVE HIZ & FAVORİ KORİDORUN",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = String.format(Locale.US, "%.0f km/h", report.topSpeedKmh),
                                    color = AccentGoldCrown,
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = report.mostFrequentRouteSummary,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                RoutePolylinePreview(
                                    routePoints = report.topRoutePreviewPoints,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.Black.copy(alpha = 0.35f))
                                )
                            }

                            2 -> {
                                Text(text = "🛡️", fontSize = 70.sp, modifier = Modifier.scale(pulseScale))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "GÜVENLİK VE REFLEKS ANALİZİ",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${report.safetyScore} / 100",
                                    color = AccentEmerald,
                                    fontSize = 52.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    WrappedStatPill("🛑 Ani Fren", report.totalHardBrakes.toString())
                                    WrappedStatPill("⚡ Ani Gaz", report.totalRapidAccels.toString())
                                    WrappedStatPill("↩️ Keskin Viraj", report.totalSharpTurns.toString())
                                }
                            }

                            3 -> {
                                val mech = report.mechanicalSummary
                                val persona = mech.driverPersona
                                Text(
                                    text = persona.badgeEmoji,
                                    fontSize = 76.sp,
                                    modifier = Modifier.scale(pulseScale)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "SÜRÜCÜ MEKANİK KARAKTERİN",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.4.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color(persona.accentColorHex).copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.5.dp, Color(persona.accentColorHex))
                                ) {
                                    Text(
                                        text = "${persona.badgeEmoji} ${persona.badgeTitle.uppercase()}",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 22.sp,
                                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = persona.description,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    WrappedStatPill("🔄 Ort. Devir", "${mech.avgRpm} RPM")
                                    WrappedStatPill("⚙️ Favori Vites", "${mech.mostUsedGear.coerceAtLeast(1)}. Vites")
                                    WrappedStatPill("🔥 Yarım Debriyaj", "${mech.clutchSlipEventsCount} Kez")
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color.Black.copy(alpha = 0.35f))
                                        .padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Mekanik Yıpranma Skoru: ${mech.mechanicalWearScore}/100 (Sağlık: %${mech.mechanicalHealthScore})",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { (mech.mechanicalHealthScore / 100f).coerceIn(0.05f, 1f) },
                                        color = Color(persona.accentColorHex),
                                        trackColor = Color.White.copy(alpha = 0.15f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }

                            4 -> {
                                Text(
                                    text = report.favoriteVehicleCategory.iconEmoji,
                                    fontSize = 78.sp,
                                    modifier = Modifier.scale(pulseScale)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "YOL ARKADAŞIN (${report.favoriteVehicleCategory.displayName.uppercase()})",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = report.favoriteVehicleTitle,
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "OBD-II Vites Oranı ve İvmeölçer Füzyonu ile Kusursuz Senkronize Edildi",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            else -> {
                                Text(text = "🏆", fontSize = 72.sp, modifier = Modifier.scale(pulseScale))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "KAZANILAN MİLESTONE ROZETLERİ",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    report.unlockedMilestones.forEach { milestone ->
                                        MilestoneRowItem(milestone = milestone)
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "İlerlemek için sağa, geri dönmek için sola dokun",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun WrappedStatPill(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text = value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

/**
 * OBD-II BAĞLANTI MERKEZİ, CANLI ECU DİAGNOSTİK TABLOSU, GARAJ VE SENSÖR KALİBRASYONU.
 */
@Composable
fun GarageAndObdScreen(
    vehicles: List<GarageVehicleEntity>,
    placementMode: DevicePlacementMode,
    isAutopilotEnabled: Boolean,
    liveState: LiveTelemetryState,
    pairedObdDevices: List<ObdBluetoothDeviceItem>,
    onSelectPlacementMode: (DevicePlacementMode) -> Unit,
    onToggleAutopilot: (Boolean) -> Unit,
    onSetActiveVehicle: (String) -> Unit,
    onAddVehicle: (VehicleCategory, String, String, String, String, Int, Int) -> Unit,
    onRefreshPairedObdDevices: () -> Unit,
    onConnectObdBluetooth: (String) -> Unit,
    onConnectObdWifi: (String, Int) -> Unit,
    onDisconnectObd: () -> Unit
) {
    var showAddVehicleDialog by remember { mutableStateOf(false) }
    var showObdSelectorModal by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            ScreenHeader(title = "OBD-II & Garage", badgeInitial = "Y", notificationCount = 1)
        }

        // OBD-II (ELM327) Bağlantı & Canlı ECU Telemetri Kartı
        item {
            val obd = liveState.obdData
            val isConnected = obd.isConnected

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardDarkSurface)
                    .border(
                        1.5.dp,
                        if (isConnected) AccentEmerald else AccentElectricBlue.copy(alpha = 0.45f),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔌 OBD-II (ELM327) ECU Bağlantısı",
                            color = TextPrimaryWhite,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        val sub = when (val st = obd.connectionState) {
                            is ObdConnectionState.Connected -> "Bağlı: ${obd.connectedTargetLabel} (${obd.transportType.displayName})"
                            is ObdConnectionState.Connecting -> "Bağlanıyor: ${obd.connectedTargetLabel}..."
                            is ObdConnectionState.Reconnecting -> "Oto-Yeniden Bağlanma (#${st.attempt}): ${st.reason}"
                            is ObdConnectionState.Disconnected -> "Bluetooth SPP veya Wi-Fi ELM327 adaptörü seçin"
                        }
                        Text(text = sub, color = TextSecondaryGray, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            onRefreshPairedObdDevices()
                            showObdSelectorModal = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) AccentEmerald else AccentElectricBlue
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (isConnected) "Ayarlar" else "Cihaz Seç",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Canlı OBD-II PID Göstergeleri (RPM, Hız, Motor Yükü, Soğutma Suyu, Gaz, Vites)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ObdPidMiniCard("Motor Devri", "${obd.rpm} RPM", "PID 010C", Modifier.weight(1f))
                    ObdPidMiniCard("ECU Hızı", "${obd.obdSpeedKmh.toInt()} km/h", "PID 010D", Modifier.weight(1f))
                    ObdPidMiniCard("Soğutma Suyu", "${obd.coolantTempC} °C", "PID 0105", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ObdPidMiniCard("Motor Yükü", "%${obd.engineLoadPercent.toInt()}", "PID 0104", Modifier.weight(1f))
                    ObdPidMiniCard("Gaz Kelebeği", "%${obd.throttlePercent.toInt()}", "PID 0111", Modifier.weight(1f))
                    val gearStr = if (obd.estimatedGear in 1..6) "${obd.estimatedGear}. Vites" else "Boş (N)"
                    ObdPidMiniCard("Tahmini Vites", gearStr, String.format(Locale.US, "%.1f km/k", obd.gearRatioKmhPer1000Rpm), Modifier.weight(1f))
                }
            }
        }

        // Otopilot & Chunked Flush Kartı
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardDarkSurface)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🛰️ Otopilot (IN_VEHICLE Tespiti)",
                            color = TextPrimaryWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Araca bindiğinizde sürüş kaydını otomatik başlatır, indiğinizde durdurur.",
                            color = TextSecondaryGray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isAutopilotEnabled,
                        onCheckedChange = onToggleAutopilot
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "💾 Crash-Proof Chunked Flush: Her 500 noktada / 3 dakikada bir Room WAL veritabanına kaydedilir (Aktif Chunk: #${liveState.flushedChunksCount})",
                    color = AccentElectricBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Dinamik Sensör Kalibrasyonu
        item {
            Text(
                text = "Dinamik Sensör Kalibrasyonu",
                color = TextPrimaryWhite,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DevicePlacementMode.entries.forEach { mode ->
                    val selected = mode == placementMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) AccentElectricBlue.copy(alpha = 0.18f) else CardDarkSurface)
                            .border(
                                width = 1.5.dp,
                                color = if (selected) AccentElectricBlue else Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { onSelectPlacementMode(mode) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { onSelectPlacementMode(mode) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = mode.title, color = TextPrimaryWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = "${mode.subtitle} (α=${mode.lowPassAlpha}, Fren=${mode.hardBrakeThresholdG}G)",
                                color = TextSecondaryGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Genişletilmiş Garaj Başlığı
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Genişletilmiş Garaj", color = TextPrimaryWhite, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = { showAddVehicleDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentElectricBlue),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("+ Araç / Motor Ekle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        items(vehicles, key = { it.vehicleId }) { vehicle ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardDarkSurface)
                    .border(
                        width = 1.5.dp,
                        color = if (vehicle.isActiveVehicle) AccentEmerald else Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSetActiveVehicle(vehicle.vehicleId) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = vehicle.categoryEnum.iconEmoji, fontSize = 32.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${vehicle.make} → ${vehicle.model}",
                        color = TextPrimaryWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Motor: ${vehicle.engineSpec} • Paket: ${vehicle.trimPackage} (${vehicle.year})",
                        color = TextSecondaryGray,
                        fontSize = 13.sp
                    )
                    val obdBinding = vehicle.obdMacAddress ?: vehicle.obdWifiHost
                    if (!obdBinding.isNullOrBlank()) {
                        Text(
                            text = "🔗 Kayıtlı ELM327: $obdBinding",
                            color = AccentElectricBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (vehicle.isActiveVehicle) {
                    Text("AKTİF", color = AccentEmerald, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }

        item {
            AboutDeveloperCard()
        }
    }

    if (showObdSelectorModal) {
        ObdConnectionDialog(
            obdData = liveState.obdData,
            pairedDevices = pairedObdDevices,
            onRefreshPaired = onRefreshPairedObdDevices,
            onConnectBluetooth = onConnectObdBluetooth,
            onConnectWifi = onConnectObdWifi,
            onDisconnect = onDisconnectObd,
            onDismiss = { showObdSelectorModal = false }
        )
    }

    if (showAddVehicleDialog) {
        AddHierarchicalVehicleDialog(
            onDismiss = { showAddVehicleDialog = false },
            onSave = { cat, make, model, engine, trim, yr, hp ->
                onAddVehicle(cat, make, model, engine, trim, yr, hp)
                showAddVehicleDialog = false
            }
        )
    }
}

@Composable
private fun ObdPidMiniCard(title: String, value: String, pidCode: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PillUnselectedBg)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, color = TextSecondaryGray, fontSize = 10.sp, maxLines = 1)
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = value, color = TextPrimaryWhite, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
        Text(text = pidCode, color = AccentElectricBlue, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AboutDeveloperCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardDarkSurface)
            .border(1.dp, AccentPurpleRing.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "ApexPulse  •  Geliştirici: YigitEXP",
            color = TextPrimaryWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Donanım ve yazılım arasındaki uyumsuzlukları çözmek için CircuitVR adlı açık kaynak projeyi de geliştiriyorum: Python'da eğitilen Edge AI modellerini doğrudan RTL donanıma çeviren, güvenlik odaklı (whitelist & RTL trojan linter) ve yerel/bulut hibrit çalışan bir tasarım ortamı.",
            color = TextSecondaryGray,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun AddHierarchicalVehicleDialog(
    onDismiss: () -> Unit,
    onSave: (VehicleCategory, String, String, String, String, Int, Int) -> Unit
) {
    var category by remember { mutableStateOf(VehicleCategory.AUTOMOBILE) }
    var make by remember { mutableStateOf("Fiat") }
    var model by remember { mutableStateOf("Linea") }
    var engine by remember { mutableStateOf("1.3 Multijet") }
    var trim by remember { mutableStateOf("Urban") }
    var yearText by remember { mutableStateOf("2016") }
    var hpText by remember { mutableStateOf("95") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDarkSurface,
        title = { Text("Yeni Araç / Motosiklet Ekle", color = TextPrimaryWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VehicleCategory.entries.forEach { cat ->
                        FilterChipButton(
                            text = "${cat.iconEmoji} ${cat.displayName}",
                            isSelected = category == cat,
                            onClick = { category = cat }
                        )
                    }
                }
                OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Marka (Örn: Fiat, Yamaha)") }, singleLine = true)
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model (Örn: Linea, MT-07)") }, singleLine = true)
                OutlinedTextField(value = engine, onValueChange = { engine = it }, label = { Text("Motor (Örn: 1.3 Multijet)") }, singleLine = true)
                OutlinedTextField(value = trim, onValueChange = { trim = it }, label = { Text("Donanım Paketi (Örn: Urban)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        category,
                        make,
                        model,
                        engine,
                        trim,
                        yearText.toIntOrNull() ?: 2020,
                        hpText.toIntOrNull() ?: 100
                    )
                }
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal", color = TextSecondaryGray) }
        }
    )
}

@Composable
fun DrivesScreen(
    drives: List<DriveEntity>,
    vehicles: List<GarageVehicleEntity>,
    selectedVehicleId: String?,
    selectedSort: DriveSortFilter,
    liveState: LiveTelemetryState,
    onSelectVehicle: (String?) -> Unit,
    onSelectSort: (DriveSortFilter) -> Unit,
    onToggleRecording: () -> Unit,
    onOpenObdModal: () -> Unit = {},
    onOpenNavigationTab: () -> Unit = {}
) {
    var carMenuExpanded by remember { mutableStateOf(false) }
    val selectedCarName = vehicles.firstOrNull { it.vehicleId == selectedVehicleId }?.shortTitle ?: "All Cars"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        ScreenHeader(title = "Drives", badgeInitial = "Y", notificationCount = 1)
        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Box {
                    FilterChipButton(
                        text = "$selectedCarName ▾",
                        leadingEmoji = "🚗",
                        isSelected = selectedVehicleId != null,
                        onClick = { carMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = carMenuExpanded,
                        onDismissRequest = { carMenuExpanded = false },
                        modifier = Modifier.background(CardDarkSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Cars", color = TextPrimaryWhite) },
                            onClick = {
                                onSelectVehicle(null)
                                carMenuExpanded = false
                            }
                        )
                        vehicles.forEach { vehicle ->
                            DropdownMenuItem(
                                text = { Text(vehicle.fullHierarchyName, color = TextPrimaryWhite) },
                                onClick = {
                                    onSelectVehicle(vehicle.vehicleId)
                                    carMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            items(DriveSortFilter.entries) { filter ->
                FilterChipButton(
                    text = filter.displayName,
                    isSelected = selectedSort == filter,
                    onClick = { onSelectSort(filter) }
                )
            }

            item {
                FilterChipButton(
                    text = if (liveState.obdData.isConnected) "OBD-II Bağlı" else "OBD-II Bağla",
                    leadingEmoji = "🔌",
                    isSelected = liveState.obdData.isConnected,
                    onClick = onOpenObdModal
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        LiveTelemetryBanner(liveState = liveState, onToggleRecording = onToggleRecording)
        Spacer(modifier = Modifier.height(14.dp))

        if (drives.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RedSportsCarVectorIllustration(modifier = Modifier.size(width = 130.dp, height = 74.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No drives yet", color = TextPrimaryWhite, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your completed drives will show up here.\nGo take a drive!",
                        color = TextSecondaryGray,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onOpenNavigationTab,
                        border = BorderStroke(1.dp, AccentElectricBlue.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = AccentElectricBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Navigasyon & Sürüş Kokpitini Aç", color = TextPrimaryWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        } else {
            val converters = remember { TelemetryTypeConverters() }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(drives, key = { it.driveId }) { drive ->
                    val points = remember(drive.previewRouteJson) {
                        converters.toRoutePoints(drive.previewRouteJson)
                    }
                    DriveHistoryCard(drive = drive, routePoints = points)
                }
            }
        }
    }
}

@Composable
fun StatisticsScreen(
    stats: StatisticsSummary,
    selectedTimeframe: RecapTimeframe,
    onSelectTimeframe: (RecapTimeframe) -> Unit,
    onOpenWrappedStory: (WrappedRecapReport) -> Unit,
    onShareStats: () -> Unit = {}
) {
    val activeReport = stats.wrappedReports[selectedTimeframe]
        ?: WrappedRecapReport(
            timeframe = selectedTimeframe,
            periodTitle = selectedTimeframe.badgeTitle,
            totalDrives = 0,
            totalDistanceMiles = 0.0,
            totalDistanceKm = 0.0,
            totalDurationHours = 0.0,
            topSpeedKmh = 0f,
            topSpeedMph = 0f,
            totalHardBrakes = 0,
            totalRapidAccels = 0,
            totalSharpTurns = 0,
            safetyScore = 100,
            favoriteVehicleTitle = "Fiat Linea • 1.3 Multijet • Urban",
            favoriteVehicleCategory = VehicleCategory.AUTOMOBILE,
            mostFrequentRouteSummary = "Henüz sürüş yok",
            topRoutePreviewPoints = emptyList(),
            unlockedMilestones = stats.distanceMilestones.take(2),
            dominantSpeedBucket = null,
            mechanicalSummary = stats.mechanicalSummary
        )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            ScreenHeader(title = "Statistics", badgeInitial = "Y", notificationCount = 1)
        }

        // Blueprint Grid Tasarımlı OCTOBER RECAP Kartı (Görsel 5 ile birebir)
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecapTimeframe.entries.forEach { tf ->
                    FilterChipButton(
                        text = tf.label,
                        isSelected = selectedTimeframe == tf,
                        onClick = { onSelectTimeframe(tf) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            BlueprintRecapBannerCard(
                title = "Your ${activeReport.periodTitle} is Here!",
                subtitle = "Tap to view",
                onClick = { onOpenWrappedStory(activeReport) }
            )
        }

        // Total Drive Distance + Share + Indy 500 / Coast to Coast / Around Earth / To the Moon
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total Drive Distance", color = TextPrimaryWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Surface(
                    color = CardDarkSurface,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.clickable { onShareStats() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = "Share", tint = TextPrimaryWhite, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Share", color = TextPrimaryWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.1f", stats.totalDistanceMiles),
                    color = TextPrimaryWhite,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("mi", color = TextSecondaryGray, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(
                text = String.format(Locale.US, "Average drive distance: %.1f mi", stats.avgDriveDistanceMiles),
                color = TextPrimaryWhite.copy(alpha = 0.9f),
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.distanceMilestones.forEach { MilestoneRowItem(it) }
            }
        }

        // Top Speed & Speed Distribution Grafik Kartı
        item {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text("Top Speed", color = TextPrimaryWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = String.format(Locale.US, "%.0f mph (%.0f km/h)", stats.rocketModeTopSpeedMph, stats.rocketModeTopSpeedMph / 0.621371f),
                    color = AccentGoldCrown,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.speedDistribution.forEach { SpeedDistributionRow(it) }
            }
        }

        // Total Time Driven & Gezegen Günleri Rozetleri (Görsel 3 ile birebir)
        item {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Total Time Driven", color = TextPrimaryWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.1f", stats.totalTimeHours),
                    color = TextPrimaryWhite,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("hrs", color = TextSecondaryGray, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.timeMilestones.forEach { MilestoneRowItem(it) }
            }
        }

        // Averages & Personal Bests (Görsel 1 ile birebir)
        item {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Averages", color = TextPrimaryWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(12.dp))
            StatMetricRow("💨", "Avg Speed", "Average speed across all drives", String.format(Locale.US, "%.0f mph", stats.avgSpeedMph))
            StatMetricRow("📏", "Avg Trip", "Average distance per trip", String.format(Locale.US, "%.1f mi", stats.avgTripMiles))
            StatMetricRow("⏱️", "Avg Drive Time", "Typical duration behind the wheel", String.format(Locale.US, "%.0f m", stats.avgDriveTimeMinutes))
            StatMetricRow("📅", "Weekly Drives", "Average drives per week", String.format(Locale.US, "%.1f", stats.weeklyDrivesCount))

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))

            Text("Personal Bests", color = TextPrimaryWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(12.dp))
            StatMetricRow("🚀", "Rocket Mode", "Fastest speed recorded", String.format(Locale.US, "%.0f mph", stats.rocketModeTopSpeedMph))
            StatMetricRow("🛣️", "Long Haul", "Longest single drive distance", String.format(Locale.US, "%.1f mi", stats.longHaulMaxDistanceMiles))
            StatMetricRow("🪑", "Seat Time", "Longest single drive duration", String.format(Locale.US, "%.0f m", stats.seatTimeMaxDurationMinutes))
        }

        // Mekanik Analiz & Sürücü Karakteristiği Özeti Kartı
        item {
            val mech = stats.mechanicalSummary
            val persona = mech.driverPersona
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardDarkSurface)
                    .border(1.dp, Color(persona.accentColorHex).copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mechanical & Gear Profile",
                        color = TextPrimaryWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Surface(
                        color = Color(persona.accentColorHex).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "${persona.badgeEmoji} ${persona.badgeTitle}",
                            color = Color(persona.accentColorHex),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = persona.description, color = TextSecondaryGray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                StatMetricRow("🔄", "Ortalama Devir", "Sürüş boyunca ortalama motor devri", "${mech.avgRpm} RPM")
                StatMetricRow("⚙️", "En Çok Kullanılan Vites", "Hız / Devir (Gear Ratio) analizi", "${mech.mostUsedGear.coerceAtLeast(1)}. Vites")
                StatMetricRow("🔥", "Debriyaj Yıpranma (Clutch Slip)", "Yarım debriyaj / sürtünme tespiti", "${mech.clutchSlipEventsCount} İhlal")
                StatMetricRow("🛠️", "Mekanik Sağlık Skoru", "100 = Kusursuz şanzıman & motor sağlığı", "%${mech.mechanicalHealthScore}")
            }
        }
    }
}

@Composable
private fun BlueprintRecapBannerCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF0B65C6), Color(0xFF1A7FE8), Color(0xFF1068C9))
                )
            )
            // Blueprint ince ızgara çizgileri
            val gridStep = 18.dp.toPx()
            val gridColor = Color.White.copy(alpha = 0.08f)
            var gx = 0f
            while (gx < size.width) {
                drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                gx += gridStep
            }
            var gy = 0f
            while (gy < size.height) {
                drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                gy += gridStep
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
        ) {
            RedSportsCarVectorIllustration(modifier = Modifier.size(width = 64.dp, height = 38.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(text = "→", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Görsel 4 ve 5'teki kırmızı spor otomobil vektör çizimi.
 */
@Composable
private fun RedSportsCarVectorIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Gölge
        drawOval(
            color = Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(w * 0.08f, h * 0.78f),
            size = Size(w * 0.84f, h * 0.16f)
        )

        // Gövde Path
        val bodyPath = Path().apply {
            moveTo(w * 0.10f, h * 0.68f)
            lineTo(w * 0.16f, h * 0.44f)
            lineTo(w * 0.34f, h * 0.22f)
            lineTo(w * 0.66f, h * 0.22f)
            lineTo(w * 0.84f, h * 0.44f)
            lineTo(w * 0.92f, h * 0.56f)
            lineTo(w * 0.90f, h * 0.72f)
            lineTo(w * 0.10f, h * 0.72f)
            close()
        }
        drawPath(
            path = bodyPath,
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFF453A), Color(0xFFD70015))
            )
        )

        // Camlar
        val windowPath = Path().apply {
            moveTo(w * 0.24f, h * 0.44f)
            lineTo(w * 0.36f, h * 0.27f)
            lineTo(w * 0.63f, h * 0.27f)
            lineTo(w * 0.76f, h * 0.44f)
            close()
        }
        drawPath(path = windowPath, color = Color(0xFF80D8FF).copy(alpha = 0.85f))

        // Ön ve Arka Tekerlekler
        val wheelRadius = h * 0.17f
        val leftWheelCenter = Offset(w * 0.26f, h * 0.72f)
        val rightWheelCenter = Offset(w * 0.74f, h * 0.72f)

        drawCircle(color = Color(0xFF1C1C1E), radius = wheelRadius, center = leftWheelCenter)
        drawCircle(color = Color(0xFFD1D1D6), radius = wheelRadius * 0.52f, center = leftWheelCenter)
        drawCircle(color = Color(0xFF1C1C1E), radius = wheelRadius, center = rightWheelCenter)
        drawCircle(color = Color(0xFFD1D1D6), radius = wheelRadius * 0.52f, center = rightWheelCenter)
    }
}

@Composable
fun LeaderboardScreen(
    entries: List<LeaderboardEntry>,
    selectedPeriod: LeaderboardPeriod,
    selectedScope: LeaderboardScope,
    selectedMetric: LeaderboardMetric,
    onSelectPeriod: (LeaderboardPeriod) -> Unit,
    onSelectScope: (LeaderboardScope) -> Unit,
    onSelectMetric: (LeaderboardMetric) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(title = "Leaderboard", badgeInitial = "Y", notificationCount = 1)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChipButton(
                    text = "${selectedPeriod.label} ▾",
                    isSelected = false,
                    onClick = {
                        onSelectPeriod(
                            if (selectedPeriod == LeaderboardPeriod.MONTH) LeaderboardPeriod.ALL_TIME
                            else LeaderboardPeriod.MONTH
                        )
                    }
                )
                FilterChipButton(
                    text = "${selectedScope.label} ▾",
                    isSelected = false,
                    onClick = {
                        onSelectScope(
                            if (selectedScope == LeaderboardScope.GLOBAL) LeaderboardScope.FRIENDS
                            else LeaderboardScope.GLOBAL
                        )
                    }
                )
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LeaderboardMetric.entries) { metric ->
                    val icon = when (metric) {
                        LeaderboardMetric.MILES -> "🛣️"
                        LeaderboardMetric.HOURS -> "🕒"
                        LeaderboardMetric.DRIVES -> "🚗"
                    }
                    FilterChipButton(
                        text = metric.label,
                        leadingEmoji = icon,
                        isSelected = selectedMetric == metric,
                        onClick = { onSelectMetric(metric) }
                    )
                }
            }
        }
        if (entries.size >= 3) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    PodiumColumn(entry = entries[1], rank = 2, ringColor = AccentSilver, isFirst = false)
                    PodiumColumn(entry = entries[0], rank = 1, ringColor = AccentGoldCrown, isFirst = true)
                    PodiumColumn(entry = entries[2], rank = 3, ringColor = AccentBronze, isFirst = false)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        item {
            Text("RANKINGS", color = TextSecondaryGray, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
        }
        items(entries.drop(3), key = { it.userId }) { item ->
            LeaderboardRankCard(entry = item)
        }
    }
}

/**
 * Görsel 3 ve 5'teki Özel Canvas İllüstrasyonlu Milestone Rozet Satırı.
 */
@Composable
private fun MilestoneRowItem(milestone: GamificationMilestone) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(CardDarkSurface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .border(3.dp, AccentPurpleRing, CircleShape)
                .background(Color(0xFF1F2330)),
            contentAlignment = Alignment.Center
        ) {
            MilestoneArtworkCanvas(
                milestoneId = milestone.id,
                fallbackEmoji = milestone.iconEmoji,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = milestone.formattedMultiplier,
            color = TextPrimaryWhite,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 19.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = milestone.title,
            color = TextSecondaryGray,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MilestoneArtworkCanvas(
    milestoneId: String,
    fallbackEmoji: String,
    modifier: Modifier = Modifier
) {
    when (milestoneId) {
        "indy_500" -> {
            // Damalı yarış bayrağı dokusu
            Canvas(modifier = modifier) {
                val cols = 5
                val cellW = size.width / cols
                val cellH = size.height / cols
                for (r in 0 until cols) {
                    for (c in 0 until cols) {
                        val isWhite = (r + c) % 2 == 0
                        drawRect(
                            color = if (isWhite) Color.White else Color(0xFF16181D),
                            topLeft = Offset(c * cellW, r * cellH),
                            size = Size(cellW + 0.5f, cellH + 0.5f)
                        )
                    }
                }
            }
        }
        "coast_to_coast" -> {
            // ABD Bayrağı Çizgileri ve Mavi Yıldız Kantonu
            Canvas(modifier = modifier) {
                val stripes = 7
                val stripeH = size.height / stripes
                for (i in 0 until stripes) {
                    drawRect(
                        color = if (i % 2 == 0) Color(0xFFD32F2F) else Color.White,
                        topLeft = Offset(0f, i * stripeH),
                        size = Size(size.width, stripeH + 0.5f)
                    )
                }
                drawRect(
                    color = Color(0xFF1565C0),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width * 0.52f, size.height * 0.54f)
                )
            }
        }
        "around_earth", "earth_days" -> {
            // Dünya Küresi (Okyanus + Yeşil Kıtalar)
            Canvas(modifier = modifier) {
                drawCircle(color = Color(0xFF1976D2))
                drawCircle(
                    color = Color(0xFF43A047),
                    radius = size.minDimension * 0.28f,
                    center = Offset(size.width * 0.38f, size.height * 0.42f)
                )
                drawCircle(
                    color = Color(0xFF66BB6A),
                    radius = size.minDimension * 0.22f,
                    center = Offset(size.width * 0.68f, size.height * 0.62f)
                )
            }
        }
        "to_the_moon" -> {
            // Ay Yüzeyi ve Kraterler
            Canvas(modifier = modifier) {
                drawCircle(color = Color(0xFFBDBDBD))
                drawCircle(color = Color(0xFF9E9E9E), radius = size.minDimension * 0.16f, center = Offset(size.width * 0.35f, size.height * 0.38f))
                drawCircle(color = Color(0xFF9E9E9E), radius = size.minDimension * 0.12f, center = Offset(size.width * 0.65f, size.height * 0.58f))
                drawCircle(color = Color(0xFF8D8D8D), radius = size.minDimension * 0.09f, center = Offset(size.width * 0.55f, size.height * 0.30f))
            }
        }
        "mercury_days", "venus_days" -> {
            val colors = if (milestoneId == "mercury_days") {
                listOf(Color(0xFFD7CCC8), Color(0xFF8D6E63))
            } else {
                listOf(Color(0xFFFFCC80), Color(0xFFEF6C00))
            }
            Canvas(modifier = modifier) {
                drawCircle(brush = Brush.radialGradient(colors))
            }
        }
        else -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Text(text = fallbackEmoji, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun SpeedDistributionRow(bucket: SpeedDistributionBucket) {
    val animatedFraction by animateFloatAsState(
        targetValue = bucket.percentage.coerceIn(0.03f, 1f),
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "speed_dist_bar"
    )
    val pct = (bucket.percentage * 100f).toInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDarkSurface)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = bucket.label, color = TextPrimaryWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(text = "$pct% (${bucket.durationSeconds}s)", color = TextSecondaryGray, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(bucket.colorHex))
            )
        }
    }
}

@Composable
private fun StatMetricRow(icon: String, title: String, subtitle: String, valueText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(CardDarkSurface)
                .border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimaryWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = subtitle, color = TextSecondaryGray, fontSize = 12.sp)
        }
        Text(text = valueText, color = TextPrimaryWhite, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

@Composable
private fun PodiumColumn(entry: LeaderboardEntry, rank: Int, ringColor: Color, isFirst: Boolean) {
    val avatarSize = if (isFirst) 88.dp else 72.dp
    val pedestalHeight = if (isFirst) 56.dp else 44.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(105.dp)
    ) {
        if (isFirst) {
            Text(text = "👑", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(2.dp))
        }
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .border(3.dp, ringColor, CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(ringColor.copy(alpha = 0.3f), CardDarkSurface)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.username.take(2).uppercase(),
                color = TextPrimaryWhite,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (isFirst) 24.sp else 18.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = entry.username, color = TextPrimaryWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = String.format(Locale.US, "%.1f %s", entry.metricValue, entry.unit),
            color = ringColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (isFirst) 17.sp else 15.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pedestalHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(ringColor.copy(alpha = 0.18f))
                .border(1.dp, ringColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = rank.toString(), color = ringColor, fontWeight = FontWeight.ExtraBold, fontSize = if (isFirst) 26.sp else 20.sp)
        }
    }
}

@Composable
private fun LeaderboardRankCard(entry: LeaderboardEntry) {
    val isCurrentUser = entry.userId == "current_user"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isCurrentUser) AccentElectricBlue.copy(alpha = 0.16f) else CardDarkSurface)
            .border(
                1.dp,
                if (isCurrentUser) AccentElectricBlue else Color.Transparent,
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = entry.rank.toString(), color = TextSecondaryGray, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF262B36)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = entry.username.take(1).uppercase(), color = TextPrimaryWhite, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.username, color = TextPrimaryWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = entry.handle, color = TextSecondaryGray, fontSize = 13.sp)
        }
        Text(
            text = String.format(Locale.US, "%.1f %s", entry.metricValue, entry.unit),
            color = TextPrimaryWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun LiveTelemetryBanner(liveState: LiveTelemetryState, onToggleRecording: () -> Unit) {
    val gearBadge = if (liveState.obdData.estimatedGear in 1..6) "${liveState.obdData.estimatedGear}. Vites" else "N"
    val slipWarning = if (liveState.obdData.isClutchSlipping) " • ⚠️ YARIM DEBRİYAJ!" else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardDarkSurface)
            .border(
                1.dp,
                if (liveState.obdData.isClutchSlipping) AccentDangerRed
                else if (liveState.isRecording) AccentEmerald
                else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(18.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (liveState.isRecording) "● SÜRÜŞ KAYDI AKTİF$slipWarning" else "OTOPİLOT & SENSÖR FÜZYONU HAZIR",
                color = if (liveState.obdData.isClutchSlipping) AccentDangerRed
                else if (liveState.isRecording) AccentEmerald
                else TextSecondaryGray,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format(
                    Locale.US,
                    "%.0f km/h • %d RPM • [%s] • %.2fG",
                    liveState.currentSpeedKmh,
                    liveState.obdData.rpm,
                    gearBadge,
                    liveState.longitudinalG
                ),
                color = TextPrimaryWhite,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }
        Button(
            onClick = onToggleRecording,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (liveState.isRecording) AccentDangerRed else AccentElectricBlue
            ),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = if (liveState.isRecording) "Bitir" else "Sürüşe Başla",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DriveHistoryCard(drive: DriveEntity, routePoints: List<RoutePoint>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardDarkSurface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = drive.vehicleFullHierarchyName,
                color = TextPrimaryWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = String.format(Locale.US, "Safety %d%%", drive.safetyScore),
                color = AccentEmerald,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        RoutePolylinePreview(
            routePoints = routePoints,
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0C0F14))
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format(Locale.US, "%.2f km (Max %.0f km/h)", drive.distanceMeters / 1000.0, drive.maxSpeedKmh),
                color = TextPrimaryWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = String.format(
                    Locale.US,
                    "Ort %d RPM • %d. Vites",
                    drive.avgRpm,
                    drive.mostUsedGear.coerceAtLeast(1)
                ),
                color = TextSecondaryGray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun RoutePolylinePreview(routePoints: List<RoutePoint>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (routePoints.size < 2) return@Canvas
        val minLat = routePoints.minOf { it.latitude }
        val maxLat = routePoints.maxOf { it.latitude }
        val minLng = routePoints.minOf { it.longitude }
        val maxLng = routePoints.maxOf { it.longitude }

        val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
        val lngSpan = (maxLng - minLng).coerceAtLeast(0.0001)

        val path = Path()
        routePoints.forEachIndexed { idx, pt ->
            val x = ((pt.longitude - minLng) / lngSpan).toFloat() * (size.width * 0.84f) + (size.width * 0.08f)
            val y = (1f - ((pt.latitude - minLat) / latSpan).toFloat()) * (size.height * 0.76f) + (size.height * 0.12f)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = AccentElectricBlue.copy(alpha = 0.3f),
            style = Stroke(width = 14f, cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            color = AccentNeonCyan,
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun ScreenHeader(title: String, badgeInitial: String, notificationCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = TextPrimaryWhite, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0B253C)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = badgeInitial, color = AccentElectricBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            if (notificationCount > 0) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(AccentDangerRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = notificationCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FilterChipButton(
    text: String,
    leadingEmoji: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) AccentElectricBlue else PillUnselectedBg
    val txtColor = if (isSelected) Color.White else TextPrimaryWhite

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingEmoji != null) {
            Text(text = leadingEmoji, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = text, color = txtColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun WheelCharmBottomNavigation(
    selectedTab: BottomNavTab,
    isObdConnected: Boolean,
    isNavigating: Boolean,
    onSelectTab: (BottomNavTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val iconVector = when (tab) {
                BottomNavTab.DRIVES -> Icons.Default.DirectionsCar
                BottomNavTab.NAVIGATION -> Icons.Default.Explore
                BottomNavTab.OBD_GARAGE -> Icons.Default.Build
                BottomNavTab.STATISTICS -> Icons.Default.BarChart
                BottomNavTab.LEADERBOARD -> Icons.Default.EmojiEvents
            }

            Box(
                modifier = Modifier
                    .size(width = 66.dp, height = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (selected) {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.25f),
                                    Color.White.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        } else {
                            Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .clickable { onSelectTab(tab) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = tab.title,
                    tint = if (selected) Color.White else TextSecondaryGray,
                    modifier = Modifier.size(25.dp)
                )

                // OBD veya Navigasyon aktif nokta rozeti
                if ((tab == BottomNavTab.OBD_GARAGE && isObdConnected) ||
                    (tab == BottomNavTab.NAVIGATION && isNavigating)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 14.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald)
                    )
                }
            }
        }
    }
}

/**
 * GÖRSEL 1-5 İLE BİREBİR UYUMLU KARANLIK VEKTÖR HARİTA KATMANI + CANLI NAVİGASYON ROTASI.
 */
@Composable
private fun DarkMapBackgroundCanvas(
    liveTelemetry: LiveTelemetryState,
    navigationState: ActiveNavigationState,
    presetWaypoints: List<NavigationWaypoint>
) {
    val textMeasurer = rememberTextMeasurer()
    val infiniteTransition = rememberInfiniteTransition(label = "map_gps_pulse")
    val gpsPulseRadius by infiniteTransition.animateFloat(
        initialValue = 14f,
        targetValue = 38f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gps_pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. İnce Sokak Ağları (Secondary Streets)
        val minorRoadColor = Color(0xFF141821)
        val majorRoadColor = Color(0xFF1E2432)

        val step = 78f
        var x = 0f
        while (x < w) {
            drawLine(minorRoadColor, Offset(x, 0f), Offset(x + 65f, h), strokeWidth = 2.2f)
            drawLine(minorRoadColor, Offset(x + 40f, 0f), Offset(x - 50f, h), strokeWidth = 1.6f)
            x += step
        }
        var y = 0f
        while (y < h) {
            drawLine(minorRoadColor, Offset(0f, y), Offset(w, y - 48f), strokeWidth = 2.0f)
            y += step
        }

        // 2. Ana Bulvarlar ve Kavşaklar (Arterial Roads)
        val arterial1 = Path().apply {
            moveTo(0f, h * 0.18f)
            cubicTo(w * 0.35f, h * 0.26f, w * 0.65f, h * 0.22f, w, h * 0.32f)
        }
        val arterial2 = Path().apply {
            moveTo(w * 0.18f, 0f)
            cubicTo(w * 0.28f, h * 0.40f, w * 0.52f, h * 0.65f, w * 0.78f, h)
        }
        val arterial3 = Path().apply {
            moveTo(0f, h * 0.68f)
            cubicTo(w * 0.40f, h * 0.58f, w * 0.70f, h * 0.52f, w, h * 0.42f)
        }
        drawPath(arterial1, majorRoadColor, style = Stroke(width = 6f, cap = StrokeCap.Round))
        drawPath(arterial2, majorRoadColor, style = Stroke(width = 7f, cap = StrokeCap.Round))
        drawPath(arterial3, majorRoadColor, style = Stroke(width = 5.5f, cap = StrokeCap.Round))

        // 3. Semt / Mahalle Harita Etiketleri (Görsellerdeki gerçekçi koyu harita tipografisi)
        val districtStyle = TextStyle(
            color = Color.White.copy(alpha = 0.13f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp
        )
        val cityStyle = TextStyle(
            color = Color.White.copy(alpha = 0.18f),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold
        )
        val hospitalStyle = TextStyle(
            color = Color(0xFFE57373).copy(alpha = 0.32f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )

        val labels = listOf(
            Triple("YENİ YÜZYIL", Offset(w * 0.60f, h * 0.11f), districtStyle),
            Triple("BAHÇELİEVLER", Offset(w * 0.36f, h * 0.28f), districtStyle),
            Triple("YEDİŞEHİTLER", Offset(w * 0.24f, h * 0.34f), districtStyle),
            Triple("İSTİKLAL", Offset(w * 0.70f, h * 0.38f), districtStyle),
            Triple("BAĞLAR", Offset(w * 0.44f, h * 0.42f), districtStyle),
            Triple("Isparta", Offset(w * 0.66f, h * 0.46f), cityStyle),
            Triple("HIZIRBEY", Offset(w * 0.28f, h * 0.50f), districtStyle),
            Triple("DERE", Offset(w * 0.11f, h * 0.64f), districtStyle),
            Triple("KİRAZLIDERE", Offset(w * 0.32f, h * 0.70f), districtStyle),
            Triple("Isparta Şehir Hastanesi", Offset(w * 0.68f, h * 0.31f), hospitalStyle)
        )

        labels.forEach { (txt, pos, style) ->
            runCatching {
                drawText(
                    textMeasurer = textMeasurer,
                    text = txt,
                    topLeft = pos,
                    style = style
                )
            }
        }

        // 4. Aktif Navigasyon Hedefi Varsa Rota Koridorunu Çiz
        val gpsCenter = Offset(w * 0.48f, h * 0.54f)
        if (navigationState.isNavigating && navigationState.destination != null) {
            val destOffset = Offset(w * 0.74f, h * 0.24f)
            val navPath = Path().apply {
                moveTo(gpsCenter.x, gpsCenter.y)
                cubicTo(
                    w * 0.52f,
                    h * 0.42f,
                    w * 0.64f,
                    h * 0.34f,
                    destOffset.x,
                    destOffset.y
                )
            }
            drawPath(
                path = navPath,
                color = AccentElectricBlue.copy(alpha = 0.28f),
                style = Stroke(width = 18f, cap = StrokeCap.Round)
            )
            drawPath(
                path = navPath,
                color = AccentNeonCyan.copy(alpha = 0.75f),
                style = Stroke(
                    width = 6f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 14f), 0f)
                )
            )
            drawCircle(color = AccentDangerRed, radius = 12f, center = destOffset)
            drawCircle(color = Color.White, radius = 5f, center = destOffset)
        }

        // 5. Canlı GPS Konum İmleci (Mavi Radar Halkası)
        drawCircle(
            color = AccentElectricBlue.copy(alpha = (1f - (gpsPulseRadius / 40f)).coerceIn(0.05f, 0.35f)),
            radius = gpsPulseRadius,
            center = gpsCenter
        )
        drawCircle(color = Color.White, radius = 9f, center = gpsCenter)
        drawCircle(color = AccentElectricBlue, radius = 6.5f, center = gpsCenter)

        // 6. Canlı Sürüş Poligon Çizgisi (Kayıt esnasında)
        val routePoints = liveTelemetry.recentPreviewPoints
        if (routePoints.size >= 2) {
            val minLat = routePoints.minOf { it.latitude }
            val maxLat = routePoints.maxOf { it.latitude }
            val minLng = routePoints.minOf { it.longitude }
            val maxLng = routePoints.maxOf { it.longitude }

            val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
            val lngSpan = (maxLng - minLng).coerceAtLeast(0.0001)

            val path = Path()
            routePoints.forEachIndexed { idx, pt ->
                val px = ((pt.longitude - minLng) / lngSpan).toFloat() * (w * 0.7f) + (w * 0.15f)
                val py = (1f - ((pt.latitude - minLat) / latSpan).toFloat()) * (h * 0.5f) + (h * 0.25f)
                if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(
                path = path,
                color = AccentElectricBlue.copy(alpha = 0.45f),
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )
        }
    }
}
