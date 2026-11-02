package com.wheelcharm.telemetry.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.wheelcharm.telemetry.data.autopilot.AutopilotActivityManager
import com.wheelcharm.telemetry.data.local.TelemetryDatabase
import com.wheelcharm.telemetry.data.obd.Elm327ObdManager
import com.wheelcharm.telemetry.data.repository.TelemetryRepository
import com.wheelcharm.telemetry.data.sensor.HybridTelemetryService
import com.wheelcharm.telemetry.domain.model.DevicePlacementMode
import com.wheelcharm.telemetry.domain.model.NavigationWaypoint
import com.wheelcharm.telemetry.ui.screens.WheelCharmTelemetryApp
import com.wheelcharm.telemetry.ui.viewmodel.TelemetryViewModel

class MainActivity : ComponentActivity() {

    private val database by lazy {
        TelemetryDatabase.getInstance(applicationContext)
    }

    private val repository by lazy { TelemetryRepository(database.telemetryDao()) }
    private val autopilotManager by lazy { AutopilotActivityManager(applicationContext) }

    private val viewModel: TelemetryViewModel by viewModels {
        TelemetryViewModel.Factory(repository)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            if (viewModel.isAutopilotEnabled.value) {
                autopilotManager.enableAutopilotMonitoring()
            }
        }
        refreshPairedBluetoothAdapters()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredTelemetryPermissions()
        refreshPairedBluetoothAdapters()

