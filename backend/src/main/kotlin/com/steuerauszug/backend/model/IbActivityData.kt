package com.steuerauszug.backend.model

import java.math.BigDecimal
import java.time.LocalDate

data class IbActivityData(
    val dividends: List<IbDividend>,
    val withholdingTax: List<IbWithholdingTax>,
    val interest: List<IbInterest>
)

data class IbDividend(
    val date: LocalDate,
    val symbol: String,
    val description: String,
    val currency: String,
    val amount: BigDecimal
)

data class IbWithholdingTax(
    val date: LocalDate,
    val symbol: String,
    val currency: String,
    val amount: BigDecimal
)

data class IbInterest(
    val date: LocalDate,
    val description: String,
    val currency: String,
    val amount: BigDecimal
)
