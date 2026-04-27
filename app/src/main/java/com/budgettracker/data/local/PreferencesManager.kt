package com.budgettracker.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    var voiceLanguageCode: String
        get() = prefs.getString(KEY_VOICE_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = prefs.edit().putString(KEY_VOICE_LANGUAGE, value).apply()

    var isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    /**
     * The account id to use for transactions added via AI chat / voice when the
     * user does not specify an account. -1 means "no default selected".
     */
    var defaultAccountId: Long
        get() = prefs.getLong(KEY_DEFAULT_ACCOUNT_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_DEFAULT_ACCOUNT_ID, value).apply()

    companion object {
        private const val PREFS_NAME = "budget_tracker_prefs"
        private const val KEY_VOICE_LANGUAGE = "voice_language"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_DEFAULT_ACCOUNT_ID = "default_account_id"
        private const val DEFAULT_LANGUAGE = "en-IN"
    }
}
