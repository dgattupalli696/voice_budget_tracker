package com.budgettracker.ui.screens.accounts

import com.budgettracker.domain.model.Account

data class AccountBalance(
    val account: Account,
    val totalIncome: Double,
    val totalExpense: Double
) {
    val balance: Double
        get() = account.initialBalance + totalIncome - totalExpense
}

data class AccountsUiState(
    val balances: List<AccountBalance> = emptyList(),
    val isLoading: Boolean = false,
    val defaultAccountId: Long = -1L
) {
    val grandTotal: Double get() = balances.sumOf { it.balance }
}
