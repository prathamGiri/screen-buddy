package dev.screenbuddy

import android.app.Activity
import android.os.Bundle
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
            text = Setup.BUTTON
            setOnClickListener { Setup.openSettings(this@MainActivity) }
        }
        add(enable)
        add(TextView(this).apply {
            text = Setup.HINT
            textSize = 13f
            alpha = 0.7f
        })

        add(Switch(this).apply {
            text = "Show the cat"
            textSize = 16f
            isChecked = Prefs.show(prefs)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean(Prefs.SHOW, on).apply()
                Setup.sync(this@MainActivity)
            }
        }, pad)
        if (Setup.CAN_CHASE) add(Switch(this).apply {
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
            text = Setup.HOW_TO
            textSize = 14f
            alpha = 0.8f
        }, pad)

        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        val ready = Setup.isReady(this)
        status.text = if (ready) Setup.READY else Setup.NOT_READY
        enable.visibility = if (ready && !Setup.CAN_CHASE) View.GONE else View.VISIBLE
        Setup.sync(this)
    }
}
