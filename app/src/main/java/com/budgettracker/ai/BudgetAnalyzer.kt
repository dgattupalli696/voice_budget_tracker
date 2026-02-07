package com.budgettracker.ai

import com.budgettracker.data.repository.TransactionRepository
import com.budgettracker.domain.model.Transaction
import com.budgettracker.domain.model.TransactionType
import com.budgettracker.utils.CurrencyFormatter
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetAnalyzer @Inject constructor(
    private val repository: TransactionRepository
) {
    
    suspend fun generateBudgetContext(): String {
        val allTransactions = repository.getAllTransactions().first()
        
        if (allTransactions.isEmpty()) {
            return "No transaction data available yet."
        }
        
        val today = LocalDate.now()
        
        // Get this week's data
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeklyTransactions = allTransactions.filter { 
            it.dateTime.toLocalDate() >= weekStart 
        }
        
        // Get this month's data
        val monthStart = today.withDayOfMonth(1)
        val monthlyTransactions = allTransactions.filter { 
            it.dateTime.toLocalDate() >= monthStart 
        }
        
        return buildString {
            appendLine("=== BUDGET DATA SUMMARY ===")
            appendLine()
            
            // Weekly Summary
            appendLine("📅 THIS WEEK (${weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} - Today):")
            appendWeeklySummary(weeklyTransactions)
            appendLine()
            
            // Monthly Summary  
            appendLine("📆 THIS MONTH (${today.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}):")
            appendMonthlySummary(monthlyTransactions)
            appendLine()
            
            // Category Breakdown for the month
            appendLine("📊 MONTHLY CATEGORY BREAKDOWN:")
            appendCategoryBreakdown(monthlyTransactions)
            appendLine()
            
            // Recent Transactions
            appendLine("🕐 RECENT TRANSACTIONS (Last 10):")
            appendRecentTransactions(allTransactions.take(10))
            appendLine()
            
            // Overall Stats
            appendLine("📈 OVERALL STATISTICS:")
            appendOverallStats(allTransactions)
        }
    }
    
    private fun StringBuilder.appendWeeklySummary(transactions: List<Transaction>) {
        val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val balance = income - expense
        
        appendLine("  Income: ${CurrencyFormatter.formatRupees(income)}")
        appendLine("  Expense: ${CurrencyFormatter.formatRupees(expense)}")
        appendLine("  Net: ${CurrencyFormatter.formatRupees(balance)}")
        appendLine("  Transactions: ${transactions.size}")
    }
    
    private fun StringBuilder.appendMonthlySummary(transactions: List<Transaction>) {
        val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val balance = income - expense
        
        appendLine("  Income: ${CurrencyFormatter.formatRupees(income)}")
        appendLine("  Expense: ${CurrencyFormatter.formatRupees(expense)}")
        appendLine("  Net: ${CurrencyFormatter.formatRupees(balance)}")
        appendLine("  Transactions: ${transactions.size}")
        
        if (expense > 0) {
            val dailyAvg = expense / LocalDate.now().dayOfMonth
            appendLine("  Avg Daily Expense: ${CurrencyFormatter.formatRupees(dailyAvg)}")
        }
    }
    
    private fun StringBuilder.appendCategoryBreakdown(transactions: List<Transaction>) {
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
        val totalExpense = expenses.sumOf { it.amount }
        
        if (expenses.isEmpty()) {
            appendLine("  No expenses this month")
            return
        }
        
        expenses.groupBy { it.category }
            .map { (category, txns) -> 
                category to txns.sumOf { it.amount }
            }
            .sortedByDescending { it.second }
            .forEach { (category, amount) ->
                val percentage = if (totalExpense > 0) (amount / totalExpense * 100).toInt() else 0
                appendLine("  ${category.emoji} ${category.displayName}: ${CurrencyFormatter.formatRupees(amount)} ($percentage%)")
            }
    }
    
    private fun StringBuilder.appendRecentTransactions(transactions: List<Transaction>) {
        if (transactions.isEmpty()) {
            appendLine("  No recent transactions")
            return
        }
        
        val dateFormatter = DateTimeFormatter.ofPattern("MMM d")
        transactions.forEach { txn ->
            val sign = if (txn.type == TransactionType.INCOME) "+" else "-"
            val date = txn.dateTime.format(dateFormatter)
            appendLine("  $date | $sign${CurrencyFormatter.formatRupees(txn.amount)} | ${txn.category.displayName} | ${txn.description}")
        }
    }
    
    private fun StringBuilder.appendOverallStats(transactions: List<Transaction>) {
        val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        
        appendLine("  Total Income (All Time): ${CurrencyFormatter.formatRupees(totalIncome)}")
        appendLine("  Total Expense (All Time): ${CurrencyFormatter.formatRupees(totalExpense)}")
        appendLine("  Total Transactions: ${transactions.size}")
        
        // Find highest expense category
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
        if (expenses.isNotEmpty()) {
            val topCategory = expenses.groupBy { it.category }
                .maxByOrNull { it.value.sumOf { txn -> txn.amount } }
            topCategory?.let {
                appendLine("  Highest Spending Category: ${it.key.displayName}")
            }
        }
    }
    
    suspend fun getWeeklySummaryText(): String {
        val allTransactions = repository.getAllTransactions().first()
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeklyTransactions = allTransactions.filter { 
            it.dateTime.toLocalDate() >= weekStart 
        }
        
        return buildString {
            appendLine("Weekly Summary (${weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} - Today):")
            appendWeeklySummary(weeklyTransactions)
        }
    }
    
    suspend fun getMonthlySummaryText(): String {
        val allTransactions = repository.getAllTransactions().first()
        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1)
        val monthlyTransactions = allTransactions.filter { 
            it.dateTime.toLocalDate() >= monthStart 
        }
        
        return buildString {
            appendLine("Monthly Summary (${today.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}):")
            appendMonthlySummary(monthlyTransactions)
            appendLine()
            appendLine("Category Breakdown:")
            appendCategoryBreakdown(monthlyTransactions)
        }
    }
}
