package com.budgettracker.ui.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.data.local.PreferencesManager
import com.budgettracker.data.repository.AccountRepository
import com.budgettracker.domain.model.Account
import com.budgettracker.domain.model.AccountType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun addDraft() {
        _uiState.update { state ->
            state.copy(drafts = state.drafts + AccountDraft())
        }
    }

    fun removeDraft(index: Int) {
        _uiState.update { state ->
            val list = state.drafts.toMutableList()
            if (list.size > 1 && index in list.indices) {
                val wasDefault = list[index].isDefault
                list.removeAt(index)
                if (wasDefault && list.isNotEmpty()) {
                    list[0] = list[0].copy(isDefault = true)
                }
            }
            state.copy(drafts = list)
        }
    }

    fun updateName(index: Int, value: String) = mutate(index) { it.copy(name = value) }
    fun updateType(index: Int, value: AccountType) = mutate(index) { it.copy(type = value) }
    fun updateLast4(index: Int, value: String) {
        val sanitized = value.filter { it.isDigit() }.take(4)
        mutate(index) { it.copy(last4 = sanitized) }
    }
    fun updateInitialBalance(index: Int, value: String) {
        if (value.isEmpty() || value.matches(Regex("^-?\\d*\\.?\\d{0,2}$"))) {
            mutate(index) { it.copy(initialBalance = value) }
        }
    }

    fun setDefault(index: Int) {
        _uiState.update { state ->
            state.copy(
                drafts = state.drafts.mapIndexed { i, d ->
                    d.copy(isDefault = i == index)
                }
            )
        }
    }

    private fun mutate(index: Int, transform: (AccountDraft) -> AccountDraft) {
        _uiState.update { state ->
            val list = state.drafts.toMutableList()
            if (index in list.indices) list[index] = transform(list[index])
            state.copy(drafts = list, error = null)
        }
    }

    fun finish() {
        val state = _uiState.value
        val cleaned = state.drafts.filter { it.name.isNotBlank() }
        if (cleaned.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one account") }
            return
        }
        if (cleaned.none { it.isDefault }) {
            _uiState.update { it.copy(error = "Pick a default account") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val accounts = cleaned.map { d ->
                    Account(
                        name = d.name.trim(),
                        type = d.type,
                        last4 = d.last4.trim(),
                        initialBalance = d.initialBalance.toDoubleOrNull() ?: 0.0,
                        isDefault = d.isDefault
                    )
                }
                val ids = accountRepository.insertAll(accounts)
                val defaultIndex = cleaned.indexOfFirst { it.isDefault }
                if (defaultIndex >= 0 && defaultIndex < ids.size) {
                    preferencesManager.defaultAccountId = ids[defaultIndex]
                }
                preferencesManager.isSetupComplete = true
                _uiState.update { it.copy(isSaving = false, isComplete = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to save: ${e.message}")
                }
            }
        }
    }
}
