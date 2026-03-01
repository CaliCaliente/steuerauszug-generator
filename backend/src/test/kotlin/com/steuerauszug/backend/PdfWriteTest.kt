package com.steuerauszug.backend

import com.steuerauszug.backend.generator.PdfGenerator
import com.steuerauszug.backend.generator.EchXmlGenerator
import com.steuerauszug.backend.model.*
import com.steuerauszug.backend.mapper.IbToEchMapper
import com.steuerauszug.backend.parser.IbCsvParser
import org.junit.jupiter.api.Test
import java.io.File

class PdfWriteTest {
    @Test
    fun generateAndWritePdf() {
        val csv = """
Dividends,Header,Currency,Date,Description,Amount
Dividends,Data,USD,2024-03-15,"AAPL(US0378331005) Cash Dividend USD 0.24 per Share (Ordinary Dividend)",96.00
Withholding Tax,Header,Currency,Date,Description,Amount
Withholding Tax,Data,USD,2024-03-15,"AAPL(US0378331005) Cash Dividend USD 0.24 per Share - US Tax",-28.80
Interest,Header,Currency,Date,Description,Amount
Interest,Data,USD,2024-01-02,"IBKR Managed Securities Lending Interest Program",15.50
""".trimIndent()
        val request = GenerationRequest("8888","Interactive Brokers","Postfach 1234, 8001 Zürich","U1234567","Max Mustermann","Musterstrasse 1, 8001 Zürich","ZH",2024)
        val ibData = IbCsvParser().parse(csv)
        println("Dividends: ${ibData.dividends.size}, WT: ${ibData.withholdingTax.size}, Interest: ${ibData.interest.size}")
        val statement = IbToEchMapper().map(ibData, request)
        println("TaxItems: ${statement.items.size}")
        val xml = EchXmlGenerator().generate(statement)
        println("XML length: ${xml.length}")
        val pdf = try { PdfGenerator().generate(statement, xml) } catch (e: Exception) {
            println("ERROR in generate: ${e::class.simpleName}: ${e.message}"); e.printStackTrace(); throw e
        }
        File("/tmp/fulltest.pdf").writeBytes(pdf)
        println("PDF written: ${pdf.size} bytes")
        val hasXObject = String(pdf).contains("XObject") || pdf.size > 5000
        println("Has image/XObject: $hasXObject")
    }
}
