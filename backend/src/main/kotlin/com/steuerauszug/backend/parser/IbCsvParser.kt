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
    }

    private val log = LoggerFactory.getLogger(IbCsvParser::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun parse(content: String): IbActivityData {
        val dividends = mutableListOf<IbDividend>()
        val withholdingTax = mutableListOf<IbWithholdingTax>()
        val interest = mutableListOf<IbInterest>()

        for (line in content.lines()) {
            val cols = parseCsvLine(line)
            if (cols.size < 2) continue
            if (cols[1].trim() != ROW_TYPE_DATA) continue

            when (cols[0].trim()) {
                IbParser.TYPE_DIVIDENDS -> parseDividendRow(cols)?.let { dividends.add(it) }
                IbParser.TYPE_WITHHOLDING_TAX -> parseWithholdingTaxRow(cols)?.let { withholdingTax.add(it) }
                IbParser.TYPE_INTEREST -> parseInterestRow(cols)?.let { interest.add(it) }
            }
        }

        return IbActivityData(dividends, withholdingTax, interest)
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
