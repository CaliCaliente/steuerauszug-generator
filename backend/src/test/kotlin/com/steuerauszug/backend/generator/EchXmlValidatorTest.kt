package com.steuerauszug.backend.generator

import com.steuerauszug.backend.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

class EchXmlValidatorTest {

    private val validator = EchXmlValidator()
    private val generator = EchXmlGenerator()

    private val validStatement = EchTaxStatement(
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
    fun `should pass validation for generator output`() {
        val xml = generator.generate(validStatement)
        assertDoesNotThrow { validator.validate(xml) }
    }

    @Test
    fun `should throw XmlValidationException for missing required attribute`() {
        val invalidXml = """<?xml version="1.0" encoding="UTF-8"?>
            <taxStatement xmlns="http://www.ech.ch/xmlns/eCH-0196/2"
                id="CH-8888-123456-2024-001"
                minorVersion="2"
                creationDate="2024-01-15T10:00:00"
                taxPeriod="2024"
                periodFrom="2024-01-01"
                periodTo="2024-12-31"
                canton="ZH"
                totalTaxValue="85.00"
                totalGrossRevenueA="100.00"
                totalGrossRevenueB="0"
                totalWithHoldingTaxClaim="15.00">
              <institution name="Test Bank"/>
            </taxStatement>"""

        val ex = assertThrows<XmlValidationException> { validator.validate(invalidXml) }
        assertTrue(ex.errors.any { it.startsWith("ERROR") || it.startsWith("FATAL") })
    }

    @Test
    fun `should throw XmlValidationException for wrong namespace`() {
        val wrongNsXml = """<?xml version="1.0" encoding="UTF-8"?>
            <taxStatement xmlns="http://wrong.namespace.example">
              <institution name="Test Bank"/>
            </taxStatement>"""

        assertThrows<XmlValidationException> { validator.validate(wrongNsXml) }
    }

    @Test
    fun `should throw XmlValidationException for non-XML content`() {
        assertThrows<Exception> { validator.validate("not xml at all") }
    }
}
