package dev.screenbuddy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** How this build gets permission to put the cat on screen: "Display over other apps". */
object Setup {
    const val CAN_CHASE = false
    const val CAN_SEE_APPS = false
    const val BUTTON = "Allow display over other apps"
    const val READY = "The cat is allowed on your screen."
    const val NOT_READY = "To let the cat live on your screen, allow Screen Buddy Lite to display over other apps."
    const val HINT = "Lite build: the cat notices when you touch the screen but cannot tell where, " +
        "so it does not run after your taps."
    val BALL_BUTTON: String? = "Throw or take away the wool ball"
    const val HOW_TO = "Tap the cat to pet it. Drag it to carry it, and let go mid-swipe to toss it. " +
        "It wanders around, perks up when you touch the screen, jumps onto the keyboard, and naps " +
        "when you leave it alone. Throw her a ball of wool from the button here or on the " +
        "Screen Buddy notification; you can fling the ball yourself too."

    fun isReady(activity: Activity): Boolean = Settings.canDrawOverlays(activity)

    fun openSettings(activity: Activity) {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")),
        )
    }

    /** Start the overlay service when the cat should be visible; it stops itself when hidden. */
    fun sync(activity: Activity) {
        if (isReady(activity) && Prefs.show(Prefs.of(activity))) {
            activity.startForegroundService(Intent(activity, OverlayService::class.java))
            // the notification carries the wool-ball button, so ask to be allowed to show it
            if (Build.VERSION.SDK_INT >= 33 && !askedForNotifications &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                askedForNotifications = true
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    private var askedForNotifications = false

    fun toggleBall(activity: Activity) {
        if (!isReady(activity) || !Prefs.show(Prefs.of(activity))) return
        activity.startForegroundService(
            Intent(activity, OverlayService::class.java).setAction(OverlayService.ACTION_BALL),
        )
    }
}
