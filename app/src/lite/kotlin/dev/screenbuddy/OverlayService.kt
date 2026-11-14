package dev.screenbuddy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager

/** Keeps the cat's overlay window alive using the "Display over other apps" permission. */
class OverlayService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var host: CatHost? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("cat", "Cat on screen", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, "cat")
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("The cat is on your screen")
            .setContentText("Tap to open settings or hide it.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        Prefs.of(this).registerOnSharedPreferenceChangeListener(this)
        host = CatHost(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(this)
        host?.stop()
        host = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        host?.applyWorld()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences, key: String?) {
        if (!Prefs.show(p)) stopSelf()
    }
}
