package com.steuerauszug.backend

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.pdf417.PDF417Writer
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class BarcodeTest {
    @Test
    fun testBarcodeGeneration() {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to 2
        )
        val content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><taxStatement>test</taxStatement>"
        val bitMatrix = PDF417Writer().encode(content, BarcodeFormat.PDF_417, 800, 400, hints)
        println("BitMatrix: ${bitMatrix.width}x${bitMatrix.height}")
        val img = MatrixToImageWriter.toBufferedImage(bitMatrix)
        println("Image: ${img.width}x${img.height}")
        val baos = ByteArrayOutputStream()
        val ok = ImageIO.write(img, "PNG", baos)
        println("ImageIO.write=$ok bytes=${baos.size()}")
        assert(ok) { "ImageIO.write returned false" }
        assert(baos.size() > 0) { "Empty PNG output" }
    }
}
