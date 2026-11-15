package dev.screenbuddy

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val SHOW = "show"
    const val CHASE = "chase"
    const val SCALE = "scale"
    const val BALL = "ball"
    const val CALM_MODE = "calm_mode"
    const val CALM_APPS = "calm_apps"

    /** Where the cat calms down: nowhere, in the chosen apps, or everywhere but the home screen. */
    const val CALM_NEVER = "never"
    const val CALM_LISTED = "listed"
    const val CALM_ALL = "all"

    /** Apps that tend to be demanding, chosen until the user picks their own. */
    val DEFAULT_CALM_APPS = setOf(
        "com.android.chrome",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.spotify.music",
    )

    /** Screen dp per sprite pixel for the small, medium and large cat. */
    val SCALES = intArrayOf(2, 3, 4)

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences("buddy", Context.MODE_PRIVATE)

    fun show(p: SharedPreferences) = p.getBoolean(SHOW, true)
    fun chase(p: SharedPreferences) = p.getBoolean(CHASE, true)
    fun ball(p: SharedPreferences) = p.getBoolean(BALL, true)
    fun calmMode(p: SharedPreferences): String = p.getString(CALM_MODE, CALM_LISTED) ?: CALM_LISTED
    fun calmApps(p: SharedPreferences): Set<String> = p.getStringSet(CALM_APPS, null) ?: DEFAULT_CALM_APPS
    fun scale(p: SharedPreferences) = p.getInt(SCALE, 3).coerceIn(SCALES.first(), SCALES.last())
}
