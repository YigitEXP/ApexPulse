package com.wheelcharm.telemetry.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import com.wheelcharm.telemetry.domain.model.ObdBluetoothDeviceItem
import com.wheelcharm.telemetry.domain.model.ObdConnectionState
import com.wheelcharm.telemetry.domain.model.ObdLiveData
import com.wheelcharm.telemetry.domain.model.ObdTransportType
import com.wheelcharm.telemetry.util.SecurityGuard
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlin.math.min
import kotlin.random.Random

class Elm327ObdManager(
    private val bluetoothAdapter: BluetoothAdapter?
) {
    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val DEFAULT_WIFI_HOST = "192.168.0.10"
        const val DEFAULT_WIFI_PORT = 35000

        private const val BASE_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val MAX_CONSECUTIVE_EMPTY_READS = 6

        private const val PID_ENGINE_LOAD = "0104"
        private const val PID_COOLANT_TEMP = "0105"
        private const val PID_ENGINE_RPM = "010C"
        private const val PID_VEHICLE_SPEED = "010D"
        private const val PID_THROTTLE_POS = "0111"

        private val OBD_KEYWORDS = listOf("OBD", "ELM", "VGATE", "ICAR", "VEEPEAK", "KONNWEI", "CARISTA", "VLINKER", "ECU")
    }

    private val _obdState = MutableStateFlow(ObdLiveData())
    val obdState: StateFlow<ObdLiveData> = _obdState.asStateFlow()

    private var connectionSupervisorJob: Job? = null
    private var btSocket: BluetoothSocket? = null
    private var tcpSocket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var prevSpeedKmh = 0f
    private var prevRpm = 0
    private var prevRatioKmhPer1000Rpm = 0f
    private var consecutiveSlipSamples = 0

    /**
     * Telefonda eşleşmiş (paired/bonded) Bluetooth cihazlarını tarar ve OBD-II / ELM327
     * adaptörü olma ihtimali yüksek olanları listenin en üstüne taşır.
     */
    @SuppressLint("MissingPermission")
    fun getPairedBluetoothDevices(): List<ObdBluetoothDeviceItem> {
        val bonded = runCatching { bluetoothAdapter?.bondedDevices }.getOrNull() ?: emptySet()
        return bonded.mapNotNull { device ->
            val mac = device.address ?: return@mapNotNull null
            val name = runCatching { device.name }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "Bluetooth Cihazı"
            val upper = name.uppercase()
            val isObd = OBD_KEYWORDS.any { upper.contains(it) }
            ObdBluetoothDeviceItem(
                name = name,
                macAddress = mac,
                isLikelyObdAdapter = isObd
            )
        }.sortedWith(compareByDescending<ObdBluetoothDeviceItem> { it.isLikelyObdAdapter }.thenBy { it.name })
    }

    @SuppressLint("MissingPermission")
    fun connectBluetoothWithBackoff(macAddress: String, scope: CoroutineScope) {
        if (!SecurityGuard.isValidBluetoothMac(macAddress)) {
            updateConnectionState(ObdConnectionState.Disconnected)
            return
        }
        disconnect()
        _obdState.value = _obdState.value.copy(
            transportType = ObdTransportType.BLUETOOTH_SPP,
            connectedTargetLabel = macAddress.trim().uppercase()
        )
        connectionSupervisorJob = scope.launch(Dispatchers.IO + SupervisorJob()) {
            var attempt = 0
            while (isActive) {
                try {
                    updateConnectionState(
                        if (attempt == 0) ObdConnectionState.Connecting
                        else ObdConnectionState.Reconnecting(attempt, computeBackoffDelayMs(attempt), "Bluetooth SPP yenileniyor")
                    )

                    val device = bluetoothAdapter?.getRemoteDevice(macAddress.trim())
                        ?: throw IOException("Bluetooth adaptörü veya MAC bulunamadı")

                    runCatching { bluetoothAdapter.cancelDiscovery() }
                    val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    socket.connect()

                    btSocket = socket
                    inputStream = socket.inputStream
                    outputStream = socket.outputStream

                    initializeElm327()
                    attempt = 0
                    startPidPollingLoop()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    closeResources()
                    attempt++
                    val delayMs = computeBackoffDelayMs(attempt)
                    updateConnectionState(
                        ObdConnectionState.Reconnecting(
                            attempt = attempt,
                            nextDelayMs = delayMs,
                            reason = "ELM327 sinyali bekleniyor ($macAddress)"
                        )
                    )
                    delay(delayMs)
                }
            }
        }
    }

    fun connectWifiWithBackoff(
        host: String = DEFAULT_WIFI_HOST,
        port: Int = DEFAULT_WIFI_PORT,
        scope: CoroutineScope
    ) {
        if (!SecurityGuard.isSafeLocalObdHost(host, port)) {
            updateConnectionState(ObdConnectionState.Disconnected)
            return
        }
        disconnect()
        _obdState.value = _obdState.value.copy(
            transportType = ObdTransportType.WIFI_TCP,
            connectedTargetLabel = "${host.trim()}:$port"
        )
        connectionSupervisorJob = scope.launch(Dispatchers.IO + SupervisorJob()) {
            var attempt = 0
            while (isActive) {
                try {
                    updateConnectionState(
                        if (attempt == 0) ObdConnectionState.Connecting
                        else ObdConnectionState.Reconnecting(attempt, computeBackoffDelayMs(attempt), "Wi-Fi soketi yenileniyor")
                    )

                    val socket = Socket()
                    socket.connect(InetSocketAddress(host.trim(), port), 3500)
                    socket.soTimeout = 2000
                    tcpSocket = socket
                    inputStream = socket.getInputStream()
                    outputStream = socket.getOutputStream()

                    initializeElm327()
                    attempt = 0
                    startPidPollingLoop()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    closeResources()
                    attempt++
                    val delayMs = computeBackoffDelayMs(attempt)
                    updateConnectionState(
                        ObdConnectionState.Reconnecting(
                            attempt = attempt,
                            nextDelayMs = delayMs,
                            reason = "Wi-Fi ELM327 bekleniyor ($host:$port)"
                        )
                    )
                    delay(delayMs)
                }
            }
        }
    }

    internal fun computeBackoffDelayMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 5)
        val exponential = BASE_BACKOFF_MS * (1L shl shift)
        val capped = min(exponential, MAX_BACKOFF_MS)
        val jitter = Random.nextLong(0L, 250L)
        return capped + jitter
    }

    private suspend fun initializeElm327() {
        val initCommands = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")
        for (cmd in initCommands) {
            sendRawCommandOrThrow(cmd)
            delay(100)
        }
        updateConnectionState(ObdConnectionState.Connected)
    }

    private suspend fun CoroutineScope.startPidPollingLoop() {
        var cycle = 0
        var emptyReads = 0

        while (isActive) {
            val rpmRaw = sendRawCommandOrThrow(PID_ENGINE_RPM)
            val speedRaw = sendRawCommandOrThrow(PID_VEHICLE_SPEED)
            val loadRaw = sendRawCommandOrThrow(PID_ENGINE_LOAD)
            val throttleRaw = sendRawCommandOrThrow(PID_THROTTLE_POS)
            val coolantRaw = if (cycle % 10 == 0) sendRawCommandOrThrow(PID_COOLANT_TEMP) else null

            if (rpmRaw.isBlank() && speedRaw.isBlank()) {
                emptyReads++
                if (emptyReads >= MAX_CONSECUTIVE_EMPTY_READS) {
                    throw IOException("ELM327 yanıt vermiyor (Timeout)")
                }
            } else {
                emptyReads = 0
            }

            val current = _obdState.value
            val rpm = parseRpm(rpmRaw) ?: current.rpm
            val speedKmh = parseSpeedKmh(speedRaw) ?: current.obdSpeedKmh
            val engineLoad = parsePercentage(loadRaw, "4104") ?: current.engineLoadPercent
            val throttle = parsePercentage(throttleRaw, "4111") ?: current.throttlePercent
            val coolant = coolantRaw?.let { parseCoolantTemp(it) } ?: current.coolantTempC

            val ratioKmhPer1000Rpm = if (rpm >= 650 && speedKmh >= 3f) {
                speedKmh / (rpm / 1000f)
            } else 0f

            val isSlipping = detectClutchSlip(
                rpm = rpm,
                speedKmh = speedKmh,
                throttlePct = throttle,
                engineLoadPct = engineLoad,
                currentRatio = ratioKmhPer1000Rpm
            )

            val estimatedGear = estimateGearFromRatio(
                speedKmh = speedKmh,
                rpm = rpm,
                ratioKmhPer1000Rpm = ratioKmhPer1000Rpm,
                isClutchSlipping = isSlipping
            )

            _obdState.value = current.copy(
                rpm = rpm,
                obdSpeedKmh = speedKmh,
                engineLoadPercent = engineLoad,
                throttlePercent = throttle,
                coolantTempC = coolant,
                estimatedGear = estimatedGear,
                gearRatioKmhPer1000Rpm = ratioKmhPer1000Rpm,
                isClutchSlipping = isSlipping,
                timestampMs = System.currentTimeMillis(),
                connectionState = ObdConnectionState.Connected
            )

            cycle++
            delay(80L)
        }
    }

    internal fun estimateGearFromRatio(
        speedKmh: Float,
        rpm: Int,
        ratioKmhPer1000Rpm: Float = if (rpm > 0) speedKmh / (rpm / 1000f) else 0f,
        isClutchSlipping: Boolean = false
    ): Int {
        if (speedKmh < 3.5f || rpm < 650 || isClutchSlipping) return 0
        return when {
            ratioKmhPer1000Rpm < 11.5f -> 1
            ratioKmhPer1000Rpm < 19.5f -> 2
            ratioKmhPer1000Rpm < 27.5f -> 3
            ratioKmhPer1000Rpm < 36.0f -> 4
            ratioKmhPer1000Rpm < 44.5f -> 5
            else -> 6
        }
    }

    internal fun detectClutchSlip(
        rpm: Int,
        speedKmh: Float,
        throttlePct: Float,
        engineLoadPct: Float,
        currentRatio: Float
    ): Boolean {
        val deltaSpeed = if (prevSpeedKmh == 0f && prevRpm == 0) 0f else (speedKmh - prevSpeedKmh)
        val deltaRpm = if (prevRpm == 0) 0 else (rpm - prevRpm)
        val ratioDrop = if (prevRatioKmhPer1000Rpm > 0f) (prevRatioKmhPer1000Rpm - currentRatio) else 0f

        val highRpmNoAcceleration = throttlePct >= 24f &&
            engineLoadPct >= 35f &&
            rpm >= 2200 &&
            speedKmh in 2.0f..55.0f &&
            deltaSpeed <= 0.6f

        val fluctuatingRatioUnderLoad = throttlePct >= 30f &&
            rpm >= 2000 &&
            deltaRpm >= 180 &&
            deltaSpeed <= 0.8f &&
            prevRatioKmhPer1000Rpm > 5f &&
            ratioDrop >= 1.8f

        prevSpeedKmh = speedKmh
        prevRpm = rpm
        prevRatioKmhPer1000Rpm = currentRatio

        if (highRpmNoAcceleration || fluctuatingRatioUnderLoad) {
            consecutiveSlipSamples++
        } else {
            consecutiveSlipSamples = (consecutiveSlipSamples - 1).coerceAtLeast(0)
        }

        return consecutiveSlipSamples >= 4
    }

    /**
     * GÜVENLİK (CAN-Bus / ECU Koruması):
     * `SecurityGuard.isSafeObdCommand` kontrolünden geçmeyen hiçbir komut sokete yazılmaz!
     */
    private fun sendRawCommandOrThrow(command: String): String {
        if (!SecurityGuard.isSafeObdCommand(command)) {
            throw SecurityException("İzin verilmeyen OBD-II komutu engellendi: $command")
        }
        val out = outputStream ?: throw IOException("OutputStream kapalı")
        val inp = inputStream ?: throw IOException("InputStream kapalı")

        val sanitized = command.trim().uppercase()
        out.write("$sanitized\r".toByteArray(Charsets.US_ASCII))
        out.flush()

        val sb = StringBuilder()
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 750L) {
            if (inp.available() > 0) {
                val b = inp.read()
                if (b == -1) throw IOException("Soket EOF (Bağlantı koptu)")
                val ch = b.toChar()
                if (ch == '>') break
                if (!ch.isWhitespace() && sb.length < 64) {
                    sb.append(ch)
                }
            }
        }
        return sb.toString().uppercase()
    }

    private fun updateConnectionState(newState: ObdConnectionState) {
        _obdState.value = _obdState.value.copy(
            connectionState = newState,
            timestampMs = System.currentTimeMillis()
        )
    }

    internal fun parseRpm(hexResponse: String): Int? {
        val clean = hexResponse.filter { !it.isWhitespace() }.uppercase()
        val idx = clean.indexOf("410C")
        if (idx == -1 || clean.length < idx + 8) return null
        return runCatching {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            val b = clean.substring(idx + 6, idx + 8).toInt(16)
            (((a * 256) + b) / 4).coerceIn(0, 18_000)
        }.getOrNull()
    }

    internal fun parseSpeedKmh(hexResponse: String): Float? {
        val clean = hexResponse.filter { !it.isWhitespace() }.uppercase()
        val idx = clean.indexOf("410D")
        if (idx == -1 || clean.length < idx + 6) return null
        return runCatching {
            clean.substring(idx + 4, idx + 6).toInt(16).toFloat().coerceIn(0f, 420f)
        }.getOrNull()
    }

    internal fun parsePercentage(hexResponse: String, header: String): Float? {
        val clean = hexResponse.filter { !it.isWhitespace() }.uppercase()
        val idx = clean.indexOf(header.uppercase())
        if (idx == -1 || clean.length < idx + 6) return null
        return runCatching {
            ((clean.substring(idx + 4, idx + 6).toInt(16) * 100f) / 255f).coerceIn(0f, 100f)
        }.getOrNull()
    }

    internal fun parseCoolantTemp(hexResponse: String): Int? {
        val clean = hexResponse.filter { !it.isWhitespace() }.uppercase()
        val idx = clean.indexOf("4105")
        if (idx == -1 || clean.length < idx + 6) return null
        return runCatching {
            (clean.substring(idx + 4, idx + 6).toInt(16) - 40).coerceIn(-40, 215)
        }.getOrNull()
    }

    fun disconnect() {
        connectionSupervisorJob?.cancel()
        connectionSupervisorJob = null
        closeResources()
        _obdState.value = ObdLiveData(connectionState = ObdConnectionState.Disconnected)
    }

    private fun closeResources() {
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
        runCatching { btSocket?.close() }
        runCatching { tcpSocket?.close() }
        inputStream = null
        outputStream = null
        btSocket = null
        tcpSocket = null
    }
}
