package com.steuerauszug.backend.model

import java.math.BigDecimal
import java.time.LocalDate

data class EchTaxStatement(
    val documentId: String,
    val taxPeriod: Int,
    val periodFrom: LocalDate,
    val periodTo: LocalDate,
    val institution: Institution,
    val customer: Customer,
    val canton: String,
    val items: List<TaxItem>,
    val totalGross: BigDecimal,
    val totalWithholding: BigDecimal,
    val totalNet: BigDecimal
)

data class Institution(
    val clearingNumber: String,
    val name: String,
    val address: String
)

data class Customer(
    val customerNumber: String,
    val name: String,
    val address: String
)

data class TaxItem(
    val type: TaxItemType,
    val description: String,
    val currency: String,
    val grossAmount: BigDecimal,
    val withholdingTax: BigDecimal,
    val netAmount: BigDecimal,
    val sourceCountry: String
)

enum class TaxItemType { DIVIDEND, INTEREST }
