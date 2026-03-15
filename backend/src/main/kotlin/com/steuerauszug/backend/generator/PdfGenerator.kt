package com.steuerauszug.backend.generator

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
		private const val PAGE_MARGIN = 36f

		// eCH-0196 barcode spec
		private const val BARCODE_SEGMENT_BYTES = 450
		private const val BARCODE_DATA_COLUMNS = 13
		private const val BARCODE_ROWS = 35
		private const val BARCODE_ECC_LEVEL = 4
		private const val BARCODE_MAGNIFICATION = 2.0

		// Physical dimensions per eCH-0196 spec (1 pt = 0.03528 cm)
		private const val ELEMENT_WIDTH_PT = 0.042f / 0.03528f    // 1.1906 pt per module
		private const val ELEMENT_HEIGHT_PT = 0.080f / 0.03528f   // 2.2677 pt per row
		// PDF417 total module width = (dataColumns + 4) × 17 + 1
		private const val PDF417_MODULE_WIDTH = (BARCODE_DATA_COLUMNS + 4) * 17 + 1  // = 290
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
		addBarcode(doc, xmlContent, statement.documentId)
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

	private fun addBarcode(doc: Document, xmlContent: String, documentId: String) {
		try {
			val randomId = Random.nextInt(1000, 9999)
			val segments = generateBarcodeSegments(xmlContent, documentId, randomId)

			// Portrait width = 35 rows × element height; portrait height = 290 modules × element width
			val portraitWidthPt = BARCODE_ROWS * ELEMENT_HEIGHT_PT      // ~79.4 pt
			val portraitHeightPt = PDF417_MODULE_WIDTH * ELEMENT_WIDTH_PT // ~345.3 pt

			val table = Table(UnitValue.createPercentArray(FloatArray(segments.size) { 100f / segments.size }))
				.useAllAvailableWidth()
			segments.forEach { pngBytes ->
				val img = Image(ImageDataFactory.create(pngBytes))
					.setWidth(portraitWidthPt)
					.setHeight(portraitHeightPt)
				table.addCell(Cell().add(img).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
			}
			doc.add(table)
		} catch (e: Exception) {
			log.warn("Barcode generation failed: ${e.message}", e)
			doc.add(Paragraph("Barcode konnte nicht generiert werden: ${e.message}").setFontSize(FONT_SIZE_SMALL))
		}
	}

	private fun generateBarcodeSegments(content: String, documentId: String, randomId: Int): List<ByteArray> {
		// ZLIB BEST_COMPRESSION per eCH-0196 spec
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

	private fun addFooter(doc: Document) {
		doc.add(
			Paragraph("Generiert gemäss eCH-0196 v2.2.0 | PDF417-Barcode enthält ZLIB-komprimiertes XML")
				.setFontSize(FONT_SIZE_FOOTER)
				.setTextAlignment(TextAlignment.CENTER)
		)
	}

	private fun fmt(amount: BigDecimal): String =
		amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
