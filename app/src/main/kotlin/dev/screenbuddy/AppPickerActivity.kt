package dev.screenbuddy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView

/** A checklist of installed apps; the cat stays calm while any checked app is open. */
class AppPickerActivity : Activity() {

    private class App(val label: String, val pkg: String) {
        override fun toString() = label
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.of(this)
        val list = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE }
        setContentView(list)

        // reading every app's name can take a moment, so do it off the main thread
        Thread {
            val pm = packageManager
            val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launchable, 0)
                .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.pkg != packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, apps)
                val chosen = Prefs.calmApps(prefs)
                apps.forEachIndexed { i, app -> list.setItemChecked(i, app.pkg in chosen) }
                list.setOnItemClickListener { _, _, _, _ ->
                    val picked = HashSet<String>()
                    val checked = list.checkedItemPositions
                    for (i in apps.indices) if (checked.get(i)) picked.add(apps[i].pkg)
                    prefs.edit().putStringSet(Prefs.CALM_APPS, picked).apply()
                }
            }
        }.start()
    }
}
