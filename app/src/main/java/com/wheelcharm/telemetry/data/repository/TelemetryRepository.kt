package com.wheelcharm.telemetry.data.repository

import android.location.Location
import com.wheelcharm.telemetry.data.local.*
import com.wheelcharm.telemetry.data.remote.FirebaseCloudSyncManager
import com.wheelcharm.telemetry.domain.model.*
import com.wheelcharm.telemetry.domain.usecase.DriveAnalyticsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class TelemetryRepository(
    private val dao: TelemetryDao,
    private val analyticsEngine: DriveAnalyticsEngine = DriveAnalyticsEngine(),
    private val converters: TelemetryTypeConverters = TelemetryTypeConverters(),
    private val cloudSyncManager: FirebaseCloudSyncManager = FirebaseCloudSyncManager()
) {

    fun observeGarageVehicles(): Flow<List<GarageVehicleEntity>> = dao.observeGarageVehicles()

    suspend fun getActiveVehicle(): GarageVehicleEntity? = withContext(Dispatchers.IO) {
        dao.getActiveVehicle()
    }

    suspend fun setActiveVehicle(vehicleId: String) = withContext(Dispatchers.IO) {
        dao.setActiveVehicleExclusive(vehicleId)
    }

    suspend fun addOrUpdateGarageVehicle(vehicle: GarageVehicleEntity) = withContext(Dispatchers.IO) {
        dao.upsertVehicle(vehicle)
    }

    suspend fun cacheSuccessfulObdProtocol(
        vehicleId: String,
        protocolCode: String,
        connectedAtMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        dao.updateVehicleCachedObdProtocol(
            vehicleId = vehicleId,
            protocolCode = protocolCode,
            connectedAtMs = connectedAtMs
        )
    }

    suspend fun seedDefaultGarageIfNeeded() = withContext(Dispatchers.IO) {
        if (dao.getVehicleCount() == 0) {
            val defaultVehicles = listOf(
                GarageVehicleEntity(
                    vehicleId = "car_fiat_linea_13mj_urban",
                    category = VehicleCategory.AUTOMOBILE.name,
                    make = "Fiat",
                    model = "Linea",
                    engineSpec = "1.3 Multijet (95 HP)",
                    trimPackage = "Urban",
                    year = 2016,
                    horsePower = 95,
                    isActiveVehicle = true
                ),
                GarageVehicleEntity(
                    vehicleId = "car_renault_megane_15dci_iconic",
                    category = VehicleCategory.AUTOMOBILE.name,
                    make = "Renault",
                    model = "Megane IV",
                    engineSpec = "1.5 Blue dCi (115 HP)",
                    trimPackage = "Icon",
                    year = 2021,
                    horsePower = 115,
                    isActiveVehicle = false
                ),
                GarageVehicleEntity(
                    vehicleId = "car_bmw_x5_e70_m57",
                    category = VehicleCategory.AUTOMOBILE.name,
                    make = "BMW",
                    model = "X5 (E70)",
                    engineSpec = "3.0d M57 TwinPower",
                    trimPackage = "M Sport",
                    year = 2012,
                    horsePower = 286,
                    isActiveVehicle = false
                ),
                GarageVehicleEntity(
                    vehicleId = "moto_yamaha_mt07_cp2",
                    category = VehicleCategory.MOTORCYCLE.name,
                    make = "Yamaha",
                    model = "MT-07",
                    engineSpec = "689cc CP2 Parallel-Twin",
                    trimPackage = "Pure ABS",
                    year = 2023,
                    horsePower = 73,
                    isActiveVehicle = false
                )
            )
            defaultVehicles.forEach { dao.upsertVehicle(it) }
        }
    }

    fun observeDrives(vehicleId: String?, sortFilter: DriveSortFilter): Flow<List<DriveEntity>> {
        return dao.observeFilteredDrives(vehicleId = vehicleId, sortMode = sortFilter.name)
    }

    fun observeStatisticsSummary(vehicleId: String? = null): Flow<StatisticsSummary> {
        return combine(
            dao.observeAggregatedStats(vehicleId),
            dao.observeFilteredDrives(vehicleId, DriveSortFilter.RECENT.name)
        ) { tuple, drives ->
            analyticsEngine.buildStatisticsSummary(tuple, drives)
        }
    }

    suspend fun beginOngoingDriveSession(
        vehicle: GarageVehicleEntity?,
        placementMode: DevicePlacementMode,
        startTimeEpochMs: Long
    ): Long = withContext(Dispatchers.IO) {
        val header = DriveEntity(
            vehicleId = vehicle?.vehicleId,
            vehicleFullHierarchyName = vehicle?.fullHierarchyName ?: "Fiat Linea • 1.3 Multijet • Urban",
            vehicleCategory = vehicle?.category ?: VehicleCategory.AUTOMOBILE.name,
            placementMode = placementMode.name,
            startTimeEpochMs = startTimeEpochMs,
            endTimeEpochMs = startTimeEpochMs,
            durationMs = 0L,
            distanceMeters = 0.0,
            avgSpeedKmh = 0f,
            maxSpeedKmh = 0f,
            hardBrakeCount = 0,
            rapidAccelCount = 0,
            sharpTurnCount = 0,
            safetyScore = 100,
            isOngoing = true
        )
        dao.insertDriveHeader(header)
    }

    suspend fun flushRoutePointChunk(
        driveId: Long,
        sequenceIndex: Int,
        pointsSnapshot: List<RoutePoint>
    ) = withContext(Dispatchers.IO) {
        if (pointsSnapshot.isEmpty()) return@withContext
        val chunkEntity = RoutePointChunkEntity(
            driveId = driveId,
            chunkSequenceIndex = sequenceIndex,
            pointCount = pointsSnapshot.size,
            chunkStartTimeMs = pointsSnapshot.first().timestampMs,
            chunkEndTimeMs = pointsSnapshot.last().timestampMs,
            pointsPayloadJson = converters.fromRoutePoints(pointsSnapshot)
        )
        dao.insertRouteChunk(chunkEntity)
    }

    /**
     * Sürüş bittiğinde tüm chunk'ları ve olayları birleştirir;
     * Mekanik Analiz (`avgRpm`, `mostUsedGear`, `clutchSlipCount`, `mechanicalWearScore`, `driverPersonaTag`)
     * hesaplamalarını yapıp `DriveEntity` kaydını tamamlar.
     */
    suspend fun finalizeDriveSession(
        driveId: Long,
        vehicle: GarageVehicleEntity?,
        placementMode: DevicePlacementMode,
        startTimeMs: Long,
        endTimeMs: Long,
        totalDistanceMeters: Double,
        avgSpeedKmh: Float,
        maxSpeedKmh: Float,
        detectedEvents: List<DrivingEvent>
    ): DriveEntity = withContext(Dispatchers.IO) {
        val chunks = dao.getRouteChunksForDrive(driveId)
        val allPoints = chunks.flatMap { converters.toRoutePoints(it.pointsPayloadJson) }

        val previewPoints = if (allPoints.size <= 140) {
            allPoints
        } else {
            val step = (allPoints.size / 140).coerceAtLeast(1)
            allPoints.filterIndexed { index, _ -> index % step == 0 || index == allPoints.lastIndex }
        }

        // Mekanik Analiz & Sürücü Karakteristiği Hesaplaması
        val mechanicalProfile = analyticsEngine.analyzeMechanicalAndDriverProfile(
            routePoints = allPoints,
            detectedEvents = detectedEvents
        )

        val avgEngineLoad = allPoints
            .filter { it.engineLoadPct > 0f }
            .map { it.engineLoadPct }
            .takeIf { it.isNotEmpty() }
            ?.average()?.toFloat() ?: 0f

        val hardBrakes = detectedEvents.count { it.type == DrivingEventType.HARD_BRAKE }
        val rapidAccels = detectedEvents.count { it.type == DrivingEventType.RAPID_ACCELERATION }
        val sharpTurns = detectedEvents.count { it.type == DrivingEventType.SHARP_TURN }
        val penalty = (hardBrakes * 4) + (rapidAccels * 2) + (sharpTurns * 3) + (mechanicalProfile.clutchSlipEventsCount * 3)
        val safetyScore = (100 - penalty).coerceIn(0, 100)

        val completedDrive = DriveEntity(
            driveId = driveId,
            vehicleId = vehicle?.vehicleId,
            vehicleFullHierarchyName = vehicle?.fullHierarchyName ?: "Fiat Linea • 1.3 Multijet • Urban",
            vehicleCategory = vehicle?.category ?: VehicleCategory.AUTOMOBILE.name,
            placementMode = placementMode.name,
            startTimeEpochMs = startTimeMs,
            endTimeEpochMs = endTimeMs,
            durationMs = (endTimeMs - startTimeMs).coerceAtLeast(1000L),
            distanceMeters = totalDistanceMeters,
            avgSpeedKmh = avgSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            avgRpm = mechanicalProfile.avgRpm,
            maxRpm = mechanicalProfile.maxRpm,
            mostUsedGear = mechanicalProfile.mostUsedGear,
            avgEngineLoadPct = avgEngineLoad,
            clutchSlipCount = mechanicalProfile.clutchSlipEventsCount,
            mechanicalWearScore = mechanicalProfile.mechanicalWearScore,
            driverPersonaTag = mechanicalProfile.driverPersona.name,
            hardBrakeCount = hardBrakes,
            rapidAccelCount = rapidAccels,
            sharpTurnCount = sharpTurns,
            safetyScore = safetyScore,
            isOngoing = false,
            previewRouteJson = converters.fromRoutePoints(previewPoints),
            eventsJson = converters.fromDrivingEvents(detectedEvents)
        )
        dao.updateDriveHeader(completedDrive)
        completedDrive
    }

    /**
     * CRASH RECOVERY (Yarım Kalan Sürüş Kurtarma Mekanizması):
     * Cihazın şarjı biterse veya işletim sistemi süreci sonlandırırsa, veritabanında `isOngoing = true`
     * olarak kalan sürüşü ve diske flush edilmiş `RoutePointChunkEntity` paketlerini bularak
     * mesafe, hız ve mekanik analiz özetini kurtarır ve sürüşü güvenli şekilde kapatır.
     */
    suspend fun recoverOrphanedDrivesIfNeeded(isServiceCurrentlyRecording: Boolean): DriveEntity? = withContext(Dispatchers.IO) {
        if (isServiceCurrentlyRecording) return@withContext null
        val unfinished = dao.findUnfinishedOngoingDrive() ?: return@withContext null
        val chunks = dao.getRouteChunksForDrive(unfinished.driveId)
        val allPoints = chunks.flatMap { converters.toRoutePoints(it.pointsPayloadJson) }

        val endTimeMs = allPoints.lastOrNull()?.timestampMs
            ?: (unfinished.startTimeEpochMs + 60_000L)

        var recoveredDistanceMeters = 0.0
        var maxSpeedKmh = 0f
        if (allPoints.size >= 2) {
            for (i in 1 until allPoints.size) {
                val p1 = allPoints[i - 1]
                val p2 = allPoints[i]
                val distArr = FloatArray(1)
                runCatching {
                    Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, distArr)
                    recoveredDistanceMeters += distArr[0].toDouble()
                }
                if (p2.speedKmh > maxSpeedKmh) maxSpeedKmh = p2.speedKmh
            }
        }
        val durationMs = (endTimeMs - unfinished.startTimeEpochMs).coerceAtLeast(1000L)
        val avgSpeedKmh = if (durationMs > 0) {
            ((recoveredDistanceMeters / (durationMs / 1000.0)) * 3.6).toFloat()
        } else 0f

        val placement = runCatching { DevicePlacementMode.valueOf(unfinished.placementMode) }
            .getOrDefault(DevicePlacementMode.MOUNTED_HOLDER)
        val activeVehicle = dao.getActiveVehicle()

        finalizeDriveSession(
            driveId = unfinished.driveId,
            vehicle = activeVehicle,
            placementMode = placement,
            startTimeMs = unfinished.startTimeEpochMs,
            endTimeMs = endTimeMs,
            totalDistanceMeters = recoveredDistanceMeters,
            avgSpeedKmh = avgSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            detectedEvents = converters.toDrivingEvents(unfinished.eventsJson)
        )
    }

    /**
     * UÇTAN UCA SİMÜLASYON SÜRÜŞÜ ÜRETİCİSİ (Test & Demo):
     * Fiziksel sürüşe çıkmadan tüm UI bileşenlerini (Chunked DB Flush, Hız Dağılımı,
     * Mekanik Analiz, Vites Oranı, Debriyaj Sürtünmesi, Rota Çizimi ve 6 Slaytlı Spotify Wrapped)
     * tek dokunuşla test edebilmek için gerçekçi çok-parçalı (multi-chunk) sürüş verisi üretir.
     */
    suspend fun simulateRealisticTestDrive(): DriveEntity = withContext(Dispatchers.IO) {
        seedDefaultGarageIfNeeded()
        val vehicle = dao.getActiveVehicle()
        val nowMs = System.currentTimeMillis()
        val durationMs = 28 * 60 * 1000L // 28 dakikalık gerçekçi sürüş
        val startTimeMs = nowMs - durationMs

        val driveId = beginOngoingDriveSession(
            vehicle = vehicle,
            placementMode = DevicePlacementMode.MOUNTED_HOLDER,
            startTimeEpochMs = startTimeMs
        )

        val baseLat = 37.7648
        val baseLng = 30.5566
        val totalSamples = 120
        val stepMs = durationMs / totalSamples

        val generatedPoints = ArrayList<RoutePoint>(totalSamples)
        var maxSpeed = 0f
        var speedSum = 0f

        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples
            val angle = progress * Math.PI * 1.6
            val lat = baseLat + 0.045 * sin(angle) + (progress * 0.025)
            val lng = baseLng + 0.055 * cos(angle) + (progress * 0.030)

            // Şehir içi -> Çevre yolu -> Otoyol hız profili (<50, 50-70, 70-100, 100-130, 130+ km/h)
            val speedKmh = when {
                i < 20 -> 38f + (i % 10)
                i < 45 -> 62f + (i % 8)
                i < 85 -> 92f + (i % 12)
                i < 110 -> 118f + (i % 14)
                else -> 136f + (i % 6)
            }
            val gear = when {
                speedKmh < 25f -> 2
                speedKmh < 48f -> 3
                speedKmh < 75f -> 4
                speedKmh < 105f -> 5
                else -> 6
            }
            val rpm = (1650 + (speedKmh * 11f).roundToInt() + ((i % 5) * 45)).coerceIn(1200, 4200)
            val isSlip = (i == 14 || i == 15)

            if (speedKmh > maxSpeed) maxSpeed = speedKmh
            speedSum += speedKmh

            generatedPoints.add(
                RoutePoint(
                    latitude = lat,
                    longitude = lng,
                    speedKmh = speedKmh,
                    rpm = rpm,
                    engineLoadPct = 42f + (i % 30),
                    throttlePct = 28f + (i % 25),
                    estimatedGear = gear,
                    isClutchSlipping = isSlip,
                    timestampMs = startTimeMs + (i * stepMs)
                )
            )
        }

        // 2 ayrı Chunk halinde Room Database'e flush et (Chunked Flush doğrulaması)
        val midpoint = generatedPoints.size / 2
        flushRoutePointChunk(driveId, 0, generatedPoints.subList(0, midpoint))
        flushRoutePointChunk(driveId, 1, generatedPoints.subList(midpoint, generatedPoints.size))

        val simulatedEvents = listOf(
            DrivingEvent(
                type = DrivingEventType.HARD_BRAKE,
                timestampMs = startTimeMs + (18 * stepMs),
                latitude = generatedPoints[18].latitude,
                longitude = generatedPoints[18].longitude,
                magnitudeG = -0.44f,
                speedKmhAtEvent = generatedPoints[18].speedKmh,
                rpmAtEvent = generatedPoints[18].rpm
            ),
            DrivingEvent(
                type = DrivingEventType.RAPID_ACCELERATION,
                timestampMs = startTimeMs + (52 * stepMs),
                latitude = generatedPoints[52].latitude,
                longitude = generatedPoints[52].longitude,
                magnitudeG = 0.39f,
                speedKmhAtEvent = generatedPoints[52].speedKmh,
                rpmAtEvent = generatedPoints[52].rpm
            ),
            DrivingEvent(
                type = DrivingEventType.SHARP_TURN,
                timestampMs = startTimeMs + (74 * stepMs),
                latitude = generatedPoints[74].latitude,
                longitude = generatedPoints[74].longitude,
                magnitudeG = 0.41f,
                speedKmhAtEvent = generatedPoints[74].speedKmh,
                rpmAtEvent = generatedPoints[74].rpm
            ),
            DrivingEvent(
                type = DrivingEventType.CLUTCH_SLIP,
                timestampMs = startTimeMs + (15 * stepMs),
                latitude = generatedPoints[15].latitude,
                longitude = generatedPoints[15].longitude,
                magnitudeG = 38f,
                speedKmhAtEvent = generatedPoints[15].speedKmh,
                rpmAtEvent = 2950
            )
        )

        val avgSpeed = speedSum / generatedPoints.size
        val distanceMeters = (avgSpeed / 3.6) * (durationMs / 1000.0)

        finalizeDriveSession(
            driveId = driveId,
            vehicle = vehicle,
            placementMode = DevicePlacementMode.MOUNTED_HOLDER,
            startTimeMs = startTimeMs,
            endTimeMs = nowMs,
            totalDistanceMeters = distanceMeters,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = maxSpeed,
            detectedEvents = simulatedEvents
        )
    }

    suspend fun syncCompletedDriveToCloud(
        drive: DriveEntity,
        stats: StatisticsSummary,
        username: String = "you_driver",
        handle: String = "@wheelcharm_pro"
    ) = withContext(Dispatchers.IO) {
        cloudSyncManager.syncDriveAndLeaderboard(
            username = username,
            handle = handle,
            drive = drive,
            stats = stats
        )
    }
}
