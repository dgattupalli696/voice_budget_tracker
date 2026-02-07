package com.budgettracker.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.ai.BudgetAnalyzer
import com.budgettracker.ai.TextCorrectionManager
import com.budgettracker.ai.TransactionParser
import com.budgettracker.data.repository.TransactionRepository
import com.budgettracker.domain.model.Transaction
import com.budgettracker.domain.model.TransactionType
import com.budgettracker.utils.CurrencyFormatter
import com.budgettracker.utils.FileLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val textCorrectionManager: TextCorrectionManager,
    private val budgetAnalyzer: BudgetAnalyzer,
    private val transactionRepository: TransactionRepository,
    private val transactionParser: TransactionParser
) : ViewModel() {
    
    companion object {
        private const val TAG = "ChatViewModel"
    }
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    init {
        observeModelState()
        refreshModelStatus()
    }
    
    private fun observeModelState() {
        // Observe model loading state from TextCorrectionManager
        viewModelScope.launch {
            textCorrectionManager.isLoading.collect { isLoading ->
                _uiState.update { it.copy(isModelLoading = isLoading) }
            }
        }
        viewModelScope.launch {
            textCorrectionManager.isModelReady.collect { isReady ->
                _uiState.update { it.copy(isModelAvailable = isReady) }
            }
        }
    }
    
    fun refreshModelStatus() {
        viewModelScope.launch {
            FileLogger.i(TAG, "refreshModelStatus() called")
            _uiState.update { it.copy(isLoading = true) }
            
            // Try to initialize if not already
            textCorrectionManager.initialize()
            
            val isAvailable = textCorrectionManager.isAvailable()
            FileLogger.i(TAG, "Model available: $isAvailable")
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    isModelAvailable = isAvailable
                ) 
            }
        }
    }
    
    fun checkModelStatus() {
        viewModelScope.launch {
            val isAvailable = textCorrectionManager.isAvailable()
            FileLogger.i(TAG, "checkModelStatus: $isAvailable")
            _uiState.update { it.copy(isModelAvailable = isAvailable) }
        }
    }
    
    fun initializeModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                textCorrectionManager.reinitialize()
                val isAvailable = textCorrectionManager.isAvailable()
                FileLogger.i(TAG, "initializeModel result: $isAvailable")
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        isModelAvailable = isAvailable,
                        error = if (!isAvailable) "Failed to initialize model" else null
                    ) 
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "initializeModel error", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = "Error: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        
        val userMessage = ChatMessage(content = message, isUser = true)
        _uiState.update { 
            it.copy(
                messages = it.messages + userMessage,
                isLoading = true,
                error = null,
                transactionAdded = false
            ) 
        }
        
        viewModelScope.launch {
            try {
                // Check if user is confirming a pending transaction
                if (_uiState.value.pendingTransaction != null && isConfirmation(message)) {
                    confirmTransaction()
                    return@launch
                }
                
                // Check if user is canceling a pending transaction
                if (_uiState.value.pendingTransaction != null && isCancellation(message)) {
                    cancelTransaction()
                    return@launch
                }
                
                // Check if this is a transaction command (add expense/income)
                if (isTransactionCommand(message)) {
                    val parsed = transactionParser.parse(message)
                    if (parsed != null) {
                        val pendingTxn = PendingTransaction(
                            amount = parsed.amount,
                            description = parsed.description,
                            category = parsed.category,
                            type = parsed.type,
                            dateTime = parsed.dateTime
                        )
                        val confirmMessage = buildTransactionConfirmation(pendingTxn)
                        val aiMessage = ChatMessage(content = confirmMessage, isUser = false)
                        _uiState.update { 
                            it.copy(
                                messages = it.messages + aiMessage,
                                isLoading = false,
                                pendingTransaction = pendingTxn
                            ) 
                        }
                        return@launch
                    }
                }
                
                // Check if this is a budget-related query
                val isBudgetQuery = isBudgetRelatedQuery(message)
                
                val prompt = if (isBudgetQuery) {
                    // Get budget context and ask LLM to analyze
                    val budgetContext = budgetAnalyzer.generateBudgetContext()
                    buildBudgetAnalysisPrompt(message, budgetContext)
                } else {
                    message
                }
                
                // Create a placeholder message for streaming
                val streamingMessage = ChatMessage(
                    content = "",
                    isUser = false,
                    isStreaming = true
                )
                _uiState.update { 
                    it.copy(
                        messages = it.messages + streamingMessage,
                        isLoading = false
                    ) 
                }
                
                // Stream the response
                val responseBuilder = StringBuilder()
                textCorrectionManager.chatStream(
                    message = prompt,
                    onToken = { token ->
                        responseBuilder.append(token)
                        // Update the last message with new tokens
                        _uiState.update { state ->
                            val messages = state.messages.toMutableList()
                            if (messages.isNotEmpty()) {
                                val lastIndex = messages.lastIndex
                                messages[lastIndex] = ChatMessage(
                                    content = responseBuilder.toString(),
                                    isUser = false,
                                    isStreaming = true
                                )
                            }
                            state.copy(messages = messages)
                        }
                    },
                    onComplete = {
                        // Mark streaming as complete
                        _uiState.update { state ->
                            val messages = state.messages.toMutableList()
                            if (messages.isNotEmpty()) {
                                val lastIndex = messages.lastIndex
                                messages[lastIndex] = ChatMessage(
                                    content = responseBuilder.toString(),
                                    isUser = false,
                                    isStreaming = false
                                )
                            }
                            state.copy(messages = messages)
                        }
                    }
                )
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    content = "Error: ${e.message ?: "Failed to get response"}",
                    isUser = false
                )
                _uiState.update { 
                    it.copy(
                        messages = it.messages + errorMessage,
                        isLoading = false,
                        error = e.message
                    ) 
                }
            }
        }
    }
    
    private fun isTransactionCommand(message: String): Boolean {
        val lower = message.lowercase()
        val transactionKeywords = listOf(
            "add expense", "add income", "spent", "bought", "paid", 
            "earned", "received", "got paid", "expense", "₹", "rs", "rupees"
        )
        // Check if it looks like a transaction command (has amount + action word)
        val hasAmount = Regex("""(\d+(?:\.\d+)?)""").containsMatchIn(message)
        val hasKeyword = transactionKeywords.any { lower.contains(it) }
        return hasAmount && hasKeyword
    }
    
    private fun buildTransactionConfirmation(transaction: PendingTransaction): String {
        val typeEmoji = if (transaction.type == TransactionType.INCOME) "💰" else "💸"
        val typeText = if (transaction.type == TransactionType.INCOME) "Income" else "Expense"
        val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
        
        return """$typeEmoji **Add $typeText?**

Amount: ${CurrencyFormatter.formatRupees(transaction.amount)}
Category: ${transaction.category.emoji} ${transaction.category.displayName}
Description: ${transaction.description}
Date: ${transaction.dateTime.format(dateFormatter)}

Reply **"yes"** to confirm or **"no"** to cancel."""
    }
    
    private fun isConfirmation(message: String): Boolean {
        val lower = message.lowercase().trim()
        return lower in listOf("yes", "y", "confirm", "ok", "okay", "sure", "add it", "save", "done")
    }
    
    private fun isCancellation(message: String): Boolean {
        val lower = message.lowercase().trim()
        return lower in listOf("no", "n", "cancel", "nope", "nevermind", "never mind", "don't", "dont")
    }
    
    private fun confirmTransaction() {
        val pending = _uiState.value.pendingTransaction ?: return
        
        viewModelScope.launch {
            try {
                val transaction = Transaction(
                    amount = pending.amount,
                    description = pending.description,
                    category = pending.category,
                    type = pending.type,
                    dateTime = pending.dateTime
                )
                
                transactionRepository.insertTransaction(transaction)
                
                val dateFormatter = DateTimeFormatter.ofPattern("MMM d")
                val dateStr = if (pending.dateTime.toLocalDate() == java.time.LocalDate.now()) {
                    "today"
                } else {
                    "on ${pending.dateTime.format(dateFormatter)}"
                }
                
                val successMessage = ChatMessage(
                    content = "✅ ${if (pending.type == TransactionType.INCOME) "Income" else "Expense"} of ${CurrencyFormatter.formatRupees(pending.amount)} added $dateStr!",
                    isUser = false
                )
                
                _uiState.update {
                    it.copy(
                        messages = it.messages + successMessage,
                        isLoading = false,
                        pendingTransaction = null,
                        transactionAdded = true
                    )
                }
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    content = "❌ Failed to add transaction: ${e.message}",
                    isUser = false
                )
                _uiState.update {
                    it.copy(
                        messages = it.messages + errorMessage,
                        isLoading = false,
                        pendingTransaction = null
                    )
                }
            }
        }
    }
    
    private fun cancelTransaction() {
        val cancelMessage = ChatMessage(
            content = "🚫 Transaction cancelled. Let me know if you want to add something else!",
            isUser = false
        )
        _uiState.update {
            it.copy(
                messages = it.messages + cancelMessage,
                isLoading = false,
                pendingTransaction = null
            )
        }
    }
    
    private fun isBudgetRelatedQuery(message: String): Boolean {
        val budgetKeywords = listOf(
            "budget", "expense", "income", "spend", "spending", "money",
            "balance", "week", "month", "year", "category", "save", "saving",
            "cost", "price", "transaction", "how much", "total", "analyze",
            "analysis", "insight", "summary", "report", "track", "tracking",
            "finance", "financial", "expenditure", "earnings", "salary",
            "food", "transport", "entertainment", "shopping", "utilities", "health"
        )
        val lowerMessage = message.lowercase()
        return budgetKeywords.any { lowerMessage.contains(it) }
    }
    
    private fun buildBudgetAnalysisPrompt(userQuery: String, budgetContext: String): String {
        return """You are a helpful budget assistant. Analyze the user's financial data and answer their question.

$budgetContext

User Question: $userQuery

Please provide a helpful, concise response based on the budget data above. Include specific numbers and insights where relevant. Keep the response under 200 words."""
    }
    
    fun clearChat() {
        _uiState.update { it.copy(messages = emptyList(), error = null) }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Don't close the manager here as it might be used by other components
    }
}
