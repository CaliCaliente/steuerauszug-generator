package com.steuerauszug.backend.generator

import com.steuerauszug.backend.model.*
import org.springframework.stereotype.Component
import java.io.StringWriter
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

@Component
class EchXmlGenerator {

    companion object {
        private const val NAMESPACE = "http://www.ech.ch/xmlns/eCH-0196/2"
        private const val MINOR_VERSION = "2"
        private const val COUNTRY_CH = "CH"
        private const val QUOTATION_TYPE = "PIECE"
        private const val QUANTITY_ONE = "1"
        private const val ZERO = "0"
        private const val SECURITY_CATEGORY_SHARE = "SHARE"
        private const val SECURITY_NAME_MAX_LENGTH = 60
        private val CREATION_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }

    fun generate(statement: EchTaxStatement): String {
        val sw = StringWriter()
        val writer = XMLOutputFactory.newInstance().createXMLStreamWriter(sw)
        writer.writeStartDocument("UTF-8", "1.0")
        writer.writeStartElement("taxStatement")
        writer.writeDefaultNamespace(NAMESPACE)
        writeRootAttributes(writer, statement)
        writeInstitution(writer, statement)
        writeClient(writer, statement)
        if (statement.securities.isNotEmpty()) {
            writeListOfSecurities(writer, statement)
        }
        writer.writeEndElement()
        writer.writeEndDocument()
        writer.flush()
        writer.close()
        return sw.toString()
    }

    private fun writeRootAttributes(writer: XMLStreamWriter, statement: EchTaxStatement) {
        val shareGross = grossForCategory(statement, SECURITY_CATEGORY_SHARE)
        val otherGross = statement.totalGross - shareGross

        writer.writeAttribute("id", statement.documentId)
        writer.writeAttribute("minorVersion", MINOR_VERSION)
        writer.writeAttribute("creationDate", LocalDateTime.now().format(CREATION_DATE_FORMATTER))
        writer.writeAttribute("taxPeriod", statement.taxPeriod.toString())
        writer.writeAttribute("periodFrom", statement.periodFrom.toString())
        writer.writeAttribute("periodTo", statement.periodTo.toString())
        writer.writeAttribute("country", COUNTRY_CH)
        writer.writeAttribute("canton", statement.canton)
        writer.writeAttribute("totalTaxValue", statement.totalNet.toPlainString())
        writer.writeAttribute("totalGrossRevenueA", shareGross.toPlainString())
        writer.writeAttribute("totalGrossRevenueB", otherGross.toPlainString())
        writer.writeAttribute("totalWithHoldingTaxClaim", statement.totalWithholding.toPlainString())
    }

    private fun grossForCategory(statement: EchTaxStatement, category: String): BigDecimal =
        statement.securities
            .filter { it.securityCategory == category }
            .flatMap { it.payments }
            .sumOf { it.grossAmountCHF ?: it.grossAmount }

    private fun writeInstitution(writer: XMLStreamWriter, statement: EchTaxStatement) {
        writer.writeEmptyElement(NAMESPACE, "institution")
        writer.writeAttribute("name", statement.institution.name.take(60))
    }

    private fun writeClient(writer: XMLStreamWriter, statement: EchTaxStatement) {
        val nameParts = statement.customer.name.split(" ", limit = 2)
        val firstName = if (nameParts.size >= 2) nameParts[0] else ""
        val lastName = if (nameParts.size >= 2) nameParts[1] else nameParts[0]

        writer.writeEmptyElement(NAMESPACE, "client")
        writer.writeAttribute("clientNumber", statement.customer.customerNumber)
        writer.writeAttribute("firstName", firstName)
        writer.writeAttribute("lastName", lastName)
    }

    private fun writeListOfSecurities(writer: XMLStreamWriter, statement: EchTaxStatement) {
        val shareGross = grossForCategory(statement, SECURITY_CATEGORY_SHARE)
        val otherGross = statement.totalGross - shareGross

        writer.writeStartElement(NAMESPACE, "listOfSecurities")
        writer.writeAttribute("totalTaxValue", statement.totalNet.toPlainString())
        writer.writeAttribute("totalGrossRevenueA", shareGross.toPlainString())
        writer.writeAttribute("totalGrossRevenueB", otherGross.toPlainString())
        writer.writeAttribute("totalWithHoldingTaxClaim", statement.totalWithholding.toPlainString())
        writer.writeAttribute("totalLumpSumTaxCredit", ZERO)
        writer.writeAttribute("totalNonRecoverableTax", ZERO)
        writer.writeAttribute("totalAdditionalWithHoldingTaxUSA", ZERO)
        writer.writeAttribute("totalGrossRevenueIUP", ZERO)
        writer.writeAttribute("totalGrossRevenueConversion", ZERO)

        writer.writeStartElement(NAMESPACE, "depot")
        writer.writeAttribute("depotNumber", "${statement.institution.clearingNumber}-${statement.customer.customerNumber}")

        statement.securities.forEachIndexed { index, security ->
            writeSecurity(writer, security, index + 1)
        }

        writer.writeEndElement() // depot
        writer.writeEndElement() // listOfSecurities
    }

