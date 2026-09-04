package com.ovi.handoff.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object TerminalQrGenerator {
    /**
     * Generates a compact, highly scannable terminal QR code using Unicode half-blocks (▀, ▄, █).
     * By packing two vertical modules per character cell, the vertical height is cut by 50%
     * and horizontal width is cut by 50% (1 char per column instead of 2).
     *
     * Uses ANSI black-background + white-foreground sequences for universal scannability
     * across both dark and light terminal themes.
     */
    fun printQrCode(url: String, width: Int = 0) {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 1
        )
        
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, 0, 0, hints)
        val matrixWidth = bitMatrix.width
        val matrixHeight = bitMatrix.height

        val out = java.io.PrintStream(System.out, true, "UTF-8")
        val ansiBlackBgWhiteFg = "\u001B[40m\u001B[97m"
        val ansiReset = "\u001B[0m"

        out.println()
        for (y in 0 until matrixHeight step 2) {
            val row = StringBuilder()
            row.append("    ") // Margin indent for clean terminal display
            row.append(ansiBlackBgWhiteFg)
            for (x in 0 until matrixWidth) {
                val topDark = bitMatrix.get(x, y)
                val bottomDark = if (y + 1 < matrixHeight) bitMatrix.get(x, y + 1) else false

                val char = when {
                    topDark && bottomDark -> ' '
                    !topDark && !bottomDark -> '█'
                    !topDark && bottomDark -> '▀'
                    else -> '▄'
                }
                row.append(char)
            }
            row.append(ansiReset)
            out.println(row.toString())
        }
        out.println()
    }
}
