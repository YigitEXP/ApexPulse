package com.wheelcharm.telemetry.domain.usecase

import com.wheelcharm.telemetry.data.local.AggregatedStatsTuple
import com.wheelcharm.telemetry.data.local.DriveEntity
import com.wheelcharm.telemetry.data.local.GarageVehicleEntity
import com.wheelcharm.telemetry.data.local.TelemetryTypeConverters
import com.wheelcharm.telemetry.data.obd.Elm327ObdManager
import com.wheelcharm.telemetry.data.sensor.DeadReckoningKalmanFilter
import com.wheelcharm.telemetry.domain.model.*
import com.wheelcharm.telemetry.util.SecurityGuard
import org.junit.Assert.*
import org.junit.Test

class DriveAnalyticsEngineTest {

    private val converters = TelemetryTypeConverters()
    private val engine = DriveAnalyticsEngine(converters)

    @Test
    fun `obd hex parsers decode RPM Speed Load Coolant and Throttle accurately and resist malformed frames`() {
        val obd = Elm327ObdManager(bluetoothAdapter = null)

        // PID 010C: RPM = ((A * 256) + B) / 4 -> 0x1A=26, 0xF8=248 -> (6656 + 248)/4 = 1726 RPM
        assertEquals(1726, obd.parseRpm("41 0C 1A F8\r>"))
        assertNull(obd.parseRpm("NO DATA"))
        assertNull(obd.parseRpm("410C"))

        // PID 010D: Speed = A -> 0x5A = 90 km/h
        assertEquals(90f, obd.parseSpeedKmh("41 0D 5A") ?: -1f, 0.01f)
        assertNull(obd.parseSpeedKmh("UNABLE TO CONNECT"))

        // PID 0104: Engine Load = (A * 100) / 255 -> 0xFF = 100%
        assertEquals(100f, obd.parsePercentage("4104FF", "4104") ?: -1f, 0.01f)
        assertEquals(0f, obd.parsePercentage("410400", "4104") ?: -1f, 0.01f)

        // PID 0105: Coolant Temp = A - 40 -> 0x82 (130) - 40 = 90°C, 0x14 (20) - 40 = -20°C
        assertEquals(90, obd.parseCoolantTemp("41 05 82"))
        assertEquals(-20, obd.parseCoolantTemp("41 05 14"))

        // PID 0111: Throttle Position = (A * 100) / 255 -> 0x80 (128) = ~50.19%
        assertEquals(50.2f, obd.parsePercentage("41 11 80", "4111") ?: -1f, 0.2f)
    }

    @Test
    fun `estimateGearFromRatio maps speed and RPM to gears 1 through 6 accurately`() {
        val obd = Elm327ObdManager(bluetoothAdapter = null)

        assertEquals(0, obd.estimateGearFromRatio(speedKmh = 0f, rpm = 800))
        assertEquals(0, obd.estimateGearFromRatio(speedKmh = 40f, rpm = 3000, isClutchSlipping = true))
        assertEquals(1, obd.estimateGearFromRatio(speedKmh = 20f, rpm = 2500))
        assertEquals(2, obd.estimateGearFromRatio(speedKmh = 35f, rpm = 2300))
        assertEquals(3, obd.estimateGearFromRatio(speedKmh = 50f, rpm = 2200))
        assertEquals(4, obd.estimateGearFromRatio(speedKmh = 72f, rpm = 2300))
        assertEquals(5, obd.estimateGearFromRatio(speedKmh = 95f, rpm = 2350))
        assertEquals(6, obd.estimateGearFromRatio(speedKmh = 120f, rpm = 2400))
    }

    @Test
    fun `detectClutchSlip ignores transient gear shift but triggers on sustained 4-sample slip`() {
        val obd = Elm327ObdManager(bluetoothAdapter = null)

        // İlk 3 örnekte normal vites geçişi toleransı nedeniyle false dönmeli
        repeat(3) {
            val transient = obd.detectClutchSlip(
                rpm = 2800,
                speedKmh = 15f,
                throttlePct = 35f,
                engineLoadPct = 50f,
                currentRatio = 15f / 2.8f
            )
            assertFalse("Transient sample #$it should not trigger clutch slip", transient)
        }

        // 4. ardışık örnekte (2+ saniye yarım debriyaj) true dönmeli
        val sustained = obd.detectClutchSlip(
            rpm = 2800,
            speedKmh = 15f,
            throttlePct = 35f,
            engineLoadPct = 50f,
            currentRatio = 15f / 2.8f
        )
        assertTrue("4th consecutive sample must trigger clutch slip alarm", sustained)
    }

