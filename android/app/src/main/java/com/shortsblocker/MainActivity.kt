package com.shortsblocker

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    // Keeps the pause countdown current while the screen is open.
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.open_accessibility).setOnClickListener {
            launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.open_app_info).setOnClickListener {
            launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        findViewById<MaterialButton>(R.id.open_battery).setOnClickListener {
            launch(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", packageName, null)),
                fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        }

        bindSwitch(R.id.youtube_hide_tab, { Prefs.hidesTab(this, Target.YOUTUBE) }) { Prefs.setHidesTab(this, Target.YOUTUBE, it) }
        bindSwitch(R.id.youtube_block, { Prefs.blocksPlayer(this, Target.YOUTUBE) }) { Prefs.setBlocksPlayer(this, Target.YOUTUBE, it) }
        bindSwitch(R.id.instagram_hide_tab, { Prefs.hidesTab(this, Target.INSTAGRAM) }) { Prefs.setHidesTab(this, Target.INSTAGRAM, it) }
        bindSwitch(R.id.instagram_block, { Prefs.blocksPlayer(this, Target.INSTAGRAM) }) { Prefs.setBlocksPlayer(this, Target.INSTAGRAM, it) }

        val actionGroup = findViewById<MaterialButtonToggleGroup>(R.id.action)
        actionGroup.check(if (Prefs.action(this) == BlockAction.HOME) R.id.action_home else R.id.action_back)
        actionGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) Prefs.setAction(this, if (id == R.id.action_home) BlockAction.HOME else BlockAction.BACK)
        }

        for ((id, minutes) in listOf(R.id.pause_5 to 5, R.id.pause_15 to 15, R.id.pause_30 to 30)) {
            findViewById<MaterialButton>(id).apply {
                text = getString(R.string.pause_minutes, minutes)
                setOnClickListener {
                    Prefs.pauseFor(this@MainActivity, minutes)
                    render()
                }
            }
        }
        findViewById<MaterialButton>(R.id.pause_resume).setOnClickListener {
            Prefs.resume(this)
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun render() {
        val serviceOn = isServiceEnabled()
        val batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val paused = Prefs.isPaused(this)

        // Status
        val (titleRes, icon, tone) = when {
            !serviceOn -> Triple(R.string.status_off_title, R.drawable.ic_shield_off, Tone.WARN)
            paused -> Triple(R.string.status_paused_title, R.drawable.ic_pause, Tone.WARN)
            else -> Triple(R.string.status_on_title, R.drawable.ic_shield, Tone.OK)
        }
        findViewById<MaterialCardView>(R.id.hero).setCardBackgroundColor(getColor(tone.container))
        findViewById<ImageView>(R.id.hero_icon).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(getColor(R.color.surface))
            backgroundTintList = ColorStateList.valueOf(getColor(tone.strong))
        }
        findViewById<TextView>(R.id.hero_title).setText(titleRes)
        findViewById<TextView>(R.id.hero_body).text = when {
            !serviceOn -> getString(R.string.status_off_body)
            paused -> getString(R.string.status_paused_body, minutesLeft())
            else -> getString(R.string.status_on_body)
        }

        // Setup
        findViewById<View>(R.id.setup).visibility = if (serviceOn && batteryOk) View.GONE else View.VISIBLE
        renderStep(R.id.step1_badge, R.id.open_accessibility, "1", serviceOn)
        renderStep(R.id.step2_badge, R.id.open_battery, "2", batteryOk)
        findViewById<View>(R.id.step1_denied).visibility = if (serviceOn) View.GONE else View.VISIBLE

        // Stats
        val week = Prefs.lastSevenDays(this)
        findViewById<TextView>(R.id.stat_today).text = Prefs.blockedToday(this).toString()
        findViewById<TextView>(R.id.stat_week).text = week.sumOf { it.second }.toString()
        findViewById<TextView>(R.id.stat_total).text = Prefs.blockedTotal(this).toString()
        findViewById<WeekChartView>(R.id.chart).setData(week)

        // Pause
        findViewById<View>(R.id.pause_choices).visibility = if (paused) View.GONE else View.VISIBLE
        findViewById<View>(R.id.pause_resume).visibility = if (paused) View.VISIBLE else View.GONE
    }

    private fun renderStep(badgeId: Int, buttonId: Int, number: String, done: Boolean) {
        findViewById<TextView>(badgeId).apply {
            text = if (done) "✓" else number
            setTextColor(getColor(if (done) R.color.surface else R.color.on_surface))
            backgroundTintList = ColorStateList.valueOf(getColor(if (done) R.color.ok else R.color.outline))
        }
        findViewById<View>(buttonId).visibility = if (done) View.INVISIBLE else View.VISIBLE
    }

    private fun minutesLeft() =
        ceil((Prefs.pausedUntil(this) - System.currentTimeMillis()) / 60_000.0).toInt().coerceAtLeast(1)

    private fun bindSwitch(viewId: Int, get: () -> Boolean, set: (Boolean) -> Unit) {
        findViewById<MaterialSwitch>(viewId).apply {
            isChecked = get()
            setOnCheckedChangeListener { _, checked -> set(checked) }
        }
    }

    private fun launch(intent: Intent, fallback: Intent? = null) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            fallback?.let { runCatching { startActivity(it) } }
        }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private enum class Tone(val strong: Int, val container: Int) {
        OK(R.color.ok, R.color.ok_container),
        WARN(R.color.warn, R.color.warn_container),
    }

    private companion object {
        const val TICK_MS = 20_000L
    }
}
