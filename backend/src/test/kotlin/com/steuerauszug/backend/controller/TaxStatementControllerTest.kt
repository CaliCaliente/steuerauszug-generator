package com.steuerauszug.backend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.steuerauszug.backend.generator.EchXmlGenerator
import com.steuerauszug.backend.generator.PdfGenerator
import com.steuerauszug.backend.mapper.IbToEchMapper
import com.steuerauszug.backend.model.*
import com.steuerauszug.backend.parser.IbCsvParser
import com.steuerauszug.backend.parser.IbFlexQueryParser
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockPart
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal
import java.time.LocalDate

@WebMvcTest(TaxStatementController::class)
class TaxStatementControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var ibCsvParser: IbCsvParser

    @MockkBean
    private lateinit var ibFlexQueryParser: IbFlexQueryParser

    @MockkBean
    private lateinit var mapper: IbToEchMapper

    @MockkBean
    private lateinit var xmlGenerator: EchXmlGenerator

    @MockkBean
    private lateinit var pdfGenerator: PdfGenerator

    private val validRequest = GenerationRequest(
        clearingNumber = "8888",
        institutionName = "Test Bank",
        institutionAddress = "Teststrasse 1",
        customerNumber = "123456",
        customerName = "Max Muster",
        customerAddress = "Musterweg 2",
        canton = "ZH",
        taxYear = 2024
    )

    private val emptyIbData = IbActivityData(emptyList(), emptyList(), emptyList())

    private val statement = EchTaxStatement(
        documentId = "CH-8888-123456-2024-001",
        taxPeriod = 2024,
        periodFrom = LocalDate.of(2024, 1, 1),
        periodTo = LocalDate.of(2024, 12, 31),
        institution = Institution("8888", "Test Bank", "Teststrasse 1"),
        customer = Customer("123456", "Max Muster", "Musterweg 2"),
        canton = "ZH",
        items = emptyList(),
        totalGross = BigDecimal.ZERO,
        totalWithholding = BigDecimal.ZERO,
        totalNet = BigDecimal.ZERO
    )

    private fun requestPart(request: GenerationRequest = validRequest): MockPart =
        MockPart("request", objectMapper.writeValueAsBytes(request)).also {
            it.headers.contentType = MediaType.APPLICATION_JSON
        }

    @Test
    fun `should detect CSV format and call CSV parser`() {
        val csvContent = "Dividends,Data,USD,2024-03-15,AAPL,100.00"
        val filePart = MockMultipartFile("file", "activity.csv", "text/csv", csvContent.toByteArray())

        every { ibCsvParser.parse(any()) } returns emptyIbData
        every { mapper.map(any(), any()) } returns statement
        every { xmlGenerator.generate(any()) } returns "<xml/>"
        every { pdfGenerator.generate(any(), any()) } returns ByteArray(0)

        mockMvc.perform(
            multipart("/api/steuerausweis/generate")
                .file(filePart)
                .part(requestPart())
        ).andExpect(status().isOk)

        verify { ibCsvParser.parse(csvContent) }
    }

    @Test
    fun `should detect XML format and call XML parser`() {
        val xmlContent = "<FlexQueryResponse/>"
        val filePart = MockMultipartFile("file", "activity.xml", "text/xml", xmlContent.toByteArray())

        every { ibFlexQueryParser.parse(any()) } returns emptyIbData
        every { mapper.map(any(), any()) } returns statement
        every { xmlGenerator.generate(any()) } returns "<xml/>"
        every { pdfGenerator.generate(any(), any()) } returns ByteArray(0)

        mockMvc.perform(
            multipart("/api/steuerausweis/generate")
                .file(filePart)
                .part(requestPart())
        ).andExpect(status().isOk)

        verify { ibFlexQueryParser.parse(xmlContent) }
    }

    @Test
    fun `should return PDF response with correct content type`() {
        val filePart = MockMultipartFile("file", "activity.csv", "text/csv", "data".toByteArray())

        every { ibCsvParser.parse(any()) } returns emptyIbData
        every { mapper.map(any(), any()) } returns statement
        every { xmlGenerator.generate(any()) } returns "<xml/>"
        every { pdfGenerator.generate(any(), any()) } returns byteArrayOf(0x25, 0x50, 0x44, 0x46)

        mockMvc.perform(
            multipart("/api/steuerausweis/generate")
                .file(filePart)
                .part(requestPart())
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
    }

    @Test
    fun `should return Content-Disposition header with tax year in filename`() {
        val filePart = MockMultipartFile("file", "activity.csv", "text/csv", "data".toByteArray())

        every { ibCsvParser.parse(any()) } returns emptyIbData
        every { mapper.map(any(), any()) } returns statement
        every { xmlGenerator.generate(any()) } returns "<xml/>"
        every { pdfGenerator.generate(any(), any()) } returns ByteArray(0)

        mockMvc.perform(
            multipart("/api/steuerausweis/generate")
                .file(filePart)
                .part(requestPart())
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", "attachment; filename=steuerausweis-2024.pdf"))
    }

    @Test
    fun `should return 400 when request fields are blank`() {
        val blankRequest = GenerationRequest(
            clearingNumber = "",
            institutionName = "",
            institutionAddress = "",
            customerNumber = "",
            customerName = "",
            customerAddress = "",
            canton = "",
            taxYear = 2024
        )
        val filePart = MockMultipartFile("file", "activity.csv", "text/csv", "data".toByteArray())

        mockMvc.perform(
            multipart("/api/steuerausweis/generate")
                .file(filePart)
                .part(requestPart(blankRequest))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 500 when parser throws unexpected exception`() {
        val filePart = MockMultipartFile("file", "activity.csv", "text/csv", "data".toByteArray())

        every { ibCsvParser.parse(any()) } throws RuntimeException("Unexpected parse error")

        mockMvc.perform(
            multipart("/api/steuerausweis/generate")
                .file(filePart)
                .part(requestPart())
        ).andExpect(status().isInternalServerError)
    }
}