    @Test
    fun `exponential backoff scales 1s 2s 4s 8s and caps at 30s ceiling`() {
        val obd = Elm327ObdManager(bluetoothAdapter = null)

        val d1 = obd.computeBackoffDelayMs(1)
        val d2 = obd.computeBackoffDelayMs(2)
        val d3 = obd.computeBackoffDelayMs(3)
        val d4 = obd.computeBackoffDelayMs(4)
        val d10 = obd.computeBackoffDelayMs(10)

        assertTrue(d1 in 1_000L..1_300L)
        assertTrue(d2 in 2_000L..2_300L)
        assertTrue(d3 in 4_000L..4_300L)
        assertTrue(d4 in 8_000L..8_300L)
        assertTrue(d10 in 30_000L..30_300L)
    }

    @Test
    fun `calculateSpeedDistribution handles empty points and partitions speed buckets accurately`() {
        val emptyDist = engine.calculateSpeedDistribution(emptyList())
        assertEquals(5, emptyDist.size)
        assertTrue(emptyDist.all { it.percentage == 0f && it.durationSeconds == 0L })

        val points = listOf(
            RoutePoint(37.7, 30.5, speedKmh = 30f, timestampMs = 0L),
            RoutePoint(37.7, 30.5, speedKmh = 40f, timestampMs = 5_000L),   // 5s in < 50 km/h
            RoutePoint(37.7, 30.5, speedKmh = 60f, timestampMs = 10_000L),  // 5s in 50-70 km/h (avg 50)
            RoutePoint(37.7, 30.5, speedKmh = 140f, timestampMs = 20_000L), // 10s in 70-100 / 100-130 (avg 100)
            RoutePoint(37.7, 30.5, speedKmh = 150f, timestampMs = 30_000L)  // 10s in 130+ km/h (avg 145)
        )
        val dist = engine.calculateSpeedDistribution(points)
        assertEquals(5, dist.size)
        val totalPct = dist.sumOf { it.percentage.toDouble() }
        assertEquals(1.0, totalPct, 0.01)
        assertEquals(10L, dist.last().durationSeconds) // 130+ km/h bucket
    }

    @Test
    fun `gamification milestones compute accurate multipliers and clamp negative inputs`() {
        val negativeSafe = engine.computeDistanceMilestones(-100.0)
        assertTrue(negativeSafe.all { it.multiplier == 0.0 && !it.isUnlocked })

        val distBadges = engine.computeDistanceMilestones(25.0) // 25 mil = 10x Indy 500 Laps
        val indy = distBadges.first { it.id == "indy_500" }
        assertEquals(10.0, indy.multiplier, 0.01)
        assertEquals("10.0x", indy.formattedMultiplier)
        assertTrue(indy.isUnlocked)

        val timeBadges = engine.computeTimeMilestones(48.0) // 48 saat = 2.00x Earth Days
        val earth = timeBadges.first { it.id == "earth_days" }
        assertEquals(2.0, earth.multiplier, 0.01)
        assertEquals("2.00x", earth.formattedMultiplier)
        assertTrue(earth.isUnlocked)
    }

