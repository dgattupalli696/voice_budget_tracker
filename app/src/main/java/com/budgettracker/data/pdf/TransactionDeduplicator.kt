package com.budgettracker.data.pdf

import com.budgettracker.domain.model.ImportedTransaction
import com.budgettracker.domain.model.Transaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class TransactionDeduplicator @Inject constructor() {

    private val stopWords = setOf(
        "the", "a", "an", "to", "from", "for", "of", "in", "on", "at", "by",
        "and", "or", "is", "was", "with", "via", "upi", "neft", "imps", "rtgs",
        "ref", "txn", "transaction", "transfer", "payment", "paid"
    )

    fun markDuplicates(
        imported: List<ImportedTransaction>,
        existing: List<Transaction>
    ): List<ImportedTransaction> {
        return imported.map { txn ->
            val duplicate = findDuplicate(txn, existing)
            if (duplicate != null) {
                txn.copy(
                    isDuplicate = true,
                    isSelected = false,
                    duplicateMatchId = duplicate.id
                )
            } else {
                txn
            }
        }
    }

    private fun findDuplicate(imported: ImportedTransaction, existing: List<Transaction>): Transaction? {
        val importedDate = imported.dateTime.toLocalDate()
        val importedWords = extractWords(imported.description)

        return existing.firstOrNull { existing ->
            val sameDay = existing.dateTime.toLocalDate() == importedDate
            val sameAmount = abs(existing.amount - imported.amount) < 0.01
            val wordOverlap = calculateWordOverlap(importedWords, extractWords(existing.description))

            sameDay && sameAmount && wordOverlap >= 0.5
        }
    }

    private fun extractWords(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length > 1 && it !in stopWords }
            .toSet()
    }

    private fun calculateWordOverlap(words1: Set<String>, words2: Set<String>): Double {
        if (words1.isEmpty() && words2.isEmpty()) return 1.0
        if (words1.isEmpty() || words2.isEmpty()) return 0.0
        val intersection = words1.intersect(words2).size
        val smaller = minOf(words1.size, words2.size)
        return intersection.toDouble() / smaller.toDouble()
    }
}
