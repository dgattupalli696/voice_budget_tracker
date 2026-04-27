package com.budgettracker.ui.screens.setup

import com.budgettracker.domain.model.AccountType

data class AccountDraft(
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val last4: String = "",
    val initialBalance: String = "",
    val isDefault: Boolean = false
)

data class SetupUiState(
    val drafts: List<AccountDraft> = listOf(AccountDraft(isDefault = true)),
    val isSaving: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false
)