    @Test
    fun `analyzeMechanicalAndDriverProfile assigns Eco Master vs Clutch Nemesis vs Redline Monster`() {
        val ecoPoints = List(10) {
            RoutePoint(37.7, 30.5, speedKmh = 85f, rpm = 1750, estimatedGear = 5, timestampMs = it * 1000L)
        }
        val ecoSummary = engine.analyzeMechanicalAndDriverProfile(ecoPoints, emptyList())
        assertEquals(DriverPersonaTag.ECO_MASTER, ecoSummary.driverPersona)
        assertEquals(5, ecoSummary.mostUsedGear)
        assertEquals(0, ecoSummary.mechanicalWearScore)
        assertEquals(100, ecoSummary.mechanicalHealthScore)

        val slipEvents = List(4) {
            DrivingEvent(DrivingEventType.CLUTCH_SLIP, it * 2000L, 37.7, 30.5, 40f, 20f, 2900)
        }
        val clutchSummary = engine.analyzeMechanicalAndDriverProfile(ecoPoints, slipEvents)
        assertEquals(DriverPersonaTag.CLUTCH_NEMESIS, clutchSummary.driverPersona)
        assertTrue(clutchSummary.mechanicalWearScore >= 48)
        assertEquals(100 - clutchSummary.mechanicalWearScore, clutchSummary.mechanicalHealthScore)

        val redlinePoints = List(10) {
            RoutePoint(37.7, 30.5, speedKmh = 130f, rpm = if (it == 9) 5200 else 3800, estimatedGear = 3, timestampMs = it * 1000L)
        }
        val redlineSummary = engine.analyzeMechanicalAndDriverProfile(redlinePoints, emptyList())
        assertEquals(DriverPersonaTag.REDLINE_MONSTER, redlineSummary.driverPersona)
    }

    @Test
    fun `typeConverters preserve 500-point chunk and driving events through JSON round-trip`() {
        val chunkPoints = List(500) { idx ->
            RoutePoint(
                latitude = 37.7648 + idx * 0.0001,
                longitude = 30.5566 + idx * 0.0001,
                speedKmh = 72.5f,
                rpm = 2150,
                engineLoadPct = 48.5f,
                throttlePct = 31.0f,
                estimatedGear = 4,
                isClutchSlipping = (idx == 42),
                timestampMs = 1_700_000_000_000L + idx * 1000L
            )
        }
        val json = converters.fromRoutePoints(chunkPoints)
        val restored = converters.toRoutePoints(json)

        assertEquals(500, restored.size)
        assertEquals(chunkPoints[42].latitude, restored[42].latitude, 1e-6)
        assertEquals(chunkPoints[42].rpm, restored[42].rpm)
        assertEquals(chunkPoints[42].estimatedGear, restored[42].estimatedGear)
        assertTrue(restored[42].isClutchSlipping)
        assertFalse(restored[0].isClutchSlipping)

        val events = listOf(
            DrivingEvent(DrivingEventType.HARD_BRAKE, 1000L, 37.7, 30.5, -0.45f, 80f, 2400),
            DrivingEvent(DrivingEventType.CLUTCH_SLIP, 2000L, 37.8, 30.6, 35f, 22f, 2950)
        )
        val eventsJson = converters.fromDrivingEvents(events)
        val restoredEvents = converters.toDrivingEvents(eventsJson)
        assertEquals(2, restoredEvents.size)
        assertEquals(DrivingEventType.CLUTCH_SLIP, restoredEvents[1].type)
        assertEquals(2950, restoredEvents[1].rpmAtEvent)
    }

