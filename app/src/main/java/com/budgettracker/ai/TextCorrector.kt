package com.budgettracker.ai

/**
 * Interface for text correction and description generation using AI models.
 * Implemented using Google LiteRT with Qwen model for on-device inference.
 */
interface TextCorrector {
    /**
     * Corrects the transcribed text for grammar and spelling mistakes.
     * Also normalizes amounts and categories for better parsing.
     * 
     * @param text The raw transcribed text from voice input
     * @return Corrected and normalized text
     */
    suspend fun correctText(text: String): TextCorrectionResult
    
    /**
     * Generates a concise, clear description from the expense/income text.
     * 
     * @param text The voice input text
     * @return A clean, concise description for the transaction
     */
    suspend fun generateDescription(text: String): String
    
    /**
     * Check if the corrector is available and ready to use
     */
    fun isAvailable(): Boolean
    
    /**
     * Initialize the model (download if needed)
     */
    suspend fun initialize(): Boolean
    
    /**
     * Release model resources
     */
    fun close()
}

data class TextCorrectionResult(
    val originalText: String,
    val correctedText: String,
    val wasModified: Boolean,
    val confidence: Float = 1.0f,
    val processingTimeMs: Long = 0,
    val generatedDescription: String? = null
)
