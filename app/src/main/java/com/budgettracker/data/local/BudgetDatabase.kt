package com.budgettracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.budgettracker.domain.model.Account
import com.budgettracker.domain.model.Transaction

@Database(
    entities = [Transaction::class, Account::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao

    companion object {
        const val DATABASE_NAME = "budget_tracker_db"
    }
}
