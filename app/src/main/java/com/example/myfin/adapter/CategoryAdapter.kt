package com.example.myfin.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myfin.R
import com.example.myfin.data.Category
import com.google.android.material.card.MaterialCardView

class CategoryAdapter(
    private var categories: List<Category>,
    private val onItemClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.categoryCard)
        val iconView: TextView = itemView.findViewById(R.id.categoryIcon)
        val iconContainer: View = itemView.findViewById(R.id.iconContainer)
        val titleView: TextView = itemView.findViewById(R.id.categoryTitle)
        val typeView: TextView = itemView.findViewById(R.id.categoryType)
        val limitView: TextView = itemView.findViewById(R.id.categoryLimit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]

        // Иконка
        holder.iconView.text = category.icon.ifEmpty {
            if (category.type == "expense") "💸" else "💰"
        }

        // Цвет фона иконки
        try {
            val color = if (category.color.isNotEmpty()) {
                Color.parseColor(category.color)
            } else {
                if (category.type == "expense") Color.parseColor("#4ECDC4") else Color.parseColor("#118AB2")
            }
            holder.iconContainer.setBackgroundColor(color)
        } catch (e: Exception) {
            holder.iconContainer.setBackgroundColor(if (category.type == "expense") Color.parseColor("#4ECDC4") else Color.parseColor("#118AB2"))
        }

        // Название
        holder.titleView.text = category.categoryName

        // Тип
        val typeText = if (category.type == "expense") "Расход" else "Доход"
        holder.typeView.text = typeText

        // Цвет текста типа
        if (category.type == "expense") {
            holder.typeView.setTextColor(Color.parseColor("#F44336"))
        } else {
            holder.typeView.setTextColor(Color.parseColor("#4CAF50"))
        }

        // Лимит (только для расходов)
        if (category.type == "expense" && category.monthlyLimit > 0) {
            holder.limitView.visibility = View.VISIBLE
            holder.limitView.text = "Лимит: ${String.format("%,.0f", category.monthlyLimit)} ₽"
            holder.limitView.setTextColor(Color.parseColor("#10B981"))
        } else {
            holder.limitView.visibility = View.GONE
        }

        // Для стандартных категорий меняем внешний вид (добавляем обводку)
        if (category.isDefault) {
            holder.cardView.strokeWidth = 2
            holder.cardView.strokeColor = Color.parseColor("#3B82F6")
        } else {
            holder.cardView.strokeWidth = 0
        }

        // Клик
        holder.cardView.setOnClickListener {
            onItemClick(category)
        }
    }

    override fun getItemCount(): Int = categories.size

    fun updateData(newCategories: List<Category>) {
        categories = newCategories
        notifyDataSetChanged()
    }
}