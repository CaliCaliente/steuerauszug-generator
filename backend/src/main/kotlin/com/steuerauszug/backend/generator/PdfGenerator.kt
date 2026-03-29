package com.steuerauszug.backend.generator

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.steuerauszug.backend.model.EchTaxStatement
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.org.okapibarcode.backend.Pdf417
import uk.org.okapibarcode.backend.Pdf417.Mode
import uk.org.okapibarcode.graphics.Color as OkapiColor
import uk.org.okapibarcode.output.Java2DRenderer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random

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
        private const val MARGIN = 36f
        private const val ROW_HEIGHT = 12f
        private const val CELL_PAD = 2f

        // Landscape A4: swap width/height
        private val PAGE_W = PDRectangle.A4.height  // 841.89
        private val PAGE_H = PDRectangle.A4.width   // 595.28
        private val USABLE_W = PAGE_W - 2 * MARGIN
        private val LANDSCAPE = PDRectangle(PAGE_W, PAGE_H)

        // eCH-0196 barcode spec
        private const val BARCODE_SEGMENT_BYTES = 450
        private const val BARCODE_DATA_COLUMNS = 13
        private const val BARCODE_ROWS = 35
        private const val BARCODE_ECC_LEVEL = 4
        private const val BARCODE_MAGNIFICATION = 2.0

        // Physical dimensions per eCH-0196 spec (1 pt = 0.03528 cm)
        private const val ELEMENT_WIDTH_PT = 0.042f / 0.03528f    // ~1.1906 pt per module
        private const val ELEMENT_HEIGHT_PT = 0.080f / 0.03528f   // ~2.2677 pt per row
        // PDF417 total module width = (dataColumns + 4) × 17 + 1
        private const val PDF417_MODULE_WIDTH = (BARCODE_DATA_COLUMNS + 4) * 17 + 1  // = 290
    }

    private data class ColSpec(val widthPct: Float, val rightAlign: Boolean = false)

    /** Mutable cursor state for a PDDocument being built. */
    private inner class DrawState(val doc: PDDocument) {
        val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

        lateinit var cs: PDPageContentStream
        var y: Float = 0f

        init { newPage() }

        fun newPage() {
            if (::cs.isInitialized) cs.close()
            val page = PDPage(LANDSCAPE)
            doc.addPage(page)
            cs = PDPageContentStream(doc, page)
            y = PAGE_H - MARGIN
        }

        fun ensureSpace(needed: Float) { if (y - needed < MARGIN + 10f) newPage() }

        fun close() = cs.close()
    }

    fun generate(statement: EchTaxStatement, xmlContent: String): ByteArray {
        val doc = PDDocument()
        val state = DrawState(doc)

        addHeader(state, statement)
        addInfoTable(state, statement)
        addIncomeSection(state, statement)
        addTradesSection(state, statement)
        addYearEndSection(state, statement)
        addBarcode(doc, state, xmlContent, statement.documentId)
        addFooter(state)

        state.close()
        val baos = ByteArrayOutputStream()
        doc.save(baos)
        doc.close()
        return baos.toByteArray()
    }

    private fun addHeader(state: DrawState, statement: EchTaxStatement) {
        state.ensureSpace(60f)

        val title = "E-Steuerausweis ${statement.taxPeriod}"
        drawCentered(state, title, state.y, state.bold, FONT_SIZE_TITLE)
        state.y -= FONT_SIZE_TITLE + 4f

        val line2 = "Dokument-ID: ${statement.documentId}   |   Kanton: ${statement.canton}"
        drawCentered(state, line2, state.y, state.regular, FONT_SIZE_BODY)
        state.y -= FONT_SIZE_BODY + 2f

        val line3 = "Steuerperiode: ${statement.periodFrom} bis ${statement.periodTo}"
        drawCentered(state, line3, state.y, state.regular, FONT_SIZE_BODY)
        state.y -= FONT_SIZE_BODY + 10f
    }

    private fun addInfoTable(state: DrawState, statement: EchTaxStatement) {
        state.ensureSpace(70f)
        val colW = USABLE_W / 2
        val startY = state.y

        // Left: institution
        drawText(state.cs, "Finanzinstitut", MARGIN, startY, state.bold, FONT_SIZE_LABEL)
        drawText(state.cs, statement.institution.name, MARGIN, startY - FONT_SIZE_LABEL - 2f, state.regular, FONT_SIZE_BODY)
        drawText(state.cs, statement.institution.address, MARGIN, startY - FONT_SIZE_LABEL - FONT_SIZE_BODY - 4f, state.regular, FONT_SIZE_BODY)
        drawText(state.cs, "Clearing-Nr.: ${statement.institution.clearingNumber}", MARGIN, startY - FONT_SIZE_LABEL - 2 * FONT_SIZE_BODY - 6f, state.regular, FONT_SIZE_BODY)

        // Right: customer
        val rx = MARGIN + colW
        drawText(state.cs, "Kunde / Steuerpflichtiger", rx, startY, state.bold, FONT_SIZE_LABEL)
        drawText(state.cs, statement.customer.name, rx, startY - FONT_SIZE_LABEL - 2f, state.regular, FONT_SIZE_BODY)
        drawText(state.cs, statement.customer.address, rx, startY - FONT_SIZE_LABEL - FONT_SIZE_BODY - 4f, state.regular, FONT_SIZE_BODY)
        drawText(state.cs, "Kundennummer: ${statement.customer.customerNumber}", rx, startY - FONT_SIZE_LABEL - 2 * FONT_SIZE_BODY - 6f, state.regular, FONT_SIZE_BODY)

        state.y = startY - FONT_SIZE_LABEL - 3 * FONT_SIZE_BODY - 16f
    }

    private fun addIncomeSection(state: DrawState, statement: EchTaxStatement) {
        val allPayments = statement.securities.flatMap { sec -> sec.payments.map { sec to it } }
        if (allPayments.isEmpty()) return

        state.ensureSpace(30f)
        drawText(state.cs, "Ertraege (Dividenden & Zinsen)", MARGIN, state.y, state.bold, FONT_SIZE_SECTION)
        state.y -= FONT_SIZE_SECTION + 4f

        val cols = listOf(
            ColSpec(8f), ColSpec(22f), ColSpec(6f, true), ColSpec(6f),
            ColSpec(10f, true), ColSpec(8f, true), ColSpec(10f, true), ColSpec(12f, true), ColSpec(10f, true)
        )
        val headers = listOf("Datum", "Titel / ISIN", "Stueck", "Waehrung", "Bruttobetrag", "Kurs CHF", "Brutto CHF", "Verrechnungsst. CHF", "Netto CHF")

        state.ensureSpace(ROW_HEIGHT)
        drawTableRow(state, cols, headers, state.bold, FONT_SIZE_SMALL)

        var totalGrossCHF = BigDecimal.ZERO
        var totalWt = BigDecimal.ZERO
        var totalNet = BigDecimal.ZERO

        for ((sec, payment) in allPayments) {
            val grossCHF = payment.grossAmountCHF ?: payment.grossAmount
            val net = grossCHF - payment.withholdingTax
            totalGrossCHF += grossCHF
            totalWt += payment.withholdingTax
            totalNet += net

            state.ensureSpace(ROW_HEIGHT)
            drawTableRow(state, cols, listOf(
                payment.date.toString(),
                "${sec.symbol}${sec.isin?.let { " / $it" } ?: ""}",
                payment.quantity.toPlainString(),
                sec.currency,
                fmt(payment.grossAmount),
                payment.exchangeRate?.let { fmt(it) } ?: "",
                fmt(grossCHF),
                fmt(payment.withholdingTax),
                fmt(net)
            ), state.regular, FONT_SIZE_SMALL)
        }

        state.ensureSpace(ROW_HEIGHT)
        drawTableRow(state, cols, listOf(
            "Total", "", "", "", "", "", fmt(totalGrossCHF), fmt(totalWt), fmt(totalNet)
        ), state.bold, FONT_SIZE_BODY)

        state.y -= 8f
    }

    private fun addTradesSection(state: DrawState, statement: EchTaxStatement) {
        val allTrades = statement.securities.flatMap { sec ->
            sec.stocks.filter { it.mutation }.map { sec to it }
        }
        if (allTrades.isEmpty()) return

        state.ensureSpace(30f)
        drawText(state.cs, "Transaktionen (Kaeufe / Verkaeufe)", MARGIN, state.y, state.bold, FONT_SIZE_SECTION)
        state.y -= FONT_SIZE_SECTION + 4f

        val cols = listOf(
            ColSpec(8f), ColSpec(5f), ColSpec(22f), ColSpec(6f, true), ColSpec(6f),
            ColSpec(10f, true), ColSpec(10f, true), ColSpec(8f, true), ColSpec(10f, true)
        )
        val headers = listOf("Datum", "K/V", "Titel / ISIN", "Stueck", "Waehrung", "Kurs", "Betrag", "Kurs CHF", "Wert CHF")

        state.ensureSpace(ROW_HEIGHT)
        drawTableRow(state, cols, headers, state.bold, FONT_SIZE_SMALL)

        for ((sec, stock) in allTrades) {
            val kv = if (stock.mutation && stock.quantity >= BigDecimal.ZERO) "K" else "V"
            state.ensureSpace(ROW_HEIGHT)
            drawTableRow(state, cols, listOf(
                stock.date.toString(),
                kv,
                "${sec.symbol}${sec.isin?.let { " / $it" } ?: ""}",
                stock.quantity.abs().toPlainString(),
                sec.currency,
                fmt(stock.unitPrice),
                fmt(stock.balance.abs()),
                stock.exchangeRate?.let { fmt(it) } ?: "",
                stock.valueCHF?.let { fmt(it.abs()) } ?: ""
            ), state.regular, FONT_SIZE_SMALL)
        }

        state.y -= 8f
    }

    private fun addYearEndSection(state: DrawState, statement: EchTaxStatement) {
        val yearEndStocks = statement.securities.flatMap { sec ->
            sec.stocks.filter { !it.mutation }.map { sec to it }
        }
        if (yearEndStocks.isEmpty()) return

        state.ensureSpace(30f)
        drawText(state.cs, "Jahresendbestand", MARGIN, state.y, state.bold, FONT_SIZE_SECTION)
        state.y -= FONT_SIZE_SECTION + 4f

        val cols = listOf(
            ColSpec(25f), ColSpec(8f, true), ColSpec(8f), ColSpec(12f, true),
            ColSpec(14f, true), ColSpec(10f, true), ColSpec(12f, true)
        )
        val headers = listOf("Titel / ISIN", "Stueck", "Waehrung", "Kurs 31.12.", "Wert Fremdwaehrung", "Kurs CHF", "Wert CHF")

        state.ensureSpace(ROW_HEIGHT)
        drawTableRow(state, cols, headers, state.bold, FONT_SIZE_SMALL)

        for ((sec, stock) in yearEndStocks) {
            state.ensureSpace(ROW_HEIGHT)
            drawTableRow(state, cols, listOf(
                "${sec.symbol}${sec.isin?.let { " / $it" } ?: ""}",
                stock.quantity.toPlainString(),
                sec.currency,
                fmt(stock.unitPrice),
                fmt(stock.balance),
                stock.exchangeRate?.let { fmt(it) } ?: "",
                stock.valueCHF?.let { fmt(it) } ?: ""
            ), state.regular, FONT_SIZE_SMALL)
        }

        state.y -= 8f
    }

    private fun addBarcode(doc: PDDocument, state: DrawState, xmlContent: String, documentId: String) {
        val randomId = Random.nextInt(1000, 9999)
        val segments = generateBarcodeSegments(xmlContent, documentId, randomId)

        // Portrait orientation: width = rows × element height, height = module-width × element width
        val portraitWidthPt = BARCODE_ROWS * ELEMENT_HEIGHT_PT      // ~79.4 pt
        val portraitHeightPt = PDF417_MODULE_WIDTH * ELEMENT_WIDTH_PT // ~345.3 pt

        state.ensureSpace(portraitHeightPt + 20f)

        val barcodeY = state.y - portraitHeightPt
        var x = MARGIN

        segments.forEachIndexed { i, pngBytes ->
            val pdImage = PDImageXObject.createFromByteArray(doc, pngBytes, "barcode_$i")
            state.cs.drawImage(pdImage, x, barcodeY, portraitWidthPt, portraitHeightPt)
            x += portraitWidthPt + 10f
        }

        state.y = barcodeY - 10f
    }

    private fun addFooter(state: DrawState) {
        state.ensureSpace(20f)
        val text = "Generiert gemaess eCH-0196 v2.2.0 | PDF417-Barcode enthaelt ZLIB-komprimiertes XML"
        drawCentered(state, text, state.y, state.regular, FONT_SIZE_FOOTER)
        state.y -= FONT_SIZE_FOOTER + 2f
    }

    private fun drawTableRow(state: DrawState, cols: List<ColSpec>, values: List<String>, font: PDType1Font, fontSize: Float) {
        var x = MARGIN
        values.forEachIndexed { i, text ->
            val w = USABLE_W * cols[i].widthPct / 100f
            state.cs.addRect(x, state.y - ROW_HEIGHT, w, ROW_HEIGHT)
            state.cs.stroke()
            val clipped = clipText(text, font, fontSize, w - 2 * CELL_PAD)
            val tx = if (cols[i].rightAlign) x + w - textWidth(clipped, font, fontSize) - CELL_PAD else x + CELL_PAD
            drawText(state.cs, clipped, tx, state.y - ROW_HEIGHT + 3f, font, fontSize)
            x += w
        }
        state.y -= ROW_HEIGHT
    }

    private fun drawText(cs: PDPageContentStream, text: String, x: Float, y: Float, font: PDType1Font, size: Float) {
        cs.beginText()
        cs.setFont(font, size)
        cs.newLineAtOffset(x, y)
        cs.showText(text)
        cs.endText()
    }

    private fun drawCentered(state: DrawState, text: String, y: Float, font: PDType1Font, size: Float) {
        val w = textWidth(text, font, size)
        drawText(state.cs, text, (PAGE_W - w) / 2, y, font, size)
    }

    private fun textWidth(text: String, font: PDType1Font, size: Float) =
        font.getStringWidth(text) / 1000f * size

    private fun clipText(text: String, font: PDType1Font, size: Float, maxWidth: Float): String {
        if (textWidth(text, font, size) <= maxWidth) return text
        var clipped = text
        val ellipsis = "..."
        while (clipped.isNotEmpty() && textWidth(clipped + ellipsis, font, size) > maxWidth) {
            clipped = clipped.dropLast(1)
        }
        return if (clipped.isEmpty()) "" else clipped + ellipsis
    }

    private fun generateBarcodeSegments(content: String, documentId: String, randomId: Int): List<ByteArray> {
        val compressed = ByteArrayOutputStream().also { baos ->
            DeflaterOutputStream(baos, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(content.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()

        val chunks = compressed.toList().chunked(BARCODE_SEGMENT_BYTES) { it.toByteArray() }

        // File ID: 9-digit numeric string in three 000–899 triplets, derived from randomId
        val fileId = listOf(randomId % 900, (randomId / 900) % 900, 0)
            .joinToString("") { it.toString().padStart(3, '0') }

        return chunks.mapIndexed { i, chunk ->
            renderSegment(chunk, position = i + 1, total = chunks.size, fileId = fileId, documentId = documentId)
        }
    }

    internal fun assertBarcodeSpec(barcode: Pdf417) {
        val errors = mutableListOf<String>()
        if (barcode.dataColumns != BARCODE_DATA_COLUMNS) {
            errors.add("Barcode data columns: expected $BARCODE_DATA_COLUMNS, actual ${barcode.dataColumns}")
        }
        if (barcode.preferredEccLevel != BARCODE_ECC_LEVEL) {
            errors.add("Barcode ECC level: expected $BARCODE_ECC_LEVEL, actual ${barcode.preferredEccLevel}")
        }
        if (errors.isNotEmpty()) throw PdfBarcodeValidationException(errors)
    }

    private fun renderSegment(data: ByteArray, position: Int, total: Int, fileId: String, documentId: String): ByteArray {
        val barcode = Pdf417(Mode.NORMAL).apply {
            setContent(String(data, Charsets.ISO_8859_1))  // ISO-8859-1 preserves byte values
            dataColumns = BARCODE_DATA_COLUMNS
            setRows(BARCODE_ROWS)
            setPreferredEccLevel(BARCODE_ECC_LEVEL)
            setStructuredAppendPosition(position)
            setStructuredAppendTotal(total)
            setStructuredAppendFileId(fileId)
            setStructuredAppendFileName(documentId)
            setStructuredAppendIncludeSegmentCount(true)
        }
        assertBarcodeSpec(barcode)

        // Render landscape bitmap, then rotate 90° → portrait
        val landscapeW = (barcode.width * BARCODE_MAGNIFICATION).toInt()
        val landscapeH = (barcode.height * BARCODE_MAGNIFICATION).toInt()
        val landscape = BufferedImage(landscapeW, landscapeH, BufferedImage.TYPE_INT_RGB)
        val g2d = landscape.createGraphics()
        Java2DRenderer(g2d, BARCODE_MAGNIFICATION, OkapiColor(255, 255, 255), OkapiColor(0, 0, 0)).render(barcode)
        g2d.dispose()

        // Rotate 90° clockwise: yields portrait image (h > w)
        val portrait = BufferedImage(landscapeH, landscapeW, BufferedImage.TYPE_INT_RGB)
        val gr = portrait.createGraphics()
        gr.translate(landscapeH, 0)
        gr.rotate(Math.PI / 2)
        gr.drawImage(landscape, 0, 0, null)
        gr.dispose()

        val baos = ByteArrayOutputStream()
        ImageIO.write(portrait, "PNG", baos)
        return baos.toByteArray()
    }

    private fun fmt(amount: BigDecimal): String =
        amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
