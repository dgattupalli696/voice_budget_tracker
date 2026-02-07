package com.budgettracker.utils

import com.budgettracker.ai.TextCorrectionManager
import com.budgettracker.domain.model.TransactionCategory
import com.budgettracker.domain.model.TransactionType
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedTransaction(
    val amount: Double,
    val description: String,
    val type: TransactionType,
    val category: TransactionCategory
)

@Singleton
class VoiceInputParser @Inject constructor(
    private val textCorrectionManager: TextCorrectionManager
) {
    
    /**
     * Parse with AI text correction and description generation
     */
    suspend fun parseWithCorrection(input: String): ParsedTransaction? {
        val correctionResult = textCorrectionManager.correctText(input)
        val correctedText = correctionResult.correctedText
        
        val text = correctedText.lowercase().trim()
        if (text.isEmpty()) return null
        
        val type = determineType(text)
        val amount = extractAmount(text) ?: return null
        val category = determineCategory(text, type)
        
        // Use AI-generated description if available, otherwise fall back to extraction
        val description = correctionResult.generatedDescription 
            ?: textCorrectionManager.generateDescription(input)
        
        return ParsedTransaction(
            amount = amount,
            description = description,
            type = type,
            category = category
        )
    }
    
    /**
     * Parse without AI correction (backwards compatibility)
     */
    fun parse(input: String): ParsedTransaction? {
        return parseInternal(input)
    }
    
    private fun parseInternal(input: String): ParsedTransaction? {
        val text = input.lowercase().trim()
        if (text.isEmpty()) return null
        
        val type = determineType(text)
        val amount = extractAmount(text) ?: return null
        val category = determineCategory(text, type)
        
        // Extract meaningful description - keep the item/purpose part
        val description = extractDescriptionSimple(text, category)
        
        return ParsedTransaction(
            amount = amount,
            description = description,
            type = type,
            category = category
        )
    }
    
    private fun extractDescriptionSimple(text: String, category: TransactionCategory): String {
        // Try to find the item after "on", "for", "at"
        val prepositionPattern = Regex("(?:on|for|at)\\s+(.+?)(?:\\s+(?:for|worth|of)\\s+\\d|$)", RegexOption.IGNORE_CASE)
        prepositionPattern.find(text)?.let { match ->
            val item = match.groupValues[1]
                .replace(Regex("₹?\\s*\\d+(\\.\\d+)?\\s*(rupees?|rs\\.?|inr)?"), "")
                .trim()
            if (item.isNotEmpty()) {
                return item.replaceFirstChar { it.uppercase() }
            }
        }
        
        // Fallback: remove action words, amounts, and clean up
        var result = text
            .replace(Regex("^(spent|paid|received|earned|got|spend|pay|bought|buy)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("₹?\\s*\\d+(\\.\\d+)?\\s*(rupees?|rs\\.?|inr)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(on|for|at)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        // If we have something meaningful, use it
        if (result.isNotEmpty() && result.length > 2) {
            return result.replaceFirstChar { it.uppercase() }
        }
        
        // Final fallback: use category name
        return category.displayName
    }
    
    private fun determineType(text: String): TransactionType {
        val incomeKeywords = listOf("received", "earned", "got", "salary", "income", "bonus", "refund")
        return if (incomeKeywords.any { text.contains(it) }) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }
    }
    
    private fun extractAmount(text: String): Double? {
        // Pattern for rupee amounts: ₹500, Rs.500, 500 rupees, etc.
        val patterns = listOf(
            Regex("₹\\s*(\\d+(?:\\.\\d+)?)"),
            Regex("rs\\.?\\s*(\\d+(?:\\.\\d+)?)"),
            Regex("(\\d+(?:\\.\\d+)?)\\s*(?:rupees?|rs\\.?|inr)"),
            Regex("(\\d+(?:\\.\\d+)?)")
        )
        
        for (pattern in patterns) {
            pattern.find(text)?.let { match ->
                return match.groupValues[1].toDoubleOrNull()
            }
        }
        return null
    }
    
    private fun determineCategory(text: String, type: TransactionType): TransactionCategory {
        if (type == TransactionType.INCOME) {
            return when {
                text.contains("salary") -> TransactionCategory.SALARY
                text.contains("bonus") -> TransactionCategory.SALARY
                text.contains("freelance") || text.contains("project") -> TransactionCategory.FREELANCE
                text.contains("investment") || text.contains("dividend") -> TransactionCategory.INVESTMENT
                else -> TransactionCategory.OTHER_INCOME
            }
        }
        
        return when {
            // Food & Dining
            listOf("food", "lunch", "dinner", "breakfast", "snack", "coffee", "tea", "chai",
                "restaurant", "cafe", "zomato", "swiggy", "biryani", "pizza", "burger",
                "dosa", "idli", "samosa", "thali", "meal").any { text.contains(it) } -> 
                TransactionCategory.FOOD
            
            // Transport
            listOf("uber", "ola", "cab", "taxi", "auto", "rickshaw", "bus", "metro",
                "train", "fuel", "petrol", "diesel", "parking", "toll").any { text.contains(it) } -> 
                TransactionCategory.TRANSPORT
            
            // Shopping
            listOf("shopping", "amazon", "flipkart", "myntra", "clothes", "shoes",
                "electronics", "gadget", "phone", "laptop").any { text.contains(it) } -> 
                TransactionCategory.SHOPPING
            
            // Entertainment
            listOf("movie", "netflix", "prime", "hotstar", "spotify", "game",
                "concert", "show", "entertainment", "fun").any { text.contains(it) } -> 
                TransactionCategory.ENTERTAINMENT
            
            // Bills
            listOf("bill", "electricity", "water", "gas", "internet", "wifi",
                "phone bill", "recharge", "rent", "emi").any { text.contains(it) } -> 
                TransactionCategory.BILLS
            
            // Health
            listOf("doctor", "medicine", "medical", "hospital", "pharmacy",
                "health", "gym", "fitness").any { text.contains(it) } -> 
                TransactionCategory.HEALTH
            
            // Education
            listOf("book", "course", "class", "tuition", "school", "college",
                "university", "education", "learning").any { text.contains(it) } -> 
                TransactionCategory.EDUCATION
            
            else -> TransactionCategory.OTHER_EXPENSE
        }
    }
}
