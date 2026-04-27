package com.budgettracker.data.local

import com.budgettracker.domain.model.TransactionType

/**
 * Row shape returned by aggregated per-account totals queries.
 * `accountId` is null for transactions not tied to an account.
 */
data class AccountTotalRow(
    val accountId: Long?,
    val type: TransactionType,
    val total: Double
)
