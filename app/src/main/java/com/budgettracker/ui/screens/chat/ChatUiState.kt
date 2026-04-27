package com.budgettracker.ui.screens.chat

import com.budgettracker.domain.model.TransactionCategory
import com.budgettracker.domain.model.TransactionType
import java.time.LocalDateTime

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false  // True while tokens are still being streamed
)

data class PendingTransaction(
    val amount: Double,
    val description: String,
    val category: TransactionCategory,
    val type: TransactionType,
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val accountId: Long? = null
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isModelLoading: Boolean = false,
    val isModelAvailable: Boolean = false,
    val error: String? = null,
    val pendingTransaction: PendingTransaction? = null,
    val transactionAdded: Boolean = false
)
