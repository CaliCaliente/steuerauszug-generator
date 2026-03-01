package com.steuerauszug.backend

import com.steuerauszug.backend.generator.EchXmlGenerator
import com.steuerauszug.backend.generator.PdfGenerator
import com.steuerauszug.backend.mapper.IbToEchMapper
import com.steuerauszug.backend.model.*
import com.steuerauszug.backend.parser.IbCsvParser
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PdfWriteTest {

    companion object {
        private const val MIN_PDF_WITH_IMAGE_SIZE = 2000
    }

    @Test
    fun `full pipeline produces a PDF with barcode`() {
        val csv = """
Dividends,Header,Currency,Date,Description,Amount
Dividends,Data,USD,2024-03-15,"AAPL(US0378331005) Cash Dividend USD 0.24 per Share (Ordinary Dividend)",96.00
Withholding Tax,Header,Currency,Date,Description,Amount
Withholding Tax,Data,USD,2024-03-15,"AAPL(US0378331005) Cash Dividend USD 0.24 per Share - US Tax",-28.80
Interest,Header,Currency,Date,Description,Amount
Interest,Data,USD,2024-01-02,"IBKR Managed Securities Lending Interest Program",15.50
""".trimIndent()
        val request = GenerationRequest("8888", "Interactive Brokers", "Postfach 1234, 8001 Zürich",
            "U1234567", "Max Mustermann", "Musterstrasse 1, 8001 Zürich", "ZH", 2024)
        val ibData = IbCsvParser().parse(csv)
        val statement = IbToEchMapper().map(ibData, request)
        val xml = EchXmlGenerator().generate(statement)
        val pdf = PdfGenerator().generate(statement, xml)
        val tmpFile = Files.createTempFile("fulltest-", ".pdf")
        Files.write(tmpFile, pdf)
        assertTrue(pdf.size > MIN_PDF_WITH_IMAGE_SIZE) { "PDF too small: ${pdf.size} bytes" }
    }
}
