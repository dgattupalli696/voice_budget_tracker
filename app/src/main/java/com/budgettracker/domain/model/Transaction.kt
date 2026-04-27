package com.budgettracker.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val description: String,
    val category: TransactionCategory,
    val type: TransactionType,
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val accountId: Long? = null
)

enum class TransactionType {
    INCOME,
    EXPENSE
}

enum class TransactionCategory(val displayName: String, val emoji: String) {
    // Income categories
    SALARY("Salary", "💰"),
    FREELANCE("Freelance", "💻"),
    INVESTMENT("Investment", "📈"),
    OTHER_INCOME("Other Income", "💵"),
    
    // Expense categories
    FOOD("Food", "🍔"),
    TRANSPORT("Transport", "🚗"),
    SHOPPING("Shopping", "🛒"),
    ENTERTAINMENT("Entertainment", "🎬"),
    BILLS("Bills", "📄"),
    HEALTH("Health", "🏥"),
    EDUCATION("Education", "📚"),
    OTHER_EXPENSE("Other Expense", "📦")
}
