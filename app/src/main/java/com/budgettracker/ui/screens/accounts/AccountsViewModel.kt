package com.budgettracker.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.data.local.PreferencesManager
import com.budgettracker.data.local.TransactionDao
import com.budgettracker.data.repository.AccountRepository
import com.budgettracker.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionDao: TransactionDao,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            accountRepository.getAllAccounts().collectLatest { accounts ->
                _uiState.update { it.copy(isLoading = true) }
                val balances = accounts.map { acc ->
                    val income = transactionDao.getTotalForAccount(acc.id, TransactionType.INCOME)
                    val expense = transactionDao.getTotalForAccount(acc.id, TransactionType.EXPENSE)
                    AccountBalance(account = acc, totalIncome = income, totalExpense = expense)
                }
                _uiState.update {
                    it.copy(
                        balances = balances,
                        isLoading = false,
                        defaultAccountId = preferencesManager.defaultAccountId
                    )
                }
            }
        }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch {
            accountRepository.setDefault(id)
            preferencesManager.defaultAccountId = id
            _uiState.update { it.copy(defaultAccountId = id) }
        }
    }
}