    private fun writeSecurity(writer: XMLStreamWriter, security: EchSecurity, positionId: Int) {
        writer.writeStartElement(NAMESPACE, "security")
        writer.writeAttribute("positionId", positionId.toString())
        if (security.isin != null && security.isin.length == 12) {
            writer.writeAttribute("isin", security.isin)
        }
        writer.writeAttribute("country", security.sourceCountry.take(2))
        writer.writeAttribute("currency", security.currency)
        writer.writeAttribute("quotationType", QUOTATION_TYPE)
        writer.writeAttribute("securityCategory", security.securityCategory)
        writer.writeAttribute("securityName", security.description.take(SECURITY_NAME_MAX_LENGTH))

        security.yearEndTaxValue?.let { writeTaxValue(writer, it) }
        security.payments.forEach { writePayment(writer, it, security.securityCategory) }
        security.stocks.forEach { writeStock(writer, it) }

        writer.writeEndElement() // security
    }

    private fun writeTaxValue(writer: XMLStreamWriter, taxValue: EchTaxValue) {
        writer.writeEmptyElement(NAMESPACE, "taxValue")
        writer.writeAttribute("referenceDate", taxValue.referenceDate.toString())
        writer.writeAttribute("quotationType", QUOTATION_TYPE)
        writer.writeAttribute("quantity", taxValue.quantity.toPlainString())
        writer.writeAttribute("balanceCurrency", "CHF")
        writer.writeAttribute("unitPrice", taxValue.unitPrice.toPlainString())
        writer.writeAttribute("balance", taxValue.balance.toPlainString())
        if (taxValue.exchangeRate != null) {
            writer.writeAttribute("exchangeRate", taxValue.exchangeRate.toPlainString())
        }
        if (taxValue.valueCHF != null) {
            writer.writeAttribute("value", taxValue.valueCHF.toPlainString())
        }
        writer.writeAttribute("undefined", "1")
    }

    private fun writePayment(writer: XMLStreamWriter, payment: EchPayment, securityCategory: String) {
        val isShare = securityCategory == SECURITY_CATEGORY_SHARE
        writer.writeEmptyElement(NAMESPACE, "payment")
        writer.writeAttribute("paymentDate", payment.date.toString())
        writer.writeAttribute("quotationType", QUOTATION_TYPE)
        writer.writeAttribute("quantity", payment.quantity.toPlainString())
        writer.writeAttribute("amountCurrency", "CHF")
        if (isShare) {
            writer.writeAttribute("grossRevenueA", payment.grossAmount.toPlainString())
            writer.writeAttribute("grossRevenueB", BigDecimal.ZERO.toPlainString())
        } else {
            writer.writeAttribute("grossRevenueA", BigDecimal.ZERO.toPlainString())
            writer.writeAttribute("grossRevenueB", payment.grossAmount.toPlainString())
        }
        if (payment.withholdingTax > BigDecimal.ZERO) {
            writer.writeAttribute("withHoldingTaxClaim", payment.withholdingTax.toPlainString())
        }
        if (payment.exchangeRate != null) {
            writer.writeAttribute("exchangeRate", payment.exchangeRate.toPlainString())
        }
    }

    private fun writeStock(writer: XMLStreamWriter, stock: EchStock) {
        writer.writeEmptyElement(NAMESPACE, "stock")
        writer.writeAttribute("referenceDate", stock.date.toString())
        writer.writeAttribute("mutation", stock.mutation.toString())
        writer.writeAttribute("name", stock.name)
        writer.writeAttribute("quotationType", QUOTATION_TYPE)
        writer.writeAttribute("quantity", stock.quantity.toPlainString())
        writer.writeAttribute("balanceCurrency", "CHF")
        writer.writeAttribute("unitPrice", stock.unitPrice.toPlainString())
        writer.writeAttribute("balance", stock.balance.toPlainString())
        if (stock.exchangeRate != null) {
            writer.writeAttribute("exchangeRate", stock.exchangeRate.toPlainString())
        }
        if (stock.valueCHF != null) {
            writer.writeAttribute("value", stock.valueCHF.toPlainString())
        }
    }
}
