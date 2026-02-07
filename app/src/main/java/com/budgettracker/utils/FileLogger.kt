package com.budgettracker.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple file-based logger for debugging crashes.
 * Logs are written to: /data/data/com.budgettracker/files/logs/app_log.txt
 * 
 * You can pull the log file using:
 * adb pull /data/data/com.budgettracker/files/logs/app_log.txt
 */
object FileLogger {
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private const val TAG = "FileLogger"
    private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB max
    
    fun init(context: Context) {
        try {
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            logFile = File(logDir, "app_log.txt")
            
            // Rotate log if too large
            if (logFile?.exists() == true && (logFile?.length() ?: 0) > MAX_LOG_SIZE) {
                val backupFile = File(logDir, "app_log_old.txt")
                backupFile.delete()
                logFile?.renameTo(backupFile)
                logFile = File(logDir, "app_log.txt")
            }
            
            log("INFO", "FileLogger", "=== App Started ===")
            log("INFO", "FileLogger", "Log file: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init FileLogger", e)
        }
    }
    
    fun d(tag: String, message: String) {
        log("DEBUG", tag, message)
        Log.d(tag, message)
    }
    
    fun i(tag: String, message: String) {
        log("INFO", tag, message)
        Log.i(tag, message)
    }
    
    fun w(tag: String, message: String) {
        log("WARN", tag, message)
        Log.w(tag, message)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\nException: ${throwable.javaClass.simpleName}: ${throwable.message}\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        log("ERROR", tag, fullMessage)
        Log.e(tag, message, throwable)
    }
    
    private fun log(level: String, tag: String, message: String) {
        try {
            val timestamp = dateFormat.format(Date())
            val logLine = "[$timestamp] [$level] [$tag] $message\n"
            logFile?.appendText(logLine)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }
    
    fun getLogFilePath(): String? = logFile?.absolutePath
    
    fun getLogContent(): String {
        return try {
            logFile?.readText() ?: "Log file not found"
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }
    
    fun clearLog() {
        try {
            logFile?.writeText("")
            log("INFO", "FileLogger", "Log cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log", e)
        }
    }
}
