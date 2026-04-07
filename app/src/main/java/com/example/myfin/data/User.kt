package com.example.myfin.data

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    var id: String = "",
    var fio: String = "",
    var email: String = "",
    var currency: String = "Российский рубль",
    var currencySymbol: String = "₽",
    var darkMode: Boolean = false,
    var isDefault: Boolean = false,
    var language: String = "Русский",
    var notifications: Boolean = true,
    var orderIndex: Int = 0,
    var categories: Map<String, Any>? = null,
    var transactions: Map<String, Any>? = null
)