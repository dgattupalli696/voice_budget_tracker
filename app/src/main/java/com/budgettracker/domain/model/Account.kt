package com.budgettracker.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val last4: String = "",
    val initialBalance: Double = 0.0,
    val isDefault: Boolean = false
)

enum class AccountType(val displayName: String, val emoji: String) {
    BANK("Bank Account", "🏦"),
    DEBIT_CARD("Debit Card", "💳"),
    CREDIT_CARD("Credit Card", "💳"),
    CASH("Cash", "💵")
}
