package dev.screenbuddy

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** How this build gets permission to put the cat on screen: "Display over other apps". */
object Setup {
    const val CAN_CHASE = false
    const val BUTTON = "Allow display over other apps"
    const val READY = "The cat is allowed on your screen."
    const val NOT_READY = "To let the cat live on your screen, allow Screen Buddy Lite to display over other apps."
    const val HINT = "Lite build: the cat notices when you touch the screen but cannot tell where, " +
        "so it does not run after your taps."
    const val HOW_TO = "Tap the cat to pet it. Drag it to carry it, and let go mid-swipe to toss it. " +
        "It wanders around, perks up when you touch the screen, and naps when you leave it alone."

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
        }
    }
}
