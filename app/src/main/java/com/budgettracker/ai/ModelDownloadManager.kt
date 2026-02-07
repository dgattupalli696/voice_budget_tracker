package com.budgettracker.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class ModelDownloadState {
    object NotDownloaded : ModelDownloadState()
    object Checking : ModelDownloadState()
    data class Downloading(val progress: Int, val downloadedMB: Int = 0, val totalMB: Int = 0) : ModelDownloadState()
    object Downloaded : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

data class AIModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val size: String,
    val sizeBytes: Long,
    val url: String,
    val fileName: String,
    val isImported: Boolean = false  // True for user-imported models
)

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "model_prefs", Context.MODE_PRIVATE
    )
    
    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()
    
    private val _selectedModelId = MutableStateFlow("gemma_2b_gpu")
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()
    
    private val _customModelPath = MutableStateFlow<String?>(null)
    val customModelPath: StateFlow<String?> = _customModelPath.asStateFlow()
    
    private val _importedModels = MutableStateFlow<List<AIModelInfo>>(emptyList())
    val importedModels: StateFlow<List<AIModelInfo>> = _importedModels.asStateFlow()
    
    private val modelDir = File(context.filesDir, "models")
    
    @Volatile
    private var isDownloading = false
    
    // Available models - User needs to download these manually from Hugging Face
    // as they require authentication/license agreement
    val availableModels = listOf(
        AIModelInfo(
            id = "gemma3_1b",
            name = "Gemma 3 1B (Recommended)",
            description = "Smaller, faster model. Download from Hugging Face.",
            size = "~530 MB",
            sizeBytes = 530_000_000L,
            url = "https://huggingface.co/litert-community/Gemma3-1B-IT",
            fileName = "gemma3_1b.task"
        ),
        AIModelInfo(
            id = "gemma2_2b",
            name = "Gemma 2 2B",
            description = "Larger model with better quality. Download from Hugging Face.",
            size = "~2.7 GB",
            sizeBytes = 2_700_000_000L,
            url = "https://huggingface.co/litert-community/Gemma2-2B-IT",
            fileName = "gemma2_2b.task"
        ),
        AIModelInfo(
            id = "phi2",
            name = "Phi-2",
            description = "Microsoft's small language model. Download from Hugging Face.",
            size = "~1.4 GB",
            sizeBytes = 1_400_000_000L,
            url = "https://huggingface.co/litert-community/Phi-2",
            fileName = "phi2.task"
        )
    )
    
    init {
        modelDir.mkdirs()
        loadImportedModels()
        checkModelStatus()
    }
    
    // Load saved imported models from preferences
    private fun loadImportedModels() {
        val savedModels = prefs.getStringSet(KEY_IMPORTED_MODELS, emptySet()) ?: emptySet()
        val models = savedModels.mapNotNull { entry ->
            try {
                val parts = entry.split("|")
                if (parts.size >= 3) {
                    val path = parts[0]
                    val name = parts[1]
                    val size = parts[2]
                    val file = File(path)
                    if (file.exists()) {
                        AIModelInfo(
                            id = "imported_${file.name.hashCode()}",
                            name = name,
                            description = "Imported model",
                            size = size,
                            sizeBytes = file.length(),
                            url = "",
                            fileName = file.name,
                            isImported = true
                        ).also { it to path }  // Store path association
                    } else null
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse imported model: $entry", e)
                null
            }
        }
        _importedModels.value = models
        
        // Load last used custom path
        prefs.getString(KEY_CURRENT_MODEL_PATH, null)?.let { path ->
            if (File(path).exists()) {
                _customModelPath.value = path
            }
        }
    }
    
    // Save imported model to preferences
    fun saveImportedModel(path: String, name: String) {
        val file = File(path)
        if (!file.exists()) return
        
        val sizeMB = file.length() / 1024 / 1024
        val sizeStr = if (sizeMB > 1024) "~${sizeMB / 1024} GB" else "~$sizeMB MB"
        
        val savedModels = prefs.getStringSet(KEY_IMPORTED_MODELS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        savedModels.add("$path|$name|$sizeStr")
        prefs.edit().putStringSet(KEY_IMPORTED_MODELS, savedModels).apply()
        
        // Also save as current model
        prefs.edit().putString(KEY_CURRENT_MODEL_PATH, path).apply()
        
        loadImportedModels()
        Log.d(TAG, "Saved imported model: $name at $path")
    }
    
    // Remove imported model from list (doesn't delete file)
    fun removeImportedModel(modelId: String) {
        val model = _importedModels.value.find { it.id == modelId }
        if (model != null) {
            val savedModels = prefs.getStringSet(KEY_IMPORTED_MODELS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            savedModels.removeIf { it.contains(model.fileName) }
            prefs.edit().putStringSet(KEY_IMPORTED_MODELS, savedModels).apply()
            loadImportedModels()
        }
    }
    
    // Delete imported model file from cache
    fun deleteImportedModelFile(modelId: String) {
        val model = _importedModels.value.find { it.id == modelId }
        if (model != null) {
            // Find the actual path from preferences
            val savedModels = prefs.getStringSet(KEY_IMPORTED_MODELS, emptySet()) ?: emptySet()
            savedModels.find { it.contains(model.fileName) }?.let { entry ->
                val path = entry.split("|").firstOrNull()
                path?.let {
                    val file = File(it)
                    if (file.exists() && file.absolutePath.startsWith(context.filesDir.absolutePath)) {
                        file.delete()
                        Log.d(TAG, "Deleted model file: $path")
                    }
                }
            }
            removeImportedModel(modelId)
            
            // Clear current if it was the deleted one
            if (_customModelPath.value?.contains(model.fileName) == true) {
                _customModelPath.value = null
                prefs.edit().remove(KEY_CURRENT_MODEL_PATH).apply()
            }
            checkModelStatus()
        }
    }
    
    // Get total cache size used by models
    fun getModelsCacheSize(): Long {
        var totalSize = 0L
        modelDir.listFiles()?.forEach { file ->
            totalSize += file.length()
        }
        return totalSize
    }
    
    fun checkModelStatus() {
        _downloadState.value = ModelDownloadState.Checking
        
        // Check custom path first
        _customModelPath.value?.let { path ->
            val customFile = File(path)
            if (customFile.exists() && customFile.length() > 0) {
                _downloadState.value = ModelDownloadState.Downloaded
                Log.d(TAG, "Custom model found: $path")
                return
            }
        }
        
        // Check downloaded models
        val modelFile = getCurrentModelFile()
        if (modelFile?.exists() == true && modelFile.length() > 0) {
            _downloadState.value = ModelDownloadState.Downloaded
            Log.d(TAG, "Model found: ${modelFile.absolutePath}")
        } else {
            _downloadState.value = ModelDownloadState.NotDownloaded
            Log.d(TAG, "Model not found")
        }
    }
    
    private fun getCurrentModelFile(): File? {
        val selectedModel = availableModels.find { it.id == _selectedModelId.value }
        return selectedModel?.let { File(modelDir, it.fileName) }
    }
    
    fun isModelAvailable(): Boolean {
        // Check custom path first
        _customModelPath.value?.let { path ->
            val customFile = File(path)
            if (customFile.exists() && customFile.length() > 0) {
                return true
            }
        }
        
        val modelFile = getCurrentModelFile()
        return modelFile?.exists() == true && (modelFile.length() > 0)
    }
    
    fun getModelPath(): String? {
        // Return custom path if set and valid
        _customModelPath.value?.let { path ->
            val customFile = File(path)
            if (customFile.exists() && customFile.length() > 0) {
                return path
            }
        }
        
        val modelFile = getCurrentModelFile()
        return if (modelFile?.exists() == true && modelFile.length() > 0) {
            modelFile.absolutePath
        } else null
    }
    
    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
        checkModelStatus()
    }
    
    fun setCustomModelPath(path: String?, saveToPermanentList: Boolean = true) {
        _customModelPath.value = path
        if (path != null) {
            val file = File(path)
            if (file.exists() && file.length() > 0) {
                _downloadState.value = ModelDownloadState.Downloaded
                Log.d(TAG, "Custom model path set: $path")
                
                // Save to permanent list so user doesn't have to re-select
                if (saveToPermanentList) {
                    val modelName = file.nameWithoutExtension.replace("_", " ").replaceFirstChar { it.uppercase() }
                    saveImportedModel(path, modelName)
                }
            } else {
                _downloadState.value = ModelDownloadState.Error("File not found or empty: $path")
            }
        } else {
            checkModelStatus()
        }
    }
    
    // Select an imported model by its id
    fun selectImportedModel(modelId: String) {
        val model = _importedModels.value.find { it.id == modelId }
        if (model != null) {
            // Find the path from preferences
            val savedModels = prefs.getStringSet(KEY_IMPORTED_MODELS, emptySet()) ?: emptySet()
            savedModels.find { it.contains(model.fileName) }?.let { entry ->
                val path = entry.split("|").firstOrNull()
                path?.let {
                    setCustomModelPath(it, saveToPermanentList = false)
                }
            }
        }
    }
    
    fun clearCustomModelPath() {
        _customModelPath.value = null
        checkModelStatus()
    }
    
    fun downloadModel(modelId: String = _selectedModelId.value): Flow<ModelDownloadState> = flow {
        if (isDownloading) {
            emit(ModelDownloadState.Error("Download already in progress"))
            return@flow
        }
        
        val modelInfo = availableModels.find { it.id == modelId }
        if (modelInfo == null) {
            emit(ModelDownloadState.Error("Model not found"))
            return@flow
        }
        
        isDownloading = true
        
        try {
            modelDir.mkdirs()
            val targetFile = File(modelDir, modelInfo.fileName)
            val tempFile = File(modelDir, "${modelInfo.fileName}.tmp")
            
            // Delete existing temp file
            if (tempFile.exists()) tempFile.delete()
            
            Log.d(TAG, "Starting download: ${modelInfo.url}")
            emit(ModelDownloadState.Downloading(0, 0, (modelInfo.sizeBytes / 1_000_000).toInt()))
            _downloadState.value = ModelDownloadState.Downloading(0, 0, (modelInfo.sizeBytes / 1_000_000).toInt())
            
            val url = URL(modelInfo.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "BudgetTracker/1.0")
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned: $responseCode ${connection.responseMessage}")
            }
            
            val totalSize = connection.contentLength.toLong().takeIf { it > 0 } ?: modelInfo.sizeBytes
            val totalMB = (totalSize / 1_000_000).toInt()
            var downloadedBytes = 0L
            
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastProgressUpdate = 0
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        val progress = ((downloadedBytes * 100) / totalSize).toInt()
                        val downloadedMB = (downloadedBytes / 1_000_000).toInt()
                        
                        // Update every 1%
                        if (progress > lastProgressUpdate) {
                            lastProgressUpdate = progress
                            val state = ModelDownloadState.Downloading(progress, downloadedMB, totalMB)
                            emit(state)
                            _downloadState.value = state
                        }
                    }
                }
            }
            
            connection.disconnect()
            
            // Verify download
            if (tempFile.length() < totalSize * 0.9) {
                tempFile.delete()
                throw Exception("Download incomplete: ${tempFile.length()} bytes (expected ~$totalSize)")
            }
            
            // Move to final location
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            
            _selectedModelId.value = modelId
            _downloadState.value = ModelDownloadState.Downloaded
            emit(ModelDownloadState.Downloaded)
            Log.d(TAG, "Download complete: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            val errorMessage = when {
                e.message?.contains("Unable to resolve host") == true -> "No internet connection"
                e.message?.contains("timeout") == true -> "Connection timeout - try again"
                e.message?.contains("403") == true -> "Access denied - URL may have changed"
                e.message?.contains("404") == true -> "Model file not found on server"
                else -> e.message ?: "Unknown error"
            }
            _downloadState.value = ModelDownloadState.Error(errorMessage)
            emit(ModelDownloadState.Error(errorMessage))
        } finally {
            isDownloading = false
        }
    }.flowOn(Dispatchers.IO)
    
    fun cancelDownload() {
        isDownloading = false
        _downloadState.value = ModelDownloadState.NotDownloaded
    }
    
    fun deleteModel(modelId: String? = null) {
        val id = modelId ?: _selectedModelId.value
        val modelInfo = availableModels.find { it.id == id }
        modelInfo?.let {
            val file = File(modelDir, it.fileName)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted model: ${file.absolutePath}")
            }
        }
        checkModelStatus()
    }
    
    fun deleteAllModels() {
        modelDir.listFiles()?.forEach { it.delete() }
        _customModelPath.value = null
        _downloadState.value = ModelDownloadState.NotDownloaded
    }
    
    fun getDownloadedModels(): List<AIModelInfo> {
        return availableModels.filter { model ->
            File(modelDir, model.fileName).exists()
        }
    }
    
    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val KEY_IMPORTED_MODELS = "imported_models"
        private const val KEY_CURRENT_MODEL_PATH = "current_model_path"
    }
}
