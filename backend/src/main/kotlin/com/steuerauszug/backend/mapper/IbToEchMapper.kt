package com.steuerauszug.backend.mapper

import com.steuerauszug.backend.model.*
import com.steuerauszug.backend.service.EstvExchangeRateService
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Component
class IbToEchMapper(private val exchangeRateService: EstvExchangeRateService) {

    companion object {
        private const val DOCUMENT_ID_PREFIX = "CH"
        private const val DOCUMENT_ID_SEQUENCE = "001"
        private const val INTEREST_SOURCE_COUNTRY = "CH"
        private const val UNKNOWN_COUNTRY_CODE = "XX"
        private const val SECURITY_CATEGORY_SHARE = "SHARE"
        private const val SECURITY_CATEGORY_OTHER = "OTHER"
        private const val STOCK_NAME_BUY = "Kauf"
        private const val STOCK_NAME_SELL = "Verkauf"
        private const val STOCK_NAME_YEAR_END = "Jahresendbestand"
    }

    fun map(ibData: IbActivityData, request: GenerationRequest): EchTaxStatement {
        val year = request.taxYear

        val dividendCurrencies = ibData.dividends.map { it.currency }.toSet()
        val positionCurrencies = ibData.openPositions.map { it.currency }.toSet()

        val annualRates = dividendCurrencies.associateWith { exchangeRateService.getAnnualAverageRate(it, year) }
        val yearEndRates = positionCurrencies.associateWith { exchangeRateService.getYearEndRate(it, year) }

        val wtBySymbol = ibData.withholdingTax.groupBy { it.symbol }
        val dividendsBySymbol = ibData.dividends.groupBy { it.symbol }
        val tradesBySymbol = ibData.trades.groupBy { it.symbol }
        val positionsBySymbol = ibData.openPositions.groupBy { it.symbol }

        val allSymbols = (dividendsBySymbol.keys + tradesBySymbol.keys + positionsBySymbol.keys).toSet()
        val interestSecurities = buildInterestSecurities(ibData, annualRates)

        val stockSecurities = allSymbols.map { symbol ->
            buildSecurity(
                symbol = symbol,
                dividends = dividendsBySymbol[symbol] ?: emptyList(),
                trades = tradesBySymbol[symbol] ?: emptyList(),
                positions = positionsBySymbol[symbol] ?: emptyList(),
                wtBySymbol = wtBySymbol,
                annualRates = annualRates,
                yearEndRates = yearEndRates,
                taxYear = year
            )
        }

        val securities = stockSecurities + interestSecurities

        val totalGross = securities.flatMap { it.payments }.sumOf { it.grossAmountCHF ?: it.grossAmount }
        val totalWithholding = securities.flatMap { it.payments }.sumOf { it.withholdingTax }
        val totalNet = totalGross - totalWithholding

        val documentId = "$DOCUMENT_ID_PREFIX-${request.clearingNumber}-${request.customerNumber}-$year-$DOCUMENT_ID_SEQUENCE"

        return EchTaxStatement(
            documentId = documentId,
            taxPeriod = year,
            periodFrom = LocalDate.of(year, 1, 1),
            periodTo = LocalDate.of(year, 12, 31),
            institution = buildInstitution(request),
            customer = buildCustomer(request),
            canton = request.canton,
            securities = securities,
            totalGross = totalGross,
            totalWithholding = totalWithholding,
            totalNet = totalNet
        )
    }

    private fun buildSecurity(
        symbol: String,
        dividends: List<IbDividend>,
        trades: List<IbTrade>,
        positions: List<IbOpenPosition>,
        wtBySymbol: Map<String, List<IbWithholdingTax>>,
        annualRates: Map<String, BigDecimal>,
        yearEndRates: Map<String, BigDecimal>,
        taxYear: Int
    ): EchSecurity {
        val firstDiv = dividends.firstOrNull()
        val firstPos = positions.firstOrNull()
        val firstTrade = trades.firstOrNull()
        val currency = firstDiv?.currency ?: firstPos?.currency ?: firstTrade?.currency ?: "USD"
        val description = firstDiv?.description ?: firstPos?.description ?: firstTrade?.description ?: symbol
        val isin = firstDiv?.let { extractIsinFromDescription(it.description) }
            ?: firstPos?.isin ?: firstTrade?.isin
        val sourceCountry = firstDiv?.let { extractCountryFromDescription(it.description) } ?: UNKNOWN_COUNTRY_CODE
        val securityCategory = if (dividends.isNotEmpty()) SECURITY_CATEGORY_SHARE else SECURITY_CATEGORY_OTHER

        val payments = buildPayments(symbol, dividends, wtBySymbol, annualRates)
        val yearEndTaxValue = buildYearEndTaxValue(positions, yearEndRates, taxYear)
        val stocks = buildStocks(trades, positions, yearEndRates, taxYear)

        return EchSecurity(
            symbol = symbol,
            isin = isin,
            description = description.take(60),
            currency = currency,
            sourceCountry = sourceCountry,
            securityCategory = securityCategory,
            payments = payments,
            yearEndTaxValue = yearEndTaxValue,
            stocks = stocks
        )
    }

