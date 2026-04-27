package com.budgettracker.data.local

import androidx.room.*
import com.budgettracker.domain.model.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, name ASC")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, name ASC")
    suspend fun getAllAccountsList(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): Account?

    @Query("SELECT * FROM accounts WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultAccount(): Account?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<Account>): List<Long>

    @Update
    suspend fun updateAccount(account: Account)

    @Query("UPDATE accounts SET isDefault = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setDefaultAtomic(id: Long)

    @Delete
    suspend fun deleteAccount(account: Account)
}
