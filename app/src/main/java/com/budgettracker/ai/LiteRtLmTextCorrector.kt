package com.budgettracker.ai

import android.content.Context
import android.os.Build
import com.budgettracker.utils.FileLogger
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Text corrector using LiteRT-LM with .litertlm models.
 * Runs entirely on-device for privacy and offline capability.
 * 
 * Supports:
 * - Gemma3-1B (~557 MB)
 * - Gemma-3n-E2B (~2.9 GB)
 * - phi-4-mini (~3.7 GB)
 * - qwen2.5-1.5b (~1.5 GB)
 * 
 * NOTE: Does NOT reliably support emulators.
 * Use a physical device (Samsung S24/S25, Pixel 8+ recommended).
 */
class LiteRtLmTextCorrector(
    private val context: Context,
    private val modelPath: String? = null,
    private val backend: Backend = Backend.CPU()
) : TextCorrector {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var isInitialized = false
    private var initError: String? = null
    
    fun getInitializationError(): String? = initError

    override suspend fun initialize(): Boolean {
        FileLogger.i(TAG, "initialize() called, modelPath=$modelPath")
        initError = null
        
        return withContext(Dispatchers.IO) {
            try {
                // Log device info
                FileLogger.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                FileLogger.i(TAG, "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                FileLogger.i(TAG, "ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
                
                // Check model path
                val modelFile = if (modelPath != null) {
                    File(modelPath)
                } else {
                    File(context.filesDir, "models/model.litertlm")
                }
                
                FileLogger.i(TAG, "Model file path: ${modelFile.absolutePath}")
                FileLogger.i(TAG, "Model file exists: ${modelFile.exists()}")
                
                if (!modelFile.exists()) {
                    FileLogger.w(TAG, "Model file not found: ${modelFile.absolutePath}")
                    initError = "Model file not found"
                    return@withContext false
                }
                
                val fileSizeMB = modelFile.length() / 1024 / 1024
                FileLogger.i(TAG, "Model file size: ${modelFile.length()} bytes ($fileSizeMB MB)")
                
                // Validate file header
                if (!validateLiteRtLmFile(modelFile)) {
                    initError = "Invalid model format. Use .litertlm files from Hugging Face litert-community"
                    return@withContext false
                }
                
                // Reduce native log verbosity
                Engine.setNativeMinLogSeverity(LogSeverity.WARNING)
                
                // Create engine config
                FileLogger.i(TAG, "Creating EngineConfig with backend: $backend...")
                val engineConfig = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = backend,
                    cacheDir = context.cacheDir.absolutePath
                )
                
                // Create engine
                FileLogger.i(TAG, "Creating Engine...")
                engine = Engine(engineConfig)
                
                // Initialize engine (this can take 10+ seconds)
                FileLogger.i(TAG, "Initializing Engine (this may take 10-30 seconds)...")
                engine!!.initialize()
                FileLogger.i(TAG, "Engine initialized successfully!")
                
                // Create conversation
                FileLogger.i(TAG, "Creating Conversation...")
                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are a helpful assistant for a budget tracking app. " +
                        "Correct any grammar or transcription errors in user input. " +
                        "Keep responses concise and natural."
                    ),
                    samplerConfig = SamplerConfig(
                        topK = 40,
                        topP = 0.95,
                        temperature = 0.7
                    )
                )
                conversation = engine!!.createConversation(conversationConfig)
                
                isInitialized = true
                FileLogger.i(TAG, "LiteRT-LM initialization complete!")
                true
            } catch (e: Exception) {
                FileLogger.e(TAG, "Failed to initialize LiteRT-LM", e)
                initError = e.message ?: "Unknown initialization error"
                isInitialized = false
                false
            }
        }
    }
    
    private fun validateLiteRtLmFile(file: File): Boolean {
        return try {
            // Check file extension first
            val fileName = file.name.lowercase()
            if (fileName.endsWith(".task")) {
                FileLogger.i(TAG, "Valid .task file detected by extension")
                return true
            }
            
            file.inputStream().use { stream ->
                val header = ByteArray(8)
                val bytesRead = stream.read(header)
                if (bytesRead < 8) {
                    FileLogger.e(TAG, "File too small to be a valid model")
                    return false
                }
                
                // Check for LITERTLM magic header
                val magic = String(header, Charsets.US_ASCII)
                FileLogger.i(TAG, "File header: $magic")
                
                if (magic.startsWith("LITERTLM")) {
                    FileLogger.i(TAG, "Valid .litertlm file detected")
                    true
                } else if (header[4] == 0x50.toByte() && header[5] == 0x4B.toByte()) {
                    // PK header at offset 4 indicates a .task file (ZIP-based)
                    FileLogger.i(TAG, "Valid .task file detected (ZIP format)")
                    true
                } else {
                    FileLogger.w(TAG, "Unknown file format, attempting to load anyway")
                    true // Let LiteRT-LM try to load it
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "Error validating file", e)
            false
        }
    }

    override suspend fun correctText(text: String): TextCorrectionResult {
        if (!isInitialized || conversation == null) {
            FileLogger.w(TAG, "correctText called but not initialized")
            return TextCorrectionResult(text, text, false)
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val prompt = "Correct any errors in this budget entry: \"$text\". " +
                    "Return only the corrected text, nothing else."
                
                FileLogger.i(TAG, "Sending prompt to model...")
                val response = conversation!!.sendMessage(prompt)
                val correctedText = response.toString().trim()
                val processingTime = System.currentTimeMillis() - startTime
                
                FileLogger.i(TAG, "Model response: $correctedText")
                
                // Clean up response if needed
                val finalText = if (correctedText.isNotBlank() && 
                    !correctedText.startsWith("Error") &&
                    correctedText.length < text.length * 3) {
                    correctedText
                } else {
                    text
                }
                TextCorrectionResult(
                    originalText = text,
                    correctedText = finalText,
                    wasModified = finalText != text,
                    processingTimeMs = processingTime
                )
            } catch (e: Exception) {
                FileLogger.e(TAG, "Error during text correction", e)
                TextCorrectionResult(text, text, false)
            }
        }
    }
    
    override suspend fun generateDescription(text: String): String {
        if (!isInitialized || conversation == null) {
            return extractSimpleDescription(text)
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val prompt = "Generate a short description (2-4 words) for this expense: \"$text\". Return only the description."
                val response = conversation!!.sendMessage(prompt)
                val description = response.toString().trim()
                
                if (description.isNotBlank() && description.length < 50) {
                    description
                } else {
                    extractSimpleDescription(text)
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "Error generating description", e)
                extractSimpleDescription(text)
            }
        }
    }
    
    private fun extractSimpleDescription(text: String): String {
        // Simple extraction: remove numbers and common words
        return text.replace(Regex("[0-9₹$,.]"), "")
            .split(" ")
            .filter { it.length > 2 }
            .take(3)
            .joinToString(" ")
            .trim()
            .ifBlank { "Expense" }
    }
    
    /**
     * Chat with the model for testing purposes.
     */
    suspend fun chat(message: String): String {
        if (!isInitialized || conversation == null) {
            throw IllegalStateException("Model not initialized")
        }
        
        return withContext(Dispatchers.IO) {
            try {
                FileLogger.i(TAG, "Chat message: $message")
                val response = conversation!!.sendMessage(message)
                val responseText = response.toString()
                FileLogger.i(TAG, "Chat response: $responseText")
                responseText
            } catch (e: Exception) {
                FileLogger.e(TAG, "Error during chat", e)
                throw e
            }
        }
    }
    
    /**
     * Stream chat response for real-time output.
     */
    suspend fun chatStream(message: String, onToken: (String) -> Unit, onComplete: () -> Unit) {
        if (!isInitialized || conversation == null) {
            throw IllegalStateException("Model not initialized")
        }
        
        withContext(Dispatchers.IO) {
            try {
                FileLogger.i(TAG, "Chat stream: $message")
                conversation!!.sendMessageAsync(message)
                    .catch { e -> 
                        FileLogger.e(TAG, "Stream error", e)
                        throw e
                    }
                    .onCompletion { onComplete() }
                    .collect { partialMessage ->
                        onToken(partialMessage.toString())
                    }
            } catch (e: Exception) {
                FileLogger.e(TAG, "Error during chat stream", e)
                throw e
            }
        }
    }

    override fun isAvailable(): Boolean = isInitialized

    override fun close() {
        FileLogger.i(TAG, "Closing LiteRT-LM resources")
        try {
            conversation?.close()
            conversation = null
            engine?.close()
            engine = null
            isInitialized = false
        } catch (e: Exception) {
            FileLogger.e(TAG, "Error closing resources", e)
        }
    }

    companion object {
        private const val TAG = "LiteRtLmTextCorrector"
    }
}
