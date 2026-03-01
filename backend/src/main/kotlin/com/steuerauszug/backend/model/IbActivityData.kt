package com.steuerauszug.backend.model

import java.math.BigDecimal
import java.time.LocalDate

data class IbActivityData(
    val dividends: List<IbDividend>,
    val withholdingTax: List<IbWithholdingTax>,
    val interest: List<IbInterest>,
    val trades: List<IbTrade> = emptyList(),
    val openPositions: List<IbOpenPosition> = emptyList()
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

enum class BuySell { BUY, SELL }

data class IbTrade(
    val date: LocalDate,
    val symbol: String,
    val isin: String?,
    val description: String,
    val currency: String,
    val buySell: BuySell,
    val quantity: BigDecimal,
    val tradePrice: BigDecimal,
    val proceeds: BigDecimal,
    val fxRateToBase: BigDecimal
)

data class IbOpenPosition(
    val reportDate: LocalDate,
    val symbol: String,
    val isin: String?,
    val description: String,
    val currency: String,
    val quantity: BigDecimal,
    val markPrice: BigDecimal,
    val positionValue: BigDecimal,
    val fxRateToBase: BigDecimal
)
