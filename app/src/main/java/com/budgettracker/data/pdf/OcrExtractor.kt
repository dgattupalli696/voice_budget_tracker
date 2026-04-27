package com.budgettracker.data.pdf

import android.content.Context
import android.graphics.Bitmap
import com.budgettracker.utils.FileLogger
import com.googlecode.tesseract.android.TessBaseAPI
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tessDataReady = false

    private fun ensureTessData() {
        if (tessDataReady) return
        val tessDir = File(context.filesDir, "tesseract")
        val tessDataDir = File(tessDir, "tessdata")
        val engFile = File(tessDataDir, "eng.traineddata")

        if (!engFile.exists()) {
            tessDataDir.mkdirs()
            context.assets.open("tessdata/eng.traineddata").use { input ->
                engFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            FileLogger.i("OcrExtractor", "Copied eng.traineddata to ${engFile.absolutePath}")
        }
        tessDataReady = true
    }

    fun extractText(inputStream: InputStream, password: String? = null): List<String> {
        ensureTessData()
        PDFBoxResourceLoader.init(context)

        val pages = mutableListOf<String>()
        val tessDir = File(context.filesDir, "tesseract")

        val document = try {
            if (password != null) PDDocument.load(inputStream, password)
            else PDDocument.load(inputStream)
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("password") || msg.contains("encrypted") || msg.contains("decrypt")) {
                if (password != null) throw WrongPasswordException()
                else throw PasswordRequiredException()
            }
            throw e
        }
        document.use { doc ->
            val renderer = PDFRenderer(doc)
            val tess = TessBaseAPI()
            try {
                if (!tess.init(tessDir.absolutePath, "eng")) {
                    FileLogger.e("OcrExtractor", "Tesseract init failed")
                    return emptyList()
                }
                tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO

                for (i in 0 until doc.numberOfPages) {
                    try {
                        val bitmap = renderer.renderImageWithDPI(i, 300f)
                        tess.setImage(bitmap)
                        val text = tess.utF8Text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            pages.add(text)
                        }
                        bitmap.recycle()
                    } catch (e: Exception) {
                        FileLogger.e("OcrExtractor", "Failed OCR on page ${i + 1}", e)
                    }
                }
            } finally {
                tess.recycle()
            }
        }
        return pages
    }
}
