package com.budgettracker.ui.screens.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.ai.TransactionParser
import com.budgettracker.data.repository.TransactionRepository
import com.budgettracker.domain.model.Transaction
import com.budgettracker.domain.model.TransactionCategory
import com.budgettracker.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val transactionParser: TransactionParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()
    
    private var editingTransactionId: Long? = null

    fun loadTransaction(transactionId: Long) {
        viewModelScope.launch {
            try {
                val transaction = repository.getTransactionById(transactionId)
                if (transaction != null) {
                    editingTransactionId = transactionId
                    _uiState.update { state ->
                        state.copy(
                            amount = transaction.amount.toString(),
                            description = transaction.description,
                            selectedType = transaction.type,
                            selectedCategory = transaction.category,
                            selectedDateTime = transaction.dateTime
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(errorMessage = "Failed to load transaction: ${e.message}")
                }
            }
        }
    }

    fun processVoiceInput(voiceInput: String) {
        viewModelScope.launch {
            val parsed = transactionParser.parse(voiceInput)
            
            if (parsed != null) {
                _uiState.update { state ->
                    state.copy(
                        amount = parsed.amount.toString(),
                        description = parsed.description,
                        selectedType = parsed.type,
                        selectedCategory = parsed.category,
                        selectedDateTime = parsed.dateTime
                    )
                }
            }
        }
    }

    fun updateAmount(amount: String) {
        // Only allow valid decimal numbers
        if (amount.isEmpty() || amount.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _uiState.update { it.copy(amount = amount, errorMessage = null) }
        }
    }

    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description, errorMessage = null) }
    }

    fun updateType(type: TransactionType) {
        _uiState.update { state ->
            state.copy(
                selectedType = type,
                selectedCategory = getDefaultCategory(type),
                errorMessage = null
            )
        }
    }

    fun updateCategory(category: TransactionCategory) {
        _uiState.update { it.copy(selectedCategory = category, errorMessage = null) }
    }
    
    fun updateDate(date: LocalDate) {
        _uiState.update { state ->
            val currentTime = state.selectedDateTime.toLocalTime()
            state.copy(selectedDateTime = date.atTime(currentTime))
        }
    }
    
    fun updateTime(hour: Int, minute: Int) {
        _uiState.update { state ->
            val currentDate = state.selectedDateTime.toLocalDate()
            state.copy(selectedDateTime = currentDate.atTime(LocalTime.of(hour, minute)))
        }
    }

    fun saveTransaction() {
        val state = _uiState.value
        
        // Validation
        val amount = state.amount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid amount") }
            return
        }
        
        if (state.description.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a description") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val transaction = Transaction(
                    id = editingTransactionId ?: 0L,
                    amount = amount,
                    description = state.description.trim(),
                    category = state.selectedCategory,
                    type = state.selectedType,
                    dateTime = state.selectedDateTime
                )
                
                if (editingTransactionId != null) {
                    repository.updateTransaction(transaction)
                } else {
                    repository.insertTransaction(transaction)
                }
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        errorMessage = "Failed to save transaction: ${e.message}"
                    ) 
                }
            }
        }
    }

    private fun getDefaultCategory(type: TransactionType): TransactionCategory {
        return when (type) {
            TransactionType.INCOME -> TransactionCategory.OTHER_INCOME
            TransactionType.EXPENSE -> TransactionCategory.OTHER_EXPENSE
        }
    }

    fun getExpenseCategories(): List<TransactionCategory> = listOf(
        TransactionCategory.FOOD,
        TransactionCategory.TRANSPORT,
        TransactionCategory.SHOPPING,
        TransactionCategory.ENTERTAINMENT,
        TransactionCategory.BILLS,
        TransactionCategory.HEALTH,
        TransactionCategory.EDUCATION,
        TransactionCategory.OTHER_EXPENSE
    )

    fun getIncomeCategories(): List<TransactionCategory> = listOf(
        TransactionCategory.SALARY,
        TransactionCategory.FREELANCE,
        TransactionCategory.INVESTMENT,
        TransactionCategory.OTHER_INCOME
    )
}
