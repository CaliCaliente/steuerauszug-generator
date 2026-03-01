package com.steuerauszug.backend.parser

import com.steuerauszug.backend.model.BuySell
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class IbFlexQueryParserTest {

    private val parser = IbFlexQueryParser()

    private fun xml(vararg transactions: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <FlexQueryResponse>
            <FlexStatements>
                <FlexStatement>
                    <CashTransactions>
                        ${transactions.joinToString("\n")}
                    </CashTransactions>
                </FlexStatement>
            </FlexStatements>
        </FlexQueryResponse>
    """.trimIndent()

    @Test
    fun `should parse dividend transaction correctly`() {
        val input = xml(
            """<CashTransaction type="Dividends" currency="USD" dateTime="2024-03-15;093000" description="AAPL(US0378331005) Cash Dividend" symbol="AAPL" amount="100.00"/>"""
        )

        val result = parser.parse(input)

        assertEquals(1, result.dividends.size)
        val div = result.dividends[0]
        assertEquals("AAPL", div.symbol)
        assertEquals(LocalDate.of(2024, 3, 15), div.date)
        assertEquals("USD", div.currency)
        assertEquals(BigDecimal("100.00"), div.amount)
    }

    @Test
    fun `should parse withholding tax transaction and take absolute value`() {
        val input = xml(
            """<CashTransaction type="Withholding Tax" currency="USD" dateTime="2024-03-15;093000" description="AAPL(US0378331005) Cash Dividend WHT" symbol="AAPL" amount="-15.00"/>"""
        )

        val result = parser.parse(input)

        assertEquals(1, result.withholdingTax.size)
        assertEquals(BigDecimal("15.00"), result.withholdingTax[0].amount)
    }

    @Test
    fun `should parse broker interest transaction`() {
        val input = xml(
            """<CashTransaction type="BrokerInterest" currency="USD" dateTime="2024-03-31;093000" description="USD Credit Interest" symbol="" amount="5.50"/>"""
        )

        val result = parser.parse(input)

        assertEquals(1, result.interest.size)
        assertEquals(BigDecimal("5.50"), result.interest[0].amount)
        assertEquals("USD", result.interest[0].currency)
    }

    @Test
    fun `should skip dividend with non-positive amount`() {
        val input = xml(
            """<CashTransaction type="Dividends" currency="USD" dateTime="2024-03-15;093000" description="AAPL Cash Dividend" symbol="AAPL" amount="0.00"/>"""
        )

        val result = parser.parse(input)

        assertTrue(result.dividends.isEmpty())
    }

    @Test
    fun `should skip transaction with malformed amount`() {
        val input = xml(
            """<CashTransaction type="Dividends" currency="USD" dateTime="2024-03-15;093000" description="AAPL Cash Dividend" symbol="AAPL" amount="not-a-number"/>"""
        )

        val result = parser.parse(input)

        assertTrue(result.dividends.isEmpty())
    }

    @Test
    fun `should skip transaction with malformed date`() {
        val input = xml(
            """<CashTransaction type="Dividends" currency="USD" dateTime="not-a-date" description="AAPL Cash Dividend" symbol="AAPL" amount="100.00"/>"""
        )

        val result = parser.parse(input)

        assertTrue(result.dividends.isEmpty())
    }

    @Test
    fun `should fall back to extractSymbol when symbol attribute is empty`() {
        val input = xml(
            """<CashTransaction type="Dividends" currency="USD" dateTime="2024-03-15;093000" description="MSFT(US5949181045) Cash Dividend" symbol="" amount="50.00"/>"""
        )

        val result = parser.parse(input)

        assertEquals(1, result.dividends.size)
        assertEquals("MSFT", result.dividends[0].symbol)
    }

    @Test
    fun `should match type containing Withholding as withholding tax`() {
        val input = xml(
            """<CashTransaction type="Payment in Lieu of a Dividend (Withholding)" currency="USD" dateTime="2024-03-15;093000" description="AAPL WHT" symbol="AAPL" amount="-10.00"/>"""
        )

        val result = parser.parse(input)

        assertEquals(1, result.withholdingTax.size)
        assertEquals(BigDecimal("10.00"), result.withholdingTax[0].amount)
    }

    @Test
    fun `should match type containing Interest as interest`() {
        val input = xml(
            """<CashTransaction type="Credit Interest" currency="CHF" dateTime="2024-03-31;093000" description="CHF Interest" symbol="" amount="2.00"/>"""
        )

        val result = parser.parse(input)

        assertEquals(1, result.interest.size)
        assertEquals("CHF", result.interest[0].currency)
    }

    private fun xmlWithTrades(vararg tradeElements: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <FlexQueryResponse>
            <FlexStatements>
                <FlexStatement>
                    <CashTransactions/>
                    <Trades>
                        ${tradeElements.joinToString("\n")}
                    </Trades>
                    <OpenPositions/>
                </FlexStatement>
            </FlexStatements>
        </FlexQueryResponse>
    """.trimIndent()

    private fun xmlWithPositions(vararg positionElements: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <FlexQueryResponse>
            <FlexStatements>
                <FlexStatement>
                    <CashTransactions/>
                    <Trades/>
                    <OpenPositions>
                        ${positionElements.joinToString("\n")}
                    </OpenPositions>
                </FlexStatement>
            </FlexStatements>
        </FlexQueryResponse>
    """.trimIndent()

    @Test
    fun `should parse BUY trade correctly`() {
        val input = xmlWithTrades(
            """<Trade assetCategory="STK" symbol="AAPL" isin="US0378331005" description="APPLE INC" dateTime="2024-06-01;120000" buySell="BUY" quantity="10" tradePrice="150.00" proceeds="-1500.00" currency="USD" fxRateToBase="0.895" levelOfDetail="EXECUTION"/>"""
        )

        val result = parser.parse(input)

        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals("AAPL", trade.symbol)
        assertEquals("US0378331005", trade.isin)
        assertEquals(BuySell.BUY, trade.buySell)
        assertEquals(BigDecimal("10"), trade.quantity)
        assertEquals(BigDecimal("150.00"), trade.tradePrice)
        assertEquals(LocalDate.of(2024, 6, 1), trade.date)
    }

    @Test
    fun `should parse SELL trade and set negative quantity as SELL with positive stored quantity`() {
        val input = xmlWithTrades(
            """<Trade assetCategory="STK" symbol="AAPL" isin="" description="APPLE INC" dateTime="2024-09-01;120000" buySell="SELL" quantity="-5" tradePrice="175.00" proceeds="875.00" currency="USD" fxRateToBase="0.900" levelOfDetail="EXECUTION"/>"""
        )

        val result = parser.parse(input)

        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals(BuySell.SELL, trade.buySell)
        assertEquals(BigDecimal("5"), trade.quantity)
    }

    @Test
    fun `should skip trade with levelOfDetail ORDER_AGGREGATE`() {
        val input = xmlWithTrades(
            """<Trade assetCategory="STK" symbol="AAPL" isin="" description="APPLE INC" dateTime="2024-06-01;120000" buySell="BUY" quantity="10" tradePrice="150.00" proceeds="-1500.00" currency="USD" fxRateToBase="0.895" levelOfDetail="ORDER_AGGREGATE"/>"""
        )

        val result = parser.parse(input)

        assertTrue(result.trades.isEmpty())
    }

    @Test
    fun `should skip trade with non-STK assetCategory`() {
        val input = xmlWithTrades(
            """<Trade assetCategory="OPT" symbol="AAPL" isin="" description="OPTION" dateTime="2024-06-01;120000" buySell="BUY" quantity="1" tradePrice="5.00" proceeds="-500.00" currency="USD" fxRateToBase="0.895" levelOfDetail="EXECUTION"/>"""
        )

        val result = parser.parse(input)

        assertTrue(result.trades.isEmpty())
    }

    @Test
    fun `should parse open position correctly`() {
        val input = xmlWithPositions(
            """<OpenPosition assetCategory="STK" symbol="AAPL" isin="US0378331005" description="APPLE INC" reportDate="2024-12-31" position="10" markPrice="182.00" positionValue="1820.00" currency="USD" fxRateToBase="0.882" side="Long"/>"""
        )

        val result = parser.parse(input)

        assertEquals(1, result.openPositions.size)
        val pos = result.openPositions[0]
        assertEquals("AAPL", pos.symbol)
        assertEquals("US0378331005", pos.isin)
        assertEquals(BigDecimal("10"), pos.quantity)
        assertEquals(BigDecimal("182.00"), pos.markPrice)
        assertEquals(BigDecimal("1820.00"), pos.positionValue)
        assertEquals(LocalDate.of(2024, 12, 31), pos.reportDate)
    }

    @Test
    fun `should skip short position`() {
        val input = xmlWithPositions(
            """<OpenPosition assetCategory="STK" symbol="AAPL" isin="" description="APPLE INC" reportDate="2024-12-31" position="-5" markPrice="182.00" positionValue="-910.00" currency="USD" fxRateToBase="0.882" side="Short"/>"""
        )

        val result = parser.parse(input)

        assertTrue(result.openPositions.isEmpty())
    }
}
