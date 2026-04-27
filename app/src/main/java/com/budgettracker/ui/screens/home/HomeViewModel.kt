package com.budgettracker.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.data.repository.TransactionRepository
import com.budgettracker.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                repository.getAllTransactions(),
                repository.getTotalIncome(),
                repository.getTotalExpense()
            ) { transactions, totalIncome, totalExpense ->
                val income = totalIncome ?: 0.0
                val expense = totalExpense ?: 0.0
                HomeUiState(
                    transactions = transactions,
                    totalIncome = income,
                    totalExpense = expense,
                    balance = income - expense,
                    isLoading = false
                )
            }
            .catch { e ->
                _uiState.value = HomeUiState(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load data"
                )
            }
            .collect { state ->
                _uiState.value = state
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }
}
