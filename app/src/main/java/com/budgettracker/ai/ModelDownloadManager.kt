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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class ModelDownloadState {
    object NotDownloaded : ModelDownloadState()
    object Checking : ModelDownloadState()
    data class Downloading(
        val progress: Int,
        val downloadedMB: Int = 0,
        val totalMB: Int = 0,
        val bytesPerSecond: Long = 0,
        val remainingSeconds: Long = 0,
        val modelId: String = ""
    ) : ModelDownloadState()
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
    val isImported: Boolean = false
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
    
    private val _selectedModelId = MutableStateFlow("gemma3_1b")
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()
    
    private val _customModelPath = MutableStateFlow<String?>(null)
    val customModelPath: StateFlow<String?> = _customModelPath.asStateFlow()
    
    private val _importedModels = MutableStateFlow<List<AIModelInfo>>(emptyList())
    val importedModels: StateFlow<List<AIModelInfo>> = _importedModels.asStateFlow()
    
    // Use external files dir (doesn't count against storage quota, survives app updates)
    // Falls back to internal filesDir if external is unavailable
    private val modelDir: File = (context.getExternalFilesDir(null)
        ?: context.filesDir).let { File(it, "models") }
    
    @Volatile
    private var isDownloading = false
    
    @Volatile
    private var cancelRequested = false
    
    // Available models from Hugging Face litert-community
    // Based on google-ai-edge/gallery model_allowlists/1_0_12.json
    val availableModels = listOf(
        AIModelInfo(
            id = "gemma4_e2b",
            name = "Gemma 4 E2B (Best Quality)",
            description = "Latest Gemma 4 with thinking, vision & audio. Requires 8GB+ RAM.",
            size = "~2.4 GB",
            sizeBytes = 2_583_085_056L,
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "gemma-4-E2B-it.litertlm"
        ),
        AIModelInfo(
            id = "gemma3_1b",
            name = "Gemma3 1B IT (Recommended)",
            description = "Fast and efficient. Best for budget tracking on most devices. 6GB+ RAM.",
            size = "~557 MB",
            sizeBytes = 584_417_280L,
            url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm",
            fileName = "gemma3-1b-it-int4.litertlm"
        ),
        AIModelInfo(
            id = "qwen25_15b",
            name = "Qwen2.5 1.5B Instruct",
            description = "Good quality with 4K context. 6GB+ RAM.",
            size = "~1.5 GB",
            sizeBytes = 1_597_931_520L,
            url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
        ),
        AIModelInfo(
            id = "deepseek_r1_15b",
            name = "DeepSeek R1 Distill 1.5B",
            description = "Reasoning-focused model with chain-of-thought. 6GB+ RAM.",
            size = "~1.7 GB",
            sizeBytes = 1_833_451_520L,
            url = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm"
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
    
    fun downloadModel(modelId: String): Flow<ModelDownloadState> = flow {
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
        cancelRequested = false
        
        try {
            modelDir.mkdirs()
            val targetFile = File(modelDir, modelInfo.fileName)
            val tempFile = File(modelDir, "${modelInfo.fileName}.tmp")
            
            Log.d(TAG, "Starting download: ${modelInfo.url}")
            Log.d(TAG, "Target: ${targetFile.absolutePath}")
            
            val totalMB = (modelInfo.sizeBytes / 1_000_000).toInt()
            emit(ModelDownloadState.Downloading(0, 0, totalMB, modelId = modelId))
            _downloadState.value = ModelDownloadState.Downloading(0, 0, totalMB, modelId = modelId)
            
            val url = URL(modelInfo.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 300_000  // 5 min read timeout for large files
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "BudgetTracker/1.0")
            
            // HuggingFace access token for gated models (like Gemma)
            // Based on google-ai-edge/gallery DownloadWorker pattern
            val accessToken = getHuggingFaceToken()
            if (accessToken != null) {
                Log.d(TAG, "Using HF access token: ${accessToken.take(10)}...")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }
            
            // Resume support: check for partial download (like Google Gallery does)
            var resumeOffset = 0L
            if (tempFile.exists() && tempFile.length() > 0) {
                resumeOffset = tempFile.length()
                Log.d(TAG, "Resuming download from byte $resumeOffset")
                connection.setRequestProperty("Range", "bytes=$resumeOffset-")
                // Force non-compressed data for resume to work
                connection.setRequestProperty("Accept-Encoding", "identity")
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")
            
            when (responseCode) {
                HttpURLConnection.HTTP_UNAUTHORIZED, 403 -> {
                    connection.disconnect()
                    if (accessToken == null) {
                        throw Exception("This model requires a Hugging Face access token. " +
                            "Go to huggingface.co/settings/tokens to create one, " +
                            "then enter it in the token field below.")
                    } else {
                        throw Exception("Access denied. Your Hugging Face token may be invalid " +
                            "or you haven't accepted the model's license agreement. " +
                            "Visit the model page on huggingface.co and accept the license.")
                    }
                }
                HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL -> {
                    // OK - continue with download
                }
                else -> {
                    connection.disconnect()
                    throw Exception("Server returned HTTP $responseCode: ${connection.responseMessage}")
                }
            }
            
            // Parse total size from Content-Range or Content-Length
            var downloadedBytes = resumeOffset
            val totalSize: Long
            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                val contentRange = connection.getHeaderField("Content-Range")
                totalSize = if (contentRange != null) {
                    val rangeParts = contentRange.substringAfter("bytes ").split("/")
                    rangeParts.getOrNull(1)?.toLongOrNull() ?: modelInfo.sizeBytes
                } else {
                    modelInfo.sizeBytes
                }
                Log.d(TAG, "Resuming: downloaded=$downloadedBytes, total=$totalSize")
            } else {
                // Fresh download - delete any stale temp file
                if (tempFile.exists()) tempFile.delete()
                downloadedBytes = 0L
                totalSize = connection.contentLength.toLong()
                    .takeIf { it > 0 } ?: modelInfo.sizeBytes
            }
            
            val totalMBActual = (totalSize / 1_000_000).toInt()
            
            // Sliding window for download rate calculation (like Google Gallery)
            val bytesReadSizes = mutableListOf<Long>()
            val bytesReadLatencies = mutableListOf<Long>()
            
            connection.inputStream.use { input ->
                FileOutputStream(tempFile, resumeOffset > 0).use { output ->
                    val buffer = ByteArray(65536)  // 64KB buffer for large files
                    var bytesRead: Int
                    var lastProgressUpdate = System.currentTimeMillis()
                    var deltaBytes = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (cancelRequested) {
                            Log.d(TAG, "Download cancelled by user")
                            throw Exception("Download cancelled")
                        }
                        
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        deltaBytes += bytesRead
                        
                        // Update progress every 200ms (like Google Gallery)
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 200) {
                            // Calculate download rate using sliding window
                            var bytesPerSecond = 0L
                            if (lastProgressUpdate > 0) {
                                val elapsed = now - lastProgressUpdate
                                if (bytesReadSizes.size >= 5) bytesReadSizes.removeAt(0)
                                bytesReadSizes.add(deltaBytes)
                                if (bytesReadLatencies.size >= 5) bytesReadLatencies.removeAt(0)
                                bytesReadLatencies.add(elapsed)
                                
                                val totalLatency = bytesReadLatencies.sum()
                                if (totalLatency > 0) {
                                    bytesPerSecond = bytesReadSizes.sum() * 1000 / totalLatency
                                }
                                deltaBytes = 0L
                            }
                            
                            // Calculate remaining time
                            val remainingSeconds = if (bytesPerSecond > 0 && totalSize > 0) {
                                (totalSize - downloadedBytes) / bytesPerSecond
                            } else 0L
                            
                            val progress = if (totalSize > 0) {
                                ((downloadedBytes * 100) / totalSize).toInt().coerceIn(0, 99)
                            } else 0
                            val downloadedMB = (downloadedBytes / 1_000_000).toInt()
                            
                            val state = ModelDownloadState.Downloading(
                                progress, downloadedMB, totalMBActual,
                                bytesPerSecond, remainingSeconds, modelId = modelId
                            )
                            emit(state)
                            _downloadState.value = state
                            lastProgressUpdate = now
                        }
                    }
                }
            }
            
            connection.disconnect()
            
            // Verify download completeness
            val downloadedSize = tempFile.length()
            if (totalSize > 0 && downloadedSize < totalSize * 0.95) {
                throw Exception("Download incomplete: got ${downloadedSize / 1_000_000}MB, " +
                    "expected ~${totalSize / 1_000_000}MB. Try again to resume.")
            }
            
            // Move temp file to final location (atomic rename)
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
                e.message?.contains("cancelled") == true -> "Download cancelled"
                e.message?.contains("Hugging Face") == true -> e.message ?: "Auth required"
                e.message?.contains("Access denied") == true -> e.message ?: "Access denied"
                e.message?.contains("Unable to resolve host") == true -> 
                    "No internet connection. Check your network and try again."
                e.message?.contains("timeout") == true || e.message?.contains("Timeout") == true -> 
                    "Connection timed out. Try again on a stable connection."
                e.message?.contains("HTTP 403") == true -> 
                    "Access denied (403). Model may require a HuggingFace token."
                e.message?.contains("HTTP 404") == true -> 
                    "Model file not found on server (404). The URL may have changed."
                e.message?.contains("incomplete") == true -> e.message ?: "Download incomplete"
                else -> e.message ?: "Unknown download error"
            }
            _downloadState.value = ModelDownloadState.Error(errorMessage)
            emit(ModelDownloadState.Error(errorMessage))
        } finally {
            isDownloading = false
            cancelRequested = false
        }
    }.flowOn(Dispatchers.IO)
    
    fun cancelDownload() {
        cancelRequested = true
        isDownloading = false
        _downloadState.value = ModelDownloadState.NotDownloaded
    }
    
    // HuggingFace access token management
    // Based on google-ai-edge/gallery pattern for gated model access
    fun setHuggingFaceToken(token: String?) {
        if (token.isNullOrBlank()) {
            prefs.edit().remove(KEY_HF_TOKEN).apply()
        } else {
            prefs.edit().putString(KEY_HF_TOKEN, token.trim()).apply()
        }
    }
    
    fun getHuggingFaceToken(): String? {
        return prefs.getString(KEY_HF_TOKEN, null)?.takeIf { it.isNotBlank() }
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
        private const val KEY_HF_TOKEN = "hf_access_token"
        private const val TMP_FILE_EXT = "tmp"
    }
}
