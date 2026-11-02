package com.wheelcharm.telemetry.domain.usecase

import com.wheelcharm.telemetry.data.local.AggregatedStatsTuple
import com.wheelcharm.telemetry.data.local.DriveEntity
import com.wheelcharm.telemetry.data.local.TelemetryTypeConverters
import com.wheelcharm.telemetry.domain.model.*
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * İstatistik, Hız Dağılımı, Gamification Rozetleri, Mekanik Analiz (Vites, Devir, Debriyaj Yıpranması)
 * ve Sürücü Karakteristiği ('Ekonomi Ustası', 'Redline Canavarı', 'Debriyaj Düşmanı', 'Şanzıman Dostu')
 * hesaplamalarını yürüten saf Domain Motoru.
 */
class DriveAnalyticsEngine(
    private val converters: TelemetryTypeConverters = TelemetryTypeConverters()
) {
    companion object {
        private const val METERS_TO_MILES = 0.000621371
        private const val KMH_TO_MPH = 0.621371f

        const val INDY_500_LAP_MILES = 2.5
        const val COAST_TO_COAST_MILES = 2800.0
        const val AROUND_EARTH_MILES = 24901.0
        const val TO_THE_MOON_MILES = 238855.0

        const val EARTH_DAY_HOURS = 24.0
        const val MERCURY_DAY_HOURS = 1407.6
        const val VENUS_DAY_HOURS = 5832.5
    }

    /**
     * MEKANİK ANALİZ VE SÜRÜCÜ PROFİLİ MOTORU:
     * Rota noktalarındaki RPM, Vites (`estimatedGear`), Motor Yükü ve Yarım Debriyaj (`CLUTCH_SLIP`)
     * verilerini işleyerek:
     * 1. Ortalama Devir (`avgRpm`) ve En Çok Kullanılan Vites (`mostUsedGear`)
     * 2. Mekanik Yıpranma Skoru (`mechanicalWearScore`: 0..100) ve Mekanik Sağlık (`100 - wear`)
     * 3. Sürücü Karakteristiği Etiketi (`DriverPersonaTag`: Ekonomi Ustası, Redline Canavarı, Debriyaj Düşmanı, Şanzıman Dostu)
     * üretir.
     */
    fun analyzeMechanicalAndDriverProfile(
        routePoints: List<RoutePoint>,
        detectedEvents: List<DrivingEvent>,
        fallbackAvgRpm: Int = 0,
        fallbackMaxRpm: Int = 0,
        fallbackClutchSlips: Int = 0
    ): MechanicalAnalysisSummary {
        val activeRpmPoints = routePoints.filter { it.rpm > 500 }
        val avgRpm = if (activeRpmPoints.isNotEmpty()) {
            activeRpmPoints.map { it.rpm }.average().roundToInt()
        } else {
            fallbackAvgRpm
        }

        val maxRpm = activeRpmPoints.maxOfOrNull { it.rpm } ?: fallbackMaxRpm

        // Vites kullanım dağılımı (1..6 vitesler)
        val validGearPoints = routePoints.filter { it.estimatedGear in 1..6 }
        val gearCounts = validGearPoints.groupingBy { it.estimatedGear }.eachCount()
        val totalGearSamples = validGearPoints.size.coerceAtLeast(1)

        val gearPercentages = (1..6).associateWith { gear ->
            ((gearCounts[gear] ?: 0).toFloat() / totalGearSamples.toFloat()).coerceIn(0f, 1f)
        }

        val mostUsedGear = gearCounts.maxByOrNull { it.value }?.key ?: 3

        val clutchSlipsFromEvents = detectedEvents.count { it.type == DrivingEventType.CLUTCH_SLIP }
        val totalClutchSlips = max(clutchSlipsFromEvents, fallbackClutchSlips)

        // Mekanik Yıpranma Skoru (0 = Sıfır Yıpranma, 100 = Maksimum Yıpranma):
        // - Her yarım debriyaj hatası +12 puan yıpranma
        // - Ortalama devir 3000 RPM üzerindeyse her 100 RPM için +3 puan yıpranma
        // - Maksimum devir 4500 RPM üzerindeyse +10 puan yıpranma
        val rpmWearPenalty = if (avgRpm > 2800) ((avgRpm - 2800) / 100) * 3 else 0
        val redlinePenalty = if (maxRpm >= 4600) 12 else 0
        val clutchWearPenalty = totalClutchSlips * 12

        val wearScore = (rpmWearPenalty + redlinePenalty + clutchWearPenalty).coerceIn(0, 100)
        val healthScore = (100 - wearScore).coerceIn(0, 100)

        // Sürücü Karakteristiği (Driver Persona) Karar Ağacı:
        val persona = when {
            totalClutchSlips >= 3 -> DriverPersonaTag.CLUTCH_NEMESIS
            avgRpm >= 3300 || maxRpm >= 4800 -> DriverPersonaTag.REDLINE_MONSTER
            avgRpm in 1100..2150 && totalClutchSlips == 0 && mostUsedGear >= 4 -> DriverPersonaTag.ECO_MASTER
            else -> DriverPersonaTag.SMOOTH_OPERATOR
        }

        return MechanicalAnalysisSummary(
            avgRpm = avgRpm,
            maxRpm = maxRpm,
            mostUsedGear = mostUsedGear,
            clutchSlipEventsCount = totalClutchSlips,
            mechanicalWearScore = wearScore,
            mechanicalHealthScore = healthScore,
            driverPersona = persona,
            gearUsagePercentages = gearPercentages
        )
    }

    fun calculateSpeedDistribution(routePoints: List<RoutePoint>): List<SpeedDistributionBucket> {
        val bucketDefs = listOf(
            Triple("< 50 km/h", 0f, 50f) to 0xFF34C759,
            Triple("50 - 70 km/h", 50f, 70f) to 0xFF30B0C7,
            Triple("70 - 100 km/h", 70f, 100f) to 0xFF007AFF,
            Triple("100 - 130 km/h", 100f, 130f) to 0xFFFF9500,
            Triple("130+ km/h", 130f, Float.MAX_VALUE) to 0xFFFF3B30
        )

        val durationsMs = LongArray(bucketDefs.size)

        if (routePoints.size >= 2) {
            for (i in 1 until routePoints.size) {
                val prev = routePoints[i - 1]
                val curr = routePoints[i]
                val deltaMs = (curr.timestampMs - prev.timestampMs).coerceIn(0L, 10_000L)
                val avgSegmentSpeed = (prev.speedKmh + curr.speedKmh) * 0.5f

                val bucketIndex = bucketDefs.indexOfFirst { (range, _) ->
                    avgSegmentSpeed >= range.second && avgSegmentSpeed < range.third
                }.takeIf { it >= 0 } ?: bucketDefs.lastIndex

                durationsMs[bucketIndex] += deltaMs
            }
        }

        val totalDurationMs = durationsMs.sum().coerceAtLeast(1L)
        val hasData = durationsMs.sum() > 0L

        return bucketDefs.mapIndexed { index, (range, colorHex) ->
            val durationSec = durationsMs[index] / 1000L
            val fraction = if (hasData) {
                (durationsMs[index].toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            SpeedDistributionBucket(
                label = range.first,
                minSpeedKmh = range.second,
                maxSpeedKmh = range.third,
                durationSeconds = durationSec,
                percentage = fraction,
                colorHex = colorHex
            )
        }
    }

    fun computeDistanceMilestones(totalMiles: Double): List<GamificationMilestone> {
        val safeMiles = max(0.0, totalMiles)
        return listOf(
            GamificationMilestone("indy_500", "Indy 500 Laps", "🏁", INDY_500_LAP_MILES, safeMiles, "%.1fx"),
            GamificationMilestone("coast_to_coast", "Coast to Coast", "🇺🇸", COAST_TO_COAST_MILES, safeMiles, "%.2fx"),
            GamificationMilestone("around_earth", "Around Earth", "🌍", AROUND_EARTH_MILES, safeMiles, "%.2fx"),
            GamificationMilestone("to_the_moon", "To the Moon", "🌕", TO_THE_MOON_MILES, safeMiles, "%.2fx")
        )
    }

    fun computeTimeMilestones(totalHours: Double): List<GamificationMilestone> {
        val safeHours = max(0.0, totalHours)
        return listOf(
            GamificationMilestone("earth_days", "Earth Days", "🌎", EARTH_DAY_HOURS, safeHours, "%.2fx"),
            GamificationMilestone("mercury_days", "Mercury Days", "🪐", MERCURY_DAY_HOURS, safeHours, "%.3fx"),
            GamificationMilestone("venus_days", "Venus Days", "🟠", VENUS_DAY_HOURS, safeHours, "%.3fx")
        )
    }

    fun generateWrappedReport(
        timeframe: RecapTimeframe,
        allDrives: List<DriveEntity>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): WrappedRecapReport {
        val cutoffMs = nowEpochMs - (timeframe.daysLookback * 24L * 3600L * 1000L)
        val periodDrives = allDrives.filter { it.startTimeEpochMs >= cutoffMs }

        if (periodDrives.isEmpty()) {
            return WrappedRecapReport(
                timeframe = timeframe,
                periodTitle = timeframe.badgeTitle,
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
                favoriteVehicleTitle = "Fiat Linea • 1.3 Multijet (95 HP) • Urban",
                favoriteVehicleCategory = VehicleCategory.AUTOMOBILE,
                mostFrequentRouteSummary = "Henüz sürüş kaydedilmedi • İlk sürüşüne çık!",
                topRoutePreviewPoints = emptyList(),
                unlockedMilestones = computeDistanceMilestones(0.0).take(2) + computeTimeMilestones(0.0).take(1),
                dominantSpeedBucket = null,
                mechanicalSummary = MechanicalAnalysisSummary()
            )
        }

        val totalMeters = periodDrives.sumOf { it.distanceMeters }
        val totalMiles = totalMeters * METERS_TO_MILES
        val totalKm = totalMeters / 1000.0
        val totalHours = periodDrives.sumOf { it.durationMs } / 3_600_000.0
        val topKmh = periodDrives.maxOf { it.maxSpeedKmh }
        val hardBrakes = periodDrives.sumOf { it.hardBrakeCount }
        val rapidAccels = periodDrives.sumOf { it.rapidAccelCount }
        val sharpTurns = periodDrives.sumOf { it.sharpTurnCount }
        val avgSafety = periodDrives.map { it.safetyScore }.average().roundToInt().coerceIn(0, 100)

        val favoriteDriveGroup = periodDrives
            .groupBy { it.vehicleFullHierarchyName }
            .maxByOrNull { (_, list) -> list.sumOf { it.distanceMeters } }

        val favVehicleTitle = favoriteDriveGroup?.key ?: periodDrives.first().vehicleFullHierarchyName
        val favCategoryName = favoriteDriveGroup?.value?.firstOrNull()?.vehicleCategory ?: VehicleCategory.AUTOMOBILE.name
        val favCategory = runCatching { VehicleCategory.valueOf(favCategoryName) }.getOrDefault(VehicleCategory.AUTOMOBILE)

        val longestDrive = periodDrives.maxByOrNull { it.distanceMeters }
        val topPreviewPoints = longestDrive?.let { converters.toRoutePoints(it.previewRouteJson) } ?: emptyList()

        val allPreviewPoints = periodDrives.flatMap { converters.toRoutePoints(it.previewRouteJson) }
        val allEvents = periodDrives.flatMap { converters.toDrivingEvents(it.eventsJson) }
        val speedDist = calculateSpeedDistribution(allPreviewPoints)
        val dominantBucket = speedDist.maxByOrNull { it.durationSeconds }

        val avgRpmFallback = periodDrives.map { it.avgRpm }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
        val maxRpmFallback = periodDrives.maxOfOrNull { it.maxRpm } ?: 0
        val clutchSlipsFallback = periodDrives.sumOf { it.clutchSlipCount }

        val mechanicalSummary = analyzeMechanicalAndDriverProfile(
            routePoints = allPreviewPoints,
            detectedEvents = allEvents,
            fallbackAvgRpm = avgRpmFallback,
            fallbackMaxRpm = maxRpmFallback,
            fallbackClutchSlips = clutchSlipsFallback
        )

        val routeSummary = String.format(
            Locale.US,
            "%s ile %d sürüş • En uzun rota %.1f km",
            favVehicleTitle.substringBefore("•").trim(),
            periodDrives.size,
            (longestDrive?.distanceMeters ?: 0.0) / 1000.0
        )

        return WrappedRecapReport(
            timeframe = timeframe,
            periodTitle = timeframe.badgeTitle,
            totalDrives = periodDrives.size,
            totalDistanceMiles = totalMiles,
            totalDistanceKm = totalKm,
            totalDurationHours = totalHours,
            topSpeedKmh = topKmh,
            topSpeedMph = topKmh * KMH_TO_MPH,
            totalHardBrakes = hardBrakes,
            totalRapidAccels = rapidAccels,
            totalSharpTurns = sharpTurns,
            safetyScore = avgSafety,
            favoriteVehicleTitle = favVehicleTitle,
            favoriteVehicleCategory = favCategory,
            mostFrequentRouteSummary = routeSummary,
            topRoutePreviewPoints = topPreviewPoints,
            unlockedMilestones = computeDistanceMilestones(totalMiles).take(2) + computeTimeMilestones(totalHours).take(2),
            dominantSpeedBucket = dominantBucket,
            mechanicalSummary = mechanicalSummary
        )
    }

    fun buildStatisticsSummary(
        tuple: AggregatedStatsTuple,
        allDrives: List<DriveEntity>
    ): StatisticsSummary {
        val totalMiles = tuple.totalDistanceMeters * METERS_TO_MILES
        val avgDriveMiles = tuple.avgDistanceMeters * METERS_TO_MILES
        val totalHours = tuple.totalDurationMs / 3_600_000.0

        val weeklyDrives = if (allDrives.size >= 2) {
            val oldestMs = allDrives.minOf { it.startTimeEpochMs }
            val newestMs = allDrives.maxOf { it.endTimeEpochMs }
            val weeks = ((newestMs - oldestMs) / (7.0 * 24 * 3600 * 1000)).coerceAtLeast(1.0)
            (allDrives.size / weeks).toFloat()
        } else {
            allDrives.size.toFloat()
        }

        val combinedPreviewPoints = allDrives.flatMap { converters.toRoutePoints(it.previewRouteJson) }
        val combinedEvents = allDrives.flatMap { converters.toDrivingEvents(it.eventsJson) }

        val overallMechanical = analyzeMechanicalAndDriverProfile(
            routePoints = combinedPreviewPoints,
            detectedEvents = combinedEvents,
            fallbackAvgRpm = tuple.overallAvgRpm.roundToInt(),
            fallbackMaxRpm = tuple.overallMaxRpm,
            fallbackClutchSlips = tuple.totalClutchSlips
        )

        val wrappedMap = RecapTimeframe.entries.associateWith { tf ->
            generateWrappedReport(tf, allDrives)
        }

        return StatisticsSummary(
            totalDistanceMiles = totalMiles,
            avgDriveDistanceMiles = avgDriveMiles,
            totalTimeHours = totalHours,
            avgSpeedMph = tuple.avgSpeedKmh * KMH_TO_MPH,
            avgTripMiles = avgDriveMiles.toFloat(),
            avgDriveTimeMinutes = (tuple.avgDurationMs / 60_000.0).toFloat(),
            weeklyDrivesCount = weeklyDrives,
            rocketModeTopSpeedMph = tuple.maxSpeedKmh * KMH_TO_MPH,
            longHaulMaxDistanceMiles = (tuple.maxSingleDriveDistanceMeters * METERS_TO_MILES).toFloat(),
            seatTimeMaxDurationMinutes = (tuple.maxSingleDriveDurationMs / 60_000f),
            distanceMilestones = computeDistanceMilestones(totalMiles),
            timeMilestones = computeTimeMilestones(totalHours),
            speedDistribution = calculateSpeedDistribution(combinedPreviewPoints),
            mechanicalSummary = overallMechanical,
            wrappedReports = wrappedMap
        )
    }
}
