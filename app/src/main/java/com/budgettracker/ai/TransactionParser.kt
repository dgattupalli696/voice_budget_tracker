package com.budgettracker.ai

import com.budgettracker.domain.model.TransactionCategory
import com.budgettracker.domain.model.TransactionType
import com.budgettracker.utils.FileLogger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parsed transaction data from natural language input
 */
data class ParsedTransactionData(
    val amount: Double,
    val description: String,
    val type: TransactionType,
    val category: TransactionCategory,
    val dateTime: LocalDateTime,
    val confidence: Float = 1.0f
)

/**
 * Unified transaction parser that uses LLM for intelligent parsing
 * of natural language input (from voice or chat).
 * 
 * Supports:
 * - Amount extraction (₹500, Rs 500, 500 rupees, etc.)
 * - Date/time parsing (yesterday, last Monday, 2 days ago, specific dates)
 * - Category detection
 * - Description generation
 * - Income/expense type detection
 */
@Singleton
class TransactionParser @Inject constructor(
    private val textCorrectionManager: TextCorrectionManager
) {
    companion object {
        private const val TAG = "TransactionParser"
    }
    
    /**
     * Parse natural language input into structured transaction data.
     * Uses LLM if available, falls back to rule-based parsing.
     */
    suspend fun parse(input: String): ParsedTransactionData? {
        if (input.isBlank()) return null
        
        FileLogger.i(TAG, "Parsing input: $input")
        
        // Try LLM-based parsing first
        if (textCorrectionManager.isAvailable()) {
            try {
                val llmResult = parsWithLLM(input)
                if (llmResult != null) {
                    FileLogger.i(TAG, "LLM parsed: $llmResult")
                    return llmResult
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "LLM parsing failed", e)
            }
        }
        
        // Fallback to rule-based parsing
        return parseWithRules(input)
    }
    
    /**
     * Parse using LLM for better understanding of natural language
     */
    private suspend fun parsWithLLM(input: String): ParsedTransactionData? {
        val prompt = buildParsingPrompt(input)
        val response = textCorrectionManager.chat(prompt)
        
        FileLogger.i(TAG, "LLM response: $response")
        
        return parseLLMResponse(response, input)
    }
    
    private fun buildParsingPrompt(input: String): String {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val currentTime = now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        val dayOfWeek = today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        
        return """You are a transaction parser. Extract transaction details from the input.

CURRENT DATE/TIME: $dayOfWeek, ${today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))} $currentTime

INPUT: "$input"

RESPOND WITH EXACTLY THIS FORMAT (no extra text):
AMOUNT: <number>
TYPE: <EXPENSE or INCOME>
CATEGORY: <FOOD|TRANSPORT|SHOPPING|ENTERTAINMENT|BILLS|HEALTH|EDUCATION|OTHER_EXPENSE|SALARY|FREELANCE|INVESTMENT|OTHER_INCOME>
DESCRIPTION: <2-4 words>
DATE: <YYYY-MM-DD>
TIME: <HH:MM>

DATE CALCULATION RULES:
- "yesterday" = ${today.minusDays(1)}
- "day before yesterday" = ${today.minusDays(2)}  
- "X days ago" = subtract X days from today
- "last week" = ${today.minusWeeks(1)}
- "last Monday" = find previous Monday from today
- No date mentioned = use today ($today)

TIME RULES:
- "morning" = 09:00
- "afternoon" = 14:00
- "evening" = 18:00
- "night" = 21:00
- No time mentioned = $currentTime

RESPOND ONLY WITH THE 6 FIELDS ABOVE."""
    }
    
    private fun parseLLMResponse(response: String, originalInput: String): ParsedTransactionData? {
        try {
            val lines = response.lines().associate { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    parts[0].trim().uppercase() to parts[1].trim()
                } else {
                    "" to ""
                }
            }
            
            val amount = lines["AMOUNT"]?.toDoubleOrNull() ?: return null
            if (amount <= 0) return null
            
            val typeStr = lines["TYPE"]?.uppercase() ?: "EXPENSE"
            val type = if (typeStr.contains("INCOME")) TransactionType.INCOME else TransactionType.EXPENSE
            
            val categoryStr = lines["CATEGORY"]?.uppercase() ?: ""
            val category = try {
                TransactionCategory.valueOf(categoryStr)
            } catch (e: Exception) {
                if (type == TransactionType.INCOME) TransactionCategory.OTHER_INCOME 
                else TransactionCategory.OTHER_EXPENSE
            }
            
            val description = lines["DESCRIPTION"]?.takeIf { it.isNotBlank() } 
                ?: extractDescriptionFromInput(originalInput)
            
            val dateStr = lines["DATE"] ?: LocalDate.now().toString()
            val date = try {
                LocalDate.parse(dateStr)
            } catch (e: Exception) {
                LocalDate.now()
            }
            
            val timeStr = lines["TIME"] ?: "12:00"
            val time = try {
                LocalTime.parse(timeStr)
            } catch (e: Exception) {
                LocalTime.of(12, 0)
            }
            
            return ParsedTransactionData(
                amount = amount,
                description = description.replaceFirstChar { it.uppercase() },
                type = type,
                category = category,
                dateTime = LocalDateTime.of(date, time),
                confidence = 0.9f
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to parse LLM response", e)
            return null
        }
    }
    
    /**
     * Rule-based parsing fallback
     */
    private fun parseWithRules(input: String): ParsedTransactionData? {
        val text = input.lowercase().trim()
        
        val amount = extractAmount(text) ?: return null
        val type = determineType(text)
        val category = determineCategory(text, type)
        val dateTime = parseDateTime(text)
        val description = extractDescriptionFromInput(input)
        
        return ParsedTransactionData(
            amount = amount,
            description = description,
            type = type,
            category = category,
            dateTime = dateTime,
            confidence = 0.7f
        )
    }
    
    private fun extractAmount(text: String): Double? {
        val patterns = listOf(
            Regex("""₹\s*(\d+(?:,\d+)*(?:\.\d+)?)"""),
            Regex("""rs\.?\s*(\d+(?:,\d+)*(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:rupees?|rs\.?|inr)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:,\d+)*(?:\.\d+)?)""")
        )
        
        for (pattern in patterns) {
            pattern.find(text)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return amountStr.toDoubleOrNull()
            }
        }
        return null
    }
    
    private fun determineType(text: String): TransactionType {
        val incomeKeywords = listOf(
            "received", "earned", "got paid", "salary", "income", "bonus", 
            "refund", "cashback", "dividend", "interest"
        )
        return if (incomeKeywords.any { text.contains(it) }) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }
    }
    
    private fun determineCategory(text: String, type: TransactionType): TransactionCategory {
        if (type == TransactionType.INCOME) {
            return when {
                text.contains("salary") || text.contains("paycheck") -> TransactionCategory.SALARY
                text.contains("freelance") || text.contains("project") || text.contains("client") -> TransactionCategory.FREELANCE
                text.contains("invest") || text.contains("dividend") || text.contains("interest") -> TransactionCategory.INVESTMENT
                else -> TransactionCategory.OTHER_INCOME
            }
        }
        
        return when {
            listOf("food", "lunch", "dinner", "breakfast", "snack", "coffee", "tea",
                "restaurant", "cafe", "zomato", "swiggy", "biryani", "pizza", 
                "burger", "grocery", "eat", "meal").any { text.contains(it) } -> TransactionCategory.FOOD
            
            listOf("uber", "ola", "cab", "taxi", "auto", "rickshaw", "bus", "metro",
                "train", "fuel", "petrol", "diesel", "parking", "toll", "transport").any { text.contains(it) } -> TransactionCategory.TRANSPORT
            
            listOf("shopping", "amazon", "flipkart", "myntra", "clothes", "shoes",
                "electronics", "gadget", "phone", "laptop", "bought").any { text.contains(it) } -> TransactionCategory.SHOPPING
            
            listOf("movie", "netflix", "prime", "hotstar", "spotify", "game",
                "concert", "show", "entertainment", "fun").any { text.contains(it) } -> TransactionCategory.ENTERTAINMENT
            
            listOf("bill", "electricity", "water", "gas", "internet", "wifi",
                "phone bill", "recharge", "rent", "emi").any { text.contains(it) } -> TransactionCategory.BILLS
            
            listOf("doctor", "medicine", "medical", "hospital", "pharmacy",
                "health", "gym", "fitness").any { text.contains(it) } -> TransactionCategory.HEALTH
            
            listOf("book", "course", "class", "tuition", "school", "college",
                "university", "education", "learning", "udemy").any { text.contains(it) } -> TransactionCategory.EDUCATION
            
            else -> TransactionCategory.OTHER_EXPENSE
        }
    }
    
    private fun parseDateTime(text: String): LocalDateTime {
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        
        // Parse date from text
        val date = parseDateFromText(text, today)
        
        // Parse time from text
        val time = parseTimeFromText(text, now.toLocalTime())
        
        return LocalDateTime.of(date, time)
    }
    
    private fun parseDateFromText(text: String, today: LocalDate): LocalDate {
        // Check for "yesterday"
        if (text.contains("yesterday")) {
            return today.minusDays(1)
        }
        
        // Check for "day before yesterday"
        if (text.contains("day before yesterday")) {
            return today.minusDays(2)
        }
        
        // Check for "X days ago"
        Regex("""(\d+)\s*days?\s*ago""").find(text)?.let { match ->
            val days = match.groupValues[1].toLongOrNull() ?: 0
            if (days > 0) return today.minusDays(days)
        }
        
        // Check for "last week"
        if (text.contains("last week")) {
            return today.minusWeeks(1)
        }
        
        // Check for "X weeks ago"
        Regex("""(\d+)\s*weeks?\s*ago""").find(text)?.let { match ->
            val weeks = match.groupValues[1].toLongOrNull() ?: 0
            if (weeks > 0) return today.minusWeeks(weeks)
        }
        
        // Check for "last month"
        if (text.contains("last month")) {
            return today.minusMonths(1)
        }
        
        // Check for specific days of week
        val dayOfWeekMap = mapOf(
            "monday" to java.time.DayOfWeek.MONDAY,
            "tuesday" to java.time.DayOfWeek.TUESDAY,
            "wednesday" to java.time.DayOfWeek.WEDNESDAY,
            "thursday" to java.time.DayOfWeek.THURSDAY,
            "friday" to java.time.DayOfWeek.FRIDAY,
            "saturday" to java.time.DayOfWeek.SATURDAY,
            "sunday" to java.time.DayOfWeek.SUNDAY
        )
        
        for ((dayName, dayOfWeek) in dayOfWeekMap) {
            if (text.contains("last $dayName")) {
                return findLastDayOfWeek(today, dayOfWeek)
            }
        }
        
        // Check for "this morning", "today" - use today
        if (text.contains("this morning") || text.contains("today")) {
            return today
        }
        
        // Default to today
        return today
    }
    
    private fun parseTimeFromText(text: String, defaultTime: LocalTime): LocalTime {
        // Check for "morning" - use 9:00 AM
        if (text.contains("morning")) {
            return LocalTime.of(9, 0)
        }
        
        // Check for "afternoon" - use 2:00 PM
        if (text.contains("afternoon")) {
            return LocalTime.of(14, 0)
        }
        
        // Check for "evening" - use 6:00 PM
        if (text.contains("evening")) {
            return LocalTime.of(18, 0)
        }
        
        // Check for "night" - use 9:00 PM
        if (text.contains("night")) {
            return LocalTime.of(21, 0)
        }
        
        // Try to extract specific time like "3pm", "3:30 pm", "15:00"
        val timePattern = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE)
        timePattern.find(text)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: return defaultTime
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val amPm = match.groupValues[3].lowercase()
            
            // Only process if it looks like a time (not just a number like amount)
            if (amPm.isNotEmpty() || match.groupValues[2].isNotEmpty()) {
                if (amPm == "pm" && hour != 12) hour += 12
                if (amPm == "am" && hour == 12) hour = 0
                return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            }
        }
        
        // Default to current time
        return defaultTime
    }
    
    private fun findLastDayOfWeek(from: LocalDate, dayOfWeek: java.time.DayOfWeek): LocalDate {
        var date = from.minusDays(1)
        while (date.dayOfWeek != dayOfWeek) {
            date = date.minusDays(1)
        }
        return date
    }
    
    private fun extractDescriptionFromInput(input: String): String {
        var desc = input
            .replace(Regex("""(?i)\b(add|expense|income|spent|bought|paid|earned|received|for|on|rs|rupees|₹|yesterday|today|last\s+\w+|\d+\s*days?\s*ago)\b"""), "")
            .replace(Regex("""₹?\s*\d+(?:,\d+)*(?:\.\d+)?"""), "")
            .replace(Regex("""\d{1,2}(?::\d{2})?\s*(?:am|pm)?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        
        if (desc.isNotEmpty()) {
            desc = desc.replaceFirstChar { it.uppercase() }
        }
        
        return desc.ifEmpty { "Transaction" }
    }
}
