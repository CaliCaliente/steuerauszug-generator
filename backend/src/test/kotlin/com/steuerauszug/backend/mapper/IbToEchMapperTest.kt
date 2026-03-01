package com.steuerauszug.backend.mapper

import com.steuerauszug.backend.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class IbToEchMapperTest {

    private val mapper = IbToEchMapper()

    private val baseRequest = GenerationRequest(
        clearingNumber = "8888",
        institutionName = "Test Bank",
        institutionAddress = "Teststrasse 1, 8001 Zürich",
        customerNumber = "123456",
        customerName = "Max Muster",
        customerAddress = "Musterweg 2, 8001 Zürich",
        canton = "ZH",
        taxYear = 2024
    )

    private fun dividend(symbol: String, amount: String, description: String = "$symbol(US0000000000) Cash Dividend", currency: String = "USD") =
        IbDividend(LocalDate.of(2024, 3, 15), symbol, description, currency, BigDecimal(amount))

    private fun withholdingTax(symbol: String, amount: String, currency: String = "USD") =
        IbWithholdingTax(LocalDate.of(2024, 3, 15), symbol, currency, BigDecimal(amount))

    private fun interest(currency: String, amount: String) =
        IbInterest(LocalDate.of(2024, 3, 31), "Interest description", currency, BigDecimal(amount))

    @Test
    fun `should group dividends by symbol and sum amounts`() {
        val ibData = IbActivityData(
            dividends = listOf(
                dividend("AAPL", "100.00"),
                dividend("AAPL", "50.00")
            ),
            withholdingTax = emptyList(),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        assertEquals(1, result.items.size)
        assertEquals(BigDecimal("150.00"), result.items[0].grossAmount)
    }

    @Test
    fun `should match withholding tax to dividend by symbol`() {
        val ibData = IbActivityData(
            dividends = listOf(dividend("AAPL", "100.00")),
            withholdingTax = listOf(withholdingTax("AAPL", "15.00")),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        assertEquals(BigDecimal("15.00"), result.items[0].withholdingTax)
        assertEquals(BigDecimal("85.00"), result.items[0].netAmount)
    }

    @Test
    fun `should use zero withholding when no match found`() {
        val ibData = IbActivityData(
            dividends = listOf(dividend("AAPL", "100.00")),
            withholdingTax = listOf(withholdingTax("MSFT", "15.00")),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        assertEquals(BigDecimal.ZERO, result.items[0].withholdingTax)
        assertEquals(BigDecimal("100.00"), result.items[0].netAmount)
    }

    @Test
    fun `should group interest by currency and sum amounts`() {
        val ibData = IbActivityData(
            dividends = emptyList(),
            withholdingTax = emptyList(),
            interest = listOf(
                interest("USD", "5.00"),
                interest("USD", "3.00"),
                interest("CHF", "1.00")
            )
        )

        val result = mapper.map(ibData, baseRequest)

        assertEquals(2, result.items.size)
        val usdItem = result.items.first { it.currency == "USD" }
        assertEquals(BigDecimal("8.00"), usdItem.grossAmount)
    }

    @Test
    fun `should extract country code from ISIN in description`() {
        val ibData = IbActivityData(
            dividends = listOf(dividend("AAPL", "100.00", "AAPL(US0378331005) Cash Dividend")),
            withholdingTax = emptyList(),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        assertEquals("US", result.items[0].sourceCountry)
    }

    @Test
    fun `should extract full ISIN for dividend items`() {
        val ibData = IbActivityData(
            dividends = listOf(dividend("AAPL", "100.00", "AAPL(US0378331005) Cash Dividend")),
            withholdingTax = emptyList(),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        assertEquals("US0378331005", result.items[0].isin)
    }

    @Test
    fun `should set null isin for interest items`() {
        val ibData = IbActivityData(
            dividends = emptyList(),
            withholdingTax = emptyList(),
            interest = listOf(interest("USD", "5.00"))
        )

        val result = mapper.map(ibData, baseRequest)

        assertNull(result.items[0].isin)
    }

    @Test
    fun `should set null isin when ISIN not found in description`() {
        val ibData = IbActivityData(
            dividends = listOf(dividend("AAPL", "100.00", "AAPL Cash Dividend No ISIN")),
            withholdingTax = emptyList(),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        assertNull(result.items[0].isin)
    }

    @Test
    fun `should return XX when ISIN not found in description`() {
        val ibData = IbActivityData(
            dividends = listOf(dividend("AAPL", "100.00", "AAPL Cash Dividend No ISIN")),
            withholdingTax = emptyList(),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        assertEquals("XX", result.items[0].sourceCountry)
    }

    @Test
    fun `should compute correct totals`() {
        val ibData = IbActivityData(
            dividends = listOf(dividend("AAPL", "100.00")),
            withholdingTax = listOf(withholdingTax("AAPL", "15.00")),
            interest = listOf(interest("USD", "5.00"))
        )

        val result = mapper.map(ibData, baseRequest)

        assertEquals(BigDecimal("105.00"), result.totalGross)
        assertEquals(BigDecimal("15.00"), result.totalWithholding)
        assertEquals(BigDecimal("90.00"), result.totalNet)
    }

    @Test
    fun `should build document ID from clearing number, customer number and tax year`() {
        val ibData = IbActivityData(emptyList(), emptyList(), emptyList())

        val result = mapper.map(ibData, baseRequest)

        assertEquals("CH-8888-123456-2024-001", result.documentId)
    }

    @Test
    fun `should set period from 1 January to 31 December of tax year`() {
        val ibData = IbActivityData(emptyList(), emptyList(), emptyList())

        val result = mapper.map(ibData, baseRequest)

        assertEquals(LocalDate.of(2024, 1, 1), result.periodFrom)
        assertEquals(LocalDate.of(2024, 12, 31), result.periodTo)
    }
}
