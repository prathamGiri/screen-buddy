package dev.screenbuddy

import android.content.Context
import android.content.SharedPreferences
import dev.screenbuddy.core.Behavior
import dev.screenbuddy.core.Species

object Prefs {
    const val SHOW = "show"
    const val SCALE = "scale"
    const val SPECIES = "species"

    /** Screen dp per sprite pixel for the petite, classic and grand companion. */
    val SCALES = intArrayOf(2, 3, 4)

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences("buddy", Context.MODE_PRIVATE)

    fun show(p: SharedPreferences) = p.getBoolean(SHOW, true)
    fun scale(p: SharedPreferences) = p.getInt(SCALE, 3).coerceIn(SCALES.first(), SCALES.last())
    fun species(p: SharedPreferences): Species = Species.byId(p.getString(SPECIES, null))

    fun key(b: Behavior) = "behavior_" + b.name.lowercase()
    fun isOn(p: SharedPreferences, b: Behavior) = p.getBoolean(key(b), true)
    fun setOn(p: SharedPreferences, b: Behavior, on: Boolean) = p.edit().putBoolean(key(b), on).apply()

    /** Every behaviour the user has left switched on. */
    fun behaviors(p: SharedPreferences): Set<Behavior> = Behavior.entries.filterTo(HashSet()) { isOn(p, it) }
}
