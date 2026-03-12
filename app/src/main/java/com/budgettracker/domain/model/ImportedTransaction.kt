package com.budgettracker.domain.model

import java.time.LocalDateTime

data class ImportedTransaction(
    val amount: Double,
    val description: String,
    val category: TransactionCategory,
    val type: TransactionType,
    val dateTime: LocalDateTime,
    val rawText: String = "",
    val isDuplicate: Boolean = false,
    val isSelected: Boolean = true,
    val duplicateMatchId: Long? = null
)
