package com.example.myfin

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils

class CategoryDialogHelper(
    private val nameEditText: EditText,
    private val iconEditText: EditText,
    private val colorEditText: EditText,
    private val previewLayout: LinearLayout,
    private val previewIcon: TextView,
    private val previewName: TextView
) {

    init {
        setupTextWatchers()
        updatePreview()
    }

    private fun setupTextWatchers() {
        nameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePreview()
            }
        })

        iconEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePreview()
            }
        })

        colorEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePreview()
            }
        })
    }

    private fun updatePreview() {
        val name = nameEditText.text.toString().trim()
        val icon = iconEditText.text.toString().trim().ifEmpty { "💸" }
        val colorText = colorEditText.text.toString().trim().ifEmpty { "#4ECDC4" }

        // Обновляем название
        previewName.text = if (name.isNotEmpty()) name else "Название категории"

        // Обновляем иконку
        previewIcon.text = icon

        // Обновляем цвет
        try {
            val color = Color.parseColor(colorText)
            previewIcon.setBackgroundColor(color)

            // Делаем цвет фона немного светлее для контраста
            val lighterColor = ColorUtils.blendARGB(color, Color.WHITE, 0.2f)
            previewLayout.setBackgroundColor(lighterColor)
        } catch (e: Exception) {
            // Если цвет некорректный, используем цвет по умолчанию
            previewIcon.setBackgroundColor(Color.parseColor("#4ECDC4"))
            previewLayout.setBackgroundColor(Color.parseColor("#F7F8FA"))
        }
    }
}