package com.wheelcharm.telemetry.domain.model

import java.util.Locale

enum class DevicePlacementMode(
    val title: String,
    val subtitle: String,
    val lowPassAlpha: Float,
    val hardBrakeThresholdG: Float,
    val rapidAccelThresholdG: Float,
    val sharpTurnLateralG: Float,
    val sharpTurnYawRadSec: Float
) {
    MOUNTED_HOLDER(
        title = "Telefon Tutucuda (Sabit)",
        subtitle = "Ön cam veya havalandırma tutucusunda rijit bağlantı (Yüksek Hassasiyet)",
        lowPassAlpha = 0.80f,
        hardBrakeThresholdG = -0.38f,
        rapidAccelThresholdG = 0.35f,
        sharpTurnLateralG = 0.36f,
        sharpTurnYawRadSec = 0.50f
    ),
    LOOSE_CONSOLE(
        title = "Konsolda / Bardaklıkta (Serbest)",
        subtitle = "Kayma ve sarsıntı gürültüsünü süzmek için yumuşatılmış filtre",
        lowPassAlpha = 0.35f,
        hardBrakeThresholdG = -0.52f,
        rapidAccelThresholdG = 0.48f,
        sharpTurnLateralG = 0.50f,
        sharpTurnYawRadSec = 0.68f
    )
}

enum class VehicleCategory(val displayName: String, val iconEmoji: String) {
    AUTOMOBILE("Otomobil", "🚗"),
    MOTORCYCLE("Motosiklet", "🏍️")
}

/**
 * Sürüş esnasında tespit edilen agresif manevra ve mekanik aşınma tipleri.
 * `CLUTCH_SLIP`: Yarım debriyaj / baskı balata sürtünme hatası.
 */
enum class DrivingEventType(val label: String) {
    HARD_BRAKE("Ani Fren"),
    RAPID_ACCELERATION("Ani Hızlanma"),
    SHARP_TURN("Keskin Viraj"),
    CLUTCH_SLIP("Yarım Debriyaj / Sürtünme")
}

data class DrivingEvent(
    val type: DrivingEventType,
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val magnitudeG: Float,      // Manevralarda G-kuvveti, CLUTCH_SLIP durumunda kayma şiddeti (%)
    val speedKmhAtEvent: Float,
    val rpmAtEvent: Int = 0
)

/**
 * Sürücü Mekanik Karakteristiği / Profil Etiketleri.
 */
enum class DriverPersonaTag(
    val badgeTitle: String,
    val badgeEmoji: String,
    val description: String,
    val accentColorHex: Long
) {
    ECO_MASTER(
        badgeTitle = "Ekonomi Ustası",
        badgeEmoji = "🌿",
        description = "Optimum devir aralığı, üst vites disiplini ve sıfıra yakın mekanik aşınma.",
        accentColorHex = 0xFF34C759
    ),
    REDLINE_MONSTER(
        badgeTitle = "Redline Canavarı",
        badgeEmoji = "🔥",
        description = "Yüksek devir çevirmeyi seven, agresif gaz tepkili performans odaklı sürüş.",
        accentColorHex = 0xFFFF3B30
    ),
    CLUTCH_NEMESIS(
        badgeTitle = "Debriyaj Düşmanı",
        badgeEmoji = "⚙️",
        description = "Yüksek gaz/devir altında sık yarım debriyaj ve baskı-balata sürtünmesi tespit edildi.",
        accentColorHex = 0xFFFF9500
    ),
    SMOOTH_OPERATOR(
        badgeTitle = "Şanzıman Dostu",
        badgeEmoji = "💎",
        description = "Pürüzsüz vites geçişleri, dengeli motor yükü ve sağlıklı debriyaj kullanımı.",
        accentColorHex = 0xFF0A84FF
    )
}

/**
 * Rota çizimi ve mekanik analiz için tekil örneklem noktası.
 */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val rpm: Int = 0,
    val engineLoadPct: Float = 0f,
    val throttlePct: Float = 0f,
    val estimatedGear: Int = 0, // 0 = Boş/Duruyor (N), 1..6 = Aktif Vites
    val isClutchSlipping: Boolean = false,
    val timestampMs: Long
)

sealed class ObdConnectionState {
    data object Disconnected : ObdConnectionState()
    data object Connecting : ObdConnectionState()
    data object Connected : ObdConnectionState()
    data class Reconnecting(val attempt: Int, val nextDelayMs: Long, val reason: String) : ObdConnectionState()
}

data class ObdLiveData(
    val rpm: Int = 0,
    val obdSpeedKmh: Float = 0f,
    val engineLoadPercent: Float = 0f,
    val coolantTempC: Int = 0,
    val throttlePercent: Float = 0f,
    val estimatedGear: Int = 0,           // Hız / Devir oranından hesaplanan anlık vites (0=N, 1..6)
    val gearRatioKmhPer1000Rpm: Float = 0f,
    val isClutchSlipping: Boolean = false,// Anlık yarım debriyaj / kaçırma uyarısı
    val timestampMs: Long = System.currentTimeMillis(),
    val connectionState: ObdConnectionState = ObdConnectionState.Disconnected
) {
    val isConnected: Boolean
        get() = connectionState is ObdConnectionState.Connected
}

