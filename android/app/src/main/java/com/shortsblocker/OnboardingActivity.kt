package com.shortsblocker

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity

/** First-run walkthrough: welcome, disclosure and consent, permission, done. */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var flipper: ViewFlipper
    private val dots by lazy { listOf(R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4).map { findViewById<View>(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        flipper = findViewById(R.id.flipper)

        click(R.id.welcome_next) { show(PAGE_CONSENT) }
        click(R.id.consent_accept) {
            Prefs.setConsented(this)
            show(if (SettingsLinks.isServiceEnabled(this)) PAGE_DONE else PAGE_PERMISSION)
        }
        click(R.id.consent_decline) { finish() }
        click(R.id.permission_open) { SettingsLinks.openAccessibility(this) }
        click(R.id.permission_help_toggle) {
            val box = findViewById<View>(R.id.permission_help)
            box.visibility = if (box.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        click(R.id.permission_app_info) { SettingsLinks.openAppInfo(this) }
        click(R.id.done_youtube) {
            complete()
            SettingsLinks.openApp(this, Target.YOUTUBE.packageName)
        }
        click(R.id.done_finish) {
            complete()
            startActivity(Intent(this, MainActivity::class.java))
        }

        // "Restricted settings" only exist from Android 13.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            findViewById<View>(R.id.permission_help_toggle).visibility = View.GONE
        }
        if (!SettingsLinks.openAppAvailable(this, Target.YOUTUBE.packageName)) {
            findViewById<View>(R.id.done_youtube).visibility = View.GONE
        }

        val requested = intent.getIntExtra(EXTRA_PAGE, -1)
        show(
            when {
                requested >= 0 && Prefs.hasConsented(this) -> requested
                Prefs.hasConsented(this) -> PAGE_PERMISSION
                else -> PAGE_WELCOME
            },
            animate = false,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    // Returning from Settings, whether by the service bringing us back or by the user.
    override fun onResume() {
        super.onResume()
        val page = flipper.displayedChild
        if (page == PAGE_PERMISSION && SettingsLinks.isServiceEnabled(this)) show(PAGE_DONE)
        if (page == PAGE_DONE && !SettingsLinks.isServiceEnabled(this)) show(PAGE_PERMISSION)
    }

    private fun show(page: Int, animate: Boolean = true) {
        if (animate) {
            flipper.setInAnimation(this, android.R.anim.fade_in)
            flipper.setOutAnimation(this, android.R.anim.fade_out)
        } else {
            flipper.inAnimation = null
            flipper.outAnimation = null
        }
        flipper.displayedChild = page
        dots.forEachIndexed { i, dot ->
            val active = i == page
            dot.backgroundTintList = ColorStateList.valueOf(getColor(if (active) R.color.accent else R.color.outline))
            dot.layoutParams = dot.layoutParams.apply { width = dp(if (active) 22 else 8) }
        }
    }

    private fun complete() {
        Prefs.setOnboarded(this)
        finish()
    }

    private fun click(id: Int, action: () -> Unit) = findViewById<View>(id).setOnClickListener { action() }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PAGE_WELCOME = 0
        const val PAGE_CONSENT = 1
        const val PAGE_PERMISSION = 2
        const val PAGE_DONE = 3
        private const val EXTRA_PAGE = "page"

        fun permissionIntent(context: Context) =
            Intent(context, OnboardingActivity::class.java).putExtra(EXTRA_PAGE, PAGE_PERMISSION)
    }
}
