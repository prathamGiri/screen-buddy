package dev.screenbuddy

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val SHOW = "show"
    const val CHASE = "chase"
    const val SCALE = "scale"

    /** Screen dp per sprite pixel for the small, medium and large cat. */
    val SCALES = intArrayOf(2, 3, 4)

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences("buddy", Context.MODE_PRIVATE)

    fun show(p: SharedPreferences) = p.getBoolean(SHOW, true)
    fun chase(p: SharedPreferences) = p.getBoolean(CHASE, true)
    fun scale(p: SharedPreferences) = p.getInt(SCALE, 3).coerceIn(SCALES.first(), SCALES.last())
}
