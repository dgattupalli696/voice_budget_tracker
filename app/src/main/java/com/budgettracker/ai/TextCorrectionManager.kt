package com.budgettracker.ai

import android.content.Context
import android.util.Log
import com.budgettracker.utils.FileLogger
import com.google.ai.edge.litertlm.Backend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for text correction using LiteRT-LM model with rule-based fallback.
 * 
 * Priority:
 * 1. LiteRT-LM model (.litertlm files) - if downloaded by user
 * 2. Rule-based corrector (always available)
 */
@Singleton
class TextCorrectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloadManager: ModelDownloadManager
) {
    private var liteRtLmCorrector: LiteRtLmTextCorrector? = null
    private val ruleBasedCorrector = RuleBasedTextCorrector()
    private var isInitialized = false
    private val initMutex = Mutex()
    
    private var currentBackend: Backend = Backend.CPU()
    
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()
    
    fun setBackend(backend: Backend) {
        currentBackend = backend
    }

    /**
     * Initialize the text correction system.
     * Will use AI model if downloaded, otherwise falls back to rules.
     */
    suspend fun initialize() {
        FileLogger.i(TAG, "initialize() called, isInitialized=$isInitialized")
        
        initMutex.withLock {
            if (isInitialized) {
                FileLogger.i(TAG, "Already initialized, skipping")
                return
            }
        
        _isLoading.value = true
        _isModelReady.value = false
        
        _lastError.value = null
        ruleBasedCorrector.initialize()
        FileLogger.i(TAG, "Rule-based corrector initialized")
        
        // Check if model is downloaded
        val modelPath = modelDownloadManager.getModelPath()
        FileLogger.i(TAG, "Model path from manager: $modelPath")
        
        if (modelPath != null) {
            try {
                FileLogger.i(TAG, "Creating LiteRtLmTextCorrector with backend: $currentBackend...")
                liteRtLmCorrector = LiteRtLmTextCorrector(context, modelPath, currentBackend)
                
                FileLogger.i(TAG, "Calling liteRtLmCorrector.initialize()...")
                val success = liteRtLmCorrector?.initialize() ?: false
                
                if (success) {
                    FileLogger.i(TAG, "AI model initialized successfully")
                    _lastError.value = null
                    _isModelReady.value = true
                } else {
                    FileLogger.w(TAG, "AI model failed to load, using rule-based fallback")
                    val error = liteRtLmCorrector?.getInitializationError()
                    FileLogger.w(TAG, "Error: $error")
                    _lastError.value = error ?: "Failed to load model"
                    liteRtLmCorrector = null
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "Exception during AI model init", e)
                _lastError.value = e.message ?: "Unknown error"
                liteRtLmCorrector = null
            } catch (e: Error) {
                FileLogger.e(TAG, "FATAL ERROR during AI model init", Exception(e))
                _lastError.value = "Fatal error: ${e.message}"
                liteRtLmCorrector = null
            }
        } else {
            FileLogger.i(TAG, "No AI model downloaded, using rule-based corrections")
        }
        
        isInitialized = true
        _isLoading.value = false
        FileLogger.i(TAG, "TextCorrectionManager initialized (AI: ${liteRtLmCorrector != null})")
        }
    }
    
    /**
     * Reinitialize after model download
     */
    suspend fun reinitialize() {
        FileLogger.i(TAG, "reinitialize() called")
        close()
        isInitialized = false
        _isModelReady.value = false
        initialize()
    }

    /**
     * Correct the voice transcribed text.
     * Uses LiteRT-LM model if available, otherwise falls back to rules.
     */
    suspend fun correctText(text: String): TextCorrectionResult {
        if (text.isBlank()) {
            return TextCorrectionResult(text, text, false)
        }
        
        // Try LiteRT-LM first
        liteRtLmCorrector?.let { corrector ->
            if (corrector.isAvailable()) {
                try {
                    val result = corrector.correctText(text)
                    Log.d(TAG, "LiteRT-LM correction: '$text' -> '${result.correctedText}'")
                    return result
                } catch (e: Exception) {
                    Log.e(TAG, "LiteRT-LM correction failed", e)
                }
            }
        }
        
        // Fallback to rule-based (also generate description)
        val result = ruleBasedCorrector.correctText(text)
        val description = ruleBasedCorrector.generateDescription(text)
        Log.d(TAG, "Rule-based correction: '${result.originalText}' -> '${result.correctedText}', desc: '$description'")
        return result.copy(generatedDescription = description)
    }
    
    /**
     * Generate a concise description from the voice input.
     */
    suspend fun generateDescription(text: String): String {
        if (text.isBlank()) return "Expense"
        
        // Fallback to rule-based for now
        return ruleBasedCorrector.generateDescription(text)
    }

    /**
     * Check if AI model is available
     */
    fun isAIAvailable(): Boolean = liteRtLmCorrector?.isAvailable() ?: false
    
    /**
     * Check if any AI functionality is available
     */
    fun isAvailable(): Boolean = isInitialized && liteRtLmCorrector?.isAvailable() == true
    
    /**
     * Chat with the AI model for testing/conversation
     */
    suspend fun chat(message: String): String {
        if (!isInitialized) {
            initialize()
        }
        
        liteRtLmCorrector?.let { corrector ->
            if (corrector.isAvailable()) {
                return corrector.chat(message)
            }
        }
        
        return "AI model not available. Please download a .litertlm model from Settings > AI Model."
    }
    
    /**
     * Stream chat response token by token
     */
    suspend fun chatStream(message: String, onToken: (String) -> Unit, onComplete: () -> Unit) {
        if (!isInitialized) {
            initialize()
        }
        
        liteRtLmCorrector?.let { corrector ->
            if (corrector.isAvailable()) {
                try {
                    corrector.chatStream(message, onToken, onComplete)
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Stream chat failed", e)
                    onToken("Error: ${e.message}")
                    onComplete()
                    return
                }
            }
        }
        
        onToken("AI model not available. Please download a .litertlm model from Settings > AI Model.")
        onComplete()
    }

    /**
     * Release resources
     */
    fun close() {
        liteRtLmCorrector?.close()
        liteRtLmCorrector = null
        ruleBasedCorrector.close()
        isInitialized = false
    }

    companion object {
        private const val TAG = "TextCorrectionManager"
    }
}
