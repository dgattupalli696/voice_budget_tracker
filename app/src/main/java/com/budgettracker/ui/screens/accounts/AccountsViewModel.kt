package com.budgettracker.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.data.local.PreferencesManager
import com.budgettracker.data.repository.AccountRepository
import com.budgettracker.data.repository.TransactionRepository
import com.budgettracker.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            // Single combined stream: any change to accounts or transactions
            // recomputes balances from one grouped totals query.
            combine(
                accountRepository.getAllAccounts(),
                transactionRepository.getAccountTotals()
            ) { accounts, totals ->
                val incomeByAccount = totals
                    .filter { it.type == TransactionType.INCOME }
                    .associate { it.accountId to it.total }
                val expenseByAccount = totals
                    .filter { it.type == TransactionType.EXPENSE }
                    .associate { it.accountId to it.total }

                val real = accounts.map { acc ->
                    AccountBalance(
                        account = acc,
                        totalIncome = incomeByAccount[acc.id] ?: 0.0,
                        totalExpense = expenseByAccount[acc.id] ?: 0.0
                    )
                }

                // Surface unassigned transactions (accountId IS NULL) as a
                // synthetic bucket so balances always sum correctly.
                val unassignedIncome = incomeByAccount[null] ?: 0.0
                val unassignedExpense = expenseByAccount[null] ?: 0.0
                val withUnassigned = if (unassignedIncome > 0.0 || unassignedExpense > 0.0) {
                    real + AccountBalance(
                        account = unassignedAccount(),
                        totalIncome = unassignedIncome,
                        totalExpense = unassignedExpense,
                        isUnassigned = true
                    )
                } else real

                AccountsUiState(
                    balances = withUnassigned,
                    isLoading = false,
                    defaultAccountId = preferencesManager.defaultAccountId
                )
            }.collect { newState -> _uiState.value = newState }
        }
    }

    fun setDefault(id: Long) {
        if (id == UNASSIGNED_PLACEHOLDER_ID) return
        viewModelScope.launch {
            accountRepository.setDefault(id)
            preferencesManager.defaultAccountId = id
            _uiState.update { it.copy(defaultAccountId = id) }
        }
    }
}
