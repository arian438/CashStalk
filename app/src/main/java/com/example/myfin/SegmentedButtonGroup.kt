// Создайте файл SegmentedButtonGroup.kt
package com.example.myfin

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton

class SegmentedButtonGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var selectedPosition = 0
    private var listener: OnPositionChangedListener? = null

    interface OnPositionChangedListener {
        fun onPositionChanged(position: Int)
    }

    fun setOnPositionChangedListener(listener: OnPositionChangedListener) {
        this.listener = listener
    }

    init {
        orientation = HORIZONTAL
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        setupButtons()
    }

    private fun setupButtons() {
        for (i in 0 until childCount) {
            val button = getChildAt(i) as? MaterialButton ?: continue
            button.tag = i
            button.setOnClickListener {
                selectButton(i)
            }
        }
        selectButton(0) // Выбираем первую кнопку по умолчанию
    }

    private fun selectButton(position: Int) {
        if (position == selectedPosition) return

        for (i in 0 until childCount) {
            val button = getChildAt(i) as? MaterialButton ?: continue
            button.isChecked = (i == position)
        }

        selectedPosition = position
        listener?.onPositionChanged(position)
    }
}