package com.budgettracker.ui.screens.settings

import com.budgettracker.ai.AIModelInfo
import com.budgettracker.ai.ModelDownloadState

enum class ModelBackend(val displayName: String) {
    CPU("CPU"),
    GPU("GPU"),
    NPU("NPU (Neural)")
}

data class SettingsUiState(
    val selectedLanguageCode: String = "en-IN",
    val availableLanguages: List<VoiceLanguage> = defaultLanguages,
    val modelDownloadState: ModelDownloadState = ModelDownloadState.NotDownloaded,
    val availableModels: List<AIModelInfo> = emptyList(),
    val importedModels: List<AIModelInfo> = emptyList(),
    val selectedModelId: String = "gemma_2b_gpu",
    val customModelPath: String? = null,
    val selectedBackend: ModelBackend = ModelBackend.CPU,
    val modelLoadError: String? = null,
    val modelCacheSizeMB: Long = 0
)

data class VoiceLanguage(
    val code: String,
    val displayName: String
)

val defaultLanguages = listOf(
    VoiceLanguage("en-IN", "English (India)"),
    VoiceLanguage("en-US", "English (US)"),
    VoiceLanguage("en-GB", "English (UK)"),
    VoiceLanguage("hi-IN", "Hindi (हिंदी)"),
    VoiceLanguage("ta-IN", "Tamil (தமிழ்)"),
    VoiceLanguage("te-IN", "Telugu (తెలుగు)"),
    VoiceLanguage("kn-IN", "Kannada (ಕನ್ನಡ)"),
    VoiceLanguage("ml-IN", "Malayalam (മലയാളം)"),
    VoiceLanguage("mr-IN", "Marathi (मराठी)"),
    VoiceLanguage("bn-IN", "Bengali (বাংলা)"),
    VoiceLanguage("gu-IN", "Gujarati (ગુજરાતી)"),
    VoiceLanguage("pa-IN", "Punjabi (ਪੰਜਾਬੀ)")
)
