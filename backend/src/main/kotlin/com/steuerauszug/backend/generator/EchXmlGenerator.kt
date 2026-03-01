package com.steuerauszug.backend.generator

import com.steuerauszug.backend.model.EchTaxStatement
import com.steuerauszug.backend.model.TaxItemType
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
        private const val QUANTITY = "1"
        private const val ZERO = "0"
        private const val SECURITY_CATEGORY_SHARE = "SHARE"
        private const val SECURITY_CATEGORY_OTHER = "OTHER"
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
        if (statement.items.isNotEmpty()) {
            writeListOfSecurities(writer, statement)
        }
        writer.writeEndElement()
        writer.writeEndDocument()
        writer.flush()
        writer.close()
        return sw.toString()
    }

    private fun writeRootAttributes(writer: XMLStreamWriter, statement: EchTaxStatement) {
        val dividendTotal = statement.items.filter { it.type == TaxItemType.DIVIDEND }.sumOf { it.grossAmount }
        val interestTotal = statement.items.filter { it.type == TaxItemType.INTEREST }.sumOf { it.grossAmount }

        writer.writeAttribute("id", statement.documentId)
        writer.writeAttribute("minorVersion", MINOR_VERSION)
        writer.writeAttribute("creationDate", LocalDateTime.now().format(CREATION_DATE_FORMATTER))
        writer.writeAttribute("taxPeriod", statement.taxPeriod.toString())
        writer.writeAttribute("periodFrom", statement.periodFrom.toString())
        writer.writeAttribute("periodTo", statement.periodTo.toString())
        writer.writeAttribute("country", COUNTRY_CH)
        writer.writeAttribute("canton", statement.canton)
        writer.writeAttribute("totalTaxValue", statement.totalNet.toPlainString())
        writer.writeAttribute("totalGrossRevenueA", dividendTotal.toPlainString())
        writer.writeAttribute("totalGrossRevenueB", interestTotal.toPlainString())
        writer.writeAttribute("totalWithHoldingTaxClaim", statement.totalWithholding.toPlainString())
    }

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
        val dividendTotal = statement.items.filter { it.type == TaxItemType.DIVIDEND }.sumOf { it.grossAmount }
        val interestTotal = statement.items.filter { it.type == TaxItemType.INTEREST }.sumOf { it.grossAmount }

        writer.writeStartElement(NAMESPACE, "listOfSecurities")
        writer.writeAttribute("totalTaxValue", statement.totalNet.toPlainString())
        writer.writeAttribute("totalGrossRevenueA", dividendTotal.toPlainString())
        writer.writeAttribute("totalGrossRevenueB", interestTotal.toPlainString())
        writer.writeAttribute("totalWithHoldingTaxClaim", statement.totalWithholding.toPlainString())
        writer.writeAttribute("totalLumpSumTaxCredit", ZERO)
        writer.writeAttribute("totalNonRecoverableTax", ZERO)
        writer.writeAttribute("totalAdditionalWithHoldingTaxUSA", ZERO)
        writer.writeAttribute("totalGrossRevenueIUP", ZERO)
        writer.writeAttribute("totalGrossRevenueConversion", ZERO)

        writer.writeStartElement(NAMESPACE, "depot")
        writer.writeAttribute("depotNumber", "${statement.institution.clearingNumber}-${statement.customer.customerNumber}")

        statement.items.forEachIndexed { index, item ->
            val positionId = (index + 1).toString()
            val category = if (item.type == TaxItemType.DIVIDEND) SECURITY_CATEGORY_SHARE else SECURITY_CATEGORY_OTHER

            writer.writeStartElement(NAMESPACE, "security")
            writer.writeAttribute("positionId", positionId)
            if (item.isin != null && item.isin.length == 12) {
                writer.writeAttribute("isin", item.isin)
            }
            writer.writeAttribute("country", item.sourceCountry.take(2))
            writer.writeAttribute("currency", item.currency)
            writer.writeAttribute("quotationType", QUOTATION_TYPE)
            writer.writeAttribute("securityCategory", category)
            writer.writeAttribute("securityName", item.description.take(SECURITY_NAME_MAX_LENGTH))

            writePayment(writer, item, statement.periodTo.toString())
            writer.writeEndElement() // security
        }

        writer.writeEndElement() // depot
        writer.writeEndElement() // listOfSecurities
    }

    private fun writePayment(writer: XMLStreamWriter, item: com.steuerauszug.backend.model.TaxItem, paymentDate: String) {
        writer.writeEmptyElement(NAMESPACE, "payment")
        writer.writeAttribute("paymentDate", paymentDate)
        writer.writeAttribute("quotationType", QUOTATION_TYPE)
        writer.writeAttribute("quantity", QUANTITY)
        writer.writeAttribute("amountCurrency", item.currency)
        if (item.type == TaxItemType.DIVIDEND) {
            writer.writeAttribute("grossRevenueA", item.grossAmount.toPlainString())
            writer.writeAttribute("grossRevenueB", BigDecimal.ZERO.toPlainString())
        } else {
            writer.writeAttribute("grossRevenueA", BigDecimal.ZERO.toPlainString())
            writer.writeAttribute("grossRevenueB", item.grossAmount.toPlainString())
        }
        if (item.withholdingTax > BigDecimal.ZERO) {
            writer.writeAttribute("withHoldingTaxClaim", item.withholdingTax.toPlainString())
        }
    }
}
