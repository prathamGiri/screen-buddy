package dev.screenbuddy

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Setup and settings: turn the service on, show or hide the cat, pick its size. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var enable: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.of(this)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun add(v: View, topMargin: Int = pad / 2) = column.addView(
            v,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { this.topMargin = topMargin },
        )

        add(TextView(this).apply { text = getString(R.string.app_name); textSize = 28f }, 0)
        status = TextView(this).apply { textSize = 16f }
        add(status)
        enable = Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        add(enable)
        add(TextView(this).apply {
            text = "If Android says the setting is restricted: open this app's App info page, tap the " +
                "three-dot menu and choose \"Allow restricted settings\", then try again."
            textSize = 13f
            alpha = 0.7f
        })

        add(Switch(this).apply {
            text = "Show the cat"
            textSize = 16f
            isChecked = Prefs.show(prefs)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(Prefs.SHOW, on).apply() }
        }, pad)
        add(Switch(this).apply {
            text = "Chase my taps"
            textSize = 16f
            isChecked = Prefs.chase(prefs)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(Prefs.CHASE, on).apply() }
        })

        add(TextView(this).apply { text = "Size"; textSize = 16f }, pad)
        val sizes = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; gravity = Gravity.START }
        val labels = arrayOf("Small", "Medium", "Large")
        Prefs.SCALES.forEachIndexed { i, scale ->
            sizes.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = labels[i]
                isChecked = Prefs.scale(prefs) == scale
                setOnClickListener { prefs.edit().putInt(Prefs.SCALE, scale).apply() }
            })
        }
        add(sizes, 0)

        add(TextView(this).apply {
            text = "Tap the cat to pet it. Drag it to carry it, and let go mid-swipe to toss it. " +
                "It runs after things you tap, climbs onto the keyboard, and naps when you leave it alone."
            textSize = 14f
            alpha = 0.8f
        }, pad)

        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceEnabled()
        status.text = if (on) {
            "The cat is allowed on your screen."
        } else {
            "To let the cat live on your screen, turn on Screen Buddy under Accessibility > Installed apps."
        }
        enable.visibility = View.VISIBLE
    }

    private fun isServiceEnabled(): Boolean {
        val mine = ComponentName(this, BuddyService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }
}
