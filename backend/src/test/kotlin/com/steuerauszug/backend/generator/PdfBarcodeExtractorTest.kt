package com.steuerauszug.backend.generator

import com.steuerauszug.backend.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class PdfBarcodeExtractorTest {

    private val generator = EchXmlGenerator()
    private val pdfGenerator = PdfGenerator()
    private val extractor = PdfBarcodeExtractor()

    private val statement = EchTaxStatement(
        documentId = "CH-8888-123456-2024-001",
        taxPeriod = 2024,
        periodFrom = LocalDate.of(2024, 1, 1),
        periodTo = LocalDate.of(2024, 12, 31),
        institution = Institution("8888", "Test Bank", "Teststrasse 1"),
        customer = Customer("123456", "Max Muster", "Musterweg 2"),
        canton = "ZH",
        items = listOf(
            TaxItem(
                type = TaxItemType.DIVIDEND,
                description = "AAPL – Dividende",
                currency = "USD",
                grossAmount = BigDecimal("100.00"),
                withholdingTax = BigDecimal("15.00"),
                netAmount = BigDecimal("85.00"),
                sourceCountry = "US",
                isin = "US0378331005"
            )
        ),
        totalGross = BigDecimal("100.00"),
        totalWithholding = BigDecimal("15.00"),
        totalNet = BigDecimal("85.00")
    )

    @Test
    fun `should extract and decompress XML from PDF barcode`() {
        val xml = generator.generate(statement)
        val pdfBytes = pdfGenerator.generate(statement, xml)

        val recoveredXml = extractor.extract(pdfBytes)

        assertEquals(xml, recoveredXml)
    }

    @Test
    fun `should recover XML that passes schema validation`() {
        val xml = generator.generate(statement)
        val pdfBytes = pdfGenerator.generate(statement, xml)

        val recoveredXml = extractor.extract(pdfBytes)

        assertDoesNotThrow { EchXmlValidator().validate(recoveredXml) }
    }
}
