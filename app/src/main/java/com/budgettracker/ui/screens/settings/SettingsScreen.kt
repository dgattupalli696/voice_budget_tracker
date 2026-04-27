package com.budgettracker.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgettracker.ai.ModelDownloadState
import com.budgettracker.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // File picker for custom model path
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { 
            // Get the actual file path from content resolver
            val path = viewModel.resolveFilePath(context, it)
            if (path != null) {
                viewModel.setCustomModelPath(path)
            }
        }
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            languages = uiState.availableLanguages,
            selectedCode = uiState.selectedLanguageCode,
            onLanguageSelected = { code ->
                viewModel.updateVoiceLanguage(code)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
    
    if (showModelDialog) {
        ModelManagementDialog(
            downloadState = uiState.modelDownloadState,
            customModelPath = uiState.customModelPath,
            importedModels = uiState.importedModels,
            selectedBackend = uiState.selectedBackend,
            modelLoadError = uiState.modelLoadError,
            modelCacheSizeMB = uiState.modelCacheSizeMB,
            onBrowseCustomPath = { filePickerLauncher.launch(arrayOf("*/*")) },
            onClearCustomPath = { viewModel.clearCustomModelPath() },
            onBackendSelected = { viewModel.selectBackend(it) },
            onSelectImportedModel = { viewModel.selectImportedModel(it) },
            onRemoveImportedModel = { viewModel.removeImportedModel(it) },
            onDeleteImportedModelFile = { viewModel.deleteImportedModelFile(it) },
            onClearAllCache = { viewModel.clearAllModelCache() },
            onDismiss = { showModelDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Voice Settings Section
            Text(
                text = "Voice Input",
                style = MaterialTheme.typography.titleSmall,
                color = Primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // Language Setting
            ListItem(
                headlineContent = { Text("Voice Language") },
                supportingContent = {
                    val selectedLanguage = uiState.availableLanguages.find { 
                        it.code == uiState.selectedLanguageCode 
                    }
                    Text(selectedLanguage?.displayName ?: "English (India)")
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = Primary
                    )
                },
                modifier = Modifier.clickable { showLanguageDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            
            // AI Model Section
            Text(
                text = "AI Features",
                style = MaterialTheme.typography.titleSmall,
                color = Primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            
            // AI Model Setting
            ListItem(
                headlineContent = { Text("AI Text Correction") },
                supportingContent = {
                    val statusText = when {
                        uiState.customModelPath != null && uiState.modelDownloadState is ModelDownloadState.Downloaded -> 
                            "✓ Custom model loaded"
                        uiState.modelDownloadState is ModelDownloadState.Downloaded -> {
                            val modelName = uiState.availableModels.find { it.id == uiState.selectedModelId }?.name ?: "Model"
                            "✓ $modelName ready"
                        }
                        uiState.modelDownloadState is ModelDownloadState.Downloading -> {
                            val state = uiState.modelDownloadState as ModelDownloadState.Downloading
                            "Downloading... ${state.progress}%"
                        }
                        uiState.modelDownloadState is ModelDownloadState.Error -> 
                            "Error: ${(uiState.modelDownloadState as ModelDownloadState.Error).message}"
                        else -> "Not downloaded - using basic corrections"
                    }
                    Text(statusText)
                },
                leadingContent = {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = if (uiState.modelDownloadState is ModelDownloadState.Downloaded) 
                            Primary else MaterialTheme.colorScheme.outline
                    )
                },
                trailingContent = {
                    when (uiState.modelDownloadState) {
                        is ModelDownloadState.Downloading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        is ModelDownloadState.Downloaded -> {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Downloaded",
                                tint = Primary
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = "Setup",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                modifier = Modifier.clickable { showModelDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // About Section
            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall,
                color = Primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0.0") }
            )
        }
    }
}

@Composable
private fun LanguageSelectionDialog(
    languages: List<VoiceLanguage>,
    selectedCode: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Voice Language") },
        text = {
            LazyColumn {
                items(languages) { language ->
                    ListItem(
                        headlineContent = { Text(language.displayName) },
                        trailingContent = {
                            if (language.code == selectedCode) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Primary
                                )
                            }
                        },
                        modifier = Modifier.clickable { onLanguageSelected(language.code) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ModelManagementDialog(
    downloadState: ModelDownloadState,
    customModelPath: String?,
    importedModels: List<com.budgettracker.ai.AIModelInfo>,
    selectedBackend: ModelBackend,
    modelLoadError: String?,
    modelCacheSizeMB: Long,
    onBrowseCustomPath: () -> Unit,
    onClearCustomPath: () -> Unit,
    onBackendSelected: (ModelBackend) -> Unit,
    onSelectImportedModel: (String) -> Unit,
    onRemoveImportedModel: (String) -> Unit,
    onDeleteImportedModelFile: (String) -> Unit,
    onClearAllCache: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) }
    
    // Delete confirmation dialog
    if (showDeleteConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete Model File?") },
            text = { Text("This will delete the model file from app storage. You'll need to import it again to use it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteImportedModelFile(showDeleteConfirmDialog!!)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Model Settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Models") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Guide") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Storage") }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                when (selectedTab) {
                    0 -> {
                        // Imported models list
                        if (importedModels.isNotEmpty()) {
                            Text(
                                text = "Saved Models:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            importedModels.forEach { model ->
                                val isSelected = customModelPath?.contains(model.fileName) == true
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectImportedModel(model.id) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = Primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = model.name,
                                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                                )
                                            }
                                            Text(
                                                text = model.size,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { showDeleteConfirmDialog = model.id }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        
                        // Backend selection
                        Text(
                            text = "Processing Backend:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ModelBackend.entries.forEach { backend ->
                                FilterChip(
                                    selected = selectedBackend == backend,
                                    onClick = { onBackendSelected(backend) },
                                    label = { Text(backend.displayName) },
                                    leadingIcon = if (selectedBackend == backend) {
                                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Show model load error if any
                        if (modelLoadError != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "⚠️ Model Load Error:",
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = modelLoadError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Try:\n• A different backend (CPU is most compatible)\n• A smaller model (Gemma3 1B, Qwen 0.6B)\n• Check if file is corrupted",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        
                        // Import new model button
                        Button(
                            onClick = onBrowseCustomPath,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import New Model")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Supported: .litertlm and .task model files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    1 -> {
                        // Download Guide tab
                        Text(
                            text = "How to get AI models:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Step 1: Create Hugging Face Account",
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Go to huggingface.co and sign up (free)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Step 2: Accept Model License",
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Visit one of these model pages and accept the license:\n" +
                                        "• litert-community/gemma-4-E2B-it-litert-lm (~2.4GB)\n" +
                                        "• litert-community/Gemma3-1B-IT (~557MB)\n" +
                                        "• litert-community/Qwen2.5-1.5B-Instruct (~1.5GB)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Step 3: Download Model File",
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Download the .litertlm file from 'Files and versions' tab:\n" +
                                        "• gemma-4-E2B-it.litertlm (~2.4GB) — best quality\n" +
                                        "• gemma3-1b-it-int4.litertlm (~557MB) — fast\n" +
                                        "• Or use in-app download from 'Available Models'",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Step 4: Load in App",
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Go to 'Load Model' tab and browse to select the downloaded model file",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Recommended: Gemma3-1B-IT (~557MB) for speed, Gemma 4 E2B (~2.4GB) for best quality",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Primary
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "⚠️ Note: Only .litertlm / .task models from litert-community on Hugging Face are supported. Use the in-app download or manually download from the model's 'Files and versions' tab.",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    2 -> {
                        // Storage tab
                        Text(
                            text = "Model Storage",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Cache Used:",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (modelCacheSizeMB > 1024) 
                                        "${modelCacheSizeMB / 1024}.${(modelCacheSizeMB % 1024) / 100} GB"
                                    else 
                                        "$modelCacheSizeMB MB",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (modelCacheSizeMB > 2048) 
                                        MaterialTheme.colorScheme.error 
                                    else 
                                        Primary
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (importedModels.isNotEmpty()) {
                            Text(
                                text = "Imported models: ${importedModels.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        OutlinedButton(
                            onClick = onClearAllCache,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear All Model Cache")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "This will delete all imported model files from app storage. You'll need to re-import models to use them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = null
    )
}
