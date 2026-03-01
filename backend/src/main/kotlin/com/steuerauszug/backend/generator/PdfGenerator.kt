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
import com.steuerauszug.backend.model.EchPayment
import com.steuerauszug.backend.model.EchSecurity
import com.steuerauszug.backend.model.EchStock
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
        private const val FONT_SIZE_SECTION = 11f
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
        addIncomeSection(doc, statement)
        addTradesSection(doc, statement)
        addYearEndSection(doc, statement)
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

    private fun addIncomeSection(doc: Document, statement: EchTaxStatement) {
        val allPayments = statement.securities.flatMap { sec ->
            sec.payments.map { payment -> sec to payment }
        }
        if (allPayments.isEmpty()) return

        doc.add(Paragraph("Erträge (Dividenden & Zinsen)").setBold().setFontSize(FONT_SIZE_SECTION))

        val headers = listOf("Datum", "Titel / ISIN", "Stück", "Währung", "Bruttobetrag", "Kurs CHF", "Brutto CHF", "Verrechnungssteuer CHF", "Netto CHF")
        val widths = floatArrayOf(8f, 22f, 6f, 6f, 10f, 8f, 10f, 12f, 10f)
        val table = Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth()
        headers.forEach { h -> table.addHeaderCell(headerCell(h)) }

        var totalGrossCHF = BigDecimal.ZERO
        var totalWt = BigDecimal.ZERO
        var totalNet = BigDecimal.ZERO

        for ((sec, payment) in allPayments) {
            val grossCHF = payment.grossAmountCHF ?: payment.grossAmount
            val net = grossCHF - payment.withholdingTax
            totalGrossCHF += grossCHF
            totalWt += payment.withholdingTax
            totalNet += net

            table.addCell(textCell(payment.date.toString()))
            table.addCell(textCell("${sec.symbol}${sec.isin?.let { " / $it" } ?: ""}"))
            table.addCell(numCell(payment.quantity.toPlainString()))
            table.addCell(textCell(sec.currency))
            table.addCell(numCell(fmt(payment.grossAmount)))
            table.addCell(numCell(payment.exchangeRate?.let { fmt(it) } ?: ""))
            table.addCell(numCell(fmt(grossCHF)))
            table.addCell(numCell(fmt(payment.withholdingTax)))
            table.addCell(numCell(fmt(net)))
        }

        // Total row
        table.addCell(boldCell("Total"))
        table.addCell(Cell())
        table.addCell(Cell())
        table.addCell(Cell())
        table.addCell(Cell())
        table.addCell(Cell())
        table.addCell(boldNumCell(fmt(totalGrossCHF)))
        table.addCell(boldNumCell(fmt(totalWt)))
        table.addCell(boldNumCell(fmt(totalNet)))

        doc.add(table)
        doc.add(Paragraph("\n"))
    }

    private fun addTradesSection(doc: Document, statement: EchTaxStatement) {
        val allTrades = statement.securities.flatMap { sec ->
            sec.stocks.filter { it.mutation }.map { stock -> sec to stock }
        }
        if (allTrades.isEmpty()) return

        doc.add(Paragraph("Transaktionen (Käufe / Verkäufe)").setBold().setFontSize(FONT_SIZE_SECTION))

        val headers = listOf("Datum", "K/V", "Titel / ISIN", "Stück", "Währung", "Kurs", "Betrag", "Kurs CHF", "Wert CHF")
        val widths = floatArrayOf(8f, 5f, 22f, 6f, 6f, 10f, 10f, 8f, 10f)
        val table = Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth()
        headers.forEach { h -> table.addHeaderCell(headerCell(h)) }

        for ((sec, stock) in allTrades) {
            val kv = if (stock.mutation && stock.quantity >= BigDecimal.ZERO) "K" else "V"
            table.addCell(textCell(stock.date.toString()))
            table.addCell(textCell(kv))
            table.addCell(textCell("${sec.symbol}${sec.isin?.let { " / $it" } ?: ""}"))
            table.addCell(numCell(stock.quantity.abs().toPlainString()))
            table.addCell(textCell(sec.currency))
            table.addCell(numCell(fmt(stock.unitPrice)))
            table.addCell(numCell(fmt(stock.balance.abs())))
            table.addCell(numCell(stock.exchangeRate?.let { fmt(it) } ?: ""))
            table.addCell(numCell(stock.valueCHF?.let { fmt(it.abs()) } ?: ""))
        }

        doc.add(table)
        doc.add(Paragraph("\n"))
    }

    private fun addYearEndSection(doc: Document, statement: EchTaxStatement) {
        val yearEndStocks = statement.securities.flatMap { sec ->
            sec.stocks.filter { !it.mutation }.map { stock -> sec to stock }
        }
        if (yearEndStocks.isEmpty()) return

        doc.add(Paragraph("Jahresendbestand").setBold().setFontSize(FONT_SIZE_SECTION))

        val headers = listOf("Titel / ISIN", "Stück", "Währung", "Kurs 31.12.", "Wert Fremdwährung", "Kurs CHF", "Wert CHF")
        val widths = floatArrayOf(25f, 8f, 8f, 12f, 14f, 10f, 12f)
        val table = Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth()
        headers.forEach { h -> table.addHeaderCell(headerCell(h)) }

        for ((sec, stock) in yearEndStocks) {
            table.addCell(textCell("${sec.symbol}${sec.isin?.let { " / $it" } ?: ""}"))
            table.addCell(numCell(stock.quantity.toPlainString()))
            table.addCell(textCell(sec.currency))
            table.addCell(numCell(fmt(stock.unitPrice)))
            table.addCell(numCell(fmt(stock.balance)))
            table.addCell(numCell(stock.exchangeRate?.let { fmt(it) } ?: ""))
            table.addCell(numCell(stock.valueCHF?.let { fmt(it) } ?: ""))
        }

        doc.add(table)
        doc.add(Paragraph("\n"))
    }

    private fun headerCell(text: String) = Cell().apply {
        add(Paragraph(text).setBold().setFontSize(FONT_SIZE_SMALL))
    }

    private fun textCell(text: String) = Cell().apply {
        add(Paragraph(text).setFontSize(FONT_SIZE_SMALL))
    }

    private fun numCell(text: String) = Cell().apply {
        add(Paragraph(text).setFontSize(FONT_SIZE_SMALL).setTextAlignment(TextAlignment.RIGHT))
    }

    private fun boldCell(text: String) = Cell().apply {
        add(Paragraph(text).setBold().setFontSize(FONT_SIZE_BODY))
    }

    private fun boldNumCell(text: String) = Cell().apply {
        add(Paragraph(text).setBold().setFontSize(FONT_SIZE_BODY).setTextAlignment(TextAlignment.RIGHT))
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
