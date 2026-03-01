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
    val securities: List<EchSecurity>,
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

data class EchSecurity(
    val symbol: String,
    val isin: String?,
    val description: String,
    val currency: String,
    val sourceCountry: String,
    val securityCategory: String,
    val payments: List<EchPayment>,
    val yearEndTaxValue: EchTaxValue?,
    val stocks: List<EchStock>
)

data class EchPayment(
    val date: LocalDate,
    val quantity: BigDecimal,
    val grossAmount: BigDecimal,
    val withholdingTax: BigDecimal,
    val exchangeRate: BigDecimal?,
    val grossAmountCHF: BigDecimal?
)

data class EchTaxValue(
    val referenceDate: LocalDate,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val balance: BigDecimal,
    val exchangeRate: BigDecimal?,
    val valueCHF: BigDecimal?
)

data class EchStock(
    val date: LocalDate,
    val mutation: Boolean,
    val name: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val balance: BigDecimal,
    val exchangeRate: BigDecimal?,
    val valueCHF: BigDecimal?
)
