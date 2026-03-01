package com.steuerauszug.backend.generator

import org.springframework.stereotype.Component
import org.w3c.dom.ls.LSInput
import org.w3c.dom.ls.LSResourceResolver
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

@Component
class EchXmlValidator {

    private val schema: Schema by lazy { loadSchema() }

    private fun loadSchema(): Schema {
        val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        factory.resourceResolver = ClasspathLSResourceResolver()
        val xsd = javaClass.getResource("/xsd/eCH-0196-2-0.xsd")
            ?: error("eCH-0196-2-0.xsd not found on classpath")
        return factory.newSchema(xsd)
    }

    fun validate(xml: String) {
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
}

private class ClasspathLSResourceResolver : LSResourceResolver {

    override fun resolveResource(
        type: String?,
        namespaceURI: String?,
        publicId: String?,
        systemId: String?,
        baseURI: String?
    ): LSInput? {
        val filename = systemId?.substringAfterLast("/") ?: return null
        val stream = ClasspathLSResourceResolver::class.java.getResourceAsStream("/xsd/$filename")
            ?: return null
        return ClasspathLSInput(publicId, systemId, stream)
    }
}

private class ClasspathLSInput(
    private val publicId: String?,
    private val systemId: String?,
    private val inputStream: InputStream
) : LSInput {
    override fun getCharacterStream(): Reader? = null
    override fun setCharacterStream(characterStream: Reader?) {}
    override fun getByteStream(): InputStream = inputStream
    override fun setByteStream(byteStream: InputStream?) {}
    override fun getStringData(): String? = null
    override fun setStringData(stringData: String?) {}
    override fun getSystemId(): String? = systemId
    override fun setSystemId(systemId: String?) {}
    override fun getPublicId(): String? = publicId
    override fun setPublicId(publicId: String?) {}
    override fun getBaseURI(): String? = null
    override fun setBaseURI(baseURI: String?) {}
    override fun getEncoding(): String? = null
    override fun setEncoding(encoding: String?) {}
    override fun getCertifiedText(): Boolean = false
    override fun setCertifiedText(certifiedText: Boolean) {}
}
