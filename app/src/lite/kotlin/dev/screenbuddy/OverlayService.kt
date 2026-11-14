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
    private var keyboard: KeyboardWatcher? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("cat", "Cat on screen", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val ball = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_BALL), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, "cat")
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("The cat is on your screen")
            .setContentText("Tap Wool ball to throw her a ball, or to take it away again.")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Wool ball", ball).build())
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
        val watcher = KeyboardWatcher(this) { host?.applyWorld() }
        keyboard = watcher
        host = CatHost(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) { watcher.top }
            .also { it.start() }
        watcher.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_BALL) host?.toggleBall()
        return START_STICKY
    }

    override fun onDestroy() {
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(this)
        keyboard?.stop()
        keyboard = null
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

    companion object {
        const val ACTION_BALL = "dev.screenbuddy.action.BALL"
    }
}
