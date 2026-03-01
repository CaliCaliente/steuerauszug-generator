package com.steuerauszug.backend.parser

import com.steuerauszug.backend.model.BuySell
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class IbCsvParserTest {

    private val parser = IbCsvParser()

    @Test
    fun `should parse dividend row correctly`() {
        val csv = """
            Dividends,Data,USD,2024-03-15,AAPL(US0378331005) Cash Dividend USD 0.24 per Share,100.00
        """.trimIndent()

        val result = parser.parse(csv)

        assertEquals(1, result.dividends.size)
        val div = result.dividends[0]
        assertEquals(LocalDate.of(2024, 3, 15), div.date)
        assertEquals("AAPL", div.symbol)
        assertEquals("USD", div.currency)
        assertEquals(BigDecimal("100.00"), div.amount)
    }

    @Test
    fun `should parse withholding tax row and take absolute value`() {
        val csv = """
            Withholding Tax,Data,USD,2024-03-15,AAPL(US0378331005) Cash Dividend,-15.00
        """.trimIndent()

        val result = parser.parse(csv)

        assertEquals(1, result.withholdingTax.size)
        val wt = result.withholdingTax[0]
        assertEquals("AAPL", wt.symbol)
        assertEquals(BigDecimal("15.00"), wt.amount)
    }

    @Test
    fun `should parse interest row correctly`() {
        val csv = """
            Interest,Data,USD,2024-03-31,USD Credit Interest for Mar-2024,5.50
        """.trimIndent()

        val result = parser.parse(csv)

        assertEquals(1, result.interest.size)
        val interest = result.interest[0]
        assertEquals(LocalDate.of(2024, 3, 31), interest.date)
        assertEquals("USD", interest.currency)
        assertEquals(BigDecimal("5.50"), interest.amount)
    }

    @Test
    fun `should skip dividend row with zero amount`() {
        val csv = """
            Dividends,Data,USD,2024-03-15,AAPL(US0378331005) Cash Dividend,0.00
        """.trimIndent()

        val result = parser.parse(csv)

        assertTrue(result.dividends.isEmpty())
    }

    @Test
    fun `should skip dividend row with negative amount`() {
        val csv = """
            Dividends,Data,USD,2024-03-15,AAPL(US0378331005) Cash Dividend,-10.00
        """.trimIndent()

        val result = parser.parse(csv)

        assertTrue(result.dividends.isEmpty())
    }

    @Test
    fun `should skip row with invalid date`() {
        val csv = """
            Dividends,Data,USD,not-a-date,AAPL(US0378331005) Cash Dividend,100.00
        """.trimIndent()

        val result = parser.parse(csv)

        assertTrue(result.dividends.isEmpty())
    }

    @Test
    fun `should skip row with invalid amount`() {
        val csv = """
            Dividends,Data,USD,2024-03-15,AAPL(US0378331005) Cash Dividend,not-a-number
        """.trimIndent()

        val result = parser.parse(csv)

        assertTrue(result.dividends.isEmpty())
    }

    @Test
    fun `should handle quoted fields in CSV`() {
        val csv = """
            Dividends,Data,USD,2024-03-15,"AAPL(US0378331005) Cash Dividend, USD 0.24","1,000.00"
        """.trimIndent()

        val result = parser.parse(csv)

        assertEquals(1, result.dividends.size)
        assertEquals(BigDecimal("1000.00"), result.dividends[0].amount)
    }

    @Test
    fun `should ignore lines that are not Data rows`() {
        val csv = """
            Dividends,Header,Currency,Date,Description,Amount
            Dividends,Data,USD,2024-03-15,AAPL(US0378331005) Cash Dividend,100.00
            Dividends,Total,,,,100.00
        """.trimIndent()

        val result = parser.parse(csv)

        assertEquals(1, result.dividends.size)
    }

    @Test
    fun `should return empty collections for empty input`() {
        val result = parser.parse("")

        assertTrue(result.dividends.isEmpty())
        assertTrue(result.withholdingTax.isEmpty())
        assertTrue(result.interest.isEmpty())
    }

    @Test
    fun `should parse BUY trade from Trades section`() {
        val csv = """
Trades,Header,DataDiscriminator,Asset Category,Currency,Symbol,Date/Time,Quantity,T. Price,Proceeds
Trades,Data,Order,Stocks,USD,AAPL,2024-06-01,10,150.00,-1500.00
        """.trimIndent()

        val result = parser.parse(csv)

        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals("AAPL", trade.symbol)
        assertEquals(BuySell.BUY, trade.buySell)
        assertEquals(BigDecimal("10"), trade.quantity)
        assertEquals(BigDecimal("150.00"), trade.tradePrice)
    }

    @Test
    fun `should parse SELL trade when quantity is negative`() {
        val csv = """
Trades,Header,DataDiscriminator,Asset Category,Currency,Symbol,Date/Time,Quantity,T. Price,Proceeds
Trades,Data,Order,Stocks,USD,AAPL,2024-09-01,-5,175.00,875.00
        """.trimIndent()

        val result = parser.parse(csv)

        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals(BuySell.SELL, trade.buySell)
        assertEquals(BigDecimal("5"), trade.quantity)
    }

    @Test
    fun `should skip non-Order non-Stocks trade rows`() {
        val csv = """
Trades,Header,DataDiscriminator,Asset Category,Currency,Symbol,Date/Time,Quantity,T. Price,Proceeds
Trades,Data,SubTotal,Stocks,USD,AAPL,2024-06-01,10,150.00,-1500.00
        """.trimIndent()

        val result = parser.parse(csv)

        assertTrue(result.trades.isEmpty())
    }

    @Test
    fun `should parse open position from Open Positions section`() {
        val csv = """
Open Positions,Header,DataDiscriminator,Asset Category,Currency,Symbol,Quantity,Close Price,Value
Open Positions,Data,Summary,Stocks,USD,AAPL,10,182.00,1820.00
        """.trimIndent()

        val result = parser.parse(csv)

        assertEquals(1, result.openPositions.size)
        val pos = result.openPositions[0]
        assertEquals("AAPL", pos.symbol)
        assertEquals(BigDecimal("10"), pos.quantity)
        assertEquals(BigDecimal("182.00"), pos.markPrice)
        assertEquals(BigDecimal("1820.00"), pos.positionValue)
    }

    @Test
    fun `should skip open position rows that are not Summary Stocks`() {
        val csv = """
Open Positions,Header,DataDiscriminator,Asset Category,Currency,Symbol,Quantity,Close Price,Value
Open Positions,Data,Summary,Options,USD,AAPL,1,5.00,500.00
        """.trimIndent()

        val result = parser.parse(csv)

        assertTrue(result.openPositions.isEmpty())
    }
}
