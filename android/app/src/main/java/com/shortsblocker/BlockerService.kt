package com.shortsblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi

/**
 * Watches YouTube and Instagram: covers the Shorts/Reels tab in the bottom bar
 * and leaves the screen as soon as the Shorts/Reels player shows anyway.
 * Only view IDs, labels and bounds are read; nothing leaves the device.
 */
class BlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val tabCover by lazy { TabCover(this) }
    private val fakeBar by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) FakeTabBar(this) else null
    }
    private val notice by lazy { BlockNotice(this) }
    private var evaluateScheduled = false
    private var lastBlockAt = 0L
    private var sampling = false
    // Bar colour per target, sampled once each time the user enters the app.
    private val coverColors = mutableMapOf<Target, Int>()

    private val evaluateRunnable = Runnable {
        evaluateScheduled = false
        evaluate()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Finishes the tutorial: bring the user back from Settings straight away.
        if (!Prefs.isOnboarded(this)) {
            startActivity(
                Intent(this, OnboardingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            // A new screen: check right away instead of waiting out the throttle.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                handler.removeCallbacks(evaluateRunnable)
                evaluateScheduled = false
                schedule(0)
            }
            else -> schedule(EVENT_DELAY_MS)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideTab()
        fakeBar?.release()
        notice.hide()
        super.onDestroy()
    }

    // Events arrive in bursts while videos play; evaluate at most once per delay.
    private fun schedule(delayMs: Long) {
        if (evaluateScheduled) return
        evaluateScheduled = true
        handler.postDelayed(evaluateRunnable, delayMs)
    }

    private fun evaluate() {
        val root = rootInActiveWindow
        val target = root?.packageName?.toString()?.let(Target::forPackage)
        if (root == null || target == null || !Prefs.hasConsented(this) || Prefs.isPaused(this)) {
            hideTab()
            coverColors.clear()
            return
        }

        if (Prefs.blocksPlayer(this, target) && isPlayerShowing(root, target)) {
            hideTab()
            // Give the back action time to take effect, then make sure it did.
            if (SystemClock.uptimeMillis() - lastBlockAt >= COOLDOWN_MS) block(target)
            schedule(COOLDOWN_MS)
            return
        }

        val tab = if (Prefs.hidesTab(this, target)) findTab(root, target) else null
        val bar = fakeBar
        val layout = if (tab != null && bar != null) findBar(tab) else null
        if (tab == null) {
            hideTab()
        } else if (bar != null && layout != null) {
            // Redraw the bar without the tab.
            tabCover.hide()
            bar.update(layout, root.windowId)
        } else {
            // Older Android, or a bar we cannot take apart: cover just the tab.
            bar?.hide()
            coverTab(target, boundsOf(tab))
        }
        // An idle screen sends no events, so keep tracking the tab while it is hidden.
        if (tab != null) schedule(POLL_MS)
    }

    private fun hideTab() {
        tabCover.hide()
        fakeBar?.hide()
    }

    private fun block(target: Target) {
        lastBlockAt = SystemClock.uptimeMillis()
        val action = when (Prefs.action(this)) {
            BlockAction.BACK -> GLOBAL_ACTION_BACK
            BlockAction.HOME -> GLOBAL_ACTION_HOME
        }
        performGlobalAction(action)
        Prefs.recordBlock(this)
        notice.show(getString(target.noticeRes), getString(R.string.notice_count, Prefs.blockedToday(this)))
    }

    // Requiring the player to fill most of the screen avoids firing on small
    // previews, such as Shorts shelves in the regular YouTube feed.
    private fun isPlayerShowing(root: AccessibilityNodeInfo, target: Target): Boolean {
        val minHeight = resources.displayMetrics.heightPixels * PLAYER_MIN_SCREEN_FRACTION
        val bounds = Rect()
        return target.playerViewIds.any { id ->
            root.findAccessibilityNodeInfosByViewId("${target.packageName}:id/$id").any { node ->
                node.getBoundsInScreen(bounds)
                node.isVisibleToUser && bounds.height() >= minHeight
            }
        }
    }

    private fun findTab(root: AccessibilityNodeInfo, target: Target): AccessibilityNodeInfo? {
        val metrics = resources.displayMetrics
        val byId = target.tabViewIds.flatMap { root.findAccessibilityNodeInfosByViewId("${target.packageName}:id/$it") }
        // findAccessibilityNodeInfosByText matches substrings, so keep exact labels only.
        val byLabel = target.tabLabels.flatMap { label ->
            root.findAccessibilityNodeInfosByText(label).filter { node ->
                node.text?.toString()?.trim().equals(label, ignoreCase = true) ||
                    node.contentDescription?.toString()?.trim().equals(label, ignoreCase = true)
            }
        }
        val bounds = Rect()
        for (node in byId + byLabel) {
            val tab = clickableSelfOrAncestor(node) ?: continue
            tab.getBoundsInScreen(bounds)
            // Only a button in the bottom bar, not e.g. a "Shorts" shelf title in the feed.
            val inBottomBar = bounds.top > metrics.heightPixels * 0.7f &&
                bounds.height() < metrics.heightPixels * 0.15f &&
                bounds.width() < metrics.widthPixels / 2
            if (tab.isVisibleToUser && !bounds.isEmpty && inBottomBar && !keyboardOverlaps(bounds)) return tab
        }
        return null
    }

    /** Finds the tab's siblings in the bottom bar, walking up past wrapper views. */
    private fun findBar(tab: AccessibilityNodeInfo): BarLayout? {
        val tabBounds = boundsOf(tab)
        var container = tab.parent
        repeat(3) {
            val c = container ?: return null
            val tabs = tabsIn(c, tabBounds)
            if (tabs.size >= MIN_BAR_TABS && tabs.any { boundsOf(it) == tabBounds }) {
                val others = tabs.filter { boundsOf(it) != tabBounds }
                val bar = Rect(tabBounds)
                tabs.forEach { bar.union(boundsOf(it)) }
                // Take in the bar's own padding and divider when the container is just the bar.
                val containerBounds = boundsOf(c)
                if (containerBounds.height() <= bar.height() * 1.5f && containerBounds.contains(bar)) bar.set(containerBounds)
                return BarLayout(bar, others.map(::boundsOf), others)
            }
            container = c.parent
        }
        return null
    }

    // Clickable children, or grandchildren for tabs wrapped in a layout, in the tab's row.
    private fun tabsIn(container: AccessibilityNodeInfo, tabBounds: Rect): List<AccessibilityNodeInfo> =
        children(container)
            .flatMap { child -> if (child.isClickable) listOf(child) else children(child).filter { it.isClickable } }
            .filter { node ->
                val r = boundsOf(node)
                node.isVisibleToUser && !r.isEmpty && kotlin.math.abs(r.centerY() - tabBounds.centerY()) < tabBounds.height() / 2
            }
            .sortedBy { boundsOf(it).left }

    private fun children(node: AccessibilityNodeInfo) = (0 until node.childCount).mapNotNull(node::getChild)

    private fun boundsOf(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)

    private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(4) {
            val n = current ?: return null
            if (n.isClickable) return n
            current = n.parent
        }
        return null
    }

    // The cover sits above the keyboard, so it must never overlap one.
    private fun keyboardOverlaps(bounds: Rect): Boolean {
        val windowBounds = Rect()
        return windows.any { window ->
            window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                Rect.intersects(windowBounds.also(window::getBoundsInScreen), bounds)
        }
    }

    private fun coverTab(target: Target, bounds: Rect) {
        val known = coverColors[target]
        if (known != null || tabCover.isShowing || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            tabCover.show(bounds, known ?: target.fallbackBackground(isNight()))
            return
        }
        // Sample the bar colour before covering it, otherwise the cover samples itself.
        if (sampling) return
        sampling = true
        sampleColor(bounds.left + SAMPLE_INSET_PX, bounds.top + SAMPLE_INSET_PX) { color ->
            sampling = false
            coverColors[target] = color ?: target.fallbackBackground(isNight())
            schedule(0)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun sampleColor(x: Int, y: Int, onResult: (Int?) -> Unit) {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val buffer = result.hardwareBuffer
                val color = try {
                    val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                    val bitmap = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                    hardware?.recycle()
                    bitmap?.let {
                        val pixel = if (x in 0 until it.width && y in 0 until it.height) it.getPixel(x, y) else null
                        it.recycle()
                        pixel
                    }
                } finally {
                    buffer.close()
                }
                onResult(color)
            }

            override fun onFailure(errorCode: Int) = onResult(null)
        })
    }

    private fun isNight() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private companion object {
        const val EVENT_DELAY_MS = 60L
        const val POLL_MS = 300L
        const val COOLDOWN_MS = 1200L
        const val PLAYER_MIN_SCREEN_FRACTION = 0.6f
        const val SAMPLE_INSET_PX = 4
        const val MIN_BAR_TABS = 3
    }
}
