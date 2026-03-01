package com.steuerauszug.backend.generator

import com.steuerauszug.backend.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
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
                sourceCountry = "US",
                isin = "US0378331005"
            ),
            TaxItem(
                type = TaxItemType.INTEREST,
                description = "Zinsen – USD",
                currency = "USD",
                grossAmount = BigDecimal("5.00"),
                withholdingTax = BigDecimal.ZERO,
                netAmount = BigDecimal("5.00"),
                sourceCountry = "CH"
            )
        ),
        totalGross = BigDecimal("105.00"),
        totalWithholding = BigDecimal("15.00"),
        totalNet = BigDecimal("90.00")
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
    fun `should include id and minorVersion attributes`() {
        val xml = generator.generate(statement)
        val doc = parseXml(xml)

        val root = doc.documentElement
        assertEquals("CH-8888-123456-2024-001", root.getAttribute("id"))
        assertEquals("2", root.getAttribute("minorVersion"))
    }

    @Test
    fun `should include required root attributes`() {
        val xml = generator.generate(statement)
        val doc = parseXml(xml)

        val root = doc.documentElement
        assertEquals("2024", root.getAttribute("taxPeriod"))
        assertEquals("2024-01-01", root.getAttribute("periodFrom"))
        assertEquals("2024-12-31", root.getAttribute("periodTo"))
        assertEquals("CH", root.getAttribute("country"))
        assertEquals("ZH", root.getAttribute("canton"))
        assertEquals("90.00", root.getAttribute("totalTaxValue"))
        assertEquals("100.00", root.getAttribute("totalGrossRevenueA"))
        assertEquals("5.00", root.getAttribute("totalGrossRevenueB"))
        assertEquals("15.00", root.getAttribute("totalWithHoldingTaxClaim"))
    }

    @Test
    fun `should include institution element with name attribute`() {
        val xml = generator.generate(statement)
        val doc = parseXml(xml)

        val institutions = doc.getElementsByTagNameNS("http://www.ech.ch/xmlns/eCH-0196/2", "institution")
        assertEquals(1, institutions.length)
        assertEquals("Test Bank", (institutions.item(0) as Element).getAttribute("name"))
    }

    @Test
    fun `should include client element with split name`() {
        val xml = generator.generate(statement)
        val doc = parseXml(xml)

        val clients = doc.getElementsByTagNameNS("http://www.ech.ch/xmlns/eCH-0196/2", "client")
        assertEquals(1, clients.length)
        val client = clients.item(0) as Element
        assertEquals("123456", client.getAttribute("clientNumber"))
        assertEquals("Max", client.getAttribute("firstName"))
        assertEquals("Muster", client.getAttribute("lastName"))
    }

    @Test
    fun `should include listOfSecurities with aggregate attributes`() {
        val xml = generator.generate(statement)
        val doc = parseXml(xml)

        val lists = doc.getElementsByTagNameNS("http://www.ech.ch/xmlns/eCH-0196/2", "listOfSecurities")
        assertEquals(1, lists.length)
        val list = lists.item(0) as Element
        assertEquals("90.00", list.getAttribute("totalTaxValue"))
        assertEquals("100.00", list.getAttribute("totalGrossRevenueA"))
        assertEquals("5.00", list.getAttribute("totalGrossRevenueB"))
        assertEquals("15.00", list.getAttribute("totalWithHoldingTaxClaim"))
        assertEquals("0", list.getAttribute("totalLumpSumTaxCredit"))
        assertEquals("0", list.getAttribute("totalNonRecoverableTax"))
    }

    @Test
    fun `should include depot element with correct depotNumber`() {
        val xml = generator.generate(statement)
        val doc = parseXml(xml)

        val depots = doc.getElementsByTagNameNS("http://www.ech.ch/xmlns/eCH-0196/2", "depot")
        assertEquals(1, depots.length)
        assertEquals("8888-123456", (depots.item(0) as Element).getAttribute("depotNumber"))
    }

    @Test
    fun `should include security elements with correct category and isin`() {
        val xml = generator.generate(statement)
        val doc = parseXml(xml)

        val securities = doc.getElementsByTagNameNS("http://www.ech.ch/xmlns/eCH-0196/2", "security")
        assertEquals(2, securities.length)

        val dividend = securities.item(0) as Element
        assertEquals("SHARE", dividend.getAttribute("securityCategory"))
        assertEquals("US0378331005", dividend.getAttribute("isin"))
        assertEquals("US", dividend.getAttribute("country"))
        assertEquals("USD", dividend.getAttribute("currency"))
        assertEquals("PIECE", dividend.getAttribute("quotationType"))

        val interest = securities.item(1) as Element
        assertEquals("OTHER", interest.getAttribute("securityCategory"))
        assertEquals("", interest.getAttribute("isin"))
        assertEquals("CH", interest.getAttribute("country"))
    }

    @Test
    fun `should include payment elements with correct revenue attributes`() {
        val xml = generator.generate(statement)
        val doc = parseXml(xml)

        val payments = doc.getElementsByTagNameNS("http://www.ech.ch/xmlns/eCH-0196/2", "payment")
        assertEquals(2, payments.length)

        val dividendPayment = payments.item(0) as Element
        assertEquals("100.00", dividendPayment.getAttribute("grossRevenueA"))
        assertEquals("0", dividendPayment.getAttribute("grossRevenueB"))
        assertEquals("15.00", dividendPayment.getAttribute("withHoldingTaxClaim"))
        assertEquals("2024-12-31", dividendPayment.getAttribute("paymentDate"))
        assertEquals("PIECE", dividendPayment.getAttribute("quotationType"))
        assertEquals("1", dividendPayment.getAttribute("quantity"))

        val interestPayment = payments.item(1) as Element
        assertEquals("0", interestPayment.getAttribute("grossRevenueA"))
        assertEquals("5.00", interestPayment.getAttribute("grossRevenueB"))
    }

    @Test
    fun `should not include listOfSecurities when there are no items`() {
        val emptyStatement = statement.copy(items = emptyList(), totalGross = BigDecimal.ZERO,
            totalWithholding = BigDecimal.ZERO, totalNet = BigDecimal.ZERO)
        val xml = generator.generate(emptyStatement)
        val doc = parseXml(xml)

        val lists = doc.getElementsByTagNameNS("http://www.ech.ch/xmlns/eCH-0196/2", "listOfSecurities")
        assertEquals(0, lists.length)
    }

    @Test
    fun `should split single-word customer name correctly`() {
        val singleNameStatement = statement.copy(
            customer = Customer("123456", "Muster", "Musterweg 2")
        )
        val xml = generator.generate(singleNameStatement)
        val doc = parseXml(xml)

        val client = doc.getElementsByTagNameNS("http://www.ech.ch/xmlns/eCH-0196/2", "client").item(0) as Element
        assertEquals("", client.getAttribute("firstName"))
        assertEquals("Muster", client.getAttribute("lastName"))
    }
}