        setContent {
            val darkScheme = darkColorScheme(
                primary = Color(0xFF0A84FF),
                background = Color(0xFF07090C),
                surface = Color(0xFF12151B),
                onPrimary = Color.White,
                onBackground = Color(0xFFF5F7FA),
                onSurface = Color(0xFFF5F7FA)
            )

            MaterialTheme(colorScheme = darkScheme) {
                val drives by viewModel.filteredDrives.collectAsState()
                val vehicles by viewModel.garageVehicles.collectAsState()
                val selectedVehicleId by viewModel.selectedVehicleFilterId.collectAsState()
                val selectedSort by viewModel.selectedSortFilter.collectAsState()
                val placementMode by viewModel.placementMode.collectAsState()
                val isAutopilotEnabled by viewModel.isAutopilotEnabled.collectAsState()
                val selectedRecapTimeframe by viewModel.selectedRecapTimeframe.collectAsState()
                val stats by viewModel.statisticsSummary.collectAsState()
                val leaderboard by viewModel.leaderboardEntries.collectAsState()
                val period by viewModel.leaderboardPeriod.collectAsState()
                val scope by viewModel.leaderboardScope.collectAsState()
                val metric by viewModel.leaderboardMetric.collectAsState()
                val liveTelemetry by viewModel.liveTelemetry.collectAsState()
                val pairedBluetoothDevices by viewModel.pairedBluetoothDevices.collectAsState()
                val navigationState by viewModel.activeNavigationState.collectAsState()

                WheelCharmTelemetryApp(
                    drives = drives,
                    vehicles = vehicles,
                    selectedVehicleId = selectedVehicleId,
                    selectedSort = selectedSort,
                    placementMode = placementMode,
                    isAutopilotEnabled = isAutopilotEnabled,
                    selectedRecapTimeframe = selectedRecapTimeframe,
                    stats = stats,
                    leaderboard = leaderboard,
                    selectedPeriod = period,
                    selectedScope = scope,
                    selectedMetric = metric,
                    liveTelemetry = liveTelemetry,
                    pairedObdDevices = pairedBluetoothDevices,
                    presetWaypoints = viewModel.presetWaypoints,
                    navigationState = navigationState,
                    onSelectVehicleFilter = viewModel::selectVehicleFilter,
                    onSelectSort = viewModel::selectSortFilter,
                    onSelectPlacementMode = { mode ->
                        viewModel.setPlacementMode(mode)
                        updateServiceCalibration(mode)
                    },
                    onToggleAutopilot = { enabled ->
                        viewModel.setAutopilotEnabled(enabled)
                        if (enabled) autopilotManager.enableAutopilotMonitoring()
                        else autopilotManager.disableAutopilotMonitoring()
                    },
                    onSelectRecapTimeframe = viewModel::setRecapTimeframe,
                    onSetActiveGarageVehicle = viewModel::setActiveGarageVehicle,
                    onAddGarageVehicle = viewModel::addCustomGarageVehicle,
                    onSelectPeriod = viewModel::selectLeaderboardPeriod,
                    onSelectScope = viewModel::selectLeaderboardScope,
                    onSelectMetric = viewModel::selectLeaderboardMetric,
                    onToggleRecording = {
                        toggleTelemetryService(
                            currentlyRecording = liveTelemetry.isRecording,
                            placementMode = placementMode
                        )
                    },
                    onRefreshPairedObdDevices = ::refreshPairedBluetoothAdapters,
                    onConnectObdBluetooth = { mac ->
                        viewModel.bindObdBluetoothToActiveVehicle(mac)
                        connectObdBluetoothViaService(mac)
                    },
                    onConnectObdWifi = { host, port ->
                        viewModel.bindObdWifiToActiveVehicle(host)
                        connectObdWifiViaService(host, port)
                    },
                    onDisconnectObd = ::disconnectObdViaService,
                    onStartNavigation = viewModel::startInAppNavigation,
                    onStopNavigation = viewModel::stopInAppNavigation,
                    onToggleHudMirror = viewModel::toggleHudMirrorMode,
                    onLaunchExternalTurnByTurn = ::launchExternalTurnByTurnNavigation,
                    onShareTelemetrySummary = ::shareTelemetrySummaryText
                )
            }
        }
    }

    private fun refreshPairedBluetoothAdapters() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val devices = Elm327ObdManager(btManager?.adapter).getPairedBluetoothDevices()
        viewModel.updatePairedBluetoothDevices(devices)
    }

    private fun connectObdBluetoothViaService(macAddress: String) {
        val intent = Intent(this, HybridTelemetryService::class.java).apply {
            action = HybridTelemetryService.ACTION_CONNECT_OBD_BLUETOOTH
            putExtra(HybridTelemetryService.EXTRA_OBD_MAC, macAddress)
        }
        startService(intent)
    }

    private fun connectObdWifiViaService(host: String, port: Int) {
        val intent = Intent(this, HybridTelemetryService::class.java).apply {
            action = HybridTelemetryService.ACTION_CONNECT_OBD_WIFI
            putExtra(HybridTelemetryService.EXTRA_OBD_WIFI_HOST, host)
            putExtra(HybridTelemetryService.EXTRA_OBD_WIFI_PORT, port)
        }
        startService(intent)
    }

    private fun disconnectObdViaService() {
        val intent = Intent(this, HybridTelemetryService::class.java).apply {
            action = HybridTelemetryService.ACTION_DISCONNECT_OBD
        }
        startService(intent)
    }

    private fun launchExternalTurnByTurnNavigation(waypoint: NavigationWaypoint) {
        runCatching {
            val navUri = Uri.parse("google.navigation:q=${waypoint.latitude},${waypoint.longitude}&mode=d")
            val mapIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
                setPackage("com.google.android.apps.maps")
            }
            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            } else {
                val fallbackUri = Uri.parse(
                    "geo:${waypoint.latitude},${waypoint.longitude}?q=${waypoint.latitude},${waypoint.longitude}(${Uri.encode(waypoint.title)})"
                )
                startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
            }
        }
    }

    private fun shareTelemetrySummaryText(summaryText: String) {
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "ApexPulse Sürüş & Telemetri Özeti")
                putExtra(Intent.EXTRA_TEXT, summaryText)
            }
            startActivity(Intent.createChooser(sendIntent, "Sürüş Raporunu Paylaş"))
        }
    }

    private fun toggleTelemetryService(
        currentlyRecording: Boolean,
        placementMode: DevicePlacementMode
    ) {
        val intent = Intent(this, HybridTelemetryService::class.java).apply {
            action = if (currentlyRecording) {
                HybridTelemetryService.ACTION_STOP_RECORDING
            } else {
                HybridTelemetryService.ACTION_START_RECORDING
            }
            putExtra(HybridTelemetryService.EXTRA_PLACEMENT_MODE, placementMode.name)
        }
        if (currentlyRecording) {
            startService(intent)
        } else {
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun updateServiceCalibration(mode: DevicePlacementMode) {
        val intent = Intent(this, HybridTelemetryService::class.java).apply {
            action = HybridTelemetryService.ACTION_UPDATE_CALIBRATION
            putExtra(HybridTelemetryService.EXTRA_PLACEMENT_MODE, mode.name)
        }
        startService(intent)
    }

    private fun requestRequiredTelemetryPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else if (viewModel.isAutopilotEnabled.value) {
            autopilotManager.enableAutopilotMonitoring()
        }
    }
}
