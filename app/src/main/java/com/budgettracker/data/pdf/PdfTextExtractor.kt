package com.budgettracker.data.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

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

    fun extractText(inputStream: InputStream): List<String> {
        ensureInitialized()
        val pages = mutableListOf<String>()
        PDDocument.load(inputStream).use { document ->
            val stripper = PDFTextStripper()
            for (i in 1..document.numberOfPages) {
                stripper.startPage = i
                stripper.endPage = i
                val text = stripper.getText(document).trim()
                if (text.isNotEmpty()) {
                    pages.add(text)
                }
            }
        }
        return pages
    }

    fun hasTextContent(inputStream: InputStream): Boolean {
        ensureInitialized()
        return try {
            PDDocument.load(inputStream).use { document ->
                if (document.numberOfPages == 0) return false
                val stripper = PDFTextStripper()
                stripper.startPage = 1
                stripper.endPage = 1
                val text = stripper.getText(document).trim()
                text.length > 50
            }
        } catch (e: Exception) {
            false
        }
    }
}
