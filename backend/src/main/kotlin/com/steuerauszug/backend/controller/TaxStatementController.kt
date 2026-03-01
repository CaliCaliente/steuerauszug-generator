package com.steuerauszug.backend.controller

import com.steuerauszug.backend.generator.EchXmlGenerator
import com.steuerauszug.backend.generator.PdfGenerator
import com.steuerauszug.backend.mapper.IbToEchMapper
import com.steuerauszug.backend.model.GenerationRequest
import com.steuerauszug.backend.parser.IbCsvParser
import com.steuerauszug.backend.parser.IbFlexQueryParser
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/steuerausweis")
@CrossOrigin(origins = ["http://localhost:4200"])
class TaxStatementController(
    private val ibCsvParser: IbCsvParser,
    private val ibFlexQueryParser: IbFlexQueryParser,
    private val mapper: IbToEchMapper,
    private val xmlGenerator: EchXmlGenerator,
    private val pdfGenerator: PdfGenerator
) {

    companion object {
        private const val PDF_FILENAME_PREFIX = "steuerausweis-"
    }

    @PostMapping("/generate", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun generate(
        @RequestPart("file") file: MultipartFile,
        @Valid @RequestPart("request") request: GenerationRequest
    ): ResponseEntity<ByteArray> {
        val content = file.inputStream.bufferedReader().readText()
        val ibData = if (content.trimStart().startsWith("<")) {
            ibFlexQueryParser.parse(content)
        } else {
            ibCsvParser.parse(content)
        }
        val statement = mapper.map(ibData, request)
        val xml = xmlGenerator.generate(statement)
        val pdf = pdfGenerator.generate(statement, xml)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$PDF_FILENAME_PREFIX${request.taxYear}.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf)
    }
}
