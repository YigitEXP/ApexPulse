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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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

private val MapDarkBg = Color(0xFF07090C)
private val SheetGlassBg = Color(0xEB0E1116)
private val CardDarkSurface = Color(0xFF171A21)
private val PillUnselectedBg = Color(0xFF1A1D24)
private val AccentElectricBlue = Color(0xFF0A84FF)
private val AccentGoldCrown = Color(0xFFE5B842)
private val AccentSilver = Color(0xFF9BA3B0)
private val AccentBronze = Color(0xFFC47A45)
private val AccentPurpleRing = Color(0xFF7B61FF)
private val TextPrimaryWhite = Color(0xFFF5F7FA)
private val TextSecondaryGray = Color(0xFF8E95A2)

enum class BottomNavTab(val title: String) {
    DRIVES("Drives"),
    GARAGE("Garage & Calibration"),
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
    onSimulateTestDrive: () -> Unit = {}
) {
    var currentTab by remember { mutableStateOf(BottomNavTab.STATISTICS) }
    var activeWrappedStoryReport by remember { mutableStateOf<WrappedRecapReport?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MapDarkBg)
    ) {
        DarkMapBackgroundCanvas(routePoints = liveTelemetry.recentPreviewPoints)

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
                    .background(SheetGlassBg)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(32.dp))
            ) {
                when (currentTab) {
                    BottomNavTab.DRIVES -> DrivesScreen(
                        drives = drives,
                        vehicles = vehicles,
                        selectedVehicleId = selectedVehicleId,
                        selectedSort = selectedSort,
                        liveState = liveTelemetry,
                        onSelectVehicle = onSelectVehicleFilter,
                        onSelectSort = onSelectSort,
                        onToggleRecording = onToggleRecording,
                        onSimulateTestDrive = onSimulateTestDrive
                    )

                    BottomNavTab.GARAGE -> GarageAndCalibrationScreen(
                        vehicles = vehicles,
                        placementMode = placementMode,
                        isAutopilotEnabled = isAutopilotEnabled,
                        liveState = liveTelemetry,
                        onSelectPlacementMode = onSelectPlacementMode,
                        onToggleAutopilot = onToggleAutopilot,
                        onSetActiveVehicle = onSetActiveGarageVehicle,
                        onAddVehicle = onAddGarageVehicle
                    )

                    BottomNavTab.STATISTICS -> StatisticsScreen(
                        stats = stats,
                        selectedTimeframe = selectedRecapTimeframe,
                        onSelectTimeframe = onSelectRecapTimeframe,
                        onOpenWrappedStory = { report -> activeWrappedStoryReport = report }
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

            WheelCharmBottomNavigation(
                selectedTab = currentTab,
                onSelectTab = { currentTab = it }
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
 * SPOTIFY WRAPPED / STORY TARZI 6 SLAYTLI ANİMASYONLU RAPORLAMA:
 * - Slayt 1: Toplam Mesafe ve Koltuk Süresi
 * - Slayt 2: Zirve Hız & En Çok Gidilen Rota Harita Çizimi
 * - Slayt 3: Güvenlik ve Refleks Analizi (Ani Fren, Ani Gaz, Keskin Viraj)
 * - Slayt 4: Mekanik Analiz & Sürücü Karakteristiği ('Ekonomi Ustası', 'Redline Canavarı', 'Debriyaj Düşmanı', Ortalama Devir, Favori Vites ve Debriyaj Yıpranma Skoru)
 * - Slayt 5: Favori Araç & Motor (Genişletilmiş Garaj)
 * - Slayt 6: Kazanılan Milestone Rozetleri
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
        listOf(Color(0xFF2D1206), Color(0xFFE65100), Color(0xFF07090C)), // Mekanik Analiz & Sürücü Profili Slaytı
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
                                    color = Color(0xFF34C759),
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

                            // YENİ SLAYT 4: MEKANİK ANALİZ & SÜRÜCÜ KARAKTERİSTİĞİ (OBD-II VİTES, DEVİR & DEBRİYAJ)
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
                                    WrappedStatPill("⚙️ Favori Vites", "${mech.mostUsedGear}. Vites")
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

@Composable
fun GarageAndCalibrationScreen(
    vehicles: List<GarageVehicleEntity>,
    placementMode: DevicePlacementMode,
    isAutopilotEnabled: Boolean,
    liveState: LiveTelemetryState,
    onSelectPlacementMode: (DevicePlacementMode) -> Unit,
    onToggleAutopilot: (Boolean) -> Unit,
    onSetActiveVehicle: (String) -> Unit,
    onAddVehicle: (VehicleCategory, String, String, String, String, Int, Int) -> Unit
) {
    var showAddVehicleDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            ScreenHeader(title = "Garage & Sensor", badgeInitial = "Y", notificationCount = 1)
        }

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
                            text = "Sıfır batarya tüketimiyle araca bindiğinde kaydı otomatik başlatır, indiğinde durdurur.",
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
                    text = "💾 Crash-Proof Chunked Flush: Her 500 noktada / 3 dakikada bir Room DB'ye yazılır (Yazılan Chunk: #${liveState.flushedChunksCount})",
                    color = AccentElectricBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

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
                        color = if (vehicle.isActiveVehicle) Color(0xFF34C759) else Color.Transparent,
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
                }
                if (vehicle.isActiveVehicle) {
                    Text("AKTİF", color = Color(0xFF34C759), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }

        item {
            AboutDeveloperCard()
        }
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
            text = "ApexPulse  •  geliştirici: YigitEXP",
            color = TextPrimaryWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Donanım ve yazılım arasındaki uyumsuzlukları çözmek için CircuitVR adlı açık kaynak projeyi de geliştiriyorum: Python'da eğitilen Edge AI modellerini RTL donanıma çeviren, güvenlik odaklı ve yerel/bulut hibrit çalışan bir tasarım ortamı. Demoyu inceleyip geri bildirim verirseniz çok sevinirim.",
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
    onSimulateTestDrive: () -> Unit = {}
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
                    text = "Simüle Et",
                    leadingEmoji = "🧪",
                    isSelected = false,
                    onClick = onSimulateTestDrive
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        LiveTelemetryBanner(liveState = liveState, onToggleRecording = onToggleRecording)
        Spacer(modifier = Modifier.height(16.dp))

        if (drives.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🚗", fontSize = 68.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No drives yet", color = TextPrimaryWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Your completed drives will show up here.\nGo take a drive!",
                        color = TextSecondaryGray,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onSimulateTestDrive,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentElectricBlue),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "🧪 Simülasyon Sürüşü Üret (Test)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
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
    onOpenWrappedStory: (WrappedRecapReport) -> Unit
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0062CC), Color(0xFF1E88E5))
                        )
                    )
                    .clickable { onOpenWrappedStory(activeReport) }
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "🚗", fontSize = 36.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Your ${activeReport.periodTitle} is Here!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Tap to view animated Wrapped Story & Mechanical Profile",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                    Text(text = "→", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
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
                        text = "Mekanik Analiz & Sürücü Profili",
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
                StatMetricRow("⚙️", "En Çok Kullanılan Vites", "Hız / Devir (Gear Ratio) analizi", "${mech.mostUsedGear}. Vites")
                StatMetricRow("🔥", "Debriyaj Yıpranma (Clutch Slip)", "Yarım debriyaj / sürtünme tespiti", "${mech.clutchSlipEventsCount} İhlal")
                StatMetricRow("🛠️", "Mekanik Yıpranma Skoru", "0 = Kusursuz, 100 = Yüksek Aşınma", "${mech.mechanicalWearScore} / 100")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total Drive Distance", color = TextPrimaryWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Surface(
                    color = CardDarkSurface,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Text("↑ Share", color = TextPrimaryWhite, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.1f", stats.totalDistanceMiles),
                    color = TextPrimaryWhite,
                    fontSize = 56.sp,
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

        item {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Total Time Driven", color = TextPrimaryWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.1f", stats.totalTimeHours),
                    color = TextPrimaryWhite,
                    fontSize = 44.sp,
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

        item {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Averages", color = TextPrimaryWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            StatMetricRow("💨", "Avg Speed", "Average speed across all drives", String.format(Locale.US, "%.0f mph", stats.avgSpeedMph))
            StatMetricRow("📏", "Avg Trip", "Average distance per trip", String.format(Locale.US, "%.1f mi", stats.avgTripMiles))
            StatMetricRow("⏱️", "Avg Drive Time", "Typical duration behind the wheel", String.format(Locale.US, "%.0f m", stats.avgDriveTimeMinutes))
            StatMetricRow("📅", "Weekly Drives", "Average drives per week", String.format(Locale.US, "%.1f", stats.weeklyDrivesCount))

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))

            Text("Personal Bests", color = TextPrimaryWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            StatMetricRow("🚀", "Rocket Mode", "Fastest speed recorded", String.format(Locale.US, "%.0f mph", stats.rocketModeTopSpeedMph))
            StatMetricRow("🛣️", "Long Haul", "Longest single drive distance", String.format(Locale.US, "%.1f mi", stats.longHaulMaxDistanceMiles))
            StatMetricRow("🪑", "Seat Time", "Longest single drive duration", String.format(Locale.US, "%.0f m", stats.seatTimeMaxDurationMinutes))
        }

        item {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Speed Distribution", color = TextPrimaryWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.speedDistribution.forEach { SpeedDistributionRow(it) }
            }
        }
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
                .size(44.dp)
                .clip(CircleShape)
                .border(3.dp, AccentPurpleRing, CircleShape)
                .background(Color(0xFF232733)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = milestone.iconEmoji, fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = milestone.formattedMultiplier, color = TextPrimaryWhite, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = milestone.title, color = TextSecondaryGray, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SpeedDistributionRow(bucket: SpeedDistributionBucket) {
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
                    .fillMaxWidth(bucket.percentage.coerceIn(0.02f, 1f))
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
                .size(42.dp)
                .clip(CircleShape)
                .background(CardDarkSurface),
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
                .background(CardDarkSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.username.take(2).uppercase(),
                color = TextPrimaryWhite,
                fontWeight = FontWeight.Bold,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardDarkSurface)
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
                if (liveState.obdData.isClutchSlipping) Color(0xFFFF3B30)
                else if (liveState.isRecording) Color(0xFF34C759)
                else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(18.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (liveState.isRecording) "● KAYIT AKTİF$slipWarning" else "OTOPİLOT HAZIR",
                color = if (liveState.obdData.isClutchSlipping) Color(0xFFFF3B30)
                else if (liveState.isRecording) Color(0xFF34C759)
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
                containerColor = if (liveState.isRecording) Color(0xFFFF3B30) else AccentElectricBlue
            ),
            shape = RoundedCornerShape(50)
        ) {
            Text(text = if (liveState.isRecording) "Stop" else "Start", color = Color.White, fontWeight = FontWeight.Bold)
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
                color = Color(0xFF34C759),
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
                text = String.format(Locale.US, "%.2f km", drive.distanceMeters / 1000.0),
                color = TextPrimaryWhite,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format(
                    Locale.US,
                    "Ort %d RPM • %d. Vites • %d Debriyaj İhlali",
                    drive.avgRpm,
                    drive.mostUsedGear.coerceAtLeast(1),
                    drive.clutchSlipCount
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
            val x = ((pt.longitude - minLng) / lngSpan).toFloat() * size.width
            val y = (1f - ((pt.latitude - minLat) / latSpan).toFloat()) * size.height
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = AccentElectricBlue,
            style = Stroke(width = 6f, cap = StrokeCap.Round)
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
                        .background(Color(0xFFFF3B30)),
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
    onSelectTab: (BottomNavTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val iconVector = when (tab) {
                BottomNavTab.DRIVES -> Icons.Default.DirectionsCar
                BottomNavTab.GARAGE -> Icons.Default.Tune
                BottomNavTab.STATISTICS -> Icons.Default.BarChart
                BottomNavTab.LEADERBOARD -> Icons.Default.EmojiEvents
            }

            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (selected) {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.04f),
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
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun DarkMapBackgroundCanvas(routePoints: List<RoutePoint>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridColor = Color.White.copy(alpha = 0.03f)
        val step = 110f
        var x = 0f
        while (x < size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x + 80f, size.height), strokeWidth = 1.5f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y - 60f), strokeWidth = 1.5f)
            y += step
        }

        if (routePoints.size >= 2) {
            val minLat = routePoints.minOf { it.latitude }
            val maxLat = routePoints.maxOf { it.latitude }
            val minLng = routePoints.minOf { it.longitude }
            val maxLng = routePoints.maxOf { it.longitude }

            val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
            val lngSpan = (maxLng - minLng).coerceAtLeast(0.0001)

            val path = Path()
            routePoints.forEachIndexed { idx, pt ->
                val px = ((pt.longitude - minLng) / lngSpan).toFloat() * size.width
                val py = (1f - ((pt.latitude - minLat) / latSpan).toFloat()) * size.height
                if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(
                path = path,
                color = AccentElectricBlue.copy(alpha = 0.25f),
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )
        }
    }
}
