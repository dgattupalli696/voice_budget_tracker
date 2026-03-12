package com.budgettracker.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.ai.ModelDownloadManager
import com.budgettracker.ai.ModelDownloadState
import com.budgettracker.ai.TextCorrectionManager
import com.budgettracker.data.local.PreferencesManager
import com.budgettracker.utils.FileLogger
import com.google.ai.edge.litertlm.Backend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val modelDownloadManager: ModelDownloadManager,
    private val textCorrectionManager: TextCorrectionManager
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        FileLogger.i(TAG, "SettingsViewModel init")
        loadSettings()
        observeModelState()
    }

    private fun loadSettings() {
        FileLogger.i(TAG, "loadSettings()")
        val cacheSizeMB = modelDownloadManager.getModelsCacheSize() / 1024 / 1024
        _uiState.update { state ->
            state.copy(
                selectedLanguageCode = preferencesManager.voiceLanguageCode,
                availableModels = modelDownloadManager.availableModels,
                importedModels = modelDownloadManager.importedModels.value,
                selectedModelId = modelDownloadManager.selectedModelId.value,
                customModelPath = modelDownloadManager.customModelPath.value,
                modelCacheSizeMB = cacheSizeMB,
                modelDownloadState = if (modelDownloadManager.isModelAvailable()) 
                    ModelDownloadState.Downloaded 
                else 
                    ModelDownloadState.NotDownloaded
            )
        }
    }
    
    private fun observeModelState() {
        modelDownloadManager.downloadState
            .onEach { state ->
                _uiState.update { it.copy(modelDownloadState = state) }
            }
            .launchIn(viewModelScope)
            
        modelDownloadManager.selectedModelId
            .onEach { id ->
                _uiState.update { it.copy(selectedModelId = id) }
            }
            .launchIn(viewModelScope)
            
        modelDownloadManager.customModelPath
            .onEach { path ->
                _uiState.update { it.copy(customModelPath = path) }
            }
            .launchIn(viewModelScope)
            
        // Observe imported models list
        modelDownloadManager.importedModels
            .onEach { models ->
                val cacheSizeMB = modelDownloadManager.getModelsCacheSize() / 1024 / 1024
                _uiState.update { it.copy(importedModels = models, modelCacheSizeMB = cacheSizeMB) }
            }
            .launchIn(viewModelScope)
            
        // Observe model load errors
        textCorrectionManager.lastError
            .onEach { error ->
                _uiState.update { it.copy(modelLoadError = error) }
            }
            .launchIn(viewModelScope)
    }

    fun updateVoiceLanguage(languageCode: String) {
        preferencesManager.voiceLanguageCode = languageCode
        _uiState.update { state ->
            state.copy(selectedLanguageCode = languageCode)
        }
    }
    
    fun selectModel(modelId: String) {
        modelDownloadManager.selectModel(modelId)
    }
    
    fun selectBackend(backend: ModelBackend) {
        _uiState.update { it.copy(selectedBackend = backend) }
        val liteRtBackend = when (backend) {
            ModelBackend.CPU -> Backend.CPU()
            ModelBackend.GPU -> Backend.GPU()
            ModelBackend.NPU -> Backend.NPU()
        }
        textCorrectionManager.setBackend(liteRtBackend)
        
        // Reinitialize with new backend
        viewModelScope.launch {
            _uiState.update { it.copy(modelDownloadState = ModelDownloadState.Checking, modelLoadError = null) }
            textCorrectionManager.reinitialize()
            val isAvailable = textCorrectionManager.isAvailable()
            _uiState.update { 
                it.copy(
                    modelDownloadState = if (isAvailable) 
                        ModelDownloadState.Downloaded 
                    else 
                        ModelDownloadState.Error(textCorrectionManager.lastError.value ?: "Failed to load model")
                ) 
            }
        }
    }
    
    fun downloadAIModel(modelId: String? = null) {
        val id = modelId ?: _uiState.value.selectedModelId
        viewModelScope.launch {
            modelDownloadManager.downloadModel(id)
                .onEach { state ->
                    _uiState.update { it.copy(modelDownloadState = state) }
                    
                    // Reinitialize text correction when download completes
                    if (state is ModelDownloadState.Downloaded) {
                        textCorrectionManager.reinitialize()
                    }
                }
                .launchIn(this)
        }
    }
    
    fun deleteAIModel(modelId: String? = null) {
        if (modelId != null) {
            modelDownloadManager.deleteModel(modelId)
        } else {
            modelDownloadManager.deleteModel()
        }
        _uiState.update { it.copy(modelDownloadState = ModelDownloadState.NotDownloaded) }
        viewModelScope.launch {
            textCorrectionManager.reinitialize()
        }
    }
    
    fun setCustomModelPath(path: String) {
        modelDownloadManager.setCustomModelPath(path)
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(modelDownloadState = ModelDownloadState.Checking, modelLoadError = null) }
                textCorrectionManager.reinitialize()
                val isAvailable = textCorrectionManager.isAvailable()
                val error = textCorrectionManager.lastError.value
                _uiState.update { 
                    it.copy(
                        modelDownloadState = if (isAvailable) 
                            ModelDownloadState.Downloaded 
                        else 
                            ModelDownloadState.Error(error ?: "Model failed to load"),
                        modelLoadError = error
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        modelDownloadState = ModelDownloadState.Error("Error: ${e.message}"),
                        modelLoadError = e.message
                    ) 
                }
            }
        }
    }
    
    fun clearCustomModelPath() {
        modelDownloadManager.clearCustomModelPath()
        viewModelScope.launch {
            try {
                textCorrectionManager.reinitialize()
            } catch (e: Exception) {
                // Ignore errors when clearing
            }
        }
    }
    
    fun selectImportedModel(modelId: String) {
        modelDownloadManager.selectImportedModel(modelId)
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(modelDownloadState = ModelDownloadState.Checking, modelLoadError = null) }
                textCorrectionManager.reinitialize()
                val isAvailable = textCorrectionManager.isAvailable()
                val error = textCorrectionManager.lastError.value
                _uiState.update { 
                    it.copy(
                        modelDownloadState = if (isAvailable) 
                            ModelDownloadState.Downloaded 
                        else 
                            ModelDownloadState.Error(error ?: "Model failed to load"),
                        modelLoadError = error
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        modelDownloadState = ModelDownloadState.Error("Error: ${e.message}"),
                        modelLoadError = e.message
                    ) 
                }
            }
        }
    }
    
    fun removeImportedModel(modelId: String) {
        modelDownloadManager.removeImportedModel(modelId)
        val cacheSizeMB = modelDownloadManager.getModelsCacheSize() / 1024 / 1024
        _uiState.update { it.copy(modelCacheSizeMB = cacheSizeMB) }
    }
    
    fun deleteImportedModelFile(modelId: String) {
        modelDownloadManager.deleteImportedModelFile(modelId)
        val cacheSizeMB = modelDownloadManager.getModelsCacheSize() / 1024 / 1024
        _uiState.update { 
            it.copy(
                modelCacheSizeMB = cacheSizeMB,
                modelDownloadState = ModelDownloadState.NotDownloaded
            ) 
        }
    }
    
    fun clearAllModelCache() {
        modelDownloadManager.deleteAllModels()
        _uiState.update { 
            it.copy(
                modelCacheSizeMB = 0,
                modelDownloadState = ModelDownloadState.NotDownloaded,
                customModelPath = null
            ) 
        }
        viewModelScope.launch {
            textCorrectionManager.reinitialize()
        }
    }
    
    fun resolveFilePath(context: Context, uri: Uri): String? {
        return try {
            // For content:// URIs, we need to copy the file to app storage
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val fileName = getFileName(context, uri) ?: "custom_model.task"
                val outputFile = File(context.filesDir, "models/$fileName")
                outputFile.parentFile?.mkdirs()
                
                outputFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
                
                Log.d("SettingsViewModel", "Copied model to: ${outputFile.absolutePath}")
                outputFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Failed to resolve file path", e)
            null
        }
    }
    
    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
