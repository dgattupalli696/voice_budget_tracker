package com.budgettracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.budgettracker.data.local.PreferencesManager
import com.budgettracker.ui.navigation.BudgetNavigation
import com.budgettracker.ui.theme.VoiceBudgetTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesManager: PreferencesManager

    companion object {
        const val EXTRA_SHORTCUT_ACTION = "shortcut_action"
        const val ACTION_ADD_EXPENSE = "add_expense"
        const val ACTION_VOICE_INPUT = "voice_input"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shortcutAction = getShortcutAction(intent)
        val needsSetup = !preferencesManager.isSetupComplete

        setContent {
            VoiceBudgetTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BudgetNavigation(
                        shortcutAction = shortcutAction,
                        needsSetup = needsSetup
                    )
                }
            }
        }
    }
    
    private fun getShortcutAction(intent: Intent?): String? {
        val data = intent?.data?.toString()
        return when {
            data?.contains("add_expense") == true -> ACTION_ADD_EXPENSE
            data?.contains("voice_input") == true -> ACTION_VOICE_INPUT
            else -> null
        }
    }
}
