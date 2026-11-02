package com.wheelcharm.telemetry.ui.viewmodel

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

    val liveTelemetry: StateFlow<LiveTelemetryState> = HybridTelemetryService.telemetryState

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
            // Cihaz kapanması veya OS kill sonrası yarım kalan sürüş varsa chunk'lardan kurtar
            repository.recoverOrphanedDrivesIfNeeded(
                isServiceCurrentlyRecording = liveTelemetry.value.isRecording
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

    /**
     * Tek dokunuşla 2-chunk'lı gerçekçi bir sürüş simüle eder ve Room DB + Cloud'a yazar.
     */
    fun simulateTestDrive() {
        viewModelScope.launch {
            val drive = repository.simulateRealisticTestDrive()
            repository.syncCompletedDriveToCloud(drive = drive, stats = statisticsSummary.value)
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
                username = "you_driver",
                handle = "@wheelcharm_pro",
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