    @Test
    fun `generateWrappedReport and buildStatisticsSummary aggregate drives and vehicle hierarchy`() {
        val nowMs = 1_730_000_000_000L
        val samplePoints = List(12) { i ->
            RoutePoint(37.7 + i * 0.001, 30.5 + i * 0.001, 90f, 1950, 40f, 25f, 5, false, nowMs - (12 - i) * 5000L)
        }
        val drive = DriveEntity(
            driveId = 1L,
            vehicleId = "car_fiat_linea_13mj_urban",
            vehicleFullHierarchyName = "Fiat Linea • 1.3 Multijet (95 HP) • Urban",
            vehicleCategory = VehicleCategory.AUTOMOBILE.name,
            placementMode = DevicePlacementMode.MOUNTED_HOLDER.name,
            startTimeEpochMs = nowMs - 1_800_000L,
            endTimeEpochMs = nowMs,
            durationMs = 1_800_000L,
            distanceMeters = 45_000.0,
            avgSpeedKmh = 90f,
            maxSpeedKmh = 128f,
            avgRpm = 1950,
            maxRpm = 2800,
            mostUsedGear = 5,
            avgEngineLoadPct = 40f,
            clutchSlipCount = 0,
            mechanicalWearScore = 0,
            driverPersonaTag = DriverPersonaTag.ECO_MASTER.name,
            hardBrakeCount = 1,
            rapidAccelCount = 0,
            sharpTurnCount = 1,
            safetyScore = 93,
            isOngoing = false,
            previewRouteJson = converters.fromRoutePoints(samplePoints),
            eventsJson = "[]"
        )

        val report = engine.generateWrappedReport(RecapTimeframe.MONTHLY, listOf(drive), nowEpochMs = nowMs)
        assertEquals(1, report.totalDrives)
        assertEquals(45.0, report.totalDistanceKm, 0.01)
        assertEquals(93, report.safetyScore)
        assertEquals(DriverPersonaTag.ECO_MASTER, report.mechanicalSummary.driverPersona)

        val vehicle = GarageVehicleEntity(
            vehicleId = "car_fiat_linea_13mj_urban",
            category = VehicleCategory.AUTOMOBILE.name,
            make = "Fiat",
            model = "Linea",
            engineSpec = "1.3 Multijet",
            trimPackage = "Urban",
            year = 2016
        )
        assertEquals("Fiat Linea • 1.3 Multijet • Urban", vehicle.fullHierarchyName)
        assertTrue(DevicePlacementMode.MOUNTED_HOLDER.lowPassAlpha > DevicePlacementMode.LOOSE_CONSOLE.lowPassAlpha)
    }

    @Test
    fun `securityGuard blocks destructive OBD commands and external SSRF hosts`() {
        // Yalnızca salt-okunur Mode 01 PID ve güvenli AT komutlarına izin verilmeli
        assertTrue(SecurityGuard.isSafeObdCommand("010C"))
        assertTrue(SecurityGuard.isSafeObdCommand("010D"))
        assertTrue(SecurityGuard.isSafeObdCommand("0111"))
        assertTrue(SecurityGuard.isSafeObdCommand("ATZ"))

        // Mode 04 (ECU arıza silme / sıfırlama), Mode 08 ve rastgele enjeksiyon engellenmeli
        assertFalse(SecurityGuard.isSafeObdCommand("04"))
        assertFalse(SecurityGuard.isSafeObdCommand("08"))
        assertFalse(SecurityGuard.isSafeObdCommand("010C;ATZ"))
        assertFalse(SecurityGuard.isSafeObdCommand("010C\r04"))

        // Bluetooth MAC doğrulaması
        assertTrue(SecurityGuard.isValidBluetoothMac("00:1D:A5:68:98:8B"))
        assertFalse(SecurityGuard.isValidBluetoothMac("invalid-mac"))
        assertFalse(SecurityGuard.isValidBluetoothMac(null))

        // SSRF Koruması: Yalnızca yerel ağ IP'lerine ve geçerli portlara izin verilmeli
        assertTrue(SecurityGuard.isSafeLocalObdHost("192.168.0.10", 35000))
        assertTrue(SecurityGuard.isSafeLocalObdHost("10.0.0.1", 35000))
        assertFalse(SecurityGuard.isSafeLocalObdHost("8.8.8.8", 35000))
        assertFalse(SecurityGuard.isSafeLocalObdHost("192.168.0.10", 80))
    }

