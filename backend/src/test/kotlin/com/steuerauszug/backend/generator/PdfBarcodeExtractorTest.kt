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

    // Minimal single-security statement for single-segment tests
    private val statement = EchTaxStatement(
        documentId = "CH-8888-123456-2024-001",
        taxPeriod = 2024,
        periodFrom = LocalDate.of(2024, 1, 1),
        periodTo = LocalDate.of(2024, 12, 31),
        institution = Institution("8888", "Test Bank", "Teststrasse 1"),
        customer = Customer("123456", "Max Muster", "Musterweg 2"),
        canton = "ZH",
        securities = listOf(
            EchSecurity(
                symbol = "AAPL",
                isin = "US0378331005",
                description = "AAPL – Dividende",
                currency = "USD",
                sourceCountry = "US",
                securityCategory = "SHARE",
                payments = listOf(
                    EchPayment(
                        date = LocalDate.of(2024, 3, 15),
                        quantity = BigDecimal.ONE,
                        grossAmount = BigDecimal("100.00"),
                        withholdingTax = BigDecimal("15.00"),
                        exchangeRate = null,
                        grossAmountCHF = null
                    )
                ),
                yearEndTaxValue = null,
                stocks = emptyList()
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

    @Test
    fun `multi-segment round-trip recovers XML and passes schema validation`() {
        // 20 securities × 2 payments each → XML well over 5 KB uncompressed → forces ≥2 barcode segments
        val securities = (1..20).map { i ->
            EchSecurity(
                symbol = "SEC${i.toString().padStart(2, '0')}",
                isin = "US${i.toString().padStart(10, '0')}",
                description = "Security $i – Dividende",
                currency = "USD",
                sourceCountry = "US",
                securityCategory = "SHARE",
                payments = listOf(
                    EchPayment(
                        date = LocalDate.of(2024, 3, 15),
                        quantity = BigDecimal.ONE,
                        grossAmount = BigDecimal("100.00"),
                        withholdingTax = BigDecimal("15.00"),
                        exchangeRate = null,
                        grossAmountCHF = null
                    ),
                    EchPayment(
                        date = LocalDate.of(2024, 9, 15),
                        quantity = BigDecimal.ONE,
                        grossAmount = BigDecimal("110.00"),
                        withholdingTax = BigDecimal("16.50"),
                        exchangeRate = null,
                        grossAmountCHF = null
                    )
                ),
                yearEndTaxValue = null,
                stocks = emptyList()
            )
        }
        val largeStatement = EchTaxStatement(
            documentId = "CH-8888-123456-2024-MULTI",
            taxPeriod = 2024,
            periodFrom = LocalDate.of(2024, 1, 1),
            periodTo = LocalDate.of(2024, 12, 31),
            institution = Institution("8888", "Test Bank", "Teststrasse 1"),
            customer = Customer("123456", "Max Muster", "Musterweg 2"),
            canton = "ZH",
            securities = securities,
            totalGross = BigDecimal("4200.00"),
            totalWithholding = BigDecimal("630.00"),
            totalNet = BigDecimal("3570.00")
        )

        val xml = generator.generate(largeStatement)
        val pdfBytes = pdfGenerator.generate(largeStatement, xml)

        val recoveredXml = extractor.extract(pdfBytes)

        assertEquals(xml, recoveredXml)
        assertDoesNotThrow { EchXmlValidator().validate(recoveredXml) }
    }
}
