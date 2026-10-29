package app.gamenative.wow.solo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.gamenative.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/** Runs [SoloSetup] in a foreground service, so the downloads keep going with the screen off or the app in the background. */
class SoloSetupService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(notification("Setting up the solo server", null))
        if (job?.isActive == true) return START_NOT_STICKY
        val pathing = intent?.getBooleanExtra(EXTRA_PATHING, true) ?: true
        val gm = intent?.getBooleanExtra(EXTRA_GM, true) ?: true
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "foreverywhere:solo")
            .apply { acquire(6 * 60 * 60 * 1000L) }
        val progress = scope.launch {
            SoloSetup.state.collect { s ->
                val now = System.currentTimeMillis()
                if (s is SoloSetupState.Running && now - lastNotified > 1000) {
                    lastNotified = now
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(s.step, s.fraction))
                }
            }
        }
        job = scope.launch {
            try {
                SoloSetup.update(SoloSetupState.Running("Starting", null))
                SoloSetup.run(applicationContext, pathing, gm)
                SoloSetup.update(SoloSetupState.Done)
            } catch (e: Exception) {
                Timber.e(e, "Solo server setup failed")
                SoloSetup.update(SoloSetupState.Failed(e.message ?: "Setup failed."))
            } finally {
                progress.cancel()
                finish()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        job?.cancel()
        SoloSetup.update(SoloSetupState.Failed("Android paused setup after several hours. Press Set up again to continue."))
        finish()
    }

    private fun finish() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(text: String, fraction: Float?): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Solo server setup", NotificationManager.IMPORTANCE_LOW))
        }
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.fw_ic_download)
            .setContentTitle("Solo server")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .apply { if (fraction != null) setProgress(1000, (fraction * 1000).toInt(), false) else setProgress(0, 0, true) }
            .build()
    }

    companion object {
        private const val CHANNEL = "solo_setup"
        private const val NOTIFICATION_ID = 4712
        private const val EXTRA_PATHING = "pathing"
        private const val EXTRA_GM = "gm"

        fun start(context: Context, pathing: Boolean, gm: Boolean) {
            SoloSetup.update(SoloSetupState.Running("Starting", null))
            ContextCompat.startForegroundService(
                context,
                Intent(context, SoloSetupService::class.java).putExtra(EXTRA_PATHING, pathing).putExtra(EXTRA_GM, gm),
            )
        }
    }
}
