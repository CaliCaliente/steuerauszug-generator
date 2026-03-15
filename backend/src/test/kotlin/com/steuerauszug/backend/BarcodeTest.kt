package com.steuerauszug.backend

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import uk.org.okapibarcode.backend.Pdf417
import uk.org.okapibarcode.backend.Pdf417.Mode
import uk.org.okapibarcode.graphics.Color as OkapiColor
import uk.org.okapibarcode.output.Java2DRenderer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class BarcodeTest {

    companion object {
        private const val MIN_PNG_SIZE = 100
    }

    @Test
    fun `OkapiBarcode PDF417 produces a valid PNG`() {
        val barcode = Pdf417(Mode.NORMAL).apply {
            setContent("<?xml version=\"1.0\" encoding=\"UTF-8\"?><taxStatement>test</taxStatement>")
            dataColumns = 13
            setRows(35)
            setPreferredEccLevel(4)
        }

        val w = (barcode.width * 2.0).toInt()
        val h = (barcode.height * 2.0).toInt()
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g2d = img.createGraphics()
        Java2DRenderer(g2d, 2.0, OkapiColor(255, 255, 255), OkapiColor(0, 0, 0)).render(barcode)
        g2d.dispose()

        val baos = ByteArrayOutputStream()
        val ok = ImageIO.write(img, "PNG", baos)
        assertTrue(ok) { "ImageIO.write returned false" }
        assertTrue(baos.size() > MIN_PNG_SIZE) { "PNG output too small: ${baos.size()} bytes" }
    }
}