    @Test
    fun `repository flushes multi-chunk routes and recovers orphaned ongoing drives after crash`() = kotlinx.coroutines.test.runTest {
        val fakeDao = FakeInMemoryTelemetryDao()
        val repo = com.wheelcharm.telemetry.data.repository.TelemetryRepository(
            dao = fakeDao,
            analyticsEngine = engine,
            converters = converters
        )

        repo.seedDefaultGarageIfNeeded()
        val activeVehicle = repo.getActiveVehicle()
        assertNotNull(activeVehicle)
        assertEquals("Fiat", activeVehicle?.make)

        // Yarım kalan (isOngoing = true) bir sürüş ve 2 adet diske flush edilmiş chunk oluştur
        val startMs = 1_730_000_000_000L
        val driveId = repo.beginOngoingDriveSession(
            vehicle = activeVehicle,
            placementMode = DevicePlacementMode.MOUNTED_HOLDER,
            startTimeEpochMs = startMs
        )

        val chunk0 = List(25) { i ->
            RoutePoint(37.76 + i * 0.001, 30.55 + i * 0.001, 75f, 2000, 40f, 25f, 4, false, startMs + i * 1000L)
        }
        val chunk1 = List(25) { i ->
            RoutePoint(37.785 + i * 0.001, 30.575 + i * 0.001, 95f, 2100, 45f, 30f, 5, false, startMs + (25 + i) * 1000L)
        }
        repo.flushRoutePointChunk(driveId, 0, chunk0)
        repo.flushRoutePointChunk(driveId, 1, chunk1)

        // Servis aktif değilken recoverOrphanedDrivesIfNeeded çağrıldığında yarım kalan sürüş kapatılmalı
        val recovered = repo.recoverOrphanedDrivesIfNeeded(isServiceCurrentlyRecording = false)
        assertNotNull(recovered)
        assertFalse(recovered!!.isOngoing)
        assertEquals(95f, recovered.maxSpeedKmh, 0.01f)
        assertTrue(recovered.avgRpm in 2000..2100)
        assertNull(fakeDao.findUnfinishedOngoingDrive())
    }

    @Test
    fun `deadReckoningKalmanFilter predicts velocity and coordinates in tunnel and converges on GPS recovery`() {
        val kalman = DeadReckoningKalmanFilter()
        val t0 = 1_730_000_000_000L
        kalman.reset(
            latitude = 37.7648,
            longitude = 30.5566,
            headingDeg = 0f, // Tam Kuzey yönü
            speedMs = 20.0,  // 72 km/h ile tünele giriş
            timestampMs = t0
        )

        // Tünel içinde 2 saniye boyunca +1.5 m/s^2 ivmelenme (GPS yok)
        val step1 = kalman.predictWithImu(longitudinalAccelMs2 = 1.5f, yawRateRadSec = 0f, timestampMs = t0 + 1_000L)
        val step2 = kalman.predictWithImu(longitudinalAccelMs2 = 1.5f, yawRateRadSec = 0f, timestampMs = t0 + 2_000L)

        // 20 m/s + (1.5 * 2s) = 23.0 m/s (82.8 km/h)
        assertEquals(23.0, step2.estimatedSpeedMs, 0.15)
        assertEquals(82.8f, step2.estimatedSpeedKmh, 0.6f)
        assertTrue("Latitude must advance northward during tunnel dead reckoning", step2.estimatedLatitude > 37.7648)
        assertTrue("Step distance must be positive (~21.5m)", step1.stepDistanceMeters > 20.0 && step2.stepDistanceMeters > 21.0)

        // Tünel çıkışı GPS ölçümü (25.0 m/s = 90 km/h) geldiğinde Kalman Kazancı ile yakınsamalı
        val corrected = kalman.updateWithMeasurement(
            measuredSpeedMs = 25.0,
            measurementNoiseR = kalman.gpsMeasurementNoiseR,
            latitude = 37.7660,
            longitude = 30.5566,
            headingDeg = 5f,
            timestampMs = t0 + 3_000L
        )
        assertTrue(corrected.estimatedSpeedMs in 23.5..25.0)
        assertEquals(37.7660, corrected.estimatedLatitude, 1e-6)
        assertTrue(corrected.speedUncertaintyKmh < step2.speedUncertaintyKmh)
    }

