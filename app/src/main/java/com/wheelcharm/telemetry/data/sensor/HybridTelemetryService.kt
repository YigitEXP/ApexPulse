package com.wheelcharm.telemetry.data.sensor

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.wheelcharm.telemetry.data.local.GarageVehicleEntity
import com.wheelcharm.telemetry.data.local.TelemetryDatabase
import com.wheelcharm.telemetry.data.obd.Elm327ObdManager
import com.wheelcharm.telemetry.data.remote.FirebaseCloudSyncManager
import com.wheelcharm.telemetry.data.repository.TelemetryRepository
import com.wheelcharm.telemetry.domain.model.*
import com.wheelcharm.telemetry.util.SecurityGuard
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

class HybridTelemetryService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START_RECORDING = "com.wheelcharm.action.START_RECORDING"
        const val ACTION_AUTOPILOT_START = "com.wheelcharm.action.AUTOPILOT_START"
        const val ACTION_STOP_RECORDING = "com.wheelcharm.action.STOP_RECORDING"
        const val ACTION_UPDATE_CALIBRATION = "com.wheelcharm.action.UPDATE_CALIBRATION"
        const val EXTRA_PLACEMENT_MODE = "extra_placement_mode"

        private const val NOTIFICATION_CHANNEL_ID = "wheel_charm_crash_proof_channel"
        private const val NOTIFICATION_ID = 4201

        private const val CHUNK_FLUSH_POINT_LIMIT = 500
        private const val CHUNK_FLUSH_INTERVAL_MS = 3 * 60 * 1000L
        private const val UI_PREVIEW_RING_CAPACITY = 100
        private const val MAX_WAKELOCK_DURATION_MS = 10 * 60 * 60 * 1000L // 10 saat üst güvenlik sınırı

        private val _telemetryState = MutableStateFlow(LiveTelemetryState())
        val telemetryState: StateFlow<LiveTelemetryState> = _telemetryState.asStateFlow()

        private const val GRAVITY_EARTH = 9.80665f
        private const val GRAVITY_HIGH_PASS_ALPHA = 0.85f
        private const val EVENT_COOLDOWN_MS = 2_800L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager
    private lateinit var obdManager: Elm327ObdManager
    private lateinit var repository: TelemetryRepository
    private val cloudSyncManager = FirebaseCloudSyncManager()

    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var currentPlacementMode = DevicePlacementMode.MOUNTED_HOLDER
    private var activeVehicle: GarageVehicleEntity? = null

    private val filteredLinearAccel = FloatArray(3)
    private val filteredGyro = FloatArray(3)
    private val gravityEstimate = FloatArray(3) { 0f }
    private var gravityInitialized = false

    private var activeDriveId: Long = -1L
    private var chunkSequenceCounter = 0
    private var lastChunkFlushTimeMs = 0L
    private val chunkMutex = Mutex()
    private val currentChunkBuffer = ArrayList<RoutePoint>(CHUNK_FLUSH_POINT_LIMIT + 32)
    private val uiPreviewRingBuffer = ArrayDeque<RoutePoint>(UI_PREVIEW_RING_CAPACITY)

    private var isSessionActive = false
    private var driveStartTimeMs = 0L
    private var totalDistanceMeters = 0.0
    private var maxSpeedKmh = 0f
    private var speedSamplesSum = 0.0
    private var speedSamplesCount = 0L
    private var lastValidLocation: Location? = null
    private var lastDynamicEventTimeMs = 0L
    private var lastClutchSlipEventTimeMs = 0L

    private val detectedEvents = CopyOnWriteArrayList<DrivingEvent>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        obdManager = Elm327ObdManager(btManager?.adapter)

        // Thread-safe Singleton DB kullanımı (MainActivity ile aynı InvalidationTracker paylaşılır)
        val db = TelemetryDatabase.getInstance(applicationContext)
        repository = TelemetryRepository(db.telemetryDao())

        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_PLACEMENT_MODE)?.let { modeName ->
            runCatching { DevicePlacementMode.valueOf(modeName) }.getOrNull()?.let {
                currentPlacementMode = it
                _telemetryState.update { state -> state.copy(placementMode = it) }
            }
        }

        when (intent?.action) {
            ACTION_START_RECORDING -> startTelemetrySession(byAutopilot = false)
            ACTION_AUTOPILOT_START -> startTelemetrySession(byAutopilot = true)
            ACTION_UPDATE_CALIBRATION -> {
                _telemetryState.update { it.copy(placementMode = currentPlacementMode) }
            }
            ACTION_STOP_RECORDING -> stopTelemetrySession()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Kullanıcı son uygulamalardan ekranı kaydırarak kapatsa bile aktif sürüş kaydı korunur
        super.onTaskRemoved(rootIntent)
    }

    private fun acquireSafeWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WheelCharm:TelemetryWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(MAX_WAKELOCK_DURATION_MS)
            }
        }
    }

    private fun releaseSafeWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        }
    }

    private fun startTelemetrySession(byAutopilot: Boolean) {
        if (isSessionActive) return
        isSessionActive = true
        acquireSafeWakeLock()

        driveStartTimeMs = System.currentTimeMillis()
        lastChunkFlushTimeMs = driveStartTimeMs
        chunkSequenceCounter = 0
        totalDistanceMeters = 0.0
        maxSpeedKmh = 0f
        speedSamplesSum = 0.0
        speedSamplesCount = 0L
        lastValidLocation = null
        gravityInitialized = false
        detectedEvents.clear()
        uiPreviewRingBuffer.clear()

        val notification = buildNotification("ApexPulse Telemetry Active (0.0 km/h)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            repository.seedDefaultGarageIfNeeded()
            activeVehicle = repository.getActiveVehicle()
            activeDriveId = repository.beginOngoingDriveSession(
                vehicle = activeVehicle,
                placementMode = currentPlacementMode,
                startTimeEpochMs = driveStartTimeMs
            )
            _telemetryState.update {
                it.copy(
                    activeDriveId = activeDriveId,
                    isRecording = true,
                    isAutopilotEnabled = byAutopilot || it.isAutopilotEnabled,
                    placementMode = currentPlacementMode,
                    flushedChunksCount = 0
                )
            }

            cloudSyncManager.updateCrashlyticsTelemetryKeys(
                activeVehicleTitle = activeVehicle?.fullHierarchyName ?: "Default",
                placementMode = currentPlacementMode.name,
                flushedChunks = 0,
                obdConnected = false
            )

            activeVehicle?.let { vehicle ->
                when {
                    !vehicle.obdMacAddress.isNullOrBlank() -> {
                        obdManager.connectBluetoothWithBackoff(vehicle.obdMacAddress, serviceScope)
                    }
                    !vehicle.obdWifiHost.isNullOrBlank() -> {
                        obdManager.connectWifiWithBackoff(host = vehicle.obdWifiHost, scope = serviceScope)
                    }
                }
            }
        }

        registerMotionSensors()
        requestHighAccuracyLocationUpdates()

        // OBD-II Canlı Telemetri + Yarım Debriyaj (Clutch Riding) Olay Kaydedici
        serviceScope.launch {
            obdManager.obdState.collect { obdSnapshot ->
                _telemetryState.update { it.copy(obdData = obdSnapshot) }

                val nowMs = System.currentTimeMillis()
                if (obdSnapshot.isClutchSlipping && (nowMs - lastClutchSlipEventTimeMs > 4_000L)) {
                    lastClutchSlipEventTimeMs = nowMs
                    val loc = lastValidLocation
                    detectedEvents.add(
                        DrivingEvent(
                            type = DrivingEventType.CLUTCH_SLIP,
                            timestampMs = nowMs,
                            latitude = loc?.latitude ?: 0.0,
                            longitude = loc?.longitude ?: 0.0,
                            magnitudeG = obdSnapshot.throttlePercent,
                            speedKmhAtEvent = _telemetryState.value.currentSpeedKmh,
                            rpmAtEvent = obdSnapshot.rpm
                        )
                    )
                    _telemetryState.update { it.copy(detectedEvents = detectedEvents.toList()) }
                }
            }
        }

        // Periyodik 3 Dakikalık Chunk Flush Zamanlayıcısı
        serviceScope.launch {
            while (isActive && isSessionActive) {
                delay(30_000L)
                val now = System.currentTimeMillis()
                if (now - lastChunkFlushTimeMs >= CHUNK_FLUSH_INTERVAL_MS) {
                    flushBufferToDatabaseIfNeeded(forceFlush = true)
                }
            }
        }
    }

    private fun stopTelemetrySession() {
        if (!isSessionActive) {
            stopSelf()
            return
        }
        isSessionActive = false

        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
        obdManager.disconnect()

        val endTimeMs = System.currentTimeMillis()
        val finalDistance = totalDistanceMeters
        val finalAvgSpeed = if (speedSamplesCount > 0) (speedSamplesSum / speedSamplesCount).toFloat() else 0f
        val finalMaxSpeed = maxSpeedKmh
        val eventsSnapshot = detectedEvents.toList()
        val completedDriveId = activeDriveId
        val vehicleSnapshot = activeVehicle
        val placementSnapshot = currentPlacementMode

        serviceScope.launch {
            try {
                flushBufferToDatabaseIfNeeded(forceFlush = true)
                if (completedDriveId > 0L && (finalDistance > 15.0 || eventsSnapshot.isNotEmpty())) {
                    repository.finalizeDriveSession(
                        driveId = completedDriveId,
                        vehicle = vehicleSnapshot,
                        placementMode = placementSnapshot,
                        startTimeMs = driveStartTimeMs,
                        endTimeMs = endTimeMs,
                        totalDistanceMeters = finalDistance,
                        avgSpeedKmh = finalAvgSpeed,
                        maxSpeedKmh = finalMaxSpeed,
                        detectedEvents = eventsSnapshot
                    )
                }
            } finally {
                _telemetryState.value = LiveTelemetryState(
                    isRecording = false,
                    placementMode = placementSnapshot
                )
                releaseSafeWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun appendPointAndCheckFlush(point: RoutePoint) {
        var shouldFlushNow = false
        chunkMutex.withLock {
            currentChunkBuffer.add(point)
            if (uiPreviewRingBuffer.size >= UI_PREVIEW_RING_CAPACITY) {
                uiPreviewRingBuffer.removeFirst()
            }
            uiPreviewRingBuffer.addLast(point)

            if (currentChunkBuffer.size >= CHUNK_FLUSH_POINT_LIMIT ||
                (System.currentTimeMillis() - lastChunkFlushTimeMs >= CHUNK_FLUSH_INTERVAL_MS)
            ) {
                shouldFlushNow = true
            }
        }
        if (shouldFlushNow) {
            flushBufferToDatabaseIfNeeded(forceFlush = true)
        }
    }

    private suspend fun flushBufferToDatabaseIfNeeded(forceFlush: Boolean) {
        val snapshotToWrite: List<RoutePoint>
        val seqIdx: Int

        chunkMutex.withLock {
            if (currentChunkBuffer.isEmpty()) return
            if (!forceFlush && currentChunkBuffer.size < CHUNK_FLUSH_POINT_LIMIT) return

            snapshotToWrite = ArrayList(currentChunkBuffer)
            currentChunkBuffer.clear()
            seqIdx = chunkSequenceCounter++
            lastChunkFlushTimeMs = System.currentTimeMillis()
        }

        if (activeDriveId > 0L && snapshotToWrite.isNotEmpty()) {
            repository.flushRoutePointChunk(
                driveId = activeDriveId,
                sequenceIndex = seqIdx,
                pointsSnapshot = snapshotToWrite
            )
            _telemetryState.update {
                it.copy(
                    flushedChunksCount = seqIdx + 1,
                    totalPersistedPointsCount = it.totalPersistedPointsCount + snapshotToWrite.size
                )
            }
            cloudSyncManager.updateCrashlyticsTelemetryKeys(
                activeVehicleTitle = activeVehicle?.fullHierarchyName ?: "Default",
                placementMode = currentPlacementMode.name,
                flushedChunks = seqIdx + 1,
                obdConnected = _telemetryState.value.obdData.isConnected
            )
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    processFusedLocation(location)
                }
            }
        }
    }

    /**
     * GPS + OBD-II Hibrit Nokta İşleme ve Mock GPS Güvenlik Denetimi:
     * Sahte konum (Fake GPS / Spoofing) tespit edilirse veriyi reddeder.
     */
    private fun processFusedLocation(location: Location) {
        if (!isSessionActive) return
        if (SecurityGuard.isMockOrSpoofedLocation(location)) return
        if (location.hasAccuracy() && location.accuracy > 35f) return

        val gpsSpeedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        val obdSnapshot = _telemetryState.value.obdData
        val fusedSpeedKmh = if (obdSnapshot.isConnected && obdSnapshot.obdSpeedKmh > 0) {
            (obdSnapshot.obdSpeedKmh * 0.65f) + (gpsSpeedKmh * 0.35f)
        } else {
            gpsSpeedKmh
        }

        val cleanSpeedKmh = if (fusedSpeedKmh < 1.8f) 0f else fusedSpeedKmh

        lastValidLocation?.let { prev ->
            val deltaSec = ((location.time - prev.time) / 1000.0).coerceAtLeast(0.1)
            val distMeters = prev.distanceTo(location).toDouble()
            val impliedSpeedKmh = (distMeters / deltaSec) * 3.6
            if (distMeters >= 1.5 && impliedSpeedKmh < 320.0) {
                totalDistanceMeters += distMeters
            }
        }
        lastValidLocation = location

        if (cleanSpeedKmh > maxSpeedKmh) maxSpeedKmh = cleanSpeedKmh
        if (cleanSpeedKmh > 2.0f) {
            speedSamplesSum += cleanSpeedKmh
            speedSamplesCount++
        }

        val avgSpeed = if (speedSamplesCount > 0) (speedSamplesSum / speedSamplesCount).toFloat() else 0f

        val newPoint = RoutePoint(
            latitude = location.latitude,
            longitude = location.longitude,
            speedKmh = cleanSpeedKmh,
            rpm = obdSnapshot.rpm,
            engineLoadPct = obdSnapshot.engineLoadPercent,
            throttlePct = obdSnapshot.throttlePercent,
            estimatedGear = obdSnapshot.estimatedGear,
            isClutchSlipping = obdSnapshot.isClutchSlipping,
            timestampMs = location.time
        )

        serviceScope.launch {
            appendPointAndCheckFlush(newPoint)
            val previewSnapshot = chunkMutex.withLock { uiPreviewRingBuffer.toList() }

            _telemetryState.update { state ->
                state.copy(
                    currentSpeedKmh = cleanSpeedKmh,
                    averageSpeedKmh = avgSpeed,
                    maxSpeedKmh = maxSpeedKmh,
                    distanceMeters = totalDistanceMeters,
                    elapsedDurationMs = System.currentTimeMillis() - driveStartTimeMs,
                    recentPreviewPoints = previewSnapshot
                )
            }
        }
    }

    private fun registerMotionSensors() {
        val linearAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        linearAccel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isSessionActive) return
        val alpha = currentPlacementMode.lowPassAlpha

        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                // Eğer cihazda sanal TYPE_LINEAR_ACCELERATION yoksa ve ham TYPE_ACCELEROMETER'a
                // düşüldüyse, 9.81 m/s² yerçekimi vektörünü High-Pass filtre ile ayıkla:
                val linearX: Float
                val linearY: Float
                val linearZ: Float

                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    if (!gravityInitialized) {
                        gravityEstimate[0] = event.values[0]
                        gravityEstimate[1] = event.values[1]
                        gravityEstimate[2] = event.values[2]
                        gravityInitialized = true
                    } else {
                        for (i in 0..2) {
                            gravityEstimate[i] = GRAVITY_HIGH_PASS_ALPHA * gravityEstimate[i] +
                                (1f - GRAVITY_HIGH_PASS_ALPHA) * event.values[i]
                        }
                    }
                    linearX = event.values[0] - gravityEstimate[0]
                    linearY = event.values[1] - gravityEstimate[1]
                    linearZ = event.values[2] - gravityEstimate[2]
                } else {
                    linearX = event.values[0]
                    linearY = event.values[1]
                    linearZ = event.values[2]
                }

                filteredLinearAccel[0] = alpha * linearX + (1f - alpha) * filteredLinearAccel[0]
                filteredLinearAccel[1] = alpha * linearY + (1f - alpha) * filteredLinearAccel[1]
                filteredLinearAccel[2] = alpha * linearZ + (1f - alpha) * filteredLinearAccel[2]

                val lateralG = filteredLinearAccel[0] / GRAVITY_EARTH
                val longitudinalG = filteredLinearAccel[1] / GRAVITY_EARTH

                _telemetryState.update {
                    it.copy(longitudinalG = longitudinalG, lateralG = lateralG)
                }

                evaluateDrivingManeuvers(longitudinalG = longitudinalG, lateralG = lateralG)
            }

            Sensor.TYPE_GYROSCOPE -> {
                for (i in 0..2) {
                    filteredGyro[i] = alpha * event.values[i] + (1f - alpha) * filteredGyro[i]
                }
                val yawRateRadSec = abs(filteredGyro[2])
                evaluateCorneringWithGyro(yawRateRadSec)
            }
        }
    }

    private fun evaluateDrivingManeuvers(longitudinalG: Float, lateralG: Float) {
        val currentSpeed = _telemetryState.value.currentSpeedKmh
        if (currentSpeed < 10f) return

        val now = System.currentTimeMillis()
        if (now - lastDynamicEventTimeMs < EVENT_COOLDOWN_MS) return

        val loc = lastValidLocation
        val currentRpm = _telemetryState.value.obdData.rpm
        val mode = currentPlacementMode

        when {
            longitudinalG <= mode.hardBrakeThresholdG -> {
                recordManeuver(DrivingEventType.HARD_BRAKE, now, loc, longitudinalG, currentSpeed, currentRpm)
            }
            longitudinalG >= mode.rapidAccelThresholdG -> {
                recordManeuver(DrivingEventType.RAPID_ACCELERATION, now, loc, longitudinalG, currentSpeed, currentRpm)
            }
            abs(lateralG) >= mode.sharpTurnLateralG -> {
                recordManeuver(DrivingEventType.SHARP_TURN, now, loc, abs(lateralG), currentSpeed, currentRpm)
            }
        }
    }

    private fun evaluateCorneringWithGyro(yawRateRadSec: Float) {
        val currentSpeed = _telemetryState.value.currentSpeedKmh
        val now = System.currentTimeMillis()
        if (currentSpeed > 22f &&
            yawRateRadSec >= currentPlacementMode.sharpTurnYawRadSec &&
            (now - lastDynamicEventTimeMs >= EVENT_COOLDOWN_MS)
        ) {
            recordManeuver(
                DrivingEventType.SHARP_TURN,
                now,
                lastValidLocation,
                yawRateRadSec,
                currentSpeed,
                _telemetryState.value.obdData.rpm
            )
        }
    }

    private fun recordManeuver(
        type: DrivingEventType,
        timestampMs: Long,
        location: Location?,
        magnitudeG: Float,
        speedKmh: Float,
        rpm: Int
    ) {
        lastDynamicEventTimeMs = timestampMs
        detectedEvents.add(
            DrivingEvent(
                type = type,
                timestampMs = timestampMs,
                latitude = location?.latitude ?: 0.0,
                longitude = location?.longitude ?: 0.0,
                magnitudeG = magnitudeG,
                speedKmhAtEvent = speedKmh,
                rpmAtEvent = rpm
            )
        )
        _telemetryState.update { it.copy(detectedEvents = detectedEvents.toList()) }
    }

    @SuppressLint("MissingPermission")
    private fun requestHighAccuracyLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(1.5f)
            .setWaitForAccurateLocation(false)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "ApexPulse Telemetry Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Crash-proof chunked telemetry, gear ratio & clutch wear analysis"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ApexPulse Otopilot")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseSafeWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
}
