package com.shortsblocker

import android.content.Context
import java.time.LocalDate

enum class BlockAction { BACK, HOME }

object Prefs {
    private const val FILE = "settings"
    private const val KEY_ACTION = "action"
    private const val KEY_COUNT = "blocked_count"
    private const val KEY_COUNT_DATE = "blocked_count_date"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, target: Target) = prefs(context).getBoolean(target.name, true)

    fun setEnabled(context: Context, target: Target, enabled: Boolean) =
        prefs(context).edit().putBoolean(target.name, enabled).apply()

    fun action(context: Context): BlockAction =
        BlockAction.valueOf(prefs(context).getString(KEY_ACTION, BlockAction.BACK.name)!!)

    fun setAction(context: Context, action: BlockAction) =
        prefs(context).edit().putString(KEY_ACTION, action.name).apply()

    fun blockedToday(context: Context): Int {
        val p = prefs(context)
        return if (p.getString(KEY_COUNT_DATE, null) == LocalDate.now().toString()) p.getInt(KEY_COUNT, 0) else 0
    }

    fun recordBlock(context: Context) {
        prefs(context).edit()
            .putString(KEY_COUNT_DATE, LocalDate.now().toString())
            .putInt(KEY_COUNT, blockedToday(context) + 1)
            .apply()
    }
}
