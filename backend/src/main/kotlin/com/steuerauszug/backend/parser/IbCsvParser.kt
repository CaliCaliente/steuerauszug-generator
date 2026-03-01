package com.steuerauszug.backend.parser

import com.steuerauszug.backend.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class IbCsvParser : IbParser {

    companion object {
        private const val ROW_TYPE_DATA = "Data"
        private const val ROW_TYPE_HEADER = "Header"
        private const val SECTION_TRADES = "Trades"
        private const val SECTION_OPEN_POSITIONS = "Open Positions"
        private const val TRADES_DATA_TYPE = "Order"
        private const val TRADES_ASSET_TYPE = "Stocks"
        private const val POSITIONS_DATA_TYPE = "Summary"
        private const val POSITIONS_ASSET_TYPE = "Stocks"
    }

    private val log = LoggerFactory.getLogger(IbCsvParser::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun parse(content: String): IbActivityData {
        val dividends = mutableListOf<IbDividend>()
        val withholdingTax = mutableListOf<IbWithholdingTax>()
        val interest = mutableListOf<IbInterest>()
        val trades = mutableListOf<IbTrade>()
        val openPositions = mutableListOf<IbOpenPosition>()

        var tradesHeaderCols: List<String> = emptyList()
        var positionsHeaderCols: List<String> = emptyList()

        for (line in content.lines()) {
            val cols = parseCsvLine(line)
            if (cols.size < 2) continue
            val section = cols[0].trim()
            val rowType = cols[1].trim()

            when {
                section == SECTION_TRADES && rowType == ROW_TYPE_HEADER ->
                    tradesHeaderCols = cols.map { it.trim() }

                section == SECTION_TRADES && rowType == ROW_TYPE_DATA ->
                    parseTradeRow(cols, tradesHeaderCols)?.let { trades.add(it) }

                section == SECTION_OPEN_POSITIONS && rowType == ROW_TYPE_HEADER ->
                    positionsHeaderCols = cols.map { it.trim() }

                section == SECTION_OPEN_POSITIONS && rowType == ROW_TYPE_DATA ->
                    parseOpenPositionRow(cols, positionsHeaderCols)?.let { openPositions.add(it) }

                section == IbParser.TYPE_DIVIDENDS && rowType == ROW_TYPE_DATA ->
                    parseDividendRow(cols)?.let { dividends.add(it) }

                section == IbParser.TYPE_WITHHOLDING_TAX && rowType == ROW_TYPE_DATA ->
                    parseWithholdingTaxRow(cols)?.let { withholdingTax.add(it) }

                section == IbParser.TYPE_INTEREST && rowType == ROW_TYPE_DATA ->
                    parseInterestRow(cols)?.let { interest.add(it) }
            }
        }

        return IbActivityData(dividends, withholdingTax, interest, trades, openPositions)
    }

    private fun parseTradeRow(cols: List<String>, headers: List<String>): IbTrade? {
        if (headers.isEmpty()) return null
        // Data rows: Trades,Data,Order,Stocks,...
        val dataType = cols.getOrNull(2)?.trim() ?: return null
        val assetType = cols.getOrNull(3)?.trim() ?: return null
        if (dataType != TRADES_DATA_TYPE || assetType != TRADES_ASSET_TYPE) return null

        val currencyIdx = headers.indexOf("Currency")
        val symbolIdx = headers.indexOf("Symbol")
        val dateIdx = headers.indexOf("Date/Time")
        val quantityIdx = headers.indexOf("Quantity")
        val priceIdx = headers.indexOf("T. Price")
        val proceedsIdx = headers.indexOf("Proceeds")
        if (listOf(currencyIdx, symbolIdx, dateIdx, quantityIdx, priceIdx, proceedsIdx).any { it < 0 }) return null

        val currency = cols.getOrNull(currencyIdx)?.trim() ?: return null
        val symbol = cols.getOrNull(symbolIdx)?.trim()?.ifEmpty { null } ?: return null
        val rawDate = cols.getOrNull(dateIdx)?.trim()?.take(10) ?: return null
        val date = parseDate(rawDate) ?: return null
        val rawQuantity = parseBigDecimal(cols.getOrNull(quantityIdx)?.trim() ?: return null) ?: return null
        val tradePrice = parseBigDecimal(cols.getOrNull(priceIdx)?.trim() ?: return null) ?: return null
        val proceeds = parseBigDecimal(cols.getOrNull(proceedsIdx)?.trim() ?: return null) ?: return null

        val buySell = if (rawQuantity < BigDecimal.ZERO) BuySell.SELL else BuySell.BUY

        return IbTrade(
            date = date,
            symbol = symbol,
            isin = null,
            description = symbol,
            currency = currency,
            buySell = buySell,
            quantity = rawQuantity.abs(),
            tradePrice = tradePrice,
            proceeds = proceeds,
            fxRateToBase = BigDecimal.ONE
        )
    }

    private fun parseOpenPositionRow(cols: List<String>, headers: List<String>): IbOpenPosition? {
        if (headers.isEmpty()) return null
        // Data rows: Open Positions,Data,Summary,Stocks,...
        val dataType = cols.getOrNull(2)?.trim() ?: return null
        val assetType = cols.getOrNull(3)?.trim() ?: return null
        if (dataType != POSITIONS_DATA_TYPE || assetType != POSITIONS_ASSET_TYPE) return null

        val currencyIdx = headers.indexOf("Currency")
        val symbolIdx = headers.indexOf("Symbol")
        val quantityIdx = headers.indexOf("Quantity")
        val closePriceIdx = headers.indexOf("Close Price")
        val valueIdx = headers.indexOf("Value")
        if (listOf(currencyIdx, symbolIdx, quantityIdx, closePriceIdx, valueIdx).any { it < 0 }) return null

        val currency = cols.getOrNull(currencyIdx)?.trim() ?: return null
        val symbol = cols.getOrNull(symbolIdx)?.trim()?.ifEmpty { null } ?: return null
        val quantity = parseBigDecimal(cols.getOrNull(quantityIdx)?.trim() ?: return null) ?: return null
        val markPrice = parseBigDecimal(cols.getOrNull(closePriceIdx)?.trim() ?: return null) ?: return null
        val positionValue = parseBigDecimal(cols.getOrNull(valueIdx)?.trim() ?: return null) ?: return null

        return IbOpenPosition(
            reportDate = LocalDate.now(),
            symbol = symbol,
            isin = null,
            description = symbol,
            currency = currency,
            quantity = quantity,
            markPrice = markPrice,
            positionValue = positionValue,
            fxRateToBase = BigDecimal.ONE
        )
    }

    private fun parseDividendRow(cols: List<String>): IbDividend? {
        if (cols.size < 6) return null
        val currency = cols[2].trim()
        val date = parseDate(cols[3].trim()) ?: return null
        val description = cols[4].trim()
        val amount = parseBigDecimal(cols[5].trim()) ?: return null
        if (amount <= BigDecimal.ZERO) return null
        return IbDividend(date, IbParser.extractSymbol(description), description, currency, amount)
    }

    private fun parseWithholdingTaxRow(cols: List<String>): IbWithholdingTax? {
        if (cols.size < 6) return null
        val currency = cols[2].trim()
        val date = parseDate(cols[3].trim()) ?: return null
        val description = cols[4].trim()
        val amount = parseBigDecimal(cols[5].trim()) ?: return null
        return IbWithholdingTax(date, IbParser.extractSymbol(description), currency, amount.abs())
    }

    private fun parseInterestRow(cols: List<String>): IbInterest? {
        if (cols.size < 6) return null
        val currency = cols[2].trim()
        val date = parseDate(cols[3].trim()) ?: return null
        val description = cols[4].trim()
        val amount = parseBigDecimal(cols[5].trim()) ?: return null
        if (amount <= BigDecimal.ZERO) return null
        return IbInterest(date, description, currency, amount)
    }

    private fun parseDate(s: String): LocalDate? =
        try { LocalDate.parse(s, dateFormatter) }
        catch (e: Exception) {
            log.warn("Failed to parse date '{}': {}", s, e.message)
            null
        }

    private fun parseBigDecimal(s: String): BigDecimal? =
        try { BigDecimal(s.replace(",", "")) }
        catch (e: Exception) {
            log.warn("Failed to parse amount '{}': {}", s, e.message)
            null
        }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { result.add(sb.toString()); sb.clear() }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString())
        return result
    }
}
