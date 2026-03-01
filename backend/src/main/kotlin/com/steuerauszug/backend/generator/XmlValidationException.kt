package com.steuerauszug.backend.generator

class XmlValidationException(val errors: List<String>) : RuntimeException(errors.first())
