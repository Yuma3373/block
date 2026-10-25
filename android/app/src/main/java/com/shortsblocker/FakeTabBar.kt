package com.shortsblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi

/** The bottom bar of the target app: its bounds and every tab except the hidden one. */
class BarLayout(val bar: Rect, val tabs: List<Rect>, val tabNodes: List<AccessibilityNodeInfo>) {
    fun sameGeometry(other: BarLayout?) = other != null && bar == other.bar && tabs == other.tabs
}

/**
 * Redraws the app's bottom bar without the Shorts/Reels tab: the remaining
 * tabs are cut from a screenshot of the app window and spread evenly, and
 * taps are forwarded to the real tabs underneath.
 *
 * Window screenshots (API 34) exclude this overlay, so the bar can be
 * refreshed while it is covered.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class FakeTabBar(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val view = BarView(service, ::onTap)
    private val params = WindowManager.LayoutParams(
        0, 0,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            // Needed to draw the hardware-backed screenshot.
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.OPAQUE,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    private var attached = false
    private var layout: BarLayout? = null
    private var windowId = -1
    private var capturing = false
    private var lastCaptureAt = 0L

    val isShowing get() = attached

    fun update(newLayout: BarLayout, appWindowId: Int) {
        val moved = !newLayout.sameGeometry(layout) || appWindowId != windowId
        layout = newLayout
        windowId = appWindowId
        if (moved || SystemClock.uptimeMillis() - lastCaptureAt > REFRESH_MS) capture()
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        layout = null
        if (attached) windowManager.removeView(view)
        attached = false
        view.clear()
    }

    private fun capture() {
        val captured = layout ?: return
        if (capturing) return
        val origin = windowOrigin(windowId) ?: return
        capturing = true
        lastCaptureAt = SystemClock.uptimeMillis()
        service.takeScreenshotOfWindow(windowId, service.mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                capturing = false
                val buffer = result.hardwareBuffer
                val frame = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                buffer.close()
                // Hidden while the screenshot was being taken.
                if (frame == null || layout == null) {
                    frame?.recycle()
                    return
                }
                show(captured, frame, origin)
            }

            override fun onFailure(errorCode: Int) {
                capturing = false
            }
        })
    }

    private fun show(captured: BarLayout, frame: Bitmap, origin: Point) {
        view.setFrame(captured, frame, origin)
        params.x = captured.bar.left
        params.y = captured.bar.top
        params.width = captured.bar.width()
        params.height = captured.bar.height()
        if (attached) {
            windowManager.updateViewLayout(view, params)
        } else {
            windowManager.addView(view, params)
            attached = true
        }
    }

    private fun windowOrigin(id: Int): Point? {
        val window = service.windows.firstOrNull { it.id == id } ?: return null
        val bounds = Rect().also(window::getBoundsInScreen)
        return Point(bounds.left, bounds.top)
    }

    private fun onTap(slot: Int) {
        layout?.tabNodes?.getOrNull(slot)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        // Pick up the new selection state once the app has redrawn.
        for (delay in TAP_REFRESH_DELAYS_MS) handler.postDelayed(::capture, delay)
    }

    private companion object {
        const val REFRESH_MS = 1500L
        val TAP_REFRESH_DELAYS_MS = listOf(200L, 600L)
    }
}

@SuppressLint("ViewConstructor")
private class BarView(context: Context, private val onTap: (Int) -> Unit) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = Rect()
    private var layout: BarLayout? = null
    private var frame: Bitmap? = null
    private var origin = Point()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setFrame(newLayout: BarLayout, newFrame: Bitmap, newOrigin: Point) {
        val old = frame
        layout = newLayout
        frame = newFrame
        origin = newOrigin
        invalidate()
        // Freed after the next draw has switched to the new frame.
        if (old != null) post { old.recycle() }
    }

    fun clear() {
        frame?.recycle()
        frame = null
        layout = null
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout ?: return
        val f = frame ?: return
        if (l.tabs.isEmpty()) return

        // Background: stretch one pixel column from the bar's left edge, which
        // keeps dividers and gradients of the original bar.
        val column = l.bar.left + 1 - origin.x
        src.set(column, l.bar.top - origin.y, column + 1, l.bar.bottom - origin.y)
        dst.set(0, 0, width, height)
        canvas.drawBitmap(f, src, dst, paint)

        val slot = width / l.tabs.size
        l.tabs.forEachIndexed { i, tab ->
            src.set(tab)
            src.offset(-origin.x, -origin.y)
            val left = slot * i + (slot - tab.width()) / 2
            dst.set(left, tab.top - l.bar.top, left + tab.width(), tab.bottom - l.bar.top)
            canvas.drawBitmap(f, src, dst, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val l = layout ?: return true
        if (event.actionMasked == MotionEvent.ACTION_UP && l.tabs.isNotEmpty() && width > 0) {
            val slot = (event.x / (width.toFloat() / l.tabs.size)).toInt().coerceIn(0, l.tabs.size - 1)
            onTap(slot)
        }
        return true
    }
}
