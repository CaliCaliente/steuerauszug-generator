package com.steuerauszug.backend.mapper

import com.steuerauszug.backend.model.*
import com.steuerauszug.backend.service.EstvExchangeRateService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class IbToEchMapperTest {

    private val mockExchangeRateService = mockk<EstvExchangeRateService>()
    private val mapper = IbToEchMapper(mockExchangeRateService)

    init {
        every { mockExchangeRateService.getAnnualAverageRate(any(), any()) } returns BigDecimal.ONE
        every { mockExchangeRateService.getYearEndRate(any(), any()) } returns BigDecimal.ONE
    }

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

    private fun dividend(symbol: String, amount: String, date: LocalDate = LocalDate.of(2024, 3, 15),
                         description: String = "$symbol(US0000000000) Cash Dividend", currency: String = "USD") =
        IbDividend(date, symbol, description, currency, BigDecimal(amount))

    private fun withholdingTax(symbol: String, amount: String, currency: String = "USD") =
        IbWithholdingTax(LocalDate.of(2024, 3, 15), symbol, currency, BigDecimal(amount))

    private fun interest(currency: String, amount: String, date: LocalDate = LocalDate.of(2024, 3, 31)) =
        IbInterest(date, "Interest description", currency, BigDecimal(amount))

    private fun trade(symbol: String, quantity: String, price: String, buySell: BuySell = BuySell.BUY) =
        IbTrade(LocalDate.of(2024, 6, 1), symbol, null, symbol, "USD", buySell,
            BigDecimal(quantity), BigDecimal(price), BigDecimal(quantity).multiply(BigDecimal(price)).negate(), BigDecimal.ONE)

    private fun openPosition(symbol: String, quantity: String, price: String) =
        IbOpenPosition(LocalDate.of(2024, 12, 31), symbol, null, symbol, "USD",
            BigDecimal(quantity), BigDecimal(price), BigDecimal(quantity).multiply(BigDecimal(price)), BigDecimal.ONE)

    @Test
    fun `should create one EchPayment per dividend event`() {
        val ibData = IbActivityData(
            dividends = listOf(
                dividend("AAPL", "100.00", LocalDate.of(2024, 3, 15)),
                dividend("AAPL", "50.00", LocalDate.of(2024, 6, 15))
            ),
            withholdingTax = emptyList(),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        val sec = result.securities.first { it.symbol == "AAPL" }
        assertEquals(2, sec.payments.size)
        assertEquals(BigDecimal("100.00"), sec.payments[0].grossAmount)
        assertEquals(BigDecimal("50.00"), sec.payments[1].grossAmount)
    }

    @Test
    fun `should distribute withholding tax proportionally across dividend payments`() {
        val ibData = IbActivityData(
            dividends = listOf(
                dividend("AAPL", "100.00"),
                dividend("AAPL", "100.00")
            ),
            withholdingTax = listOf(withholdingTax("AAPL", "30.00")),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        val sec = result.securities.first { it.symbol == "AAPL" }
        assertEquals(2, sec.payments.size)
        // 15.00 each (proportional: 100/(100+100) * 30 = 15)
        assertEquals(BigDecimal("15.00"), sec.payments[0].withholdingTax.setScale(2))
        assertEquals(BigDecimal("15.00"), sec.payments[1].withholdingTax.setScale(2))
    }

    @Test
    fun `should use zero withholding when no match found`() {
        val ibData = IbActivityData(
            dividends = listOf(dividend("AAPL", "100.00")),
            withholdingTax = listOf(withholdingTax("MSFT", "15.00")),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        val sec = result.securities.first { it.symbol == "AAPL" }
        assertEquals(BigDecimal.ZERO, sec.payments[0].withholdingTax)
    }

    @Test
    fun `should create one EchSecurity per interest currency`() {
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

        val usdSec = result.securities.first { it.currency == "USD" }
        assertEquals(2, usdSec.payments.size)
        assertEquals(BigDecimal("5.00"), usdSec.payments[0].grossAmount)
        assertEquals(BigDecimal("3.00"), usdSec.payments[1].grossAmount)
    }

    @Test
    fun `should create EchStock mutation=true for BUY trade`() {
        val ibData = IbActivityData(
            dividends = emptyList(),
            withholdingTax = emptyList(),
            interest = emptyList(),
            trades = listOf(trade("AAPL", "10", "150.00", BuySell.BUY))
        )

        val result = mapper.map(ibData, baseRequest)

        val sec = result.securities.first { it.symbol == "AAPL" }
        assertEquals(1, sec.stocks.size)
        val stock = sec.stocks[0]
        assertTrue(stock.mutation)
        assertEquals("Kauf", stock.name)
        assertEquals(BigDecimal("10"), stock.quantity)
    }

    @Test
    fun `should create EchStock mutation=true with negative quantity for SELL trade`() {
        val ibData = IbActivityData(
            dividends = emptyList(),
            withholdingTax = emptyList(),
            interest = emptyList(),
            trades = listOf(trade("AAPL", "10", "150.00", BuySell.SELL))
        )

        val result = mapper.map(ibData, baseRequest)

        val sec = result.securities.first { it.symbol == "AAPL" }
        val stock = sec.stocks[0]
        assertTrue(stock.mutation)
        assertEquals("Verkauf", stock.name)
        assertTrue(stock.quantity < BigDecimal.ZERO)
    }

    @Test
    fun `should create EchTaxValue and EchStock mutation=false for open position`() {
        val ibData = IbActivityData(
            dividends = emptyList(),
            withholdingTax = emptyList(),
            interest = emptyList(),
            openPositions = listOf(openPosition("AAPL", "10", "182.00"))
        )

        val result = mapper.map(ibData, baseRequest)

        val sec = result.securities.first { it.symbol == "AAPL" }
        assertNotNull(sec.yearEndTaxValue)
        assertEquals(BigDecimal("10"), sec.yearEndTaxValue!!.quantity)

        assertEquals(1, sec.stocks.size)
        val stock = sec.stocks[0]
        assertFalse(stock.mutation)
        assertEquals("Jahresendbestand", stock.name)
    }

    @Test
    fun `should extract country code from ISIN in description`() {
        val ibData = IbActivityData(
            dividends = listOf(dividend("AAPL", "100.00", description = "AAPL(US0378331005) Cash Dividend")),
            withholdingTax = emptyList(),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        assertEquals("US", result.securities.first { it.symbol == "AAPL" }.sourceCountry)
    }

    @Test
    fun `should extract full ISIN from description`() {
        val ibData = IbActivityData(
            dividends = listOf(dividend("AAPL", "100.00", description = "AAPL(US0378331005) Cash Dividend")),
            withholdingTax = emptyList(),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        assertEquals("US0378331005", result.securities.first { it.symbol == "AAPL" }.isin)
    }

    @Test
    fun `should set null isin when ISIN not found in description`() {
        val ibData = IbActivityData(
            dividends = listOf(dividend("AAPL", "100.00", description = "AAPL Cash Dividend No ISIN")),
            withholdingTax = emptyList(),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        assertNull(result.securities.first { it.symbol == "AAPL" }.isin)
    }

    @Test
    fun `should return XX sourceCountry when ISIN not found in description`() {
        val ibData = IbActivityData(
            dividends = listOf(dividend("AAPL", "100.00", description = "AAPL Cash Dividend No ISIN")),
            withholdingTax = emptyList(),
            interest = emptyList()
        )

        val result = mapper.map(ibData, baseRequest)

        assertEquals("XX", result.securities.first { it.symbol == "AAPL" }.sourceCountry)
    }

    @Test
    fun `should compute correct totals`() {
        val ibData = IbActivityData(
            dividends = listOf(dividend("AAPL", "100.00")),
            withholdingTax = listOf(withholdingTax("AAPL", "15.00")),
            interest = listOf(interest("USD", "5.00"))
        )

        val result = mapper.map(ibData, baseRequest)

        // Exchange rate = 1.0, so CHF amounts equal original amounts
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
