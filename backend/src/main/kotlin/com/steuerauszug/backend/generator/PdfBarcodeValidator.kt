package com.steuerauszug.backend.generator

import org.springframework.stereotype.Component

@Component
class PdfBarcodeValidator(
    private val pdfBarcodeExtractor: PdfBarcodeExtractor,
    private val echXmlValidator: EchXmlValidator
) {

    fun validate(pdfBytes: ByteArray, originalXml: String) {
        val decodedXml = validateRoundTrip(pdfBytes, originalXml)
        validateDecodedSchema(decodedXml)
    }

    private fun validateRoundTrip(pdfBytes: ByteArray, originalXml: String): String {
        val decoded = try {
            pdfBarcodeExtractor.extract(pdfBytes)
        } catch (e: Exception) {
            throw PdfBarcodeValidationException(listOf("Barcode extraction failed: ${e.message}"))
        }
        if (decoded != originalXml) {
            throw PdfBarcodeValidationException(
                listOf("Decoded XML does not match original (decoded: ${decoded.length} chars, original: ${originalXml.length} chars)")
            )
        }
        return decoded
    }

    private fun validateDecodedSchema(decodedXml: String) {
        try {
            echXmlValidator.validate(decodedXml)
        } catch (e: XmlValidationException) {
            throw PdfBarcodeValidationException(e.errors.map { "Decoded barcode XML: $it" })
        }
    }
}
