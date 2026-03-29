package com.steuerauszug.backend.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PdfBarcodeValidationExceptionTest {

    @Test
    fun `message equals first error`() {
        val ex = PdfBarcodeValidationException(listOf("first error", "second error"))
        assertEquals("first error", ex.message)
    }

    @Test
    fun `errors list is accessible`() {
        val errors = listOf("error one", "error two")
        val ex = PdfBarcodeValidationException(errors)
        assertEquals(errors, ex.errors)
    }

    @Test
    fun `works with single error`() {
        val ex = PdfBarcodeValidationException(listOf("only error"))
        assertEquals("only error", ex.message)
        assertEquals(1, ex.errors.size)
    }
}
