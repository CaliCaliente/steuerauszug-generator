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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.zip.GZIPOutputStream
import javax.imageio.ImageIO

@Component
class PdfGenerator {

    companion object {
        private val log = LoggerFactory.getLogger(PdfGenerator::class.java)
        private const val FONT_SIZE_TITLE = 18f
        private const val FONT_SIZE_LABEL = 10f
        private const val FONT_SIZE_BODY = 9f
        private const val FONT_SIZE_SMALL = 8f
        private const val FONT_SIZE_FOOTER = 7f
        private const val PAGE_MARGIN = 36f
    }

    fun generate(statement: EchTaxStatement, xmlContent: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(outputStream))
        val doc = Document(pdfDoc, PageSize.A4.rotate())
        doc.setMargins(PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN)

        addHeader(doc, statement)
        addInfoTable(doc, statement)
        addItemsTable(doc, statement)
        addBarcode(doc, xmlContent)
        addFooter(doc)

        doc.close()
        return outputStream.toByteArray()
    }

    private fun addHeader(doc: Document, statement: EchTaxStatement) {
        doc.add(
            Paragraph("E-Steuerausweis ${statement.taxPeriod}")
                .setFontSize(FONT_SIZE_TITLE)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
        )
        doc.add(
            Paragraph("Dokument-ID: ${statement.documentId}   |   Kanton: ${statement.canton}")
                .setFontSize(FONT_SIZE_BODY)
                .setTextAlignment(TextAlignment.CENTER)
        )
        doc.add(
            Paragraph("Steuerperiode: ${statement.periodFrom} bis ${statement.periodTo}")
                .setFontSize(FONT_SIZE_BODY)
                .setTextAlignment(TextAlignment.CENTER)
        )
        doc.add(Paragraph("\n"))
    }

    private fun addInfoTable(doc: Document, statement: EchTaxStatement) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()

        val institutionCell = Cell()
        institutionCell.add(Paragraph("Finanzinstitut").setBold().setFontSize(FONT_SIZE_LABEL))
        institutionCell.add(Paragraph(statement.institution.name).setFontSize(FONT_SIZE_BODY))
        institutionCell.add(Paragraph(statement.institution.address).setFontSize(FONT_SIZE_BODY))
        institutionCell.add(Paragraph("Clearing-Nr.: ${statement.institution.clearingNumber}").setFontSize(FONT_SIZE_BODY))

        val customerCell = Cell()
        customerCell.add(Paragraph("Kunde / Steuerpflichtiger").setBold().setFontSize(FONT_SIZE_LABEL))
        customerCell.add(Paragraph(statement.customer.name).setFontSize(FONT_SIZE_BODY))
        customerCell.add(Paragraph(statement.customer.address).setFontSize(FONT_SIZE_BODY))
        customerCell.add(Paragraph("Kundennummer: ${statement.customer.customerNumber}").setFontSize(FONT_SIZE_BODY))

        table.addCell(institutionCell)
        table.addCell(customerCell)
        doc.add(table)
        doc.add(Paragraph("\n"))
    }

    private fun addItemsTable(doc: Document, statement: EchTaxStatement) {
        val headers = listOf("Beschreibung", "Währung", "Bruttobetrag", "Quellensteuer", "Nettobetrag", "Quellenland")
        val table = Table(UnitValue.createPercentArray(floatArrayOf(30f, 10f, 15f, 15f, 15f, 15f))).useAllAvailableWidth()

        for (header in headers) {
            table.addHeaderCell(Cell().apply { add(Paragraph(header).setBold().setFontSize(FONT_SIZE_BODY)) })
        }

        for (item in statement.items) {
            table.addCell(Cell().apply { add(Paragraph(item.description).setFontSize(FONT_SIZE_SMALL)) })
            table.addCell(Cell().apply { add(Paragraph(item.currency).setFontSize(FONT_SIZE_SMALL)) })
            table.addCell(Cell().apply { add(Paragraph(fmt(item.grossAmount)).setFontSize(FONT_SIZE_SMALL).setTextAlignment(TextAlignment.RIGHT)) })
            table.addCell(Cell().apply { add(Paragraph(fmt(item.withholdingTax)).setFontSize(FONT_SIZE_SMALL).setTextAlignment(TextAlignment.RIGHT)) })
            table.addCell(Cell().apply { add(Paragraph(fmt(item.netAmount)).setFontSize(FONT_SIZE_SMALL).setTextAlignment(TextAlignment.RIGHT)) })
            table.addCell(Cell().apply { add(Paragraph(item.sourceCountry).setFontSize(FONT_SIZE_SMALL)) })
        }

        table.addCell(Cell().apply { add(Paragraph("Total").setBold().setFontSize(FONT_SIZE_BODY)) })
        table.addCell(Cell().apply { add(Paragraph("").setFontSize(FONT_SIZE_BODY)) })
        table.addCell(Cell().apply { add(Paragraph(fmt(statement.totalGross)).setBold().setFontSize(FONT_SIZE_BODY).setTextAlignment(TextAlignment.RIGHT)) })
        table.addCell(Cell().apply { add(Paragraph(fmt(statement.totalWithholding)).setBold().setFontSize(FONT_SIZE_BODY).setTextAlignment(TextAlignment.RIGHT)) })
        table.addCell(Cell().apply { add(Paragraph(fmt(statement.totalNet)).setBold().setFontSize(FONT_SIZE_BODY).setTextAlignment(TextAlignment.RIGHT)) })
        table.addCell(Cell().apply { add(Paragraph("").setFontSize(FONT_SIZE_BODY)) })

        doc.add(table)
        doc.add(Paragraph("\n"))
    }

    private fun addBarcode(doc: Document, xmlContent: String) {
        try {
            val barcodeBytes = generateBarcode(xmlContent)
            val barcodeImg = Image(ImageDataFactory.create(barcodeBytes))
            barcodeImg.setWidth(UnitValue.createPercentValue(80f))
            val imgParagraph = Paragraph()
            imgParagraph.add(barcodeImg)
            imgParagraph.setTextAlignment(TextAlignment.CENTER)
            doc.add(imgParagraph)
        } catch (e: Exception) {
            log.warn("Barcode generation failed: ${e::class.simpleName}: ${e.message}", e)
            doc.add(Paragraph("Barcode konnte nicht generiert werden: ${e.message}").setFontSize(FONT_SIZE_SMALL))
        }
    }

    private fun addFooter(doc: Document) {
        doc.add(
            Paragraph("Generiert gemäss eCH-0196 v2.2.0 | PDF417-Barcode enthält GZIP-komprimiertes XML")
                .setFontSize(FONT_SIZE_FOOTER)
                .setTextAlignment(TextAlignment.CENTER)
        )
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
