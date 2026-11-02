package com.wheelcharm.telemetry.data.autopilot

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.wheelcharm.telemetry.data.sensor.HybridTelemetryService

class AutopilotActivityManager(private val context: Context) {

    companion object {
        const val ACTION_ACTIVITY_TRANSITION = "com.wheelcharm.action.ACTIVITY_TRANSITION"
        private const val PENDING_INTENT_REQ_CODE = 7701
    }

    private val client = ActivityRecognition.getClient(context)

    @SuppressLint("MissingPermission")
    fun enableAutopilotMonitoring(onResult: (Boolean) -> Unit = {}) {
        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.ON_FOOT)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        )

        val request = ActivityTransitionRequest(transitions)
        runCatching {
            client.requestActivityTransitionUpdates(request, buildTransitionPendingIntent())
                .addOnSuccessListener { onResult(true) }
                .addOnFailureListener { onResult(false) }
        }.onFailure { onResult(false) }
    }

    @SuppressLint("MissingPermission")
    fun disableAutopilotMonitoring() {
        runCatching {
            client.removeActivityTransitionUpdates(buildTransitionPendingIntent())
        }
    }

    /**
     * GÜVENLİK: Explicit Intent (`setPackage(context.packageName)`) kullanılarak
     * başka uygulamaların PendingIntent'i ele geçirmesi (Intent Hijacking) engellenir.
     */
    private fun buildTransitionPendingIntent(): PendingIntent {
        val intent = Intent(context, AutopilotTransitionReceiver::class.java).apply {
            action = ACTION_ACTIVITY_TRANSITION
            setPackage(context.packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, PENDING_INTENT_REQ_CODE, intent, flags)
    }
}

class AutopilotTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // GÜVENLİK: Yalnızca beklenen Action ve geçerli ActivityTransitionResult kabul edilir
        if (intent?.action != AutopilotActivityManager.ACTION_ACTIVITY_TRANSITION) return
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        for (event in result.transitionEvents) {
            when {
                event.activityType == DetectedActivity.IN_VEHICLE &&
                    event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                    val startIntent = Intent(context, HybridTelemetryService::class.java).apply {
                        action = HybridTelemetryService.ACTION_AUTOPILOT_START
                        setPackage(context.packageName)
                    }
                    ContextCompat.startForegroundService(context, startIntent)
                }

                (event.activityType == DetectedActivity.IN_VEHICLE &&
                    event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) ||
                    (event.activityType == DetectedActivity.ON_FOOT &&
                        event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) -> {
                    val stopIntent = Intent(context, HybridTelemetryService::class.java).apply {
                        action = HybridTelemetryService.ACTION_STOP_RECORDING
                        setPackage(context.packageName)
                    }
                    context.startService(stopIntent)
                }
            }
        }
    }
}
