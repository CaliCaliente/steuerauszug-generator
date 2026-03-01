package com.steuerauszug.backend.generator

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.pdf417.PDF417Reader
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

@Component
class PdfBarcodeExtractor {

    fun extract(pdfBytes: ByteArray): String {
        val image = extractBarcodeImage(pdfBytes)
        val decoded = decodePdf417(image)
        val compressed = decoded.toByteArray(Charsets.ISO_8859_1)
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { gz ->
            gz.readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun extractBarcodeImage(pdfBytes: ByteArray): BufferedImage {
        PdfDocument(PdfReader(ByteArrayInputStream(pdfBytes))).use { pdfDoc ->
            val resources = pdfDoc.getPage(1).resources
            val xObjects = resources.getResource(PdfName.XObject)
                ?: error("No XObject resources found in PDF")
            for (name in xObjects.keySet()) {
                val stream = xObjects.getAsStream(name) ?: continue
                if (PdfName.Image == stream.getAsName(PdfName.Subtype)) {
                    return PdfImageXObject(stream).bufferedImage
                }
            }
            error("No image XObject found in PDF")
        }
    }

    private fun decodePdf417(image: BufferedImage): String {
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        val source = BufferedImageLuminanceSource(image)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return PDF417Reader().decode(bitmap, hints).text
    }
}
