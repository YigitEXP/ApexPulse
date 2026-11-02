package com.wheelcharm.telemetry.data.local

import android.content.Context
import androidx.room.*
import com.wheelcharm.telemetry.domain.model.DrivingEvent
import com.wheelcharm.telemetry.domain.model.DrivingEventType
import com.wheelcharm.telemetry.domain.model.RoutePoint
import com.wheelcharm.telemetry.domain.model.VehicleCategory
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(
    tableName = "garage_vehicles",
    indices = [
        Index(value = ["category", "make", "model"]),
        Index(value = ["isActiveVehicle"])
    ]
)
data class GarageVehicleEntity(
    @PrimaryKey val vehicleId: String,
    val category: String,
    val make: String,
    val model: String,
    val engineSpec: String,
    val trimPackage: String,
    val year: Int,
    val horsePower: Int = 95,
    val obdMacAddress: String? = null,
    val obdWifiHost: String? = null,
    val isActiveVehicle: Boolean = false
) {
    val fullHierarchyName: String
        get() = "$make $model • $engineSpec • $trimPackage"

    val shortTitle: String
        get() = "$make $model ($engineSpec)"

    val categoryEnum: VehicleCategory
        get() = runCatching { VehicleCategory.valueOf(category) }.getOrDefault(VehicleCategory.AUTOMOBILE)
}

/**
 * Sürüş Ana Tablosu:
 * Mekanik Analiz alanları (`avgRpm`, `mostUsedGear`, `clutchSlipCount`, `mechanicalWearScore`, `driverPersonaTag`) eklendi.
 */
