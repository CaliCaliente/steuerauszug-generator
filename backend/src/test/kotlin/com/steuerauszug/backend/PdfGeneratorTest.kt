package com.steuerauszug.backend

import com.steuerauszug.backend.generator.PdfGenerator
import com.steuerauszug.backend.model.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class PdfGeneratorTest {
    @Test
    fun testPdfGeneration() {
        val statement = EchTaxStatement(
            documentId = "CH-8888-U1234567-2024-001",
            taxPeriod = 2024,
            periodFrom = LocalDate.of(2024, 1, 1),
            periodTo = LocalDate.of(2024, 12, 31),
            institution = Institution("8888", "Interactive Brokers", "Postfach 1234, 8001 Zürich"),
            customer = Customer("U1234567", "Max Mustermann", "Musterstrasse 1, 8001 Zürich"),
            canton = "ZH",
            items = listOf(
                TaxItem(TaxItemType.DIVIDEND, "AAPL – Dividende", "USD",
                    BigDecimal("96.00"), BigDecimal("28.80"), BigDecimal("67.20"), "US")
            ),
            totalGross = BigDecimal("96.00"),
            totalWithholding = BigDecimal("28.80"),
            totalNet = BigDecimal("67.20")
        )
        val xml = "<?xml version=\"1.0\"?><taxStatement>test</taxStatement>"
        try {
            val pdf = PdfGenerator().generate(statement, xml)
            println("PDF size: ${pdf.size} bytes")
            assert(pdf.size > 1000)
            println(String(pdf.take(8).toByteArray()))
        } catch (e: Exception) {
            println("ERROR: ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
// note: added write to file above
