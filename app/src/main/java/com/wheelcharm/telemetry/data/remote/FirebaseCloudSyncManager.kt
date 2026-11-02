package com.wheelcharm.telemetry.data.remote

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.wheelcharm.telemetry.data.local.DriveEntity
import com.wheelcharm.telemetry.domain.model.LeaderboardEntry
import com.wheelcharm.telemetry.domain.model.LeaderboardMetric
import com.wheelcharm.telemetry.domain.model.StatisticsSummary
import kotlinx.coroutines.tasks.await

/**
 * Firebase Cloud Firestore, Authentication ve Crashlytics Entegrasyon Yöneticisi:
 * 1. Kullanıcı oturumu yoksa otomatik Anonim Kimlik Doğrulama (`signInAnonymously`) başlatır.
 * 2. Tamamlanan sürüşleri `/users/{uid}/drives/{driveId}` koleksiyonuna yedekler.
 * 3. Kullanıcının toplam mil, saat, sürüş sayısı, güvenlik ve mekanik sağlık skorlarını
 *    `/leaderboard/{uid}` koleksiyonuna gerçek zamanlı senkronize eder.
 * 4. Crashlytics üzerine aktif araç, sensör kalibrasyon modu ve chunk telemetri durumunu yazar.
 */
class FirebaseCloudSyncManager {

    private fun isFirebaseConfigured(): Boolean {
        return runCatching {
            FirebaseApp.getInstance()
            true
        }.getOrDefault(false)
    }

    /**
     * Kullanıcı oturumu yoksa sessizce anonim oturum açar ve UID döndürür.
     */
    suspend fun ensureAuthenticatedUser(): String? {
        if (!isFirebaseConfigured()) return null
        return runCatching {
            val auth = Firebase.auth
            auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
        }.getOrNull()
    }

    /**
     * Crashlytics'e anlık telemetri bağlamını (Custom Keys) kaydeder.
     */
    fun updateCrashlyticsTelemetryKeys(
        activeVehicleTitle: String,
        placementMode: String,
        flushedChunks: Int,
        obdConnected: Boolean
    ) {
        if (!isFirebaseConfigured()) return
        runCatching {
            val crashlytics = Firebase.crashlytics
            crashlytics.setCustomKey("active_vehicle", activeVehicleTitle)
            crashlytics.setCustomKey("sensor_placement_mode", placementMode)
            crashlytics.setCustomKey("flushed_route_chunks", flushedChunks)
            crashlytics.setCustomKey("obd_connected", obdConnected)
        }
    }

    /**
     * Sürüş tamamlandığında hem `/users/{uid}/drives/{driveId}` belgesini hem de
     * `/leaderboard/{uid}` Global Leaderboard sıralama belgesini günceller.
     */
    suspend fun syncDriveAndLeaderboard(
        username: String,
        handle: String,
        drive: DriveEntity,
        stats: StatisticsSummary
    ) {
        val uid = ensureAuthenticatedUser() ?: return
        val db = Firebase.firestore

        runCatching {
            // 1. Kullanıcının özel sürüş geçmişine yedekle
            val drivePayload = hashMapOf(
                "driveId" to drive.driveId,
                "vehicleFullHierarchyName" to drive.vehicleFullHierarchyName,
                "vehicleCategory" to drive.vehicleCategory,
                "startTimeEpochMs" to drive.startTimeEpochMs,
                "endTimeEpochMs" to drive.endTimeEpochMs,
                "durationMs" to drive.durationMs,
                "distanceMeters" to drive.distanceMeters,
                "avgSpeedKmh" to drive.avgSpeedKmh,
                "maxSpeedKmh" to drive.maxSpeedKmh,
                "avgRpm" to drive.avgRpm,
                "maxRpm" to drive.maxRpm,
                "mostUsedGear" to drive.mostUsedGear,
                "clutchSlipCount" to drive.clutchSlipCount,
                "mechanicalWearScore" to drive.mechanicalWearScore,
                "driverPersonaTag" to drive.driverPersonaTag,
                "safetyScore" to drive.safetyScore
            )

            db.collection("users")
                .document(uid)
                .collection("drives")
                .document(drive.driveId.toString())
                .set(drivePayload, SetOptions.merge())
                .await()

            // 2. Global Leaderboard belgesini güncelle (firestore.rules şemasına %100 uyumlu)
            val leaderboardPayload = hashMapOf(
                "userId" to uid,
                "username" to username.take(32),
                "handle" to handle.take(36),
                "totalMiles" to stats.totalDistanceMiles,
                "totalHours" to stats.totalTimeHours,
                "totalDrives" to stats.weeklyDrivesCount.toInt().coerceAtLeast(1),
                "safetyScore" to drive.safetyScore.coerceIn(0, 100),
                "mechanicalHealthScore" to stats.mechanicalSummary.mechanicalHealthScore.coerceIn(0, 100),
                "driverPersonaTag" to stats.mechanicalSummary.driverPersona.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("leaderboard")
                .document(uid)
                .set(leaderboardPayload, SetOptions.merge())
                .await()
        }.onFailure { e ->
            runCatching { Firebase.crashlytics.recordException(e) }
        }
    }

    /**
     * Global Leaderboard listesini seçilen metriğe göre (`totalMiles`, `totalHours`, `totalDrives`)
     * Cloud Firestore'dan çeker.
     */
    suspend fun fetchGlobalLeaderboard(metric: LeaderboardMetric, limitCount: Long = 25): List<LeaderboardEntry> {
        if (!isFirebaseConfigured()) return emptyList()
        ensureAuthenticatedUser() ?: return emptyList()

        val orderField = when (metric) {
            LeaderboardMetric.MILES -> "totalMiles"
            LeaderboardMetric.HOURS -> "totalHours"
            LeaderboardMetric.DRIVES -> "totalDrives"
        }

        return runCatching {
            val snapshot = Firebase.firestore
                .collection("leaderboard")
                .orderBy(orderField, Query.Direction.DESCENDING)
                .limit(limitCount)
                .get()
                .await()

            snapshot.documents.mapIndexed { idx, doc ->
                val uname = doc.getString("username") ?: "Driver"
                val hndl = doc.getString("handle") ?: "@driver"
                val value = doc.getDouble(orderField) ?: 0.0
                LeaderboardEntry(
                    rank = idx + 1,
                    userId = doc.id,
                    username = uname,
                    handle = hndl,
                    avatarUrl = null,
                    metricValue = value,
                    unit = metric.unitSuffix
                )
            }
        }.getOrDefault(emptyList())
    }
}