data class LiveTelemetryState(
    val activeDriveId: Long? = null,
    val isRecording: Boolean = false,
    val isAutopilotEnabled: Boolean = true,
    val placementMode: DevicePlacementMode = DevicePlacementMode.MOUNTED_HOLDER,
    val currentSpeedKmh: Float = 0f,
    val averageSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val distanceMeters: Double = 0.0,
    val elapsedDurationMs: Long = 0L,
    val longitudinalG: Float = 0f,
    val lateralG: Float = 0f,
    val yawRateDegPerSec: Float = 0f,
    val flushedChunksCount: Int = 0,
    val totalPersistedPointsCount: Int = 0,
    val obdData: ObdLiveData = ObdLiveData(),
    val recentPreviewPoints: List<RoutePoint> = emptyList(),
    val detectedEvents: List<DrivingEvent> = emptyList()
)

data class SpeedDistributionBucket(
    val label: String,
    val minSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val durationSeconds: Long,
    val percentage: Float,
    val colorHex: Long
)

data class GamificationMilestone(
    val id: String,
    val title: String,
    val iconEmoji: String,
    val targetValue: Double,
    val currentValue: Double,
    val multiplierFormat: String
) {
    val multiplier: Double
        get() = if (targetValue > 0) currentValue / targetValue else 0.0

    val isUnlocked: Boolean
        get() = multiplier >= 1.0

    val progressFraction: Float
        get() = (multiplier % 1.0).toFloat().coerceIn(0f, 1f)

    val formattedMultiplier: String
        get() = String.format(Locale.US, multiplierFormat, multiplier)
}

enum class RecapTimeframe(val label: String, val daysLookback: Int, val badgeTitle: String) {
    WEEKLY("Haftalık", 7, "WEEKLY WRAPPED"),
    MONTHLY("Aylık", 30, "OCTOBER RECAP"),
    YEARLY("Yıllık", 365, "YEAR IN DRIVE")
}

/**
 * Mekanik Analiz ve Sürücü Profili Özeti.
 */
data class MechanicalAnalysisSummary(
    val avgRpm: Int = 0,
    val maxRpm: Int = 0,
    val mostUsedGear: Int = 0,            // 1..6 (En çok kullanılan vites)
    val clutchSlipEventsCount: Int = 0,   // Yarım debriyaj / sürtünme ihlal sayısı
    val mechanicalWearScore: Int = 0,     // 0 (Kusursuz / Sıfır Yıpranma) .. 100 (Ağır Yıpranma)
    val mechanicalHealthScore: Int = 100, // 100 - mechanicalWearScore
    val driverPersona: DriverPersonaTag = DriverPersonaTag.SMOOTH_OPERATOR,
    val gearUsagePercentages: Map<Int, Float> = emptyMap()
)

data class WrappedRecapReport(
    val timeframe: RecapTimeframe,
    val periodTitle: String,
    val totalDrives: Int,
    val totalDistanceMiles: Double,
    val totalDistanceKm: Double,
    val totalDurationHours: Double,
    val topSpeedKmh: Float,
    val topSpeedMph: Float,
    val totalHardBrakes: Int,
    val totalRapidAccels: Int,
    val totalSharpTurns: Int,
    val safetyScore: Int,
    val favoriteVehicleTitle: String,
    val favoriteVehicleCategory: VehicleCategory,
    val mostFrequentRouteSummary: String,
    val topRoutePreviewPoints: List<RoutePoint>,
    val unlockedMilestones: List<GamificationMilestone>,
    val dominantSpeedBucket: SpeedDistributionBucket?,
    val mechanicalSummary: MechanicalAnalysisSummary = MechanicalAnalysisSummary()
)

data class StatisticsSummary(
    val totalDistanceMiles: Double = 0.0,
    val avgDriveDistanceMiles: Double = 0.0,
    val totalTimeHours: Double = 0.0,
    val avgSpeedMph: Float = 0f,
    val avgTripMiles: Float = 0f,
    val avgDriveTimeMinutes: Float = 0f,
    val weeklyDrivesCount: Float = 0f,
    val rocketModeTopSpeedMph: Float = 0f,
    val longHaulMaxDistanceMiles: Float = 0f,
    val seatTimeMaxDurationMinutes: Float = 0f,
    val distanceMilestones: List<GamificationMilestone> = emptyList(),
    val timeMilestones: List<GamificationMilestone> = emptyList(),
    val speedDistribution: List<SpeedDistributionBucket> = emptyList(),
    val mechanicalSummary: MechanicalAnalysisSummary = MechanicalAnalysisSummary(),
    val wrappedReports: Map<RecapTimeframe, WrappedRecapReport> = emptyMap()
)

enum class DriveSortFilter(val displayName: String) {
    RECENT("Recent"),
    OLDEST("Oldest"),
    FASTEST("Fastest")
}

enum class LeaderboardPeriod(val label: String) {
    MONTH("Month"),
    ALL_TIME("All Time")
}

enum class LeaderboardScope(val label: String) {
    GLOBAL("Global"),
    FRIENDS("Friends")
}

enum class LeaderboardMetric(val label: String, val unitSuffix: String) {
    MILES("Miles", "mi"),
    HOURS("Hours", "hrs"),
    DRIVES("Drives", "drives")
}

data class LeaderboardEntry(
    val rank: Int,
    val userId: String,
    val username: String,
    val handle: String,
    val avatarUrl: String?,
    val metricValue: Double,
    val unit: String
)
