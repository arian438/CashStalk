package com.example.myfin.adapter

import com.example.myfin.R
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class ColorAdapter(
    private val colors: List<String>,
    private val onColorSelected: (String) -> Unit
) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

    private var selectedPosition = 0

    inner class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorView: View = itemView.findViewById(R.id.colorView)
        val selectionIndicator: View = itemView.findViewById(R.id.selectionIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_color, parent, false)
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        val colorHex = colors[position]
        holder.colorView.setBackgroundColor(Color.parseColor(colorHex))

        // Показываем выделение выбранного цвета
        holder.selectionIndicator.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                selectedPosition = adapterPosition
                notifyDataSetChanged()
                onColorSelected(colors[adapterPosition])
            }
        }
    }

    override fun getItemCount(): Int = colors.size

    fun getSelectedColor(): String = if (colors.isNotEmpty()) colors[selectedPosition] else "#4ECDC4"

    fun selectItem(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
        if (colors.isNotEmpty()) {
            onColorSelected(colors[position])
        }
    }
}