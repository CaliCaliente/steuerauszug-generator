package com.steuerauszug.backend.generator

import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class PdfBarcodeValidatorTest {

    private val extractor = mockk<PdfBarcodeExtractor>()
    private val xmlValidator = mockk<EchXmlValidator>()
    private val validator = PdfBarcodeValidator(extractor, xmlValidator)

    private val pdfBytes = byteArrayOf(1, 2, 3)
    private val originalXml = "<taxStatement>content</taxStatement>"

    @Test
    fun `should pass when decoded XML matches original and passes XSD`() {
        every { extractor.extract(pdfBytes) } returns originalXml
        every { xmlValidator.validate(originalXml) } just runs

        assertDoesNotThrow { validator.validate(pdfBytes, originalXml) }
    }

    @Test
    fun `should throw when decoded XML differs from original`() {
        every { extractor.extract(pdfBytes) } returns "<different/>"

        val ex = assertThrows<PdfBarcodeValidationException> {
            validator.validate(pdfBytes, originalXml)
        }
        assert(ex.errors.first().contains("does not match"))
    }

    @Test
    fun `should throw when extractor throws`() {
        every { extractor.extract(pdfBytes) } throws IllegalStateException("No barcode images found")

        val ex = assertThrows<PdfBarcodeValidationException> {
            validator.validate(pdfBytes, originalXml)
        }
        assert(ex.errors.first().contains("No barcode images found"))
    }

    @Test
    fun `should throw when decoded XML fails XSD validation`() {
        every { extractor.extract(pdfBytes) } returns originalXml
        every { xmlValidator.validate(originalXml) } throws XmlValidationException(listOf("ERROR: missing attribute 'id'"))

        val ex = assertThrows<PdfBarcodeValidationException> {
            validator.validate(pdfBytes, originalXml)
        }
        assert(ex.errors.any { it.contains("missing attribute 'id'") })
    }

    @Test
    fun `should not call XSD validator when extraction fails`() {
        every { extractor.extract(pdfBytes) } throws IllegalStateException("No barcode")

        assertThrows<PdfBarcodeValidationException> {
            validator.validate(pdfBytes, originalXml)
        }

        verify { xmlValidator wasNot Called }
    }
}
