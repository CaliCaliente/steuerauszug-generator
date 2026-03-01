package com.steuerauszug.backend.mapper

import com.steuerauszug.backend.model.*
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
class IbToEchMapper {

    companion object {
        private const val DOCUMENT_ID_PREFIX = "CH"
        private const val DOCUMENT_ID_SEQUENCE = "001"
        private const val INTEREST_SOURCE_COUNTRY = "CH"
        private const val UNKNOWN_COUNTRY_CODE = "XX"
        private const val DIVIDEND_DESCRIPTION_SUFFIX = "– Dividende"
        private const val INTEREST_DESCRIPTION_PREFIX = "Zinsen – "
    }

    fun map(ibData: IbActivityData, request: GenerationRequest): EchTaxStatement {
        val wtBySymbol = ibData.withholdingTax.groupBy { it.symbol }
        val items = buildDividendItems(ibData, wtBySymbol) + buildInterestItems(ibData)
        val documentId = "$DOCUMENT_ID_PREFIX-${request.clearingNumber}-${request.customerNumber}-${request.taxYear}-$DOCUMENT_ID_SEQUENCE"

        return EchTaxStatement(
            documentId = documentId,
            taxPeriod = request.taxYear,
            periodFrom = LocalDate.of(request.taxYear, 1, 1),
            periodTo = LocalDate.of(request.taxYear, 12, 31),
            institution = buildInstitution(request),
            customer = buildCustomer(request),
            canton = request.canton,
            items = items,
            totalGross = items.sumOf { it.grossAmount },
            totalWithholding = items.sumOf { it.withholdingTax },
            totalNet = items.sumOf { it.netAmount }
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

    private fun buildDividendItems(ibData: IbActivityData, wtBySymbol: Map<String, List<IbWithholdingTax>>): List<TaxItem> =
        ibData.dividends.groupBy { it.symbol }.map { (symbol, divs) ->
            val grossAmount = divs.sumOf { it.amount }
            val wt = wtBySymbol[symbol]?.sumOf { it.amount } ?: BigDecimal.ZERO
            TaxItem(
                type = TaxItemType.DIVIDEND,
                description = "$symbol $DIVIDEND_DESCRIPTION_SUFFIX",
                currency = divs.first().currency,
                grossAmount = grossAmount,
                withholdingTax = wt,
                netAmount = grossAmount - wt,
                sourceCountry = extractCountryFromDescription(divs.first().description)
            )
        }

    private fun buildInterestItems(ibData: IbActivityData): List<TaxItem> =
        ibData.interest.groupBy { it.currency }.map { (currency, interests) ->
            val grossAmount = interests.sumOf { it.amount }
            TaxItem(
                type = TaxItemType.INTEREST,
                description = "$INTEREST_DESCRIPTION_PREFIX$currency",
                currency = currency,
                grossAmount = grossAmount,
                withholdingTax = BigDecimal.ZERO,
                netAmount = grossAmount,
                sourceCountry = INTEREST_SOURCE_COUNTRY
            )
        }

    private fun extractCountryFromDescription(description: String): String {
        // ISIN starts with 2-letter country code, e.g. "AAPL(US0378331005)"
        val isinRegex = Regex("""\(([A-Z]{2}[A-Z0-9]{10})\)""")
        val match = isinRegex.find(description)
        return match?.groupValues?.get(1)?.take(2) ?: UNKNOWN_COUNTRY_CODE
    }
}
