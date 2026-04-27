package com.budgettracker.data.pdf

import com.budgettracker.domain.model.ImportedTransaction
import com.budgettracker.domain.model.TransactionCategory
import com.budgettracker.domain.model.TransactionType
import com.budgettracker.utils.FileLogger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankStatementParser @Inject constructor() {

    private data class ColumnLayout(
        val dateCol: IntRange?,
        val descriptionCol: IntRange?,
        val debitCol: IntRange?,
        val creditCol: IntRange?,
        val balanceCol: IntRange?
    )

    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yy"),
        DateTimeFormatter.ofPattern("dd-MM-yy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd MMM yyyy"),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
        DateTimeFormatter.ofPattern("dd MMM yy"),
        DateTimeFormatter.ofPattern("dd-MMM-yy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("MMM dd, yyyy")
    )

    private val datePattern = Regex(
        """(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})|(\d{1,2}[\s-][A-Za-z]{3}[\s-]\d{2,4})|(\d{4}-\d{2}-\d{2})|([A-Za-z]{3}\s+\d{1,2},\s*\d{4})"""
    )

    private val amountPattern = Regex("""[\d,]+\.\d{1,2}""")

    private val headerKeywords = mapOf(
        "date" to listOf("date", "txn date", "transaction date", "value date", "posting date"),
        "description" to listOf("description", "narration", "particulars", "details", "remarks", "transaction details"),
        "debit" to listOf("debit", "withdrawal", "dr", "withdrawals", "debit amount", "dr."),
        "credit" to listOf("credit", "deposit", "cr", "deposits", "credit amount", "cr."),
        "balance" to listOf("balance", "closing balance", "available balance", "running balance")
    )

    fun parse(pages: List<String>): List<ImportedTransaction> {
        val allText = pages.joinToString("\n")
        val lines = allText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val headerIndex = findHeaderRow(lines)
        if (headerIndex == -1) {
            FileLogger.w("BankStatementParser", "No header row found, trying line-by-line parsing")
            return parseWithoutHeader(lines)
        }

        val headerLine = lines[headerIndex]
        FileLogger.i("BankStatementParser", "Header found at line $headerIndex: $headerLine")

        val layout = detectColumnLayout(headerLine)
        val hasBalanceCol = layout.balanceCol != null
        val transactions = mutableListOf<ImportedTransaction>()
        var currentDescription = ""
        var lastDate: LocalDate? = null
        var lastDebit: Double? = null
        var lastCredit: Double? = null
        var prevClosingBalance: Double? = findOpeningBalance(lines, headerIndex)

        for (i in (headerIndex + 1) until lines.size) {
            val line = lines[i]
            if (isFooterOrSummary(line)) break
            if (line.isBlank()) continue

            val dateMatch = datePattern.find(line)
            if (dateMatch != null) {
                // Save previous accumulated transaction
                if (lastDate != null && currentDescription.isNotEmpty()) {
                    val txn = createTransaction(lastDate, currentDescription, lastDebit, lastCredit)
                    if (txn != null) transactions.add(txn)
                }

                lastDate = parseDate(dateMatch.value)
                val amounts = extractAmounts(line, layout, dateMatch.range)
                lastDebit = amounts.first
                lastCredit = amounts.second
                
                // Balance-tracking heuristic: when we have a balance column
                // and exactly one transaction amount + one balance amount,
                // use balance delta to determine debit vs credit
                if (hasBalanceCol) {
                    val resolved = resolveWithBalanceTracking(
                        lastDebit, lastCredit, amounts.third, prevClosingBalance
                    )
                    lastDebit = resolved.first
                    lastCredit = resolved.second
                    val closingBal = resolved.third
                    if (closingBal != null) prevClosingBalance = closingBal
                }

                // Extract description: text between date and amounts
                currentDescription = extractDescription(line, dateMatch.range, amounts.third)
            } else if (lastDate != null) {
                // Continuation line — append to current description
                val trimmed = line.trim()
                if (!isAmountOnlyLine(trimmed)) {
                    currentDescription = "$currentDescription $trimmed".trim()
                }
            }
        }

        // Don't forget last transaction
        if (lastDate != null && currentDescription.isNotEmpty()) {
            val txn = createTransaction(lastDate, currentDescription, lastDebit, lastCredit)
            if (txn != null) transactions.add(txn)
        }

        FileLogger.i("BankStatementParser", "Parsed ${transactions.size} transactions")
        return transactions
    }

    private fun findHeaderRow(lines: List<String>): Int {
        for (i in lines.indices) {
            val lower = lines[i].lowercase()
            val matchCount = headerKeywords.values.flatten().count { keyword ->
                lower.contains(keyword)
            }
            // At least 3 header keywords found (date + description + debit/credit)
            if (matchCount >= 3) return i
        }
        return -1
    }

    private fun detectColumnLayout(headerLine: String): ColumnLayout {
        val lower = headerLine.lowercase()

        fun findKeywordRange(keywords: List<String>): IntRange? {
            for (keyword in keywords) {
                val idx = lower.indexOf(keyword)
                if (idx >= 0) return IntRange(idx, idx + keyword.length)
            }
            return null
        }

        return ColumnLayout(
            dateCol = findKeywordRange(headerKeywords["date"]!!),
            descriptionCol = findKeywordRange(headerKeywords["description"]!!),
            debitCol = findKeywordRange(headerKeywords["debit"]!!),
            creditCol = findKeywordRange(headerKeywords["credit"]!!),
            balanceCol = findKeywordRange(headerKeywords["balance"]!!)
        )
    }

    private fun extractAmounts(line: String, layout: ColumnLayout, dateRange: IntRange): Triple<Double?, Double?, List<IntRange>> {
        val amounts = amountPattern.findAll(line).toList()
        val amountRanges = amounts.map { it.range }

        if (amounts.isEmpty()) return Triple(null, null, emptyList())

        // Filter out amounts that overlap with the date
        val nonDateAmounts = amounts.filter { !it.range.overlaps(dateRange) }

        if (nonDateAmounts.isEmpty()) return Triple(null, null, emptyList())

        // If we have column positions, use them to determine debit vs credit
        if (layout.debitCol != null && layout.creditCol != null && nonDateAmounts.size >= 2) {
            val debitCenter = layout.debitCol.center()
            val creditCenter = layout.creditCol.center()

            val sorted = nonDateAmounts.sortedBy { it.range.first }
            val amounts2 = sorted.map { parseAmount(it.value) }

            // Determine which column each amount falls into based on position
            val debit = if (debitCenter < creditCenter) amounts2.firstOrNull() else amounts2.lastOrNull()
            val credit = if (debitCenter < creditCenter) amounts2.getOrNull(1) else amounts2.firstOrNull()

            return Triple(
                debit?.takeIf { it > 0 },
                credit?.takeIf { it > 0 },
                nonDateAmounts.map { it.range }
            )
        }

        // Fallback: if only one amount, look at surrounding text for clues
        if (nonDateAmounts.size == 1) {
            val amount = parseAmount(nonDateAmounts[0].value)
            val textBefore = line.substring(0, nonDateAmounts[0].range.first).lowercase()
            val isCredit = textBefore.contains("cr") || textBefore.contains("credit") ||
                    textBefore.contains("deposit") || textBefore.contains("received")
            return if (isCredit) {
                Triple(null, amount, nonDateAmounts.map { it.range })
            } else {
                Triple(amount, null, nonDateAmounts.map { it.range })
            }
        }

        // Multiple amounts: last is usually balance, second-to-last is the transaction amount
        val balanceRemoved = if (nonDateAmounts.size > 2) nonDateAmounts.dropLast(1) else nonDateAmounts
        return Triple(
            parseAmount(balanceRemoved[0].value).takeIf { it > 0 },
            parseAmount(balanceRemoved.getOrNull(1)?.value ?: "0").takeIf { it > 0 },
            nonDateAmounts.map { it.range }
        )
    }

    private fun extractDescription(line: String, dateRange: IntRange, amountRanges: List<IntRange>): String {
        val allExclude = (listOf(dateRange) + amountRanges).sortedBy { it.first }
        val sb = StringBuilder()
        var pos = 0
        for (range in allExclude) {
            if (range.first > pos) {
                sb.append(line.substring(pos, range.first))
            }
            pos = range.last + 1
        }
        if (pos < line.length) {
            sb.append(line.substring(pos))
        }
        return sb.toString()
            .replace(Regex("""[|/\\]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(200)
    }

    private fun createTransaction(
        date: LocalDate?,
        description: String,
        debit: Double?,
        credit: Double?
    ): ImportedTransaction? {
        if (date == null) return null
        val amount = debit ?: credit ?: return null
        if (amount <= 0) return null

        val cleanDesc = cleanDescription(description)
        if (cleanDesc.isBlank()) return null

        val type = if (credit != null && credit > 0 && (debit == null || debit == 0.0))
            TransactionType.INCOME else TransactionType.EXPENSE
        val category = determineCategory(cleanDesc.lowercase(), type)

        return ImportedTransaction(
            amount = amount,
            description = cleanDesc,
            category = category,
            type = type,
            dateTime = LocalDateTime.of(date, LocalTime.NOON),
            rawText = description
        )
    }

    /**
     * Look for an opening balance line before the first transaction.
     * HDFC format: "Opening Balance" or first line balance.
     */
    private fun findOpeningBalance(lines: List<String>, headerIndex: Int): Double? {
        // Search around the header for opening balance
        val searchRange = maxOf(0, headerIndex - 5)..headerIndex
        for (i in searchRange) {
            val lower = lines[i].lowercase()
            if (lower.contains("opening balance") || lower.contains("b/f") || lower.contains("brought forward")) {
                val amounts = amountPattern.findAll(lines[i]).map { parseAmount(it.value) }.filter { it > 0 }.toList()
                if (amounts.isNotEmpty()) return amounts.last()
            }
        }
        return null
    }

    /**
     * Use balance tracking to correctly classify debit vs credit.
     * When a statement has WithdrawalAmt/DepositAmt/ClosingBalance columns but
     * PDF text extraction collapses whitespace, we can't rely on positions.
     * Instead: the last amount is the closing balance. Compare with previous
     * balance to determine if the transaction was a deposit or withdrawal.
     * 
     * Returns Triple(debit, credit, closingBalance)
     */
    private fun resolveWithBalanceTracking(
        rawDebit: Double?,
        rawCredit: Double?,
        amountRanges: List<IntRange>,
        prevBalance: Double?
    ): Triple<Double?, Double?, Double?> {
        // If we already have a clear debit-only or credit-only, keep it
        if (rawDebit != null && rawDebit > 0 && rawCredit == null) {
            return Triple(rawDebit, null, null)
        }
        if (rawCredit != null && rawCredit > 0 && rawDebit == null) {
            return Triple(null, rawCredit, null)
        }
        
        // When both rawDebit and rawCredit are set but one is actually the balance
        if (rawDebit != null && rawDebit > 0 && rawCredit != null && rawCredit > 0) {
            val txnAmount = rawDebit
            val closingBalance = rawCredit  // rightmost = balance
            
            if (prevBalance != null) {
                val delta = closingBalance - prevBalance
                // Validate: the absolute delta should roughly match the transaction amount
                if (Math.abs(Math.abs(delta) - txnAmount) < txnAmount * 0.01) {
                    return if (delta > 0) {
                        // Balance increased → this was a deposit/credit
                        Triple(null, txnAmount, closingBalance)
                    } else {
                        // Balance decreased → this was a withdrawal/debit
                        Triple(txnAmount, null, closingBalance)
                    }
                }
            }
            // Can't determine from balance, fall through to original assignment
            return Triple(rawDebit, rawCredit, rawCredit)
        }
        
        return Triple(rawDebit, rawCredit, null)
    }

    private fun parseWithoutHeader(lines: List<String>): List<ImportedTransaction> {
        val transactions = mutableListOf<ImportedTransaction>()
        for (line in lines) {
            val dateMatch = datePattern.find(line) ?: continue
            val date = parseDate(dateMatch.value) ?: continue
            val amounts = amountPattern.findAll(line)
                .map { parseAmount(it.value) }
                .filter { it > 0 }
                .toList()
            if (amounts.isEmpty()) continue

            val amount = amounts.first()
            val descPart = line.substring(dateMatch.range.last + 1)
                .replace(amountPattern, "")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .take(200)

            if (descPart.isBlank()) continue

            // Infer type from text clues
            val lower = descPart.lowercase()
            val type = if (lower.contains("cr") || lower.contains("credit") ||
                lower.contains("deposit") || lower.contains("received") ||
                lower.contains("salary") || lower.contains("neftcr") ||
                lower.contains("refund") || lower.contains("cashback") ||
                lower.contains("interest") || lower.contains("dividend")) {
                TransactionType.INCOME
            } else {
                TransactionType.EXPENSE
            }
            transactions.add(
                ImportedTransaction(
                    amount = amount,
                    description = cleanDescription(descPart),
                    category = determineCategory(descPart.lowercase(), type),
                    type = type,
                    dateTime = LocalDateTime.of(date, LocalTime.NOON),
                    rawText = line
                )
            )
        }
        return transactions
    }

    private fun parseDate(dateStr: String): LocalDate? {
        val cleaned = dateStr.trim()
        for (fmt in dateFormats) {
            try {
                return LocalDate.parse(cleaned, fmt)
            } catch (_: DateTimeParseException) {
                // Try next format
            }
        }
        return null
    }

    private fun parseAmount(amountStr: String): Double {
        return try {
            amountStr.replace(",", "").toDouble()
        } catch (_: NumberFormatException) {
            0.0
        }
    }

    private fun cleanDescription(desc: String): String {
        return desc
            .replace(Regex("""(CR|DR|Cr|Dr)\.?\s*$"""), "")
            .replace(Regex("""\b\d{10,}\b"""), "") // Remove long reference numbers
            .replace(Regex("""\b[A-Z0-9]{15,}\b"""), "") // Remove transaction IDs
            .replace(Regex("""\s+"""), " ")
            .trim()
            .replaceFirstChar { it.uppercase() }
    }

    private fun isFooterOrSummary(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("statement summary") ||
                lower.contains("opening balance") && lower.contains("closing balance") ||
                lower.contains("total debit") && lower.contains("total credit") ||
                lower.contains("page ") && lower.contains(" of ") ||
                lower.startsWith("this is a computer generated")
    }

    private fun isAmountOnlyLine(line: String): Boolean {
        val stripped = line.replace(amountPattern, "").replace(Regex("""\s+"""), "").trim()
        return stripped.isEmpty() || stripped.length < 3
    }

    private fun determineCategory(text: String, type: TransactionType): TransactionCategory {
        if (type == TransactionType.INCOME) {
            return when {
                text.contains("salary") || text.contains("paycheck") || text.contains("payroll") -> TransactionCategory.SALARY
                text.contains("freelance") || text.contains("project") || text.contains("client") -> TransactionCategory.FREELANCE
                text.contains("invest") || text.contains("dividend") || text.contains("interest") || text.contains("mutual fund") -> TransactionCategory.INVESTMENT
                else -> TransactionCategory.OTHER_INCOME
            }
        }

        return when {
            listOf("food", "lunch", "dinner", "breakfast", "snack", "coffee", "tea",
                "restaurant", "cafe", "zomato", "swiggy", "biryani", "pizza",
                "burger", "grocery", "eat", "meal", "dominos", "mcdonalds", "kfc").any { text.contains(it) } -> TransactionCategory.FOOD

            listOf("uber", "ola", "cab", "taxi", "auto", "rickshaw", "bus", "metro",
                "train", "fuel", "petrol", "diesel", "parking", "toll", "transport",
                "rapido", "irctc", "railway").any { text.contains(it) } -> TransactionCategory.TRANSPORT

            listOf("shopping", "amazon", "flipkart", "myntra", "clothes", "shoes",
                "electronics", "gadget", "phone", "laptop", "bought", "ajio",
                "meesho", "snapdeal").any { text.contains(it) } -> TransactionCategory.SHOPPING

            listOf("movie", "netflix", "prime", "hotstar", "spotify", "game",
                "concert", "show", "entertainment", "fun", "jiocinema", "youtube").any { text.contains(it) } -> TransactionCategory.ENTERTAINMENT

            listOf("bill", "electricity", "water", "gas", "internet", "wifi",
                "phone bill", "recharge", "rent", "emi", "insurance", "broadband",
                "airtel", "jio", "vodafone", "bsnl").any { text.contains(it) } -> TransactionCategory.BILLS

            listOf("doctor", "medicine", "medical", "hospital", "pharmacy",
                "health", "gym", "fitness", "apollo", "medplus", "netmeds").any { text.contains(it) } -> TransactionCategory.HEALTH

            listOf("book", "course", "class", "tuition", "school", "college",
                "university", "education", "learning", "udemy", "coursera", "byju").any { text.contains(it) } -> TransactionCategory.EDUCATION

            else -> TransactionCategory.OTHER_EXPENSE
        }
    }

    private fun IntRange.overlaps(other: IntRange): Boolean =
        this.first <= other.last && other.first <= this.last

    private fun IntRange.center(): Int = (first + last) / 2
}
