package com.budgettracker.ui.screens.categories

import com.budgettracker.domain.model.TransactionCategory
import com.budgettracker.domain.model.Transaction

data class CategoriesUiState(
    val categories: List<CategoryWithTransactions> = emptyList(),
    val selectedCategory: TransactionCategory? = null,
    val isLoading: Boolean = true
)

data class CategoryWithTransactions(
    val category: TransactionCategory,
    val transactions: List<Transaction>,
    val totalAmount: Double,
    val transactionCount: Int
)
