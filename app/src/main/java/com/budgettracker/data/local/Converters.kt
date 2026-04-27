package com.budgettracker.data.local

import androidx.room.TypeConverter
import com.budgettracker.domain.model.AccountType
import com.budgettracker.domain.model.TransactionCategory
import com.budgettracker.domain.model.TransactionType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? {
        return dateTime?.format(formatter)
    }

    @TypeConverter
    fun toLocalDateTime(dateTimeString: String?): LocalDateTime? {
        return dateTimeString?.let { LocalDateTime.parse(it, formatter) }
    }

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String {
        return type.name
    }

    @TypeConverter
    fun toTransactionType(typeName: String): TransactionType {
        return try {
            TransactionType.valueOf(typeName)
        } catch (e: IllegalArgumentException) {
            TransactionType.EXPENSE
        }
    }

    @TypeConverter
    fun fromTransactionCategory(category: TransactionCategory): String {
        return category.name
    }

    @TypeConverter
    fun toTransactionCategory(categoryName: String): TransactionCategory {
        return try {
            TransactionCategory.valueOf(categoryName)
        } catch (e: IllegalArgumentException) {
            TransactionCategory.OTHER_EXPENSE
        }
    }

    @TypeConverter
    fun fromAccountType(type: AccountType): String = type.name

    @TypeConverter
    fun toAccountType(name: String): AccountType = try {
        AccountType.valueOf(name)
    } catch (e: IllegalArgumentException) {
        AccountType.BANK
    }
}
