package com.budgettracker.ui.screens.addtransaction

import com.budgettracker.domain.model.TransactionCategory
import com.budgettracker.domain.model.TransactionType
import java.time.LocalDateTime

data class AddTransactionUiState(
    val amount: String = "",
    val description: String = "",
    val selectedType: TransactionType = TransactionType.EXPENSE,
    val selectedCategory: TransactionCategory = TransactionCategory.OTHER_EXPENSE,
    val selectedDateTime: LocalDateTime = LocalDateTime.now(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)
