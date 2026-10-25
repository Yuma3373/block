package com.shortsblocker

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

private fun overlayParams(width: Int, height: Int, flags: Int, format: Int) =
    WindowManager.LayoutParams(width, height, TYPE_ACCESSIBILITY_OVERLAY, flags, format).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Position in raw screen coordinates, matching AccessibilityNodeInfo bounds.
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

/** An opaque patch over the Shorts/Reels tab that also swallows taps on it. */
class TabCover(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val params = overlayParams(
        0, 0,
        FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE,
    ).apply { gravity = Gravity.TOP or Gravity.START }
    private var view: View? = null

    val isShowing get() = view != null

    fun show(bounds: Rect, color: Int) {
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width()
        params.height = bounds.height()
        val current = view
        if (current == null) {
            val v = View(service).apply {
                setBackgroundColor(color)
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            windowManager.addView(v, params)
            view = v
        } else {
            current.setBackgroundColor(color)
            windowManager.updateViewLayout(current, params)
        }
    }

    fun hide() {
        view?.let(windowManager::removeView)
        view = null
    }
}

/** A short, non-interactive card telling the user what just happened. */
class BlockNotice(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private val fadeOut = Runnable {
        val v = view ?: return@Runnable
        v.animate().alpha(0f).setDuration(FADE_MS).withEndAction { if (view === v) hide() }.start()
    }

    fun show(title: String, subtitle: String) {
        hide()
        val res = service.resources
        fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, res.displayMetrics)
        val fg = service.getColor(R.color.on_surface)
        val muted = service.getColor(R.color.muted)

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = dp(24f).toInt()
            setPadding(pad, pad, pad, pad)
            minimumWidth = dp(240f).toInt()
            background = GradientDrawable().apply {
                cornerRadius = dp(28f)
                setColor(service.getColor(R.color.surface))
                setStroke(dp(1f).toInt(), service.getColor(R.color.outline))
            }
            addView(ImageView(service).apply {
                setImageResource(R.drawable.ic_block)
                setColorFilter(service.getColor(R.color.accent))
            }, LinearLayout.LayoutParams(dp(44f).toInt(), dp(44f).toInt()))
            addView(TextView(service).apply {
                text = title
                setTextColor(fg)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                paint.isFakeBoldText = true
                setPadding(0, dp(12f).toInt(), 0, dp(4f).toInt())
            })
            addView(TextView(service).apply {
                text = subtitle
                setTextColor(muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            })
            alpha = 0f
            scaleX = 0.92f
            scaleY = 0.92f
        }

        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE or FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        windowManager.addView(card, params)
        view = card
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(FADE_MS).start()
        handler.postDelayed(fadeOut, VISIBLE_MS)
    }

    fun hide() {
        handler.removeCallbacks(fadeOut)
        view?.let(windowManager::removeView)
        view = null
    }

    private companion object {
        const val FADE_MS = 180L
        const val VISIBLE_MS = 1600L
    }
}
