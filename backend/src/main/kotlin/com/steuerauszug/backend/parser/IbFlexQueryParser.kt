package com.steuerauszug.backend.parser

import com.steuerauszug.backend.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.w3c.dom.Element
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

@Component
class IbFlexQueryParser : IbParser {

    private val log = LoggerFactory.getLogger(IbFlexQueryParser::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun parse(content: String): IbActivityData {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(content.byteInputStream())

        val dividends = mutableListOf<IbDividend>()
        val withholdingTax = mutableListOf<IbWithholdingTax>()
        val interest = mutableListOf<IbInterest>()

        val transactions = doc.getElementsByTagName("CashTransaction")
        for (i in 0 until transactions.length) {
            val el = transactions.item(i) as? Element ?: continue
            val type = el.getAttribute("type")
            val currency = el.getAttribute("currency")
            val rawDate = el.getAttribute("dateTime").take(10)
            val description = el.getAttribute("description")
            val symbol = el.getAttribute("symbol").ifEmpty { IbParser.extractSymbol(description) }
            val amount = el.getAttribute("amount").replace(",", "").toBigDecimalOrNull() ?: continue
            val date = try {
                LocalDate.parse(rawDate, dateFormatter)
            } catch (e: Exception) {
                log.warn("Failed to parse date '{}': {}", rawDate, e.message)
                continue
            }

            when {
                type == "Dividends" -> {
                    if (amount > BigDecimal.ZERO) {
                        dividends.add(IbDividend(date, symbol, description, currency, amount))
                    }
                }
                type == "Withholding Tax" || type.contains("Withholding") -> {
                    withholdingTax.add(IbWithholdingTax(date, symbol, currency, amount.abs()))
                }
                type == "BrokerInterest" || type.contains("Interest") -> {
                    if (amount > BigDecimal.ZERO) {
                        interest.add(IbInterest(date, description, currency, amount))
                    }
                }
            }
        }

        return IbActivityData(dividends, withholdingTax, interest)
    }
}
