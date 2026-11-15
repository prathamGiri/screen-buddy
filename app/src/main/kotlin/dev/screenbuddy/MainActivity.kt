package dev.screenbuddy

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.screenbuddy.core.Anim
import dev.screenbuddy.core.Behavior
import dev.screenbuddy.core.Species
import dev.screenbuddy.core.Sprites

/** The companion's home: a live preview, permission, and everything she is allowed to do. */
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var preview: PreviewView
    private lateinit var caption: TextView
    private lateinit var status: TextView
    private lateinit var invite: View
    private lateinit var showRow: View
    private lateinit var showSwitch: LuxSwitch
    private lateinit var title: TextView
    private lateinit var inviteButton: TextView

    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private fun dp(v: Float) = Lux.dpi(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.of(this)
        val species = Prefs.species(prefs)

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun add(v: View, top: Float = 0f, height: Int = wrap) = column.addView(
            v, LinearLayout.LayoutParams(match, height).apply { topMargin = dp(top) },
        )

        // --- heading ---
        add(Lux.eyebrow(this, "Screen Buddy"), 8f)
        add(heading(), 6f)
        add(Lux.text(this, "Your ginger tabby, at home on your screen", 15f, Lux.MUTED, Lux.LIGHT), 2f)

        // --- the stage ---
        add(stage(), 24f, dp(280f))

        // --- permission, or the switch to show her ---
        invite = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            inviteButton = Lux.goldButton(this@MainActivity, "") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            addView(inviteButton, LinearLayout.LayoutParams(match, wrap))
            addView(Lux.text(this@MainActivity,
                "Allow Screen Buddy to display over other apps, then come back here.",
                13f, Lux.MUTED, Lux.LIGHT,
            ).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(10f) })
        }
        add(invite, 20f)
        showSwitch = LuxSwitch(this, Prefs.show(prefs)) { on ->
            prefs.edit().putBoolean(Prefs.SHOW, on).apply()
            refresh()
        }
        showRow = toggleRow("On your screen", "Hide her for a while without changing anything else.", showSwitch).apply {
            background = Lux.card(this@MainActivity)
            setPadding(dp(20f), dp(18f), dp(20f), dp(18f))
        }
        add(showRow, 20f)

        // --- companion ---
        add(Lux.eyebrow(this, "Companion"), 36f)
        add(companions(species), 14f)

        // --- size ---
        add(Lux.eyebrow(this, "Size"), 36f)
        add(sizes(), 14f)

        // --- behaviours ---
        add(Lux.eyebrow(this, "Behaviours"), 36f)
        add(Lux.text(this, "She does these by herself, at a cat's own unhurried pace.", 13f, Lux.MUTED, Lux.LIGHT), 6f)
        add(behaviours(), 14f)

        // --- gestures ---
        add(Lux.eyebrow(this, "Together"), 36f)
        add(gestures(), 14f)

        add(Lux.text(this,
            "Screen Buddy has no internet access and never reads what is on your screen.",
            12f, Lux.FAINT, Lux.LIGHT,
        ).apply { gravity = Gravity.CENTER }, 36f)
        add(Lux.text(this, "Privacy policy", 13f, Lux.GOLD, Lux.MEDIUM).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
            setPadding(0, dp(10f), 0, dp(10f))
            isClickable = true
            setOnClickListener { openPrivacyPolicy() }
        }, 8f)
        add(Lux.text(this, "Version ${packageManager.getPackageInfo(packageName, 0).versionName}", 12f, Lux.FAINT, Lux.LIGHT)
            .apply { gravity = Gravity.CENTER }, 0f)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Lux.INK)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(column)
        }
        // draw behind the system bars, but keep content clear of them
        val side = dp(22f)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(side + bars.left, dp(20f) + bars.top, side + bars.right, dp(32f) + bars.bottom)
            insets
        }
        window.setDecorFitsSystemWindows(false)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun ready() = Settings.canDrawOverlays(this)

    /** Bring the screen in line with the permission and settings, and start the overlay if wanted. */
    private fun refresh() {
        val ready = ready()
        val name = Prefs.name(prefs)
        title.text = name
        // long names step down in size so they still fit on one or two lines
        title.textSize = when {
            name.length <= 11 -> 38f
            name.length <= 15 -> 32f
            else -> 28f
        }
        inviteButton.text = "Invite $name onto your screen"
        invite.visibility = if (ready) View.GONE else View.VISIBLE
        showRow.visibility = if (ready) View.VISIBLE else View.GONE
        showSwitch.set(Prefs.show(prefs), animate = false, notify = false)
        val on = ready && Prefs.show(prefs)
        status.text = when {
            !ready -> "Awaiting permission"
            on -> "On your screen"
            else -> "Resting"
        }
        status.setCompoundDrawablesRelativeWithIntrinsicBounds(dot(if (on) Lux.GOLD else Lux.FAINT), null, null, null)
        preview.enabled = Prefs.behaviors(prefs)
        if (on) startForegroundService(Intent(this, OverlayService::class.java))
    }

    /** Opens the policy in the browser; the app itself has no internet permission. */
    private fun openPrivacyPolicy() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, PRIVACY_POLICY_URL, Toast.LENGTH_LONG).show()
        }
    }

    // --- sections ---

    /** Her name in large type, with a quiet button to rename her. */
    private fun heading(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        title = Lux.text(this, "", 38f, face = Lux.SERIF).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(0f, 1f)
            isClickable = true
            setOnClickListener { rename() }
        }
        row.addView(title, LinearLayout.LayoutParams(0, wrap, 1f))
        val button = Lux.text(this, "Rename", 12f, Lux.GOLD, Lux.MEDIUM).apply {
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            background = Lux.pressable(this@MainActivity, Lux.card(this@MainActivity, stroke = Lux.GOLD_DEEP, radius = 18f), 18f)
            isClickable = true
            setOnClickListener { rename() }
        }
        row.addView(button, LinearLayout.LayoutParams(wrap, wrap).apply { leftMargin = dp(12f) })
        return row
    }

    private fun rename() {
        val species = Prefs.species(prefs)
        val field = android.widget.EditText(this).apply {
            setText(Prefs.name(prefs))
            setSelection(text.length)
            setSingleLine()
            filters = arrayOf(android.text.InputFilter.LengthFilter(Prefs.NAME_MAX))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            textSize = 20f
            typeface = Lux.SERIF
            setTextColor(Lux.IVORY)
            setHintTextColor(Lux.FAINT)
            hint = species.defaultName
            backgroundTintList = android.content.res.ColorStateList.valueOf(Lux.GOLD)
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(24f), dp(8f), dp(24f), 0)
            addView(field)
        }
        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("What would you like to call her?")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                Prefs.setName(prefs, species, field.text.toString())
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Use ${species.defaultName}") { _, _ ->
                Prefs.setName(prefs, species, "")
                refresh()
            }
            .create()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(Lux.card(this, fill = Lux.SURFACE_HI, radius = 24f))
            for (which in intArrayOf(android.app.AlertDialog.BUTTON_POSITIVE, android.app.AlertDialog.BUTTON_NEGATIVE, android.app.AlertDialog.BUTTON_NEUTRAL)) {
                dialog.getButton(which)?.setTextColor(if (which == android.app.AlertDialog.BUTTON_POSITIVE) Lux.GOLD else Lux.MUTED)
            }
            field.requestFocus()
        }
        dialog.show()
    }

    private fun stage(): View {
        val frame = FrameLayout(this)
        frame.background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(Lux.GLOW, Lux.SURFACE)
            gradientRadius = Lux.dp(this@MainActivity, 200f)
            setGradientCenter(0.5f, 0.62f)
            cornerRadius = Lux.dp(this@MainActivity, 28f)
            setStroke(dp(1f), Lux.HAIRLINE)
        }
        frame.clipToOutline = true
        caption = Lux.text(this, "", 17f, Lux.IVORY, Lux.SERIF_ITALIC).apply { gravity = Gravity.CENTER }
        preview = PreviewView(this) { caption.text = it }
        preview.contentDescription = "Preview of your companion. Tap to pet her."
        frame.addView(preview, FrameLayout.LayoutParams(match, match).apply { bottomMargin = dp(36f) })
        frame.addView(caption, FrameLayout.LayoutParams(match, wrap, Gravity.BOTTOM).apply { bottomMargin = dp(20f) })
        status = Lux.text(this, "", 12f, Lux.IVORY, Lux.MEDIUM).apply {
            letterSpacing = 0.06f
            compoundDrawablePadding = dp(8f)
            gravity = Gravity.CENTER_VERTICAL
            background = Lux.card(this@MainActivity, fill = 0x99141210.toInt(), radius = 20f)
            setPadding(dp(12f), dp(6f), dp(14f), dp(6f))
        }
        frame.addView(status, FrameLayout.LayoutParams(wrap, wrap, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(16f); topMargin = dp(16f)
        })
        frame.addView(Lux.text(this, "Tap to pet", 11f, Lux.FAINT, Lux.MEDIUM).apply { letterSpacing = 0.1f },
            FrameLayout.LayoutParams(wrap, wrap, Gravity.TOP or Gravity.END).apply { rightMargin = dp(18f); topMargin = dp(22f) })
        return frame
    }

    private fun dot(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setSize(dp(7f), dp(7f))
    }

    private fun companions(chosen: Species): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Species.entries.forEachIndexed { i, s ->
            val selected = s == chosen
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8f), dp(16f), dp(8f), dp(14f))
                background = Lux.pressable(this@MainActivity, Lux.card(
                    this@MainActivity,
                    fill = if (selected) Lux.SURFACE_HI else Lux.SURFACE,
                    stroke = if (selected) Lux.GOLD else Lux.HAIRLINE,
                    radius = 20f,
                ), 20f)
                alpha = if (s.available) 1f else 0.55f
                isClickable = true
                setOnClickListener {
                    if (!s.available) {
                        Toast.makeText(this@MainActivity, "${s.label}s are on their way.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val portrait: View = if (s == Species.CAT) {
                ImageView(this).apply {
                    setImageDrawable(BitmapDrawable(resources, Bitmap.createBitmap(
                        Sprites.frame(Anim.IDLE, 0), Sprites.SIZE, Sprites.SIZE, Bitmap.Config.ARGB_8888,
                    )).apply { isFilterBitmap = false })
                }
            } else {
                // no artwork yet: an engraved monogram stands in
                Lux.text(this, s.label.take(1), 26f, Lux.GOLD, Lux.SERIF).apply {
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setStroke(dp(1f), Lux.GOLD_DEEP)
                    }
                }
            }
            tile.addView(portrait, LinearLayout.LayoutParams(dp(56f), dp(56f)))
            tile.addView(Lux.text(this, s.label, 15f, Lux.IVORY, Lux.SERIF).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(10f) })
            tile.addView(Lux.text(this, if (s.available) (if (selected) "Selected" else s.defaultName) else "Coming soon",
                11f, if (selected) Lux.GOLD else Lux.FAINT, Lux.MEDIUM).apply {
                gravity = Gravity.CENTER
                letterSpacing = 0.08f
            }, LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(2f) })
            row.addView(tile, LinearLayout.LayoutParams(0, wrap, 1f).apply { if (i > 0) leftMargin = dp(10f) })
        }
        return row
    }

    private fun sizes(): View {
        val labels = arrayOf("Petite", "Classic", "Grand")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Lux.card(this@MainActivity, radius = 26f)
            setPadding(dp(5f), dp(5f), dp(5f), dp(5f))
        }
        val options = ArrayList<TextView>()
        fun paint() = options.forEachIndexed { i, v ->
            val on = Prefs.SCALES[i] == Prefs.scale(prefs)
            v.setTextColor(if (on) Lux.INK else Lux.MUTED)
            v.background = if (on) GradientDrawable().apply {
                colors = intArrayOf(0xFFE6C88A.toInt(), Lux.GOLD)
                cornerRadius = Lux.dp(this@MainActivity, 22f)
            } else null
        }
        Prefs.SCALES.forEachIndexed { i, scale ->
            val v = Lux.text(this, labels[i], 14f, face = Lux.MEDIUM).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(12f), 0, dp(12f))
                isClickable = true
                setOnClickListener {
                    prefs.edit().putInt(Prefs.SCALE, scale).apply()
                    paint()
                }
            }
            options.add(v)
            row.addView(v, LinearLayout.LayoutParams(0, wrap, 1f))
        }
        paint()
        return row
    }

    private fun behaviours(): View {
        val items = listOf(
            Triple(Behavior.BALL, "Ball of wool", "Every hour or two a ball rolls in. She plays for a minute or two, then bats it away."),
            Triple(Behavior.GROOM, "Grooming", "Now and then she licks her paw and washes her face."),
            Triple(Behavior.STRETCH, "Stretching", "A long stretch when she wakes from a nap by herself."),
            Triple(Behavior.YAWN, "Yawning", "A big yawn as she settles down for a nap."),
            Triple(Behavior.SCRATCH, "Ear scratching", "An occasional scratch behind the ear."),
            Triple(Behavior.WANDER, "Wandering", "Strolls along the bottom of the screen."),
            Triple(Behavior.NAP, "Napping", "Curls up for a long nap when left alone."),
            Triple(Behavior.KEYBOARD, "Keyboard visits", "Hops up onto the keyboard while you type."),
            Triple(Behavior.NOTICE, "Curiosity", "Perks up now and then when you touch the screen."),
        )
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Lux.card(this@MainActivity)
        }
        items.forEachIndexed { i, (b, title, detail) ->
            if (i > 0) card.addView(View(this).apply { setBackgroundColor(Lux.HAIRLINE) },
                LinearLayout.LayoutParams(match, dp(1f)).apply { leftMargin = dp(20f); rightMargin = dp(20f) })
            val sw = LuxSwitch(this, Prefs.isOn(prefs, b)) { on ->
                Prefs.setOn(prefs, b, on)
                preview.enabled = Prefs.behaviors(prefs)
            }
            card.addView(toggleRow(title, detail, sw).apply { setPadding(dp(20f), dp(16f), dp(20f), dp(16f)) })
        }
        return card
    }

    private fun toggleRow(title: String, detail: String, sw: LuxSwitch): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener { sw.toggle() }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(Lux.text(this, title, 16f, Lux.IVORY, Lux.SERIF))
        texts.addView(Lux.text(this, detail, 13f, Lux.MUTED, Lux.LIGHT), LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(3f) })
        row.addView(texts, LinearLayout.LayoutParams(0, wrap, 1f).apply { rightMargin = dp(16f) })
        row.addView(sw)
        sw.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        sw.contentDescription = title
        return row
    }

    private fun gestures(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Lux.card(this@MainActivity)
            setPadding(dp(8f), dp(18f), dp(8f), dp(18f))
        }
        for ((verb, what) in listOf("Tap" to "to pet her", "Drag" to "to carry her", "Fling" to "to toss her")) {
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            cell.addView(Lux.text(this, verb, 18f, Lux.GOLD, Lux.SERIF).apply { gravity = Gravity.CENTER })
            cell.addView(Lux.text(this, what, 12f, Lux.MUTED, Lux.LIGHT).apply { gravity = Gravity.CENTER })
            row.addView(cell, LinearLayout.LayoutParams(0, wrap, 1f))
        }
        return row
    }

    private companion object {
        const val PRIVACY_POLICY_URL = "https://prathamgiri.com/screen-buddy/privacy-policy.html"
    }
}
