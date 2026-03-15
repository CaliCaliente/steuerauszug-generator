package com.steuerauszug.backend.generator

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.pdf417.PDF417Reader
import com.google.zxing.pdf417.PDF417ResultMetadata
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

@Component
class PdfBarcodeExtractor {

    fun extract(pdfBytes: ByteArray): String {
        val images = extractAllBarcodeImages(pdfBytes)
        check(images.isNotEmpty()) { "No barcode images found in PDF" }

        val results = images.map { decodePdf417(it) }

        val fullIso = when {
            results.size == 1 -> results[0].text
            else -> results.sortedBy { segmentIndex(it) }.joinToString("") { it.text }
        }

        val compressed = fullIso.toByteArray(Charsets.ISO_8859_1)
        return InflaterInputStream(ByteArrayInputStream(compressed)).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun extractAllBarcodeImages(pdfBytes: ByteArray): List<BufferedImage> =
        PdfDocument(PdfReader(ByteArrayInputStream(pdfBytes))).use { pdfDoc ->
            // Barcodes are added last, so collect images from all pages in order
            val visited = mutableSetOf<Int>()
            (1..pdfDoc.numberOfPages).flatMap { pageNum ->
                collectImages(pdfDoc.getPage(pageNum).resources, visited)
            }
        }

    /** Returns all Image XObjects from the given resources, recursing into Form XObjects. */
    private fun collectImages(
        resources: com.itextpdf.kernel.pdf.PdfResources,
        visited: MutableSet<Int>
    ): List<BufferedImage> {
        val xObjects = resources.getResource(PdfName.XObject) ?: return emptyList()
        return xObjects.keySet().flatMap { name ->
            val stream = xObjects.getAsStream(name) ?: return@flatMap emptyList()
            val objNum = stream.indirectReference?.objNumber ?: System.identityHashCode(stream)
            if (!visited.add(objNum)) return@flatMap emptyList()
            when (stream.getAsName(PdfName.Subtype)) {
                PdfName.Image -> listOf(PdfImageXObject(stream).bufferedImage)
                PdfName.Form  -> collectImages(
                    com.itextpdf.kernel.pdf.PdfResources(stream.getAsDictionary(PdfName.Resources) ?: return@flatMap emptyList()),
                    visited
                )
                else -> emptyList()
            }
        }
    }

    private fun decodePdf417(image: BufferedImage): Result {
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        val bitmap = BinaryBitmap(HybridBinarizer(BufferedImageLuminanceSource(image)))
        return PDF417Reader().decode(bitmap, hints)
    }

    private fun segmentIndex(result: Result): Int =
        (result.resultMetadata?.get(ResultMetadataType.PDF417_EXTRA_METADATA) as? PDF417ResultMetadata)
            ?.segmentIndex ?: 0
}
