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

    /** Longest name people can give their companion. */
    const val NAME_MAX = 20

    fun nameKey(s: Species) = "name_" + s.id

    /** The companion's name: whatever the user called it, or the name it came with. */
    fun name(p: SharedPreferences, s: Species = species(p)): String =
        p.getString(nameKey(s), null)?.takeIf { it.isNotBlank() } ?: s.defaultName

    /** Rename the companion; a blank name goes back to the one it came with. */
    fun setName(p: SharedPreferences, s: Species, name: String) {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(NAME_MAX)
        p.edit().apply { if (clean.isEmpty()) remove(nameKey(s)) else putString(nameKey(s), clean) }.apply()
    }

    fun key(b: Behavior) = "behavior_" + b.name.lowercase()
    fun isOn(p: SharedPreferences, b: Behavior) = p.getBoolean(key(b), true)
    fun setOn(p: SharedPreferences, b: Behavior, on: Boolean) = p.edit().putBoolean(key(b), on).apply()

    /** Every behaviour the user has left switched on. */
    fun behaviors(p: SharedPreferences): Set<Behavior> = Behavior.entries.filterTo(HashSet()) { isOn(p, it) }
}
