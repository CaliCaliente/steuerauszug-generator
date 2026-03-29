package com.steuerauszug.backend.generator

import org.springframework.stereotype.Component
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

private val XSD_FILES = listOf(
	"eCH-0007-6-0.xsd",
	"eCH-0008-3-0.xsd",
	"eCH-0010-7-0.xsd",
	"eCH-0097-4-0.xsd",
	"eCH-0196-2-0.xsd"
)

@Component
class EchXmlValidator {

	private val schema: Schema by lazy {
		val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
		val sources = XSD_FILES.map { filename ->
			val url = javaClass.getResource("/xsd/$filename")
				?: error("$filename not found on classpath")
			StreamSource(url.openStream(), url.toString())
		}.toTypedArray()
		factory.newSchema(sources)
	}

	private fun validateAgainstSchemas(xml: String) {
		val errors = mutableListOf<String>()
		val validator = schema.newValidator()
		validator.errorHandler = object : ErrorHandler {
			override fun warning(e: SAXParseException) { errors.add("WARN: ${e.message}") }
			override fun error(e: SAXParseException) { errors.add("ERROR: ${e.message}") }
			override fun fatalError(e: SAXParseException) { errors.add("FATAL: ${e.message}") }
		}
		validator.validate(StreamSource(StringReader(xml)))
		if (errors.any { it.startsWith("ERROR") || it.startsWith("FATAL") }) {
			throw XmlValidationException(errors)
		}
	}

	fun validate(xml: String) = validateAgainstSchemas(xml)
}
