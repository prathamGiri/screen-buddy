package dev.screenbuddy

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings

/** How this build gets permission to put the cat on screen: an accessibility service. */
object Setup {
    const val CAN_CHASE = true

    /** The accessibility service can tell which app is open, so calm mode is available. */
    const val CAN_SEE_APPS = true
    const val BUTTON = "Open accessibility settings"
    const val READY = "The cat is allowed on your screen."
    const val NOT_READY =
        "To let the cat live on your screen, turn on Screen Buddy under Accessibility > Installed apps."
    const val HINT = "If Android says the setting is restricted: open this app's App info page, tap the " +
        "three-dot menu and choose \"Allow restricted settings\", then try again."
    const val HOW_TO = "Tap the cat to pet it. Drag it to carry it, and let go mid-swipe to toss it. " +
        "It runs after things you tap, jumps onto the keyboard, and naps when you leave it alone. " +
        "Pull down the notification tray and a ball of wool drops in; you can throw it for her too."

    fun isReady(activity: Activity): Boolean {
        val mine = ComponentName(activity, BuddyService::class.java)
        val enabled = Settings.Secure.getString(
            activity.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    fun openSettings(activity: Activity) {
        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** The ball drops in by itself when the notification tray opens, so there is no button. */
    val BALL_BUTTON: String? = null

    fun toggleBall(activity: Activity) = Unit

    /** Nothing to do: the system keeps the accessibility service running. */
    fun sync(activity: Activity) = Unit
}
