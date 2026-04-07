package com.example.myfin.data

data class Category(
    var id: String = "",
    var categoryName: String = "",
    var icon: String = "",
    var color: String = "",
    var monthlyLimit: Double = 0.0,
    var type: String = "",
    var isDefault: Boolean = false
)