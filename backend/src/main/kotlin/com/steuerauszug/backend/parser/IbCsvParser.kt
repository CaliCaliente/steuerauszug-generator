package com.steuerauszug.backend.parser

import com.steuerauszug.backend.model.*
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class IbCsvParser : IbParser {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun parse(content: String): IbActivityData {
        val dividends = mutableListOf<IbDividend>()
        val withholdingTax = mutableListOf<IbWithholdingTax>()
        val interest = mutableListOf<IbInterest>()

        for (line in content.lines()) {
            val cols = parseCsvLine(line)
            if (cols.size < 2) continue

            val section = cols[0].trim()
            val rowType = cols[1].trim()
            if (rowType != "Data") continue

            when (section) {
                "Dividends" -> {
                    if (cols.size >= 6) {
                        val currency = cols[2].trim()
                        val date = parseDate(cols[3].trim()) ?: continue
                        val description = cols[4].trim()
                        val amount = parseBigDecimal(cols[5].trim()) ?: continue
                        if (amount > BigDecimal.ZERO) {
                            dividends.add(IbDividend(date, extractSymbol(description), description, currency, amount))
                        }
                    }
                }
                "Withholding Tax" -> {
                    if (cols.size >= 6) {
                        val currency = cols[2].trim()
                        val date = parseDate(cols[3].trim()) ?: continue
                        val description = cols[4].trim()
                        val amount = parseBigDecimal(cols[5].trim()) ?: continue
                        withholdingTax.add(IbWithholdingTax(date, extractSymbol(description), currency, amount.abs()))
                    }
                }
                "Interest" -> {
                    if (cols.size >= 6) {
                        val currency = cols[2].trim()
                        val date = parseDate(cols[3].trim()) ?: continue
                        val description = cols[4].trim()
                        val amount = parseBigDecimal(cols[5].trim()) ?: continue
                        if (amount > BigDecimal.ZERO) {
                            interest.add(IbInterest(date, description, currency, amount))
                        }
                    }
                }
            }
        }

        return IbActivityData(dividends, withholdingTax, interest)
    }

    private fun extractSymbol(description: String): String {
        val idx = description.indexOf('(')
        return if (idx > 0) description.substring(0, idx).trim()
        else description.split(" ").firstOrNull() ?: description
    }

    private fun parseDate(s: String): LocalDate? =
        try { LocalDate.parse(s, dateFormatter) } catch (e: Exception) { null }

    private fun parseBigDecimal(s: String): BigDecimal? =
        try { BigDecimal(s.replace(",", "")) } catch (e: Exception) { null }

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
