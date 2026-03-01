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

    companion object {
        private const val ELEMENT_CASH_TRANSACTION = "CashTransaction"
        private const val ATTR_TYPE = "type"
        private const val ATTR_CURRENCY = "currency"
        private const val ATTR_DATE_TIME = "dateTime"
        private const val ATTR_DESCRIPTION = "description"
        private const val ATTR_SYMBOL = "symbol"
        private const val ATTR_AMOUNT = "amount"
        private const val TYPE_BROKER_INTEREST = "BrokerInterest"
    }

    private val log = LoggerFactory.getLogger(IbFlexQueryParser::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun parse(content: String): IbActivityData {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(content.byteInputStream())

        val dividends = mutableListOf<IbDividend>()
        val withholdingTax = mutableListOf<IbWithholdingTax>()
        val interest = mutableListOf<IbInterest>()

        val transactions = doc.getElementsByTagName(ELEMENT_CASH_TRANSACTION)
        for (i in 0 until transactions.length) {
            val el = transactions.item(i) as? Element ?: continue
            parseSingleTransaction(el, dividends, withholdingTax, interest)
        }

        return IbActivityData(dividends, withholdingTax, interest)
    }

    private fun parseSingleTransaction(
        el: Element,
        dividends: MutableList<IbDividend>,
        withholdingTax: MutableList<IbWithholdingTax>,
        interest: MutableList<IbInterest>
    ) {
        val type = el.getAttribute(ATTR_TYPE)
        val currency = el.getAttribute(ATTR_CURRENCY)
        val rawDate = el.getAttribute(ATTR_DATE_TIME).take(10)
        val description = el.getAttribute(ATTR_DESCRIPTION)
        val symbol = el.getAttribute(ATTR_SYMBOL).ifEmpty { IbParser.extractSymbol(description) }
        val amount = el.getAttribute(ATTR_AMOUNT).replace(",", "").toBigDecimalOrNull() ?: return
        val date = try {
            LocalDate.parse(rawDate, dateFormatter)
        } catch (e: Exception) {
            log.warn("Failed to parse date '{}': {}", rawDate, e.message)
            return
        }

        when {
            type == IbParser.TYPE_DIVIDENDS -> {
                if (amount > BigDecimal.ZERO) dividends.add(IbDividend(date, symbol, description, currency, amount))
            }
            type == IbParser.TYPE_WITHHOLDING_TAX || type.contains("Withholding") -> {
                withholdingTax.add(IbWithholdingTax(date, symbol, currency, amount.abs()))
            }
            type == TYPE_BROKER_INTEREST || type.contains("Interest") -> {
                if (amount > BigDecimal.ZERO) interest.add(IbInterest(date, description, currency, amount))
            }
        }
    }
}
