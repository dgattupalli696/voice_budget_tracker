package com.budgettracker.ai

/**
 * Rule-based text corrector as a fallback when LLM is not available.
 * Handles common voice transcription errors and number word conversions.
 */
class RuleBasedTextCorrector : TextCorrector {
    
    // Common misspellings in voice transcription
    private val corrections = mapOf(
        // Food related
        "fod" to "food",
        "foof" to "food",
        "fodd" to "food",
        "luch" to "lunch",
        "lunhc" to "lunch",
        "diner" to "dinner",
        "dineer" to "dinner",
        "brekfast" to "breakfast",
        "breakfst" to "breakfast",
        "groceris" to "groceries",
        "grocries" to "groceries",
        "groseries" to "groceries",
        
        // Transport
        "tranport" to "transport",
        "transprt" to "transport",
        "trasport" to "transport",
        "petorl" to "petrol",
        "petrl" to "petrol",
        "diesle" to "diesel",
        "uber" to "Uber",
        "ola" to "Ola",
        "ricksha" to "rickshaw",
        "rikshaw" to "rickshaw",
        "buss" to "bus",
        
        // Shopping
        "shoping" to "shopping",
        "shoppin" to "shopping",
        "cloths" to "clothes",
        "clotes" to "clothes",
        
        // Bills
        "electricty" to "electricity",
        "electrcity" to "electricity",
        "eletricity" to "electricity",
        "bil" to "bill",
        "bils" to "bills",
        "recharge" to "recharge",
        "recharg" to "recharge",
        
        // Health
        "helth" to "health",
        "medicin" to "medicine",
        "medcine" to "medicine",
        "docter" to "doctor",
        "hospitl" to "hospital",
        
        // Money related
        "rupes" to "rupees",
        "rupess" to "rupees",
        "ruppes" to "rupees",
        "ruppees" to "rupees",
        "rs" to "rupees",
        "payed" to "paid",
        "payied" to "paid",
        "payd" to "paid",
        "spended" to "spent",
        "spendt" to "spent",
        "recieved" to "received",
        "recived" to "received",
        "recevied" to "received",
        "salry" to "salary",
        "sallary" to "salary",
        "salery" to "salary",
        
        // Actions
        "bot" to "bought",
        "bougth" to "bought",
        "bougt" to "bought"
    )
    
    // Pre-compiled regex patterns for corrections
    private val correctionPatterns: List<Pair<Regex, String>> = corrections.map { (wrong, correct) ->
        Regex("\\b${Regex.escape(wrong)}\\b", RegexOption.IGNORE_CASE) to correct
    }
    
    // Number words to digits
    private val numberWords = mapOf(
        "zero" to 0,
        "one" to 1,
        "two" to 2,
        "three" to 3,
        "four" to 4,
        "five" to 5,
        "six" to 6,
        "seven" to 7,
        "eight" to 8,
        "nine" to 9,
        "ten" to 10,
        "eleven" to 11,
        "twelve" to 12,
        "thirteen" to 13,
        "fourteen" to 14,
        "fifteen" to 15,
        "sixteen" to 16,
        "seventeen" to 17,
        "eighteen" to 18,
        "nineteen" to 19,
        "twenty" to 20,
        "thirty" to 30,
        "forty" to 40,
        "fifty" to 50,
        "sixty" to 60,
        "seventy" to 70,
        "eighty" to 80,
        "ninety" to 90
    )
    
    // Multipliers
    private val multipliers = mapOf(
        "hundred" to 100,
        "thousand" to 1000,
        "lakh" to 100000,
        "lac" to 100000,
        "crore" to 10000000
    )

