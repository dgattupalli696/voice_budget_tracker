package com.budgettracker.ui.screens.reports

import com.budgettracker.domain.model.Transaction
import com.budgettracker.domain.model.TransactionCategory

data class ReportsUiState(
    val selectedPeriod: ReportPeriod = ReportPeriod.MONTHLY,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val balance: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    val categoryBreakdown: List<CategorySummary> = emptyList(),
    val periodLabel: String = "",
    val canGoNext: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

enum class ReportPeriod(val displayName: String) {
    WEEKLY("Week"),
    MONTHLY("Month"),
    YEARLY("Year")
}

data class CategorySummary(
    val category: TransactionCategory,
    val total: Double,
    val percentage: Float,
    val transactionCount: Int
)