    @Test
    fun `obdFastInitCache parses ATDPN protocol codes and persists protocol in VehicleProfileEntity`() = kotlinx.coroutines.test.runTest {
        val obd = Elm327ObdManager(bluetoothAdapter = null)

        // ATDPN "A6" veya "6" -> ISO 15765-4 CAN (11 bit, 500 kbaud) -> ATSP6
        val can11 = obd.parseProtocolNumber("A6\r>")
        assertEquals(Elm327Protocol.ISO_15765_4_CAN_11BIT_500K, can11)
        assertEquals("ATSP6", can11.atCommand)
        assertTrue(SecurityGuard.isSafeObdCommand(can11.atCommand))
        assertTrue(SecurityGuard.isSafeObdCommand("ATDPN"))

        // Fast Init PID probe doğrulaması
        assertTrue(obd.isValidPidProbeResponse("41 0C 1A F8"))
        assertFalse(obd.isValidPidProbeResponse("UNABLE TO CONNECT"))
        assertFalse(obd.isValidPidProbeResponse("NO DATA"))

        // Room DAO üzerinden protokol önbellekleme doğrulaması
        val fakeDao = FakeInMemoryTelemetryDao()
        val repo = com.wheelcharm.telemetry.data.repository.TelemetryRepository(
            dao = fakeDao,
            analyticsEngine = engine,
            converters = converters
        )
        repo.seedDefaultGarageIfNeeded()
        val activeBefore = repo.getActiveVehicle()!!
        assertNull(activeBefore.cachedObdProtocolCode)

        repo.cacheSuccessfulObdProtocol(activeBefore.vehicleId, "6", connectedAtMs = 1_730_000_050_000L)
        val activeAfter = repo.getActiveVehicle()!!
        assertEquals("6", activeAfter.cachedObdProtocolCode)
        assertEquals(Elm327Protocol.ISO_15765_4_CAN_11BIT_500K, activeAfter.cachedProtocolEnum)
    }
}

private class FakeInMemoryTelemetryDao : com.wheelcharm.telemetry.data.local.TelemetryDao {
    private val vehicles = mutableListOf<GarageVehicleEntity>()
    private val drives = mutableListOf<DriveEntity>()
    private val chunks = mutableListOf<com.wheelcharm.telemetry.data.local.RoutePointChunkEntity>()
    private var nextDriveId = 1L

    override fun observeGarageVehicles() = kotlinx.coroutines.flow.flowOf(vehicles.toList())
    override suspend fun getActiveVehicle() = vehicles.firstOrNull { it.isActiveVehicle }
    override suspend fun upsertVehicle(vehicle: GarageVehicleEntity) {
        vehicles.removeAll { it.vehicleId == vehicle.vehicleId }
        vehicles.add(vehicle)
    }
    override suspend fun setActiveVehicleExclusive(selectedId: String) {
        val updated = vehicles.map { it.copy(isActiveVehicle = it.vehicleId == selectedId) }
        vehicles.clear()
        vehicles.addAll(updated)
    }
    override suspend fun updateVehicleCachedObdProtocol(
        vehicleId: String,
        protocolCode: String,
        connectedAtMs: Long
    ) {
        val idx = vehicles.indexOfFirst { it.vehicleId == vehicleId }
        if (idx >= 0) {
            vehicles[idx] = vehicles[idx].copy(
                cachedObdProtocolCode = protocolCode,
                lastObdConnectedEpochMs = connectedAtMs
            )
        }
    }
    override suspend fun getVehicleCount() = vehicles.size
    override suspend fun insertDriveHeader(drive: DriveEntity): Long {
        val id = if (drive.driveId == 0L) nextDriveId++ else drive.driveId
        drives.add(drive.copy(driveId = id))
        return id
    }
    override suspend fun updateDriveHeader(drive: DriveEntity) {
        val idx = drives.indexOfFirst { it.driveId == drive.driveId }
        if (idx >= 0) drives[idx] = drive
    }
    override suspend fun insertRouteChunk(chunk: com.wheelcharm.telemetry.data.local.RoutePointChunkEntity) {
        chunks.add(chunk)
    }
    override suspend fun getRouteChunksForDrive(driveId: Long) =
        chunks.filter { it.driveId == driveId }.sortedBy { it.chunkSequenceIndex }
    override suspend fun findUnfinishedOngoingDrive() =
        drives.firstOrNull { it.isOngoing }
    override fun observeFilteredDrives(vehicleId: String?, sortMode: String) =
        kotlinx.coroutines.flow.flowOf(drives.filter { !it.isOngoing })
    override fun observeAggregatedStats(vehicleId: String?) =
        kotlinx.coroutines.flow.flowOf(
            AggregatedStatsTuple(drives.size, 0.0, 0L, 0f, 0.0, 0.0, 0f, 0.0, 0L, 0.0, 0, 0)
        )
}
