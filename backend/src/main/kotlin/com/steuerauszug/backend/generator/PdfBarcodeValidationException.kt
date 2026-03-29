package com.steuerauszug.backend.generator

class PdfBarcodeValidationException(val errors: List<String>) : RuntimeException(errors.first())
