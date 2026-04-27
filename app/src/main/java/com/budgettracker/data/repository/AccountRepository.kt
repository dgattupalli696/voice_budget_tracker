package com.budgettracker.data.repository

import com.budgettracker.data.local.AccountDao
import com.budgettracker.domain.model.Account
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao
) {
    fun getAllAccounts(): Flow<List<Account>> = accountDao.getAllAccounts()

    suspend fun getAllAccountsList(): List<Account> = accountDao.getAllAccountsList()

    suspend fun getAccountById(id: Long): Account? = accountDao.getAccountById(id)

    suspend fun getDefaultAccount(): Account? = accountDao.getDefaultAccount()

    suspend fun count(): Int = accountDao.count()

    suspend fun insertAccount(account: Account): Long = accountDao.insertAccount(account)

    suspend fun insertAll(accounts: List<Account>): List<Long> = accountDao.insertAll(accounts)

    suspend fun updateAccount(account: Account) = accountDao.updateAccount(account)

    suspend fun setDefault(id: Long) {
        accountDao.setDefaultAtomic(id)
    }

    suspend fun deleteAccount(account: Account) = accountDao.deleteAccount(account)
}
