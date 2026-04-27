package com.budgettracker.ui.screens.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgettracker.domain.model.AccountType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onSetupComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome — Add Your Accounts") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (uiState.error != null) {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Button(
                        onClick = { viewModel.finish() },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Saving...")
                        } else {
                            Text("Get Started")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Add your bank accounts, debit cards and credit cards. " +
                        "Pick one as the default — it'll be used when you don't specify an account in the AI chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            uiState.drafts.forEachIndexed { index, draft ->
                AccountDraftCard(
                    index = index,
                    draft = draft,
                    canRemove = uiState.drafts.size > 1,
                    onName = { viewModel.updateName(index, it) },
                    onType = { viewModel.updateType(index, it) },
                    onLast4 = { viewModel.updateLast4(index, it) },
                    onBalance = { viewModel.updateInitialBalance(index, it) },
                    onSetDefault = { viewModel.setDefault(index) },
                    onRemove = { viewModel.removeDraft(index) }
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = { viewModel.addDraft() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Another Account")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDraftCard(
    index: Int,
    draft: AccountDraft,
    canRemove: Boolean,
    onName: (String) -> Unit,
    onType: (AccountType) -> Unit,
    onLast4: (String) -> Unit,
    onBalance: (String) -> Unit,
    onSetDefault: () -> Unit,
    onRemove: () -> Unit
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Account ${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Remove account")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = draft.name,
                onValueChange = onName,
                label = { Text("Name (e.g. HDFC Savings)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = typeMenuExpanded,
                onExpandedChange = { typeMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = "${draft.type.emoji} ${draft.type.displayName}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false }
                ) {
                    AccountType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text("${type.emoji} ${type.displayName}") },
                            onClick = {
                                onType(type)
                                typeMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = draft.last4,
                onValueChange = onLast4,
                label = { Text("Last 4 digits (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = draft.initialBalance,
                onValueChange = onBalance,
                label = { Text("Initial balance (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = draft.isDefault, onClick = onSetDefault)
                Text("Use as default for AI chat", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
