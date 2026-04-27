package com.budgettracker.data.repository

import com.budgettracker.data.local.AccountTotalRow
import com.budgettracker.data.local.TransactionDao
import com.budgettracker.domain.model.Transaction
import com.budgettracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {
    fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions()

    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> =
        transactionDao.getTransactionsByType(type)

    fun getTotalIncome(): Flow<Double?> =
        transactionDao.getTotalByType(TransactionType.INCOME)

    fun getTotalExpense(): Flow<Double?> =
        transactionDao.getTotalByType(TransactionType.EXPENSE)

    fun getAccountTotals(): Flow<List<AccountTotalRow>> =
        transactionDao.getAccountTotals()

    suspend fun getTransactionById(id: Long): Transaction? =
        transactionDao.getTransactionById(id)

    suspend fun insertTransaction(transaction: Transaction): Long =
        transactionDao.insertTransaction(transaction)

    suspend fun updateTransaction(transaction: Transaction) =
        transactionDao.updateTransaction(transaction)

    suspend fun deleteTransaction(transaction: Transaction) =
        transactionDao.deleteTransaction(transaction)

    suspend fun deleteTransactionById(id: Long) =
        transactionDao.deleteTransactionById(id)
}
