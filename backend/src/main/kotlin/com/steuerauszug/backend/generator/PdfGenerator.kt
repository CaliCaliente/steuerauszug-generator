package com.steuerauszug.backend.generator

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.pdf417.encoder.Dimensions
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.pdf417.PDF417Writer
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.steuerauszug.backend.model.EchTaxStatement
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.zip.GZIPOutputStream
import javax.imageio.ImageIO

@Component
class PdfGenerator {

    fun generate(statement: EchTaxStatement, xmlContent: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(outputStream))
        val doc = Document(pdfDoc, PageSize.A4.rotate())
        doc.setMargins(36f, 36f, 36f, 36f)

        // Header
        doc.add(
            Paragraph("E-Steuerausweis ${statement.taxPeriod}")
                .setFontSize(18f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
        )
        doc.add(
            Paragraph("Dokument-ID: ${statement.documentId}   |   Kanton: ${statement.canton}")
                .setFontSize(9f)
                .setTextAlignment(TextAlignment.CENTER)
        )
        doc.add(
            Paragraph("Steuerperiode: ${statement.periodFrom} bis ${statement.periodTo}")
                .setFontSize(9f)
                .setTextAlignment(TextAlignment.CENTER)
        )
        doc.add(Paragraph("\n"))

        // Institution / Customer info block
        val infoTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()

        val institutionCell = Cell()
        institutionCell.add(Paragraph("Finanzinstitut").setBold().setFontSize(10f))
        institutionCell.add(Paragraph(statement.institution.name).setFontSize(9f))
        institutionCell.add(Paragraph(statement.institution.address).setFontSize(9f))
        institutionCell.add(Paragraph("Clearing-Nr.: ${statement.institution.clearingNumber}").setFontSize(9f))

        val customerCell = Cell()
        customerCell.add(Paragraph("Kunde / Steuerpflichtiger").setBold().setFontSize(10f))
        customerCell.add(Paragraph(statement.customer.name).setFontSize(9f))
        customerCell.add(Paragraph(statement.customer.address).setFontSize(9f))
        customerCell.add(Paragraph("Kundennummer: ${statement.customer.customerNumber}").setFontSize(9f))

        infoTable.addCell(institutionCell)
        infoTable.addCell(customerCell)
        doc.add(infoTable)
        doc.add(Paragraph("\n"))

        // Income items table
        val headers = listOf("Beschreibung", "Währung", "Bruttobetrag", "Quellensteuer", "Nettobetrag", "Quellenland")
        val itemsTable = Table(UnitValue.createPercentArray(floatArrayOf(30f, 10f, 15f, 15f, 15f, 15f))).useAllAvailableWidth()

        for (header in headers) {
            val h = Cell()
            h.add(Paragraph(header).setBold().setFontSize(9f))
            itemsTable.addHeaderCell(h)
        }

        for (item in statement.items) {
            itemsTable.addCell(Cell().apply { add(Paragraph(item.description).setFontSize(8f)) })
            itemsTable.addCell(Cell().apply { add(Paragraph(item.currency).setFontSize(8f)) })
            itemsTable.addCell(Cell().apply {
                add(Paragraph(fmt(item.grossAmount)).setFontSize(8f).setTextAlignment(TextAlignment.RIGHT))
            })
            itemsTable.addCell(Cell().apply {
                add(Paragraph(fmt(item.withholdingTax)).setFontSize(8f).setTextAlignment(TextAlignment.RIGHT))
            })
            itemsTable.addCell(Cell().apply {
                add(Paragraph(fmt(item.netAmount)).setFontSize(8f).setTextAlignment(TextAlignment.RIGHT))
            })
            itemsTable.addCell(Cell().apply { add(Paragraph(item.sourceCountry).setFontSize(8f)) })
        }

        // Totals row
        itemsTable.addCell(Cell().apply { add(Paragraph("Total").setBold().setFontSize(9f)) })
        itemsTable.addCell(Cell().apply { add(Paragraph("").setFontSize(9f)) })
        itemsTable.addCell(Cell().apply {
            add(Paragraph(fmt(statement.totalGross)).setBold().setFontSize(9f).setTextAlignment(TextAlignment.RIGHT))
        })
        itemsTable.addCell(Cell().apply {
            add(Paragraph(fmt(statement.totalWithholding)).setBold().setFontSize(9f).setTextAlignment(TextAlignment.RIGHT))
        })
        itemsTable.addCell(Cell().apply {
            add(Paragraph(fmt(statement.totalNet)).setBold().setFontSize(9f).setTextAlignment(TextAlignment.RIGHT))
        })
        itemsTable.addCell(Cell().apply { add(Paragraph("").setFontSize(9f)) })

        doc.add(itemsTable)
        doc.add(Paragraph("\n"))

        // PDF417 barcode
        try {
            val barcodeBytes = generateBarcode(xmlContent)
            val barcodeImg = Image(ImageDataFactory.create(barcodeBytes))
            barcodeImg.setWidth(UnitValue.createPercentValue(80f))
            val imgParagraph = Paragraph()
            imgParagraph.add(barcodeImg)
            imgParagraph.setTextAlignment(TextAlignment.CENTER)
            doc.add(imgParagraph)
        } catch (e: Exception) {
            System.err.println("Barcode generation failed: ${e::class.simpleName}: ${e.message}")
            e.printStackTrace(System.err)
            doc.add(Paragraph("Barcode konnte nicht generiert werden: ${e.message}").setFontSize(8f))
        }

        // Footer
        doc.add(
            Paragraph("Generiert gemäss eCH-0196 v2.2.0 | PDF417-Barcode enthält GZIP-komprimiertes XML")
                .setFontSize(7f)
                .setTextAlignment(TextAlignment.CENTER)
        )

        doc.close()
        return outputStream.toByteArray()
    }

    private fun generateBarcode(content: String): ByteArray {
        // PDF417 max binary capacity is ~1108 bytes; GZIP-compress to stay within limits.
        // Compressed bytes are re-encoded as ISO-8859-1 so every byte survives the String round-trip.
        val compressed = ByteArrayOutputStream().also { baos ->
            GZIPOutputStream(baos).use { gz -> gz.write(content.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        val barcodeString = String(compressed, Charsets.ISO_8859_1)
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
            EncodeHintType.PDF417_DIMENSIONS to Dimensions(1, 30, 2, 90)
        )
        val bitMatrix = PDF417Writer().encode(barcodeString, BarcodeFormat.PDF_417, 0, 0, hints)
        val bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix)
        val baos = ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "PNG", baos)
        return baos.toByteArray()
    }

    private fun fmt(amount: BigDecimal): String =
        amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
