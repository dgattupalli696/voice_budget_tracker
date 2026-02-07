package com.budgettracker.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.data.repository.TransactionRepository
import com.budgettracker.domain.model.TransactionCategory
import com.budgettracker.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getAllTransactions().collect { allTransactions ->
                val categoriesWithTransactions = TransactionCategory.entries
                    .map { category ->
                        val categoryTransactions = allTransactions.filter { it.category == category }
                        CategoryWithTransactions(
                            category = category,
                            transactions = categoryTransactions.sortedByDescending { it.dateTime },
                            totalAmount = categoryTransactions.sumOf { 
                                if (it.type == com.budgettracker.domain.model.TransactionType.EXPENSE) 
                                    -it.amount 
                                else 
                                    it.amount 
                            },
                            transactionCount = categoryTransactions.size
                        )
                    }
                    .filter { it.transactionCount > 0 }
                    .sortedByDescending { it.transactionCount }

                _uiState.update { state ->
                    state.copy(
                        categories = categoriesWithTransactions,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectCategory(category: TransactionCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }
}
