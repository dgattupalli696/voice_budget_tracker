package com.budgettracker.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyFormatter {
    private val indianFormat = DecimalFormat("#,##,##0.00", DecimalFormatSymbols(Locale("en", "IN")))
    
    fun formatRupees(amount: Double): String {
        return "₹${indianFormat.format(amount)}"
    }
    
    fun formatRupeesWithSign(amount: Double, isIncome: Boolean): String {
        val sign = if (isIncome) "+" else "-"
        return "$sign₹${indianFormat.format(amount)}"
    }
}
