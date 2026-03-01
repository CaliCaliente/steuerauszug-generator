package com.steuerauszug.backend.generator

import com.steuerauszug.backend.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

class EchXmlGeneratorTest {

    private val generator = EchXmlGenerator()

    private val statement = EchTaxStatement(
        documentId = "CH-8888-123456-2024-001",
        taxPeriod = 2024,
        periodFrom = LocalDate.of(2024, 1, 1),
        periodTo = LocalDate.of(2024, 12, 31),
        institution = Institution("8888", "Test Bank", "Teststrasse 1"),
        customer = Customer("123456", "Max Muster", "Musterweg 2"),
        canton = "ZH",
        items = listOf(
            TaxItem(
                type = TaxItemType.DIVIDEND,
                description = "AAPL – Dividende",
                currency = "USD",
                grossAmount = BigDecimal("100.00"),
                withholdingTax = BigDecimal("15.00"),
                netAmount = BigDecimal("85.00"),
                sourceCountry = "US"
            )
        ),
        totalGross = BigDecimal("100.00"),
        totalWithholding = BigDecimal("15.00"),
        totalNet = BigDecimal("85.00")
    )

    private fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
    }

    @Test
    fun `should produce valid XML with correct namespace`() {
        val xml = generator.generate(statement)
        val doc = parseXml(xml)

        val root = doc.documentElement
        assertEquals("taxStatement", root.localName)
        assertEquals("http://www.ech.ch/xmlns/eCH-0196/2", root.namespaceURI)
    }

    @Test
    fun `should include document ID in output`() {
        val xml = generator.generate(statement)
        val doc = parseXml(xml)

        val documentId = doc.documentElement.getAttribute("documentId")
        assertEquals("CH-8888-123456-2024-001", documentId)
    }

    @Test
    fun `should include all tax items in XML output`() {
        val xml = generator.generate(statement)
        val doc = parseXml(xml)

        val items = doc.getElementsByTagName("item")
        assertEquals(1, items.length)
        val item = items.item(0) as org.w3c.dom.Element
        assertEquals("DIVIDEND", item.getAttribute("type"))

        val descriptions = item.getElementsByTagName("description")
        assertEquals("AAPL – Dividende", descriptions.item(0).textContent)
    }

    @Test
    fun `should include totals in XML output`() {
        val xml = generator.generate(statement)
        val doc = parseXml(xml)

        val totalGrossNodes = doc.getElementsByTagName("totalGross")
        assertEquals("100.00", totalGrossNodes.item(0).textContent)

        val totalWithholdingNodes = doc.getElementsByTagName("totalWithholding")
        assertEquals("15.00", totalWithholdingNodes.item(0).textContent)

        val totalNetNodes = doc.getElementsByTagName("totalNet")
        assertEquals("85.00", totalNetNodes.item(0).textContent)
    }
}