    private fun buildPayments(
        symbol: String,
        dividends: List<IbDividend>,
        wtBySymbol: Map<String, List<IbWithholdingTax>>,
        annualRates: Map<String, BigDecimal>
    ): List<EchPayment> {
        val totalGross = dividends.sumOf { it.amount }
        val totalWt = wtBySymbol[symbol]?.sumOf { it.amount } ?: BigDecimal.ZERO

        return dividends.map { div ->
            val rate = annualRates[div.currency]
            val proportionalWt = if (totalGross > BigDecimal.ZERO && totalWt > BigDecimal.ZERO)
                totalWt.multiply(div.amount).divide(totalGross, 10, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP)
            else BigDecimal.ZERO

            EchPayment(
                date = div.date,
                quantity = BigDecimal.ONE,
                grossAmount = div.amount,
                withholdingTax = proportionalWt,
                exchangeRate = rate,
                grossAmountCHF = rate?.let { div.amount.multiply(it).setScale(2, RoundingMode.HALF_UP) }
            )
        }
    }

    private fun buildYearEndTaxValue(
        positions: List<IbOpenPosition>,
        yearEndRates: Map<String, BigDecimal>,
        taxYear: Int
    ): EchTaxValue? {
        val pos = positions.firstOrNull() ?: return null
        val rate = yearEndRates[pos.currency]
        val balance = pos.positionValue
        return EchTaxValue(
            referenceDate = LocalDate.of(taxYear, 12, 31),
            quantity = pos.quantity,
            unitPrice = pos.markPrice,
            balance = balance,
            exchangeRate = rate,
            valueCHF = rate?.let { balance.multiply(it).setScale(2, RoundingMode.HALF_UP) }
        )
    }

    private fun buildStocks(
        trades: List<IbTrade>,
        positions: List<IbOpenPosition>,
        yearEndRates: Map<String, BigDecimal>,
        taxYear: Int
    ): List<EchStock> {
        val tradeStocks = trades.map { trade ->
            val name = if (trade.buySell == BuySell.BUY) STOCK_NAME_BUY else STOCK_NAME_SELL
            val signedQty = if (trade.buySell == BuySell.SELL) trade.quantity.negate() else trade.quantity
            val balance = signedQty.multiply(trade.tradePrice)
            val rate = trade.fxRateToBase

            EchStock(
                date = trade.date,
                mutation = true,
                name = name,
                quantity = signedQty,
                unitPrice = trade.tradePrice,
                balance = balance,
                exchangeRate = rate.takeIf { it != BigDecimal.ONE },
                valueCHF = balance.multiply(rate).setScale(2, RoundingMode.HALF_UP)
            )
        }

        val yearEndStocks = positions.map { pos ->
            val rate = yearEndRates[pos.currency]
            val balance = pos.positionValue
            EchStock(
                date = LocalDate.of(taxYear, 12, 31),
                mutation = false,
                name = STOCK_NAME_YEAR_END,
                quantity = pos.quantity,
                unitPrice = pos.markPrice,
                balance = balance,
                exchangeRate = rate,
                valueCHF = rate?.let { balance.multiply(it).setScale(2, RoundingMode.HALF_UP) }
            )
        }

        return tradeStocks + yearEndStocks
    }

    private fun buildInterestSecurities(
        ibData: IbActivityData,
        annualRates: Map<String, BigDecimal>
    ): List<EchSecurity> =
        ibData.interest.groupBy { it.currency }.map { (currency, interests) ->
            val payments = interests.map { interest ->
                EchPayment(
                    date = interest.date,
                    quantity = BigDecimal.ONE,
                    grossAmount = interest.amount,
                    withholdingTax = BigDecimal.ZERO,
                    exchangeRate = annualRates[currency],
                    grossAmountCHF = annualRates[currency]?.let {
                        interest.amount.multiply(it).setScale(2, RoundingMode.HALF_UP)
                    }
                )
            }
            EchSecurity(
                symbol = "INTEREST-$currency",
                isin = null,
                description = "Zinsen $currency",
                currency = currency,
                sourceCountry = INTEREST_SOURCE_COUNTRY,
                securityCategory = SECURITY_CATEGORY_OTHER,
                payments = payments,
                yearEndTaxValue = null,
                stocks = emptyList()
            )
        }

    private fun buildInstitution(request: GenerationRequest) = Institution(
        clearingNumber = request.clearingNumber,
        name = request.institutionName,
        address = request.institutionAddress
    )

    private fun buildCustomer(request: GenerationRequest) = Customer(
        customerNumber = request.customerNumber,
        name = request.customerName,
        address = request.customerAddress
    )

    private fun extractCountryFromDescription(description: String): String {
        val isinRegex = Regex("""\(([A-Z]{2}[A-Z0-9]{10})\)""")
        val match = isinRegex.find(description)
        return match?.groupValues?.get(1)?.take(2) ?: UNKNOWN_COUNTRY_CODE
    }

    private fun extractIsinFromDescription(description: String): String? {
        val isinRegex = Regex("""\(([A-Z]{2}[A-Z0-9]{10})\)""")
        return isinRegex.find(description)?.groupValues?.get(1)
    }
}
