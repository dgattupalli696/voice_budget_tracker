package com.budgettracker.data.pdf

import android.content.Context
import android.net.Uri
import com.budgettracker.data.repository.TransactionRepository
import com.budgettracker.domain.model.ImportedTransaction
import com.budgettracker.utils.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class PdfImportResult(
    val transactions: List<ImportedTransaction>,
    val totalParsed: Int,
    val duplicateCount: Int,
    val ocrUsed: Boolean = false
)

@Singleton
class PdfImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfTextExtractor: PdfTextExtractor,
    private val ocrExtractor: OcrExtractor,
    private val bankStatementParser: BankStatementParser,
    private val deduplicator: TransactionDeduplicator,
    private val repository: TransactionRepository
) {

    suspend fun processUri(uri: Uri, onProgress: (String) -> Unit = {}): PdfImportResult =
        withContext(Dispatchers.IO) {
            try {
                onProgress("Reading PDF...")

                // Step 1: Try text extraction first
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open PDF file")

                var pages: List<String>
                var ocrUsed = false

                inputStream.use { stream ->
                    pages = pdfTextExtractor.extractText(stream)
                }

                // Step 2: If no meaningful text, fallback to OCR
                if (pages.isEmpty() || pages.all { it.length < 50 }) {
                    onProgress("No text found, running OCR...")
                    FileLogger.i("PdfImportManager", "Falling back to OCR extraction")
                    val ocrStream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("Cannot reopen PDF file")
                    ocrStream.use { stream ->
                        pages = ocrExtractor.extractText(stream)
                    }
                    ocrUsed = true
                }

                if (pages.isEmpty()) {
                    throw IllegalStateException("Could not extract any text from PDF")
                }

                FileLogger.i("PdfImportManager", "Extracted ${pages.size} pages of text")

                // Step 3: Parse transactions
                onProgress("Parsing transactions...")
                val parsed = bankStatementParser.parse(pages)

                if (parsed.isEmpty()) {
                    throw IllegalStateException("No transactions found in the PDF. The format may not be supported.")
                }

                FileLogger.i("PdfImportManager", "Parsed ${parsed.size} transactions")

                // Step 4: Deduplicate against existing transactions
                onProgress("Checking for duplicates...")
                val existing = repository.getAllTransactions().first()
                val deduped = deduplicator.markDuplicates(parsed, existing)
                val duplicateCount = deduped.count { it.isDuplicate }

                FileLogger.i("PdfImportManager", "Found $duplicateCount duplicates out of ${deduped.size}")

                PdfImportResult(
                    transactions = deduped,
                    totalParsed = deduped.size,
                    duplicateCount = duplicateCount,
                    ocrUsed = ocrUsed
                )
            } catch (e: Exception) {
                FileLogger.e("PdfImportManager", "PDF import failed", e)
                throw e
            }
        }
}
