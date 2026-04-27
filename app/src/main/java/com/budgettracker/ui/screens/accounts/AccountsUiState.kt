package com.budgettracker.ui.screens.accounts

import com.budgettracker.domain.model.Account
import com.budgettracker.domain.model.AccountType

data class AccountBalance(
    val account: Account,
    val totalIncome: Double,
    val totalExpense: Double,
    /** True for the synthetic "Unassigned" bucket (not a real saved account). */
    val isUnassigned: Boolean = false
) {
    val balance: Double
        get() = account.initialBalance + totalIncome - totalExpense
}

internal val UNASSIGNED_PLACEHOLDER_ID = -999L

internal fun unassignedAccount() = Account(
    id = UNASSIGNED_PLACEHOLDER_ID,
    name = "Unassigned",
    type = AccountType.CASH,
    last4 = "",
    initialBalance = 0.0,
    isDefault = false
)

data class AccountsUiState(
    val balances: List<AccountBalance> = emptyList(),
    val isLoading: Boolean = false,
    val defaultAccountId: Long = -1L
) {
    val grandTotal: Double get() = balances.sumOf { it.balance }
}
