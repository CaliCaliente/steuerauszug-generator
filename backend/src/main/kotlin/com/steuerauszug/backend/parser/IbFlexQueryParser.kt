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
        private const val ELEMENT_TRADE = "Trade"
        private const val ELEMENT_OPEN_POSITION = "OpenPosition"
        private const val ATTR_TYPE = "type"
        private const val ATTR_CURRENCY = "currency"
        private const val ATTR_DATE_TIME = "dateTime"
        private const val ATTR_DESCRIPTION = "description"
        private const val ATTR_SYMBOL = "symbol"
        private const val ATTR_AMOUNT = "amount"
        private const val ATTR_ISIN = "isin"
        private const val ATTR_BUY_SELL = "buySell"
        private const val ATTR_QUANTITY = "quantity"
        private const val ATTR_TRADE_PRICE = "tradePrice"
        private const val ATTR_PROCEEDS = "proceeds"
        private const val ATTR_FX_RATE_TO_BASE = "fxRateToBase"
        private const val ATTR_ASSET_CATEGORY = "assetCategory"
        private const val ATTR_LEVEL_OF_DETAIL = "levelOfDetail"
        private const val ATTR_REPORT_DATE = "reportDate"
        private const val ATTR_POSITION = "position"
        private const val ATTR_MARK_PRICE = "markPrice"
        private const val ATTR_POSITION_VALUE = "positionValue"
        private const val ATTR_SIDE = "side"
        private const val ASSET_CATEGORY_STK = "STK"
        private const val SIDE_LONG = "Long"
        private const val TYPE_BROKER_INTEREST = "BrokerInterest"
        private val SKIP_LEVELS = setOf("ORDER_AGGREGATE", "SUMMARY")
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
        val trades = mutableListOf<IbTrade>()
        val openPositions = mutableListOf<IbOpenPosition>()

        val transactions = doc.getElementsByTagName(ELEMENT_CASH_TRANSACTION)
        for (i in 0 until transactions.length) {
            val el = transactions.item(i) as? Element ?: continue
            parseSingleTransaction(el, dividends, withholdingTax, interest)
        }

        val tradeElements = doc.getElementsByTagName(ELEMENT_TRADE)
        for (i in 0 until tradeElements.length) {
            val el = tradeElements.item(i) as? Element ?: continue
            parseTrade(el)?.let { trades.add(it) }
        }

        val positionElements = doc.getElementsByTagName(ELEMENT_OPEN_POSITION)
        for (i in 0 until positionElements.length) {
            val el = positionElements.item(i) as? Element ?: continue
            parseOpenPosition(el)?.let { openPositions.add(it) }
        }

        return IbActivityData(dividends, withholdingTax, interest, trades, openPositions)
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

    private fun parseTrade(el: Element): IbTrade? {
        if (el.getAttribute(ATTR_ASSET_CATEGORY) != ASSET_CATEGORY_STK) return null
        val levelOfDetail = el.getAttribute(ATTR_LEVEL_OF_DETAIL)
        if (SKIP_LEVELS.contains(levelOfDetail)) return null

        val symbol = el.getAttribute(ATTR_SYMBOL).ifEmpty { return null }
        val rawDate = el.getAttribute(ATTR_DATE_TIME).take(10)
        val date = try {
            LocalDate.parse(rawDate, dateFormatter)
        } catch (e: Exception) {
            log.warn("Failed to parse trade date '{}': {}", rawDate, e.message)
            return null
        }
        val rawQuantity = el.getAttribute(ATTR_QUANTITY).replace(",", "").toBigDecimalOrNull() ?: return null
        val buySell = if (rawQuantity < BigDecimal.ZERO) BuySell.SELL else BuySell.BUY
        val tradePrice = el.getAttribute(ATTR_TRADE_PRICE).replace(",", "").toBigDecimalOrNull() ?: return null
        val proceeds = el.getAttribute(ATTR_PROCEEDS).replace(",", "").toBigDecimalOrNull() ?: return null
        val currency = el.getAttribute(ATTR_CURRENCY)
        val fxRateToBase = el.getAttribute(ATTR_FX_RATE_TO_BASE).toBigDecimalOrNull() ?: BigDecimal.ONE
        val isin = el.getAttribute(ATTR_ISIN).takeIf { it.isNotBlank() }
        val description = el.getAttribute(ATTR_DESCRIPTION)

        return IbTrade(
            date = date,
            symbol = symbol,
            isin = isin,
            description = description,
            currency = currency,
            buySell = buySell,
            quantity = rawQuantity.abs(),
            tradePrice = tradePrice,
            proceeds = proceeds,
            fxRateToBase = fxRateToBase
        )
    }

    private fun parseOpenPosition(el: Element): IbOpenPosition? {
        if (el.getAttribute(ATTR_ASSET_CATEGORY) != ASSET_CATEGORY_STK) return null
        if (el.getAttribute(ATTR_SIDE) != SIDE_LONG) return null

        val symbol = el.getAttribute(ATTR_SYMBOL).ifEmpty { return null }
        val rawDate = el.getAttribute(ATTR_REPORT_DATE).take(10)
        val date = try {
            LocalDate.parse(rawDate, dateFormatter)
        } catch (e: Exception) {
            log.warn("Failed to parse position date '{}': {}", rawDate, e.message)
            return null
        }
        val quantity = el.getAttribute(ATTR_POSITION).replace(",", "").toBigDecimalOrNull() ?: return null
        val markPrice = el.getAttribute(ATTR_MARK_PRICE).replace(",", "").toBigDecimalOrNull() ?: return null
        val positionValue = el.getAttribute(ATTR_POSITION_VALUE).replace(",", "").toBigDecimalOrNull() ?: return null
        val currency = el.getAttribute(ATTR_CURRENCY)
        val fxRateToBase = el.getAttribute(ATTR_FX_RATE_TO_BASE).toBigDecimalOrNull() ?: BigDecimal.ONE
        val isin = el.getAttribute(ATTR_ISIN).takeIf { it.isNotBlank() }
        val description = el.getAttribute(ATTR_DESCRIPTION)

        return IbOpenPosition(
            reportDate = date,
            symbol = symbol,
            isin = isin,
            description = description,
            currency = currency,
            quantity = quantity,
            markPrice = markPrice,
            positionValue = positionValue,
            fxRateToBase = fxRateToBase
        )
    }
}
