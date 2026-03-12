package com.budgettracker.ui.screens.pdfimport

import com.budgettracker.domain.model.ImportedTransaction

sealed class ImportUiState {
    data object Idle : ImportUiState()
    data class Processing(val progress: String = "Reading PDF...") : ImportUiState()
    data class Review(
        val transactions: List<ImportedTransaction>,
        val totalCount: Int,
        val duplicateCount: Int
    ) : ImportUiState()
    data class Importing(val progress: Int, val total: Int) : ImportUiState()
    data class Done(val importedCount: Int, val skippedCount: Int) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}
