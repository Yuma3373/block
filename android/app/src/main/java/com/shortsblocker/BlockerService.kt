package com.shortsblocker

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Watches YouTube and Instagram and leaves the screen as soon as the Shorts or
 * Reels player is showing. Nothing is read beyond view IDs and their bounds.
 */
class BlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingPackage: String? = null
    private var checkScheduled = false
    private var lastBlockAt = 0L

    private val checkRunnable = Runnable {
        checkScheduled = false
        pendingPackage?.let(::checkAndBlock)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (Target.forPackage(pkg) == null) return
        pendingPackage = pkg
        // Content events arrive in bursts while videos play; check at most once per interval.
        if (!checkScheduled) {
            checkScheduled = true
            handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
    }

    private fun checkAndBlock(pkg: String) {
        val target = Target.forPackage(pkg) ?: return
        if (!Prefs.isEnabled(this, target)) return
        // Give the back action time to take effect before checking again.
        if (SystemClock.uptimeMillis() - lastBlockAt < COOLDOWN_MS) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg || !isPlayerShowing(root, target)) return

        lastBlockAt = SystemClock.uptimeMillis()
        val action = when (Prefs.action(this)) {
            BlockAction.BACK -> GLOBAL_ACTION_BACK
            BlockAction.HOME -> GLOBAL_ACTION_HOME
        }
        performGlobalAction(action)
        Prefs.recordBlock(this)
        Toast.makeText(this, target.toastRes, Toast.LENGTH_SHORT).show()
    }

    // Requiring the player to fill most of the screen avoids firing on small
    // previews, such as Shorts shelves in the regular YouTube feed.
    private fun isPlayerShowing(root: AccessibilityNodeInfo, target: Target): Boolean {
        val minHeight = resources.displayMetrics.heightPixels * MIN_SCREEN_FRACTION
        val bounds = Rect()
        return target.playerViewIds.any { id ->
            root.findAccessibilityNodeInfosByViewId("${target.packageName}:id/$id").any { node ->
                node.getBoundsInScreen(bounds)
                node.isVisibleToUser && bounds.height() >= minHeight
            }
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 250L
        const val COOLDOWN_MS = 1200L
        const val MIN_SCREEN_FRACTION = 0.6f
    }
}
