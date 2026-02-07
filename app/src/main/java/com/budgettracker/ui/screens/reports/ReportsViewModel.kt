package com.budgettracker.ui.screens.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.data.repository.TransactionRepository
import com.budgettracker.domain.model.Transaction
import com.budgettracker.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()
    
    // Current reference date for navigation
    private var currentReferenceDate: LocalDate = LocalDate.now()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getAllTransactions().collect { allTransactions ->
                updateReportData(allTransactions)
            }
        }
    }

    fun selectPeriod(period: ReportPeriod) {
        currentReferenceDate = LocalDate.now() // Reset to current date when changing period
        _uiState.update { it.copy(selectedPeriod = period) }
        viewModelScope.launch {
            repository.getAllTransactions().collect { allTransactions ->
                updateReportData(allTransactions)
            }
        }
    }
    
    fun goToPrevious() {
        val period = _uiState.value.selectedPeriod
        currentReferenceDate = when (period) {
            ReportPeriod.WEEKLY -> currentReferenceDate.minusWeeks(1)
            ReportPeriod.MONTHLY -> currentReferenceDate.minusMonths(1)
            ReportPeriod.YEARLY -> currentReferenceDate.minusYears(1)
        }
        viewModelScope.launch {
            repository.getAllTransactions().collect { allTransactions ->
                updateReportData(allTransactions)
            }
        }
    }
    
    fun goToNext() {
        val period = _uiState.value.selectedPeriod
        val today = LocalDate.now()
        val nextDate = when (period) {
            ReportPeriod.WEEKLY -> currentReferenceDate.plusWeeks(1)
            ReportPeriod.MONTHLY -> currentReferenceDate.plusMonths(1)
            ReportPeriod.YEARLY -> currentReferenceDate.plusYears(1)
        }
        
        // Only allow going forward if not exceeding current period
        if (!nextDate.isAfter(today)) {
            currentReferenceDate = nextDate
            viewModelScope.launch {
                repository.getAllTransactions().collect { allTransactions ->
                    updateReportData(allTransactions)
                }
            }
        }
    }

    private fun updateReportData(allTransactions: List<Transaction>) {
        val today = LocalDate.now()
        val period = _uiState.value.selectedPeriod
        
        val (startDate, endDate, periodLabel) = when (period) {
            ReportPeriod.WEEKLY -> {
                val weekStart = currentReferenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekEnd = weekStart.plusDays(6)
                val weekNumber = currentReferenceDate.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())
                val monthFormatter = DateTimeFormatter.ofPattern("MMM d")
                Triple(
                    weekStart.atStartOfDay(),
                    weekEnd.atTime(23, 59, 59),
                    "${weekStart.format(monthFormatter)} - ${weekEnd.format(monthFormatter)}, ${weekStart.year}"
                )
            }
            ReportPeriod.MONTHLY -> {
                val monthStart = currentReferenceDate.withDayOfMonth(1)
                val monthEnd = currentReferenceDate.with(TemporalAdjusters.lastDayOfMonth())
                val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
                Triple(
                    monthStart.atStartOfDay(),
                    monthEnd.atTime(23, 59, 59),
                    currentReferenceDate.format(monthFormatter)
                )
            }
            ReportPeriod.YEARLY -> {
                val yearStart = currentReferenceDate.withDayOfYear(1)
                val yearEnd = currentReferenceDate.with(TemporalAdjusters.lastDayOfYear())
                Triple(
                    yearStart.atStartOfDay(),
                    yearEnd.atTime(23, 59, 59),
                    currentReferenceDate.year.toString()
                )
            }
        }
        
        // Check if we can go to next period
        val canGoNext = when (period) {
            ReportPeriod.WEEKLY -> !currentReferenceDate.plusWeeks(1).isAfter(today)
            ReportPeriod.MONTHLY -> !currentReferenceDate.plusMonths(1).withDayOfMonth(1).isAfter(today)
            ReportPeriod.YEARLY -> currentReferenceDate.year < today.year
        }

        val filteredTransactions = allTransactions.filter { 
            it.dateTime >= startDate && it.dateTime <= endDate 
        }
        
        val totalIncome = filteredTransactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }
        
        val totalExpense = filteredTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

        // Calculate category breakdown for expenses
        val expenseTransactions = filteredTransactions.filter { it.type == TransactionType.EXPENSE }
        val categoryBreakdown = expenseTransactions
            .groupBy { it.category }
            .map { (category, transactions) ->
                val total = transactions.sumOf { it.amount }
                CategorySummary(
                    category = category,
                    total = total,
                    percentage = if (totalExpense > 0) (total / totalExpense * 100).toFloat() else 0f,
                    transactionCount = transactions.size
                )
            }
            .sortedByDescending { it.total }

        _uiState.update { state ->
            state.copy(
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                balance = totalIncome - totalExpense,
                transactions = filteredTransactions,
                categoryBreakdown = categoryBreakdown,
                periodLabel = periodLabel,
                canGoNext = canGoNext,
                isLoading = false
            )
        }
    }
}
