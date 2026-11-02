package com.wheelcharm.telemetry.ui.viewmodel

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wheelcharm.telemetry.data.local.DriveEntity
import com.wheelcharm.telemetry.data.local.GarageVehicleEntity
import com.wheelcharm.telemetry.data.repository.TelemetryRepository
import com.wheelcharm.telemetry.data.sensor.HybridTelemetryService
import com.wheelcharm.telemetry.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryViewModel(
    private val repository: TelemetryRepository
) : ViewModel() {

    private val _selectedVehicleFilterId = MutableStateFlow<String?>(null)
    val selectedVehicleFilterId: StateFlow<String?> = _selectedVehicleFilterId.asStateFlow()

    private val _selectedSortFilter = MutableStateFlow(DriveSortFilter.RECENT)
    val selectedSortFilter: StateFlow<DriveSortFilter> = _selectedSortFilter.asStateFlow()

    private val _placementMode = MutableStateFlow(DevicePlacementMode.MOUNTED_HOLDER)
    val placementMode: StateFlow<DevicePlacementMode> = _placementMode.asStateFlow()

    private val _isAutopilotEnabled = MutableStateFlow(true)
    val isAutopilotEnabled: StateFlow<Boolean> = _isAutopilotEnabled.asStateFlow()

    private val _selectedRecapTimeframe = MutableStateFlow(RecapTimeframe.MONTHLY)
    val selectedRecapTimeframe: StateFlow<RecapTimeframe> = _selectedRecapTimeframe.asStateFlow()

    private val _leaderboardPeriod = MutableStateFlow(LeaderboardPeriod.MONTH)
    val leaderboardPeriod: StateFlow<LeaderboardPeriod> = _leaderboardPeriod.asStateFlow()

    private val _leaderboardScope = MutableStateFlow(LeaderboardScope.GLOBAL)
    val leaderboardScope: StateFlow<LeaderboardScope> = _leaderboardScope.asStateFlow()

    private val _leaderboardMetric = MutableStateFlow(LeaderboardMetric.MILES)
    val leaderboardMetric: StateFlow<LeaderboardMetric> = _leaderboardMetric.asStateFlow()

    private val _pairedBluetoothDevices = MutableStateFlow<List<ObdBluetoothDeviceItem>>(emptyList())
    val pairedBluetoothDevices: StateFlow<List<ObdBluetoothDeviceItem>> = _pairedBluetoothDevices.asStateFlow()

    private val _selectedWaypoint = MutableStateFlow<NavigationWaypoint?>(null)
    private val _isHudMirrored = MutableStateFlow(false)

    private val _batteryOptimizationStatus = MutableStateFlow(BatteryOptimizationStatus.UNRESTRICTED)
    val batteryOptimizationStatus: StateFlow<BatteryOptimizationStatus> = _batteryOptimizationStatus.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    val presetWaypoints: List<NavigationWaypoint> = listOf(
        NavigationWaypoint(
            id = "wp_city_center",
            title = "Şehir Merkezi & Meydan",
            subtitle = "İstiklal / Hızırbey Bulvarı",
            categoryEmoji = "🏙️",
            latitude = 37.7648,
            longitude = 30.5566
        ),
        NavigationWaypoint(
            id = "wp_hospital",
            title = "Şehir Hastanesi",
            subtitle = "Acil & Servis Koridoru",
            categoryEmoji = "🏥",
            latitude = 37.7812,
            longitude = 30.5694
        ),
        NavigationWaypoint(
            id = "wp_campus",
            title = "Üniversite & Teknokent",
            subtitle = "Batı Kampüsü Otoyol Çıkışı",
            categoryEmoji = "🎓",
            latitude = 37.8295,
            longitude = 30.5262
        ),
        NavigationWaypoint(
            id = "wp_mountain_pass",
            title = "Davraz Viraj Parkuru",
            subtitle = "Performans & Rakım Tırmanış Rotası",
            categoryEmoji = "🏔️",
            latitude = 37.7830,
            longitude = 30.7460
        ),
        NavigationWaypoint(
            id = "wp_fuel_service",
            title = "Otoyol Servis & Akaryakıt",
            subtitle = "OBD-II Diagnostik & Pit Stop Noktası",
            categoryEmoji = "⛽",
            latitude = 37.7950,
            longitude = 30.5420
        )
    )

    val liveTelemetry: StateFlow<LiveTelemetryState> = HybridTelemetryService.telemetryState

    val activeNavigationState: StateFlow<ActiveNavigationState> = combine(
        liveTelemetry,
        _selectedWaypoint,
        _isHudMirrored
    ) { telemetry, waypoint, hudMirrored ->
        if (waypoint == null) {
            ActiveNavigationState(
                isNavigating = false,
                destination = null,
                isHudMirrored = hudMirrored
            )
        } else {
            val results = FloatArray(2)
            runCatching {
                Location.distanceBetween(
                    telemetry.currentLatitude,
                    telemetry.currentLongitude,
                    waypoint.latitude,
                    waypoint.longitude,
                    results
                )
            }
            val distMeters = results[0].toDouble()
            val bearingDeg = ((results[1] + 360f) % 360f)
            val effectiveSpeedKmh = telemetry.currentSpeedKmh.coerceAtLeast(45f)
            val etaMin = ((distMeters / 1000.0) / (effectiveSpeedKmh / 60.0)).roundToInt().coerceAtLeast(1)

            val turnDiff = ((bearingDeg - telemetry.currentHeadingDeg + 540f) % 360f) - 180f
            val maneuverInstruction = when {
                distMeters < 60.0 -> "Hedefe ulaştınız: ${waypoint.title}"
                turnDiff in -25f..25f -> "Düz devam edin • ${(distMeters / 1000.0).let { String.format(java.util.Locale.US, "%.1f km", it) }}"
                turnDiff > 25f -> "Sağ koridora yönelin (${turnDiff.roundToInt()}°) • ${waypoint.title}"
                else -> "Sol koridora yönelin (${kotlin.math.abs(turnDiff).roundToInt()}°) • ${waypoint.title}"
            }

            ActiveNavigationState(
                isNavigating = true,
                destination = waypoint,
                distanceRemainingMeters = distMeters,
                bearingToTargetDegrees = bearingDeg,
                etaMinutes = etaMin,
                nextManeuverText = maneuverInstruction,
                isHudMirrored = hudMirrored
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveNavigationState())

    val garageVehicles: StateFlow<List<GarageVehicleEntity>> = repository.observeGarageVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredDrives: StateFlow<List<DriveEntity>> = combine(
        _selectedVehicleFilterId,
        _selectedSortFilter
    ) { vehicleId, sort -> vehicleId to sort }
        .flatMapLatest { (vehicleId, sort) ->
            repository.observeDrives(vehicleId = vehicleId, sortFilter = sort)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val statisticsSummary: StateFlow<StatisticsSummary> = _selectedVehicleFilterId
        .flatMapLatest { vehicleId ->
            repository.observeStatisticsSummary(vehicleId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsSummary())

    val leaderboardEntries: StateFlow<List<LeaderboardEntry>> = combine(
        _leaderboardPeriod,
        _leaderboardScope,
        _leaderboardMetric,
        statisticsSummary
    ) { period, scope, metric, stats ->
        buildLeaderboardList(period, scope, metric, stats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repository.seedDefaultGarageIfNeeded()
            repository.recoverOrphanedDrivesIfNeeded(
                isServiceCurrentlyRecording = liveTelemetry.value.isRecording
            )
        }
    }

    fun updatePairedBluetoothDevices(devices: List<ObdBluetoothDeviceItem>) {
        _pairedBluetoothDevices.value = devices
    }

    fun startInAppNavigation(waypoint: NavigationWaypoint) {
        _selectedWaypoint.value = waypoint
    }

    fun stopInAppNavigation() {
        _selectedWaypoint.value = null
    }

    fun toggleHudMirrorMode() {
        _isHudMirrored.value = !_isHudMirrored.value
    }

    fun updateBatteryOptimizationStatus(status: BatteryOptimizationStatus) {
        _batteryOptimizationStatus.value = status
    }

    fun updatePipModeState(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun bindObdBluetoothToActiveVehicle(macAddress: String) {
        viewModelScope.launch {
            val active = repository.getActiveVehicle() ?: return@launch
            repository.addOrUpdateGarageVehicle(
                active.copy(obdMacAddress = macAddress.trim().uppercase(), obdWifiHost = null)
            )
        }
    }

    fun bindObdWifiToActiveVehicle(wifiHost: String) {
        viewModelScope.launch {
            val active = repository.getActiveVehicle() ?: return@launch
            repository.addOrUpdateGarageVehicle(
                active.copy(obdWifiHost = wifiHost.trim(), obdMacAddress = null)
            )
        }
    }

    fun selectVehicleFilter(vehicleId: String?) {
        _selectedVehicleFilterId.value = vehicleId
    }

    fun selectSortFilter(filter: DriveSortFilter) {
        _selectedSortFilter.value = filter
    }

    fun setPlacementMode(mode: DevicePlacementMode) {
        _placementMode.value = mode
    }

    fun setAutopilotEnabled(enabled: Boolean) {
        _isAutopilotEnabled.value = enabled
    }

    fun setRecapTimeframe(timeframe: RecapTimeframe) {
        _selectedRecapTimeframe.value = timeframe
    }

    fun setActiveGarageVehicle(vehicleId: String) {
        viewModelScope.launch {
            repository.setActiveVehicle(vehicleId)
        }
    }

    fun addCustomGarageVehicle(
        category: VehicleCategory,
        make: String,
        model: String,
        engineSpec: String,
        trimPackage: String,
        year: Int,
        horsePower: Int
    ) {
        viewModelScope.launch {
            val id = "veh_${UUID.randomUUID().toString().take(8)}"
            val entity = GarageVehicleEntity(
                vehicleId = id,
                category = category.name,
                make = make.trim().ifEmpty { "Fiat" },
                model = model.trim().ifEmpty { "Linea" },
                engineSpec = engineSpec.trim().ifEmpty { "1.3 Multijet" },
                trimPackage = trimPackage.trim().ifEmpty { "Urban" },
                year = year,
                horsePower = horsePower,
                isActiveVehicle = true
            )
            repository.addOrUpdateGarageVehicle(entity)
            repository.setActiveVehicle(id)
        }
    }

    fun selectLeaderboardPeriod(period: LeaderboardPeriod) {
        _leaderboardPeriod.value = period
    }

    fun selectLeaderboardScope(scope: LeaderboardScope) {
        _leaderboardScope.value = scope
    }

    fun selectLeaderboardMetric(metric: LeaderboardMetric) {
        _leaderboardMetric.value = metric
    }

    private fun buildLeaderboardList(
        period: LeaderboardPeriod,
        scope: LeaderboardScope,
        metric: LeaderboardMetric,
        userStats: StatisticsSummary
    ): List<LeaderboardEntry> {
        val periodScale = if (period == LeaderboardPeriod.MONTH) 1.0 else 4.6

        data class CompetitorSeed(
            val id: String,
            val username: String,
            val handle: String,
            val baseMiles: Double,
            val baseHours: Double,
            val baseDrives: Double,
            val isFriend: Boolean
        )

        val competitors = mutableListOf(
            CompetitorSeed("u1", "e70x5m57", "@e70x5m57", 2163.8, 48.5, 94.0, isFriend = true),
            CompetitorSeed("u2", "angelvz", "@angelvz", 1801.6, 41.2, 82.0, isFriend = true),
            CompetitorSeed("u3", "kevincolin", "@kevincolin", 1694.5, 38.0, 76.0, isFriend = true),
            CompetitorSeed("u4", "daddyluz", "@daddyluz", 1595.0, 35.4, 71.0, isFriend = false),
            CompetitorSeed("u5", "bk4", "@bk4", 1391.2, 31.0, 64.0, isFriend = false),
            CompetitorSeed("u6", "abir", "@abir", 1258.3, 28.7, 58.0, isFriend = false),
            CompetitorSeed(
                id = "current_user",
                username = "YigitEXP",
                handle = "@yigitexp",
                baseMiles = userStats.totalDistanceMiles.coerceAtLeast(142.5),
                baseHours = userStats.totalTimeHours.coerceAtLeast(4.2),
                baseDrives = userStats.weeklyDrivesCount.toDouble().coerceAtLeast(6.0),
                isFriend = true
            )
        )

        val scopedCompetitors = if (scope == LeaderboardScope.FRIENDS) {
            competitors.filter { it.isFriend }
        } else {
            competitors
        }

        return scopedCompetitors
            .map { seed ->
                val rawVal = when (metric) {
                    LeaderboardMetric.MILES -> seed.baseMiles * periodScale
                    LeaderboardMetric.HOURS -> seed.baseHours * periodScale
                    LeaderboardMetric.DRIVES -> seed.baseDrives * periodScale
                }
                Triple(seed, rawVal, metric.unitSuffix)
            }
            .sortedByDescending { it.second }
            .mapIndexed { index, (seed, metricVal, unit) ->
                LeaderboardEntry(
                    rank = index + 1,
                    userId = seed.id,
                    username = seed.username,
                    handle = seed.handle,
                    avatarUrl = null,
                    metricValue = metricVal,
                    unit = unit
                )
            }
    }

    class Factory(private val repository: TelemetryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TelemetryViewModel(repository) as T
        }
    }
}
