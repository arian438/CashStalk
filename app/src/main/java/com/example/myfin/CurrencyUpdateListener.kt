package com.example.myfin

interface CurrencyUpdateListener {
    fun onCurrencyChanged(currencySymbol: String)
}