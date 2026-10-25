package com.shortsblocker

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.open_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        bindSwitch(R.id.youtube, Target.YOUTUBE)
        bindSwitch(R.id.instagram, Target.INSTAGRAM)

        val actionGroup = findViewById<RadioGroup>(R.id.action)
        actionGroup.check(
            when (Prefs.action(this)) {
                BlockAction.BACK -> R.id.action_back
                BlockAction.HOME -> R.id.action_home
            }
        )
        actionGroup.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setAction(this, if (checkedId == R.id.action_home) BlockAction.HOME else BlockAction.BACK)
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = isServiceEnabled()
        findViewById<TextView>(R.id.status).setText(if (enabled) R.string.status_on else R.string.status_off)
        findViewById<TextView>(R.id.count).text = getString(R.string.blocked_today, Prefs.blockedToday(this))
        val setupVisibility = if (enabled) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.open_settings).visibility = setupVisibility
        findViewById<TextView>(R.id.help).visibility = setupVisibility
    }

    private fun bindSwitch(viewId: Int, target: Target) {
        val switch = findViewById<Switch>(viewId)
        switch.isChecked = Prefs.isEnabled(this, target)
        switch.setOnCheckedChangeListener { _, checked -> Prefs.setEnabled(this, target, checked) }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
