package com.budgettracker.ui.screens.pdfimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.data.pdf.PasswordRequiredException
import com.budgettracker.data.pdf.PdfImportManager
import com.budgettracker.data.pdf.WrongPasswordException
import com.budgettracker.data.repository.TransactionRepository
import com.budgettracker.domain.model.ImportedTransaction
import com.budgettracker.domain.model.Transaction
import com.budgettracker.utils.FileLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val pdfImportManager: PdfImportManager,
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun processPdf(uri: Uri) {
        processPdfInternal(uri, null)
    }
    
    fun processPdfWithPassword(uri: Uri, password: String) {
        processPdfInternal(uri, password)
    }
    
    private fun processPdfInternal(uri: Uri, password: String?) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Processing("Reading PDF...")
            try {
                val result = pdfImportManager.processUri(uri, password) { progress ->
                    _uiState.value = ImportUiState.Processing(progress)
                }
                _uiState.value = ImportUiState.Review(
                    transactions = result.transactions,
                    totalCount = result.totalParsed,
                    duplicateCount = result.duplicateCount
                )
            } catch (e: PasswordRequiredException) {
                _uiState.value = ImportUiState.PasswordRequired(uri, wrongPassword = false)
            } catch (e: WrongPasswordException) {
                _uiState.value = ImportUiState.PasswordRequired(uri, wrongPassword = true)
            } catch (e: Exception) {
                FileLogger.e("ImportViewModel", "PDF processing failed", e)
                _uiState.value = ImportUiState.Error(
                    e.message ?: "Failed to process PDF"
                )
            }
        }
    }

    fun toggleTransaction(index: Int) {
        val current = _uiState.value
        if (current is ImportUiState.Review) {
            val updated = current.transactions.toMutableList()
            val txn = updated[index]
            updated[index] = txn.copy(isSelected = !txn.isSelected)
            _uiState.value = current.copy(transactions = updated)
        }
    }

    fun selectAll() {
        val current = _uiState.value
        if (current is ImportUiState.Review) {
            _uiState.value = current.copy(
                transactions = current.transactions.map { it.copy(isSelected = true) }
            )
        }
    }

    fun deselectDuplicates() {
        val current = _uiState.value
        if (current is ImportUiState.Review) {
            _uiState.value = current.copy(
                transactions = current.transactions.map {
                    if (it.isDuplicate) it.copy(isSelected = false) else it
                }
            )
        }
    }

    fun importSelected() {
        val current = _uiState.value
        if (current !is ImportUiState.Review) return

        val toImport = current.transactions.filter { it.isSelected }
        val skipped = current.transactions.size - toImport.size

        viewModelScope.launch {
            _uiState.value = ImportUiState.Importing(0, toImport.size)
            try {
                toImport.forEachIndexed { index, imported ->
                    val transaction = Transaction(
                        amount = imported.amount,
                        description = imported.description,
                        category = imported.category,
                        type = imported.type,
                        dateTime = imported.dateTime
                    )
                    repository.insertTransaction(transaction)
                    _uiState.value = ImportUiState.Importing(index + 1, toImport.size)
                }
                _uiState.value = ImportUiState.Done(
                    importedCount = toImport.size,
                    skippedCount = skipped
                )
            } catch (e: Exception) {
                FileLogger.e("ImportViewModel", "Import failed", e)
                _uiState.value = ImportUiState.Error(
                    e.message ?: "Failed to import transactions"
                )
            }
        }
    }

    fun reset() {
        _uiState.value = ImportUiState.Idle
    }
}
