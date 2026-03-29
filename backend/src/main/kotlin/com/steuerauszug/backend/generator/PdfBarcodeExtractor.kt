package com.steuerauszug.backend.generator

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.pdf417.PDF417Reader
import com.google.zxing.pdf417.PDF417ResultMetadata
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
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
        Loader.loadPDF(pdfBytes).use { doc ->
            val visited = mutableSetOf<Int>()
            doc.pages.flatMap { page -> collectImages(page.resources, visited) }
        }

    /** Returns all Image XObjects from the given resources, recursing into Form XObjects. */
    private fun collectImages(resources: PDResources, visited: MutableSet<Int>): List<BufferedImage> =
        resources.xObjectNames
            .mapNotNull { name ->
                val xObj = resources.getXObject(name)
                val id = System.identityHashCode(xObj.cosObject)
                if (!visited.add(id)) return@mapNotNull null
                xObj to name
            }
            .flatMap { (xObj, _) ->
                when (xObj) {
                    is PDImageXObject -> listOf(xObj.image)
                    is PDFormXObject  -> collectImages(xObj.resources, visited)
                    else              -> emptyList()
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
