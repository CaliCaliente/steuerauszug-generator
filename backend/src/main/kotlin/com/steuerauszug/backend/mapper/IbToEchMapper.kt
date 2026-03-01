package com.steuerauszug.backend.mapper

import com.steuerauszug.backend.model.*
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
class IbToEchMapper {

    fun map(ibData: IbActivityData, request: GenerationRequest): EchTaxStatement {
        val institution = Institution(
            clearingNumber = request.clearingNumber,
            name = request.institutionName,
            address = request.institutionAddress
        )
        val customer = Customer(
            customerNumber = request.customerNumber,
            name = request.customerName,
            address = request.customerAddress
        )

        val items = mutableListOf<TaxItem>()
        val wtBySymbol = ibData.withholdingTax.groupBy { it.symbol }

        // Group dividends by symbol
        for ((symbol, divs) in ibData.dividends.groupBy { it.symbol }) {
            val grossAmount = divs.sumOf { it.amount }
            val wt = wtBySymbol[symbol]?.sumOf { it.amount } ?: BigDecimal.ZERO
            val netAmount = grossAmount - wt
            val firstDiv = divs.first()
            val sourceCountry = extractCountryFromDescription(firstDiv.description)
            items.add(
                TaxItem(
                    type = TaxItemType.DIVIDEND,
                    description = "$symbol – Dividende",
                    currency = firstDiv.currency,
                    grossAmount = grossAmount,
                    withholdingTax = wt,
                    netAmount = netAmount,
                    sourceCountry = sourceCountry
                )
            )
        }

        // Group interest by currency
        for ((currency, interests) in ibData.interest.groupBy { it.currency }) {
            val grossAmount = interests.sumOf { it.amount }
            items.add(
                TaxItem(
                    type = TaxItemType.INTEREST,
                    description = "Zinsen – $currency",
                    currency = currency,
                    grossAmount = grossAmount,
                    withholdingTax = BigDecimal.ZERO,
                    netAmount = grossAmount,
                    sourceCountry = "CH"
                )
            )
        }

        val totalGross = items.sumOf { it.grossAmount }
        val totalWithholding = items.sumOf { it.withholdingTax }
        val totalNet = items.sumOf { it.netAmount }
        val documentId = "CH-${request.clearingNumber}-${request.customerNumber}-${request.taxYear}-001"

        return EchTaxStatement(
            documentId = documentId,
            taxPeriod = request.taxYear,
            periodFrom = LocalDate.of(request.taxYear, 1, 1),
            periodTo = LocalDate.of(request.taxYear, 12, 31),
            institution = institution,
            customer = customer,
            canton = request.canton,
            items = items,
            totalGross = totalGross,
            totalWithholding = totalWithholding,
            totalNet = totalNet
        )
    }

    private fun extractCountryFromDescription(description: String): String {
        // ISIN starts with 2-letter country code, e.g. "AAPL(US0378331005)"
        val isinRegex = Regex("""\(([A-Z]{2}[A-Z0-9]{10})\)""")
        val match = isinRegex.find(description)
        return match?.groupValues?.get(1)?.take(2) ?: "XX"
    }
}
