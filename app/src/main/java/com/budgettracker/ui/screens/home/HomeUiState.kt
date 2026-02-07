package com.budgettracker.ui.screens.home

import com.budgettracker.domain.model.Transaction

data class HomeUiState(
    val transactions: List<Transaction> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val balance: Double = 0.0,
    val isLoading: Boolean = true
)
