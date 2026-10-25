package com.shortsblocker

import android.content.Context
import java.time.LocalDate

enum class BlockAction { BACK, HOME }

object Prefs {
    private const val FILE = "settings"
    private const val KEY_ACTION = "action"
    private const val KEY_TOTAL = "blocked_total"
    private const val KEY_PAUSED_UNTIL = "paused_until"
    private const val COUNT_PREFIX = "count_"
    private const val HISTORY_DAYS = 14L
    private const val KEY_CONSENTED = "consented"
    private const val KEY_ONBOARDED = "onboarded"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The user accepted the accessibility disclosure; nothing is read before that. */
    fun hasConsented(context: Context) = prefs(context).getBoolean(KEY_CONSENTED, false)

    fun setConsented(context: Context) = prefs(context).edit().putBoolean(KEY_CONSENTED, true).apply()

    fun isOnboarded(context: Context) = prefs(context).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(context: Context) = prefs(context).edit().putBoolean(KEY_ONBOARDED, true).apply()

    // Version 1 stored the player switch under the bare target name.
    fun blocksPlayer(context: Context, target: Target): Boolean {
        val p = prefs(context)
        return p.getBoolean("${target.name}_block", p.getBoolean(target.name, true))
    }

    fun setBlocksPlayer(context: Context, target: Target, enabled: Boolean) =
        prefs(context).edit().putBoolean("${target.name}_block", enabled).apply()

    fun hidesTab(context: Context, target: Target) = prefs(context).getBoolean("${target.name}_hide_tab", true)

    fun setHidesTab(context: Context, target: Target, enabled: Boolean) =
        prefs(context).edit().putBoolean("${target.name}_hide_tab", enabled).apply()

    fun action(context: Context): BlockAction =
        BlockAction.valueOf(prefs(context).getString(KEY_ACTION, BlockAction.BACK.name)!!)

    fun setAction(context: Context, action: BlockAction) =
        prefs(context).edit().putString(KEY_ACTION, action.name).apply()

    /** Wall-clock millis until which blocking is paused, or 0. */
    fun pausedUntil(context: Context): Long = prefs(context).getLong(KEY_PAUSED_UNTIL, 0L)

    fun isPaused(context: Context) = pausedUntil(context) > System.currentTimeMillis()

    fun pauseFor(context: Context, minutes: Int) =
        prefs(context).edit().putLong(KEY_PAUSED_UNTIL, System.currentTimeMillis() + minutes * 60_000L).apply()

    fun resume(context: Context) = prefs(context).edit().remove(KEY_PAUSED_UNTIL).apply()

    fun blockedOn(context: Context, date: LocalDate) = prefs(context).getInt(COUNT_PREFIX + date, 0)

    fun blockedToday(context: Context) = blockedOn(context, LocalDate.now())

    fun blockedTotal(context: Context) = prefs(context).getInt(KEY_TOTAL, 0)

    /** Oldest first, ending today. */
    fun lastSevenDays(context: Context): List<Pair<LocalDate, Int>> {
        val today = LocalDate.now()
        return (6L downTo 0L).map { today.minusDays(it) }.map { it to blockedOn(context, it) }
    }

    fun recordBlock(context: Context) {
        val p = prefs(context)
        val today = LocalDate.now()
        val editor = p.edit()
            .putInt(COUNT_PREFIX + today, blockedOn(context, today) + 1)
            .putInt(KEY_TOTAL, blockedTotal(context) + 1)
        val cutoff = today.minusDays(HISTORY_DAYS)
        p.all.keys
            .filter { it.startsWith(COUNT_PREFIX) }
            .filter { key -> runCatching { LocalDate.parse(key.removePrefix(COUNT_PREFIX)) < cutoff }.getOrDefault(true) }
            .forEach { editor.remove(it) }
        editor.apply()
    }
}
