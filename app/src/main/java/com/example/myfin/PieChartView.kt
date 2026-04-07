package com.example.myfin

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.myfin.data.CategoryStat

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var chartData: List<CategoryStat> = emptyList()
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    fun setData(data: List<CategoryStat>) {
        this.chartData = data
        invalidate()
        requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = minOf(w, h) * 0.35f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (chartData.isEmpty()) {
            // Рисуем пустой круг
            paint.color = Color.parseColor("#E0E0E0")
            canvas.drawCircle(centerX, centerY, radius, paint)

            // Рисуем текст "Нет данных"
            textPaint.color = Color.parseColor("#666666")
            textPaint.textSize = 36f
            canvas.drawText("Нет данных", centerX, centerY, textPaint)
            return
        }

        var startAngle = 0f

        // Рисуем сектора
        chartData.forEachIndexed { index, stat ->
            val sweepAngle = (stat.percentage * 3.6f).toFloat() // 100% = 360 градусов

            try {
                paint.color = Color.parseColor(stat.color)
            } catch (e: Exception) {
                // Если цвет не удалось распарсить, используем цвет по умолчанию
                paint.color = when (index % 5) {
                    0 -> Color.parseColor("#FF6B6B")
                    1 -> Color.parseColor("#4ECDC4")
                    2 -> Color.parseColor("#FFD166")
                    3 -> Color.parseColor("#118AB2")
                    4 -> Color.parseColor("#EF476F")
                    else -> Color.parseColor("#7209B7")
                }
            }

            canvas.drawArc(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius,
                startAngle,
                sweepAngle,
                true,
                paint
            )

            startAngle += sweepAngle
        }

        // Рисуем маленький белый круг в центре для эффекта "пончика"
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, radius * 0.5f, paint)

        // Рисуем общую сумму в центре
        val total = chartData.sumOf { it.amount }
        textPaint.color = Color.BLACK
        textPaint.textSize = 32f
        val currencySymbol = if (chartData.isNotEmpty()) chartData[0].currencySymbol else "₽"
        canvas.drawText(String.format("%,.0f %s", total, currencySymbol), centerX, centerY, textPaint)
    }
}