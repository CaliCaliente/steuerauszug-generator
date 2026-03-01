package com.steuerauszug.backend.parser

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
}
