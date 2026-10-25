package com.shortsblocker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

object SettingsLinks {
    private const val ACTION_ACCESSIBILITY_DETAILS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    private const val FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
    private const val SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

    private fun component(context: Context) = ComponentName(context, BlockerService::class.java)

    /** Opens our switch directly where the system allows it, otherwise the list with our entry highlighted. */
    fun openAccessibility(context: Context) {
        val name = component(context).flattenToString()
        val details = Intent(ACTION_ACCESSIBILITY_DETAILS).putExtra(Intent.EXTRA_COMPONENT_NAME, name)
        val list = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(FRAGMENT_ARGS_KEY, name)
            .putExtra(SHOW_FRAGMENT_ARGS, Bundle().apply { putString(FRAGMENT_ARGS_KEY, name) })
        startFirst(context, details, list)
    }

    fun openAppInfo(context: Context) =
        startFirst(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

    /** Returns false when the app is not installed. */
    fun openApp(context: Context, packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        context.startActivity(launch)
        return true
    }

    fun openAppAvailable(context: Context, packageName: String) =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = component(context)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    // Settings screens differ between manufacturers; some reject or lack an action.
    private fun startFirst(context: Context, vararg intents: Intent) {
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return
            } catch (e: RuntimeException) {
                continue
            }
        }
    }
}
