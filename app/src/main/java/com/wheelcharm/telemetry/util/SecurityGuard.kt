package com.wheelcharm.telemetry.util

import android.location.Location
import android.os.Build

/**
 * ApexPulse (WheelCharm) Merkezi Güvenlik Doğrulayıcısı (Security Guard):
 * 1. Araç ECU / CAN-Bus Güvenliği: ELM327 üzerinden yalnızca beyaz listedeki (Whitelist)
 *    salt-okunur Mode 01 PID ve standart AT başlatma komutlarının gönderilmesine izin verir;
 *    Mode 04 (Arıza silme), Mode 08 (Aktüatör testi) veya ham CAN frame enjeksiyonunu engeller.
 * 2. SSRF & Bluetooth MAC Doğrulaması: Wi-Fi OBD-II bağlantılarının yalnızca yerel ağ (RFC1918)
 *    IP'lerine yapılmasını zorunlu kılar ve Bluetooth MAC formatını doğrular.
 * 3. Sahte Konum (Mock GPS / Spoofing) Tespiti: Global Leaderboard manipülasyonunu önlemek için
 *    sahte konum üretici uygulamaları (`isMock`) tespit eder ve filtreler.
 */
object SecurityGuard {

    private val BLUETOOTH_MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}([0-9A-Fa-f]{2})$")

    // Yalnızca izin verilen salt-okunur ELM327 AT ve OBD-II Mode 01 PID komutları
    private val ALLOWED_OBD_COMMANDS = setOf(
        "ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0",
        "0104", // Engine Load
        "0105", // Coolant Temp
        "010C", // RPM
        "010D", // Vehicle Speed
        "0111"  // Throttle Position
    )

    /**
     * ECU'ya zarar verebilecek veya beklenmedik yazma işlemi yapabilecek komutları reddeder.
     */
    fun isSafeObdCommand(command: String): Boolean {
        val normalized = command.trim().uppercase()
        return normalized in ALLOWED_OBD_COMMANDS
    }

    /**
     * Bluetooth MAC adresinin geçerli donanım adres formatında olduğunu doğrular.
     */
    fun isValidBluetoothMac(mac: String?): Boolean {
        if (mac.isNullOrBlank()) return false
        return BLUETOOTH_MAC_REGEX.matches(mac.trim())
    }

    /**
     * Wi-Fi ELM327 bağlantısının yalnızca yerel (RFC1918: 192.168.x.x veya 10.x.x.x)
     * adaptör adreslerine yapılmasını sağlar (Harici sunuculara SSRF sızıntısını önler).
     */
    fun isSafeLocalObdHost(host: String?, port: Int): Boolean {
        if (host.isNullOrBlank()) return false
        if (port !in 1024..65535) return false
        val clean = host.trim()
        return clean.startsWith("192.168.") || clean.startsWith("10.")
    }

    /**
     * Leaderboard hilelerini (Fake GPS) önlemek için konumun sahte (Mock) olup olmadığını denetler.
     */
    @Suppress("DEPRECATION")
    fun isMockOrSpoofedLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            location.isFromMockProvider
        }
    }
}
