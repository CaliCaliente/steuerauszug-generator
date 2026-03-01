package com.steuerauszug.backend

import com.steuerauszug.backend.generator.PdfGenerator
import com.steuerauszug.backend.model.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class PdfGeneratorTest {

    companion object {
        private const val MIN_PDF_SIZE = 1000
    }

    @Test
    fun `PDF generation produces a non-empty PDF`() {
        val statement = EchTaxStatement(
            documentId = "CH-8888-U1234567-2024-001",
            taxPeriod = 2024,
            periodFrom = LocalDate.of(2024, 1, 1),
            periodTo = LocalDate.of(2024, 12, 31),
            institution = Institution("8888", "Interactive Brokers", "Postfach 1234, 8001 Zürich"),
            customer = Customer("U1234567", "Max Mustermann", "Musterstrasse 1, 8001 Zürich"),
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
                            grossAmount = BigDecimal("96.00"),
                            withholdingTax = BigDecimal("28.80"),
                            exchangeRate = null,
                            grossAmountCHF = null
                        )
                    ),
                    yearEndTaxValue = null,
                    stocks = emptyList()
                )
            ),
            totalGross = BigDecimal("96.00"),
            totalWithholding = BigDecimal("28.80"),
            totalNet = BigDecimal("67.20")
        )
        val xml = "<?xml version=\"1.0\"?><taxStatement>test</taxStatement>"
        val pdf = PdfGenerator().generate(statement, xml)
        assertTrue(pdf.size > MIN_PDF_SIZE) { "PDF too small: ${pdf.size} bytes" }
    }
}
