package com.example.myfin.data

data class CategoryStat(
    val categoryName: String,
    val amount: Double,
    val percentage: Double,
    val color: String,
    val currencySymbol: String = "₽"
)