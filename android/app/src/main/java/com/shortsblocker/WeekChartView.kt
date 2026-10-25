package com.shortsblocker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Bar chart of blocks per day, today highlighted. */
class WeekChartView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var days: List<Pair<LocalDate, Int>> = emptyList()
    private val bar = RectF()

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
    }

    fun setData(data: List<Pair<LocalDate, Int>>) {
        days = data
        contentDescription = context.getString(
            R.string.chart_description,
            data.joinToString { (date, count) -> "${label(date)} $count" },
        )
        invalidate()
    }

    private fun label(date: LocalDate) = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return
        val today = LocalDate.now()
        val labelArea = sp(20f)
        val valueArea = sp(18f)
        val chartHeight = height - labelArea - valueArea
        val max = maxOf(days.maxOf { it.second }, 1)
        val slot = width.toFloat() / days.size
        val barWidth = minOf(slot * 0.55f, dp(28f))
        val radius = dp(6f)

        days.forEachIndexed { i, (date, count) ->
            val isToday = date == today
            val cx = slot * (i + 0.5f)
            val barHeight = maxOf(chartHeight * count / max, dp(4f))
            val bottom = valueArea + chartHeight
            bar.set(cx - barWidth / 2, bottom - barHeight, cx + barWidth / 2, bottom)
            barPaint.color = context.getColor(if (isToday) R.color.accent else R.color.chart_bar)
            canvas.drawRoundRect(bar, radius, radius, barPaint)

            textPaint.isFakeBoldText = isToday
            if (count > 0) {
                textPaint.color = context.getColor(if (isToday) R.color.accent else R.color.muted)
                canvas.drawText(count.toString(), cx, bar.top - dp(4f), textPaint)
            }
            textPaint.color = context.getColor(if (isToday) R.color.on_surface else R.color.muted)
            canvas.drawText(label(date), cx, height - dp(4f), textPaint)
        }
    }
}
