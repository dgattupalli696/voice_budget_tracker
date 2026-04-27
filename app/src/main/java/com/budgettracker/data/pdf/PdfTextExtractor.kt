package com.budgettracker.data.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

class PasswordRequiredException(message: String = "PDF is password protected") : Exception(message)
class WrongPasswordException(message: String = "Incorrect password") : Exception(message)

@Singleton
class PdfTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var initialized = false

    private fun ensureInitialized() {
        if (!initialized) {
            PDFBoxResourceLoader.init(context)
            initialized = true
        }
    }

    fun extractText(inputStream: InputStream, password: String? = null): List<String> {
        ensureInitialized()
        val pages = mutableListOf<String>()
        val document = try {
            if (password != null) {
                PDDocument.load(inputStream, password)
            } else {
                PDDocument.load(inputStream)
            }
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("password") || msg.contains("encrypted") || msg.contains("decrypt")) {
                if (password != null) throw WrongPasswordException()
                else throw PasswordRequiredException()
            }
            throw e
        }
        document.use { doc ->
            val stripper = PDFTextStripper()
            for (i in 1..doc.numberOfPages) {
                stripper.startPage = i
                stripper.endPage = i
                val text = stripper.getText(doc).trim()
                if (text.isNotEmpty()) {
                    pages.add(text)
                }
            }
        }
        return pages
    }

    fun hasTextContent(inputStream: InputStream, password: String? = null): Boolean {
        ensureInitialized()
        return try {
            val document = if (password != null) {
                PDDocument.load(inputStream, password)
            } else {
                PDDocument.load(inputStream)
            }
            document.use { doc ->
                if (doc.numberOfPages == 0) return false
                val stripper = PDFTextStripper()
                stripper.startPage = 1
                stripper.endPage = 1
                val text = stripper.getText(doc).trim()
                text.length > 50
            }
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("password") || msg.contains("encrypted") || msg.contains("decrypt")) {
                if (password != null) throw WrongPasswordException()
                else throw PasswordRequiredException()
            }
            false
        }
    }
}