    override suspend fun correctText(text: String): TextCorrectionResult {
        val startTime = System.currentTimeMillis()
        var result = text.lowercase()
        
        // Apply spelling corrections using pre-compiled patterns
        correctionPatterns.forEach { (pattern, correct) ->
            result = pattern.replace(result, correct)
        }
        
        // Convert number words to digits
        result = convertNumberWords(result)
        
        // Clean up extra spaces
        result = result.replace(Regex("\\s+"), " ").trim()
        
        return TextCorrectionResult(
            originalText = text,
            correctedText = result,
            wasModified = text.lowercase() != result,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }
    
    private fun convertNumberWords(text: String): String {
        var result = text
        
        // Handle compound numbers like "two hundred fifty" or "fifty thousand"
        val pattern = Regex(
            "(${numberWords.keys.joinToString("|")})\\s*(${multipliers.keys.joinToString("|")})?\\s*(${numberWords.keys.joinToString("|")})?",
            RegexOption.IGNORE_CASE
        )
        
        result = pattern.replace(result) { match ->
            val parts = match.value.lowercase().split(Regex("\\s+"))
            var total = 0
            var current = 0
            
            parts.forEach { part ->
                when {
                    numberWords.containsKey(part) -> {
                        current += numberWords[part]!!
                    }
                    multipliers.containsKey(part) -> {
                        if (current == 0) current = 1
                        current *= multipliers[part]!!
                        total += current
                        current = 0
                    }
                }
            }
            total += current
            
            if (total > 0) total.toString() else match.value
        }
        
        // Simple number word replacements
        numberWords.forEach { (word, digit) ->
            result = result.replace(Regex("\\b$word\\b", RegexOption.IGNORE_CASE), digit.toString())
        }
        
        return result
    }
    
    override suspend fun generateDescription(text: String): String {
        val lowercaseText = text.lowercase()
        
        // Try to extract the item/purpose after prepositions
        val prepositionPatterns = listOf(
            Regex("(?:on|for|at)\\s+([a-zA-Z][a-zA-Z\\s]{1,30})(?:\\s+(?:for|worth|of)\\s+\\d|$)"),
            Regex("(?:bought|got|had)\\s+([a-zA-Z][a-zA-Z\\s]{1,30})(?:\\s+(?:for|worth|of)\\s+\\d|$)")
        )
        
        for (pattern in prepositionPatterns) {
            pattern.find(lowercaseText)?.let { match ->
                val item = match.groupValues[1]
                    .replace(Regex("\\d+"), "")
                    .trim()
                if (item.isNotEmpty() && item.length > 2) {
                    return formatDescription(item)
                }
            }
        }
        
        // Fallback: remove action words and amounts
        var result = lowercaseText
            .replace(Regex("^(spent|paid|received|earned|got|spend|pay|bought|buy|had)\\s+"), "")
            .replace(Regex("₹?\\s*\\d+(\\.\\d+)?\\s*(rupees?|rs\\.?|inr)?"), "")
            .replace(Regex("^(on|for|at)\\s+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        if (result.isNotEmpty() && result.length > 2) {
            return formatDescription(result)
        }
        
        // Last resort: try to identify category keywords
        return when {
            lowercaseText.contains("food") || lowercaseText.contains("lunch") || 
            lowercaseText.contains("dinner") || lowercaseText.contains("breakfast") -> "Meal"
            lowercaseText.contains("uber") || lowercaseText.contains("ola") || 
            lowercaseText.contains("cab") || lowercaseText.contains("taxi") -> "Cab ride"
            lowercaseText.contains("coffee") || lowercaseText.contains("tea") -> "Beverage"
            lowercaseText.contains("grocery") || lowercaseText.contains("groceries") -> "Groceries"
            lowercaseText.contains("petrol") || lowercaseText.contains("fuel") -> "Fuel"
            lowercaseText.contains("bill") -> "Bill payment"
            lowercaseText.contains("recharge") -> "Mobile recharge"
            else -> "Expense"
        }
    }
    
    private fun formatDescription(text: String): String {
        return text
            .split(" ")
            .take(5) // Max 5 words
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }

    override fun isAvailable(): Boolean = true
    
    override suspend fun initialize(): Boolean = true
    
    override fun close() {}
}
