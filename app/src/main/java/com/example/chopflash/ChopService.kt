package com.example.chopflash

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast

/**
 * Foreground service that keeps listening to the accelerometer and toggles the
 * flashlight when a double chop is detected.
 */
class ChopService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var sensorManager: SensorManager
    private lateinit var torch: Torch
    private lateinit var detector: ChopDetector
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)

        try {
            startInForeground()
        } catch (e: Exception) {
            // Android 12+ can refuse to start a foreground service from the background.
            Log.w(TAG, "Could not start in foreground", e)
            stopSelf()
            return
        }

        torch = Torch(this)
        if (torch.cameraId == null) {
            Toast.makeText(this, R.string.no_flash, Toast.LENGTH_LONG).show()
        }

        detector = ChopDetector(ChopDetector.thresholdFor(Prefs.sensitivity(prefs))) { onDoubleChop() }
        sensorManager = getSystemService(SensorManager::class.java)
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel == null) {
            Toast.makeText(this, R.string.no_accelerometer, Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        sensorManager.registerListener(detector, accel, SensorManager.SENSOR_DELAY_GAME)

        updateWakeLock()
        prefs.registerOnSharedPreferenceChangeListener(this)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.edit().putBoolean(Prefs.KEY_ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        if (::detector.isInitialized) sensorManager.unregisterListener(detector)
        if (::torch.isInitialized) torch.release()
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(this)
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences, key: String?) {
        when (key) {
            Prefs.KEY_SENSITIVITY -> detector.threshold = ChopDetector.thresholdFor(Prefs.sensitivity(p))
            Prefs.KEY_SCREEN_OFF -> updateWakeLock()
        }
    }

    private fun onDoubleChop() {
        if (torch.toggle() && Prefs.vibrate(prefs)) buzz()
    }

    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /**
     * Many phones stop delivering accelerometer events once the CPU sleeps with the
     * screen off. A partial wake lock keeps the CPU awake so chops still register.
     */
    private fun updateWakeLock() {
        if (Prefs.screenOff(prefs)) {
            if (wakeLock?.isHeld == true) return
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChopFlash::listen")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ChopService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_flash)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stop).build())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val TAG = "ChopService"
        private const val CHANNEL_ID = "chop_listener"
        private const val NOTIF_ID = 1
        private const val ACTION_STOP = "com.example.chopflash.STOP"

        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ChopService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChopService::class.java))
        }
    }
}
