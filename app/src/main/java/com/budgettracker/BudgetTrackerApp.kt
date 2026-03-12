package com.budgettracker

import android.app.Application
import com.budgettracker.ai.TextCorrectionManager
import com.budgettracker.utils.FileLogger
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class BudgetTrackerApp : Application() {
    
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TextCorrectionManagerEntryPoint {
        fun textCorrectionManager(): TextCorrectionManager
    }
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize file logger for crash debugging
        FileLogger.init(this)
        FileLogger.i("BudgetTrackerApp", "Application onCreate")
        
        // Initialize PdfBox for PDF processing
        PDFBoxResourceLoader.init(applicationContext)
        
        // Set up global exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            FileLogger.e("CRASH", "Uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        // Preload AI model in background for faster response time
        preloadAIModel()
    }
    
    private fun preloadAIModel() {
        applicationScope.launch {
            try {
                FileLogger.i("BudgetTrackerApp", "Starting background AI model preload...")
                val startTime = System.currentTimeMillis()
                
                // Get TextCorrectionManager via EntryPoint
                val entryPoint = EntryPointAccessors.fromApplication(
                    this@BudgetTrackerApp,
                    TextCorrectionManagerEntryPoint::class.java
                )
                val textCorrectionManager = entryPoint.textCorrectionManager()
                
                textCorrectionManager.initialize()
                
                val elapsed = System.currentTimeMillis() - startTime
                val isAvailable = textCorrectionManager.isAvailable()
                
                FileLogger.i(
                    "BudgetTrackerApp", 
                    "AI model preload complete in ${elapsed}ms. Available: $isAvailable"
                )
            } catch (e: Exception) {
                FileLogger.e("BudgetTrackerApp", "Failed to preload AI model", e)
            }
        }
    }
}
