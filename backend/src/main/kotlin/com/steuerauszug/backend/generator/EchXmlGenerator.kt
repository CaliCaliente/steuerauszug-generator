package com.steuerauszug.backend.generator

import com.steuerauszug.backend.model.EchTaxStatement
import org.springframework.stereotype.Component
import java.io.StringWriter
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

@Component
class EchXmlGenerator {

    companion object {
        private const val NAMESPACE = "http://www.ech.ch/xmlns/eCH-0196/2"
    }

    fun generate(statement: EchTaxStatement): String {
        val sw = StringWriter()
        val writer = XMLOutputFactory.newInstance().createXMLStreamWriter(sw)

        writer.writeStartDocument("UTF-8", "1.0")
        writer.writeStartElement("taxStatement")
        writer.writeDefaultNamespace(NAMESPACE)
        writeRootAttributes(writer, statement)
        writeAdministrativeSection(writer, statement)
        writeItemsSection(writer, statement)
        writeTotalValuesSection(writer, statement)
        writer.writeEndElement() // taxStatement
        writer.writeEndDocument()
        writer.flush()
        writer.close()

        return sw.toString()
    }

    private fun writeRootAttributes(writer: XMLStreamWriter, statement: EchTaxStatement) {
        writer.writeAttribute("documentId", statement.documentId)
        writer.writeAttribute("taxPeriod", statement.taxPeriod.toString())
        writer.writeAttribute("periodFrom", statement.periodFrom.toString())
        writer.writeAttribute("periodTo", statement.periodTo.toString())
        writer.writeAttribute("canton", statement.canton)
        writer.writeAttribute("totalTaxValue", statement.totalNet.toPlainString())
    }

    private fun writeAdministrativeSection(writer: XMLStreamWriter, statement: EchTaxStatement) {
        writer.writeStartElement("administrativeInformation")

        writer.writeStartElement("institution")
        writeElement(writer, "clearingNumber", statement.institution.clearingNumber)
        writeElement(writer, "name", statement.institution.name)
        writeElement(writer, "address", statement.institution.address)
        writer.writeEndElement()

        writer.writeStartElement("customer")
        writeElement(writer, "customerNumber", statement.customer.customerNumber)
        writeElement(writer, "name", statement.customer.name)
        writeElement(writer, "address", statement.customer.address)
        writer.writeEndElement()

        writer.writeEndElement() // administrativeInformation
    }

    private fun writeItemsSection(writer: XMLStreamWriter, statement: EchTaxStatement) {
        writer.writeStartElement("items")
        for (item in statement.items) {
            writer.writeStartElement("item")
            writer.writeAttribute("type", item.type.name)
            writeElement(writer, "description", item.description)
            writeElement(writer, "currency", item.currency)
            writeElement(writer, "grossAmount", item.grossAmount.toPlainString())
            writeElement(writer, "withholdingTax", item.withholdingTax.toPlainString())
            writeElement(writer, "netAmount", item.netAmount.toPlainString())
            writeElement(writer, "sourceCountry", item.sourceCountry)
            writer.writeEndElement()
        }
        writer.writeEndElement() // items
    }

    private fun writeTotalValuesSection(writer: XMLStreamWriter, statement: EchTaxStatement) {
        writer.writeStartElement("totalValues")
        writeElement(writer, "totalGross", statement.totalGross.toPlainString())
        writeElement(writer, "totalWithholding", statement.totalWithholding.toPlainString())
        writeElement(writer, "totalNet", statement.totalNet.toPlainString())
        writer.writeEndElement()
    }

    private fun writeElement(writer: XMLStreamWriter, name: String, value: String) {
        writer.writeStartElement(name)
        writer.writeCharacters(value)
        writer.writeEndElement()
    }
}