@Entity(
    tableName = "drives",
    foreignKeys = [
        ForeignKey(
            entity = GarageVehicleEntity::class,
            parentColumns = ["vehicleId"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["startTimeEpochMs"]),
        Index(value = ["maxSpeedKmh"]),
        Index(value = ["isOngoing"])
    ]
)
data class DriveEntity(
    @PrimaryKey(autoGenerate = true) val driveId: Long = 0,
    val vehicleId: String?,
    val vehicleFullHierarchyName: String,
    val vehicleCategory: String = VehicleCategory.AUTOMOBILE.name,
    val placementMode: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val durationMs: Long,
    val distanceMeters: Double,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    // --- Mekanik Analiz & Sürücü Profili Sütunları ---
    val avgRpm: Int = 0,
    val maxRpm: Int = 0,
    val mostUsedGear: Int = 0,
    val avgEngineLoadPct: Float = 0f,
    val clutchSlipCount: Int = 0,
    val mechanicalWearScore: Int = 0,
    val driverPersonaTag: String = "SMOOTH_OPERATOR",
    // --- Manevra ve Güvenlik Sütunları ---
    val hardBrakeCount: Int,
    val rapidAccelCount: Int,
    val sharpTurnCount: Int,
    val safetyScore: Int,
    val isOngoing: Boolean = false,
    val previewRouteJson: String = "[]",
    val eventsJson: String = "[]"
)

@Entity(
    tableName = "drive_route_chunks",
    foreignKeys = [
        ForeignKey(
            entity = DriveEntity::class,
            parentColumns = ["driveId"],
            childColumns = ["driveId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["driveId", "chunkSequenceIndex"], unique = true)]
)
data class RoutePointChunkEntity(
    @PrimaryKey(autoGenerate = true) val chunkId: Long = 0,
    val driveId: Long,
    val chunkSequenceIndex: Int,
    val pointCount: Int,
    val chunkStartTimeMs: Long,
    val chunkEndTimeMs: Long,
    val pointsPayloadJson: String
)

data class AggregatedStatsTuple(
    val totalDrivesCount: Int,
    val totalDistanceMeters: Double,
    val totalDurationMs: Long,
    val avgSpeedKmh: Float,
    val avgDistanceMeters: Double,
    val avgDurationMs: Double,
    val maxSpeedKmh: Float,
    val maxSingleDriveDistanceMeters: Double,
    val maxSingleDriveDurationMs: Long,
    val overallAvgRpm: Double,
    val overallMaxRpm: Int,
    val totalClutchSlips: Int
)

class TelemetryTypeConverters {

    @TypeConverter
    fun fromRoutePoints(points: List<RoutePoint>): String {
        val array = JSONArray()
        points.forEach { pt ->
            val obj = JSONObject().apply {
                put("lat", pt.latitude)
                put("lng", pt.longitude)
                put("spd", pt.speedKmh.toDouble())
                put("rpm", pt.rpm)
                put("load", pt.engineLoadPct.toDouble())
                put("thr", pt.throttlePct.toDouble())
                put("gear", pt.estimatedGear)
                put("slip", pt.isClutchSlipping)
                put("ts", pt.timestampMs)
            }
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toRoutePoints(json: String): List<RoutePoint> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                RoutePoint(
                    latitude = obj.getDouble("lat"),
                    longitude = obj.getDouble("lng"),
                    speedKmh = obj.optDouble("spd", 0.0).toFloat(),
                    rpm = obj.optInt("rpm", 0),
                    engineLoadPct = obj.optDouble("load", 0.0).toFloat(),
                    throttlePct = obj.optDouble("thr", 0.0).toFloat(),
                    estimatedGear = obj.optInt("gear", 0),
                    isClutchSlipping = obj.optBoolean("slip", false),
                    timestampMs = obj.getLong("ts")
                )
            }
        }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromDrivingEvents(events: List<DrivingEvent>): String {
        val array = JSONArray()
        events.forEach { ev ->
            val obj = JSONObject().apply {
                put("type", ev.type.name)
                put("ts", ev.timestampMs)
                put("lat", ev.latitude)
                put("lng", ev.longitude)
                put("mag", ev.magnitudeG.toDouble())
                put("spd", ev.speedKmhAtEvent.toDouble())
                put("rpm", ev.rpmAtEvent)
            }
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toDrivingEvents(json: String): List<DrivingEvent> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                DrivingEvent(
                    type = DrivingEventType.valueOf(obj.getString("type")),
                    timestampMs = obj.getLong("ts"),
                    latitude = obj.getDouble("lat"),
                    longitude = obj.getDouble("lng"),
                    magnitudeG = obj.getDouble("mag").toFloat(),
                    speedKmhAtEvent = obj.getDouble("spd").toFloat(),
                    rpmAtEvent = obj.optInt("rpm", 0)
                )
            }
        }.getOrDefault(emptyList())
    }
}

@Dao
interface TelemetryDao {

    @Query("SELECT * FROM garage_vehicles ORDER BY isActiveVehicle DESC, make ASC, model ASC")
    fun observeGarageVehicles(): Flow<List<GarageVehicleEntity>>

    @Query("SELECT * FROM garage_vehicles WHERE isActiveVehicle = 1 LIMIT 1")
    suspend fun getActiveVehicle(): GarageVehicleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicle(vehicle: GarageVehicleEntity)

    @Query("UPDATE garage_vehicles SET isActiveVehicle = CASE WHEN vehicleId = :selectedId THEN 1 ELSE 0 END")
    suspend fun setActiveVehicleExclusive(selectedId: String)

    @Query("SELECT COUNT(*) FROM garage_vehicles")
    suspend fun getVehicleCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDriveHeader(drive: DriveEntity): Long

    @Update
    suspend fun updateDriveHeader(drive: DriveEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteChunk(chunk: RoutePointChunkEntity)

    @Query("SELECT * FROM drive_route_chunks WHERE driveId = :driveId ORDER BY chunkSequenceIndex ASC")
    suspend fun getRouteChunksForDrive(driveId: Long): List<RoutePointChunkEntity>

    @Query("SELECT * FROM drives WHERE isOngoing = 1 ORDER BY startTimeEpochMs DESC LIMIT 1")
    suspend fun findUnfinishedOngoingDrive(): DriveEntity?

    @Query("""
        SELECT * FROM drives
        WHERE isOngoing = 0 AND (:vehicleId IS NULL OR vehicleId = :vehicleId)
        ORDER BY
            CASE WHEN :sortMode = 'RECENT' THEN startTimeEpochMs END DESC,
            CASE WHEN :sortMode = 'OLDEST' THEN startTimeEpochMs END ASC,
            CASE WHEN :sortMode = 'FASTEST' THEN maxSpeedKmh END DESC
    """)
    fun observeFilteredDrives(vehicleId: String?, sortMode: String): Flow<List<DriveEntity>>

    @Query("""
        SELECT
            COUNT(driveId) AS totalDrivesCount,
            COALESCE(SUM(distanceMeters), 0.0) AS totalDistanceMeters,
            COALESCE(SUM(durationMs), 0) AS totalDurationMs,
            COALESCE(AVG(avgSpeedKmh), 0.0) AS avgSpeedKmh,
            COALESCE(AVG(distanceMeters), 0.0) AS avgDistanceMeters,
            COALESCE(AVG(durationMs), 0.0) AS avgDurationMs,
            COALESCE(MAX(maxSpeedKmh), 0.0) AS maxSpeedKmh,
            COALESCE(MAX(distanceMeters), 0.0) AS maxSingleDriveDistanceMeters,
            COALESCE(MAX(durationMs), 0) AS maxSingleDriveDurationMs,
            COALESCE(AVG(NULLIF(avgRpm, 0)), 0.0) AS overallAvgRpm,
            COALESCE(MAX(maxRpm), 0) AS overallMaxRpm,
            COALESCE(SUM(clutchSlipCount), 0) AS totalClutchSlips
        FROM drives
        WHERE isOngoing = 0 AND (:vehicleId IS NULL OR vehicleId = :vehicleId)
    """)
    fun observeAggregatedStats(vehicleId: String? = null): Flow<AggregatedStatsTuple>
}

@Database(
    entities = [
        GarageVehicleEntity::class,
        DriveEntity::class,
        RoutePointChunkEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(TelemetryTypeConverters::class)
abstract class TelemetryDatabase : RoomDatabase() {
    abstract fun telemetryDao(): TelemetryDao

    companion object {
        private const val DATABASE_NAME = "wheel_charm_telemetry_v3.db"

        @Volatile
        private var INSTANCE: TelemetryDatabase? = null

        /**
         * Thread-Safe Singleton Room Database Instance:
         * Hem UI (MainActivity / ViewModel) hem de arka plan Foreground Service (HybridTelemetryService)
         * aynı SQLite bağlantısını ve InvalidationTracker'ı paylaşır. WRITE_AHEAD_LOGGING (WAL)
         * modu sayesinde 500 noktalık chunk yazımları sırasında UI okumaları bloklanmaz.
         */
        fun getInstance(context: Context): TelemetryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TelemetryDatabase::class.java,
                    DATABASE_NAME
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
