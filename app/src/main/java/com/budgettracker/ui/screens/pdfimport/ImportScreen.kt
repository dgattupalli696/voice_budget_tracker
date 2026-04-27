package com.budgettracker.ui.screens.pdfimport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgettracker.domain.model.ImportedTransaction
import com.budgettracker.domain.model.TransactionType
import com.budgettracker.ui.theme.Green40
import com.budgettracker.ui.theme.GreenLight
import com.budgettracker.ui.theme.Red40
import com.budgettracker.ui.theme.RedLight
import com.budgettracker.utils.CurrencyFormatter
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.processPdf(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Bank Statement") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = uiState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            label = "import_state"
        ) { state ->
            when (state) {
                is ImportUiState.Idle -> IdleContent(
                    onSelectPdf = { pdfLauncher.launch(arrayOf("application/pdf")) }
                )
                is ImportUiState.Processing -> ProcessingContent(progress = state.progress)
                is ImportUiState.PasswordRequired -> PasswordContent(
                    wrongPassword = state.wrongPassword,
                    onSubmitPassword = { password ->
                        viewModel.processPdfWithPassword(state.uri, password)
                    },
                    onBack = { viewModel.reset() }
                )
                is ImportUiState.Review -> ReviewContent(
                    state = state,
                    onToggle = viewModel::toggleTransaction,
                    onSelectAll = viewModel::selectAll,
                    onDeselectDuplicates = viewModel::deselectDuplicates,
                    onImport = viewModel::importSelected
                )
                is ImportUiState.Importing -> ImportingContent(
                    progress = state.progress,
                    total = state.total
                )
                is ImportUiState.Done -> DoneContent(
                    importedCount = state.importedCount,
                    skippedCount = state.skippedCount,
                    onDone = onNavigateBack
                )
                is ImportUiState.Error -> ErrorContent(
                    message = state.message,
                    onRetry = { pdfLauncher.launch(arrayOf("application/pdf")) },
                    onBack = { viewModel.reset() }
                )
            }
        }
    }
}

@Composable
private fun IdleContent(onSelectPdf: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Import Bank Statement",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select a PDF bank statement to extract and import transactions automatically",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onSelectPdf,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select PDF File", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Supported formats:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("• Text-based PDF statements", style = MaterialTheme.typography.bodySmall)
                Text("• Scanned/image PDFs (via OCR)", style = MaterialTheme.typography.bodySmall)
                Text("• Auto-detects table columns", style = MaterialTheme.typography.bodySmall)
                Text("• Duplicates are automatically flagged", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProcessingContent(progress: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = progress,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ReviewContent(
    state: ImportUiState.Review,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectDuplicates: () -> Unit,
    onImport: () -> Unit
) {
    val selectedCount = state.transactions.count { it.isSelected }

    Column(modifier = Modifier.fillMaxSize()) {
        // Summary card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Found ${state.totalCount} transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (state.duplicateCount > 0) {
                    Text(
                        "⚠️ ${state.duplicateCount} possible duplicates detected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "$selectedCount selected for import",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSelectAll,
                modifier = Modifier.weight(1f)
            ) {
                Text("Select All", maxLines = 1)
            }
            if (state.duplicateCount > 0) {
                OutlinedButton(
                    onClick = onDeselectDuplicates,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Hide Dupes", maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Transaction list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(state.transactions) { index, txn ->
                ImportedTransactionItem(
                    transaction = txn,
                    onToggle = { onToggle(index) }
                )
            }
        }

        // Import button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp
        ) {
            Button(
                onClick = onImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                enabled = selectedCount > 0,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import $selectedCount Transactions", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ImportedTransactionItem(
    transaction: ImportedTransaction,
    onToggle: () -> Unit
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                transaction.isDuplicate -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = transaction.isSelected,
                onCheckedChange = { onToggle() }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Category emoji
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (transaction.type == TransactionType.INCOME) GreenLight else RedLight
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = transaction.category.emoji, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (transaction.isDuplicate) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = "Duplicate",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "${transaction.category.displayName} • ${transaction.dateTime.format(dateFormat)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transaction.isDuplicate) {
                    Text(
                        text = "Possible duplicate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = CurrencyFormatter.formatRupeesWithSign(
                    transaction.amount,
                    transaction.type == TransactionType.INCOME
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (transaction.type == TransactionType.INCOME) Green40 else Red40
            )
        }
    }
}

@Composable
private fun ImportingContent(progress: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Importing transactions...",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$progress / $total",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) progress.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DoneContent(importedCount: Int, skippedCount: Int, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Green40
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Import Complete!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$importedCount transactions imported",
            style = MaterialTheme.typography.bodyLarge,
            color = Green40
        )
        if (skippedCount > 0) {
            Text(
                text = "$skippedCount transactions skipped",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Done", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PasswordContent(
    wrongPassword: Boolean,
    onSubmitPassword: (String) -> Unit,
    onBack: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Password Protected PDF",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (wrongPassword) "Incorrect password. Please try again."
                   else "This PDF requires a password to open.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (wrongPassword) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("PDF Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible)
                androidx.compose.ui.text.input.VisualTransformation.None
            else
                androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide" else "Show"
                    )
                }
            },
            isError = wrongPassword,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { if (password.isNotBlank()) onSubmitPassword(password) },
            enabled = password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.LockOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Unlock & Import")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Red40
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Import Failed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Another PDF")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Go Back")
        }
    }
}
