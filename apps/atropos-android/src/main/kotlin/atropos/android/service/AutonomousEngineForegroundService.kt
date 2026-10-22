/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * F-AND-006: Background Autonomous Execution Foreground Service
 * Keeps SWE-bench Pro batches executing uninterrupted across sleep and low-memory conditions.
 */
class AutonomousEngineForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val CHANNEL_ID = "atropos_autonomous_engine"
    private val NOTIFICATION_ID = 42001

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun startAutonomousBatch(batchIntent: Intent) {
        acquireExecutionWakeLock()
        val notification = buildNotification("SWE-bench Batch Initiated", "Starting autonomous pipeline", 0)
        startForeground(NOTIFICATION_ID, notification)
    }

    fun updateNotificationTelemetry(issueId: String, currentOp: String, tokensUsed: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(issueId, currentOp, tokensUsed)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun acquireExecutionWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Atropos::AutonomousExecutionLock")
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(12 * 60 * 60 * 1000L) // 12 hours max
            }
        }
    }

    fun releaseExecutionWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Emergency state checkpoint triggered on low memory
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            // Persist emergency recovery checkpoint
        }
    }

    override fun onDestroy() {
        releaseExecutionWakeLock()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Atropos Autonomous Engine Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains execution continuity for autonomous batches"
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(issueId: String, currentOp: String, tokensUsed: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Atropos Autonomous Engine: $issueId")
            .setContentText("$currentOp (Tokens: $tokensUsed)")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }
}
