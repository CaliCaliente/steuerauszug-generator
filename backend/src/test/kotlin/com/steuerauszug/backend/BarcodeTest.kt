package com.steuerauszug.backend

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.pdf417.PDF417Writer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class BarcodeTest {

    companion object {
        private const val MIN_PNG_SIZE = 100
    }

    @Test
    fun `barcode generation produces a valid PNG`() {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to 2
        )
        val content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><taxStatement>test</taxStatement>"
        val bitMatrix = PDF417Writer().encode(content, BarcodeFormat.PDF_417, 800, 400, hints)
        val img = MatrixToImageWriter.toBufferedImage(bitMatrix)
        val baos = ByteArrayOutputStream()
        val ok = ImageIO.write(img, "PNG", baos)
        assertTrue(ok) { "ImageIO.write returned false" }
        assertTrue(baos.size() > MIN_PNG_SIZE) { "PNG output too small: ${baos.size()} bytes" }
    }
}
