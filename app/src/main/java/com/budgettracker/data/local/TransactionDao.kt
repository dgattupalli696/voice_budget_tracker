package com.budgettracker.data.local

import androidx.room.*
import com.budgettracker.domain.model.Transaction
import com.budgettracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY dateTime DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY dateTime DESC")
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?

    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type")
    fun getTotalByType(type: TransactionType): Flow<Double?>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE accountId = :accountId AND type = :type")
    suspend fun getTotalForAccount(accountId: Long, type: TransactionType): Double

    @Query("SELECT * FROM transactions WHERE dateTime >= :startDate AND dateTime <= :endDate ORDER BY dateTime DESC")
    fun getTransactionsInRange(startDate: String, endDate: String): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)
}
