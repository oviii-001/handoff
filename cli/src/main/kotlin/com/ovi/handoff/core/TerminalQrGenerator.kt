package com.ovi.handoff.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.EncodeHintType

object TerminalQrGenerator {
    /**
     * Generates an ASCII QR Code for the terminal.
     * Uses the ANSI escape codes for inverted colors (black/white blocks)
     * 
     * █ = black module
     *   = white module
     */
    fun printQrCode(url: String, width: Int = 40) {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, width, width, hints)

        println()
        for (y in 0 until bitMatrix.height) {
            val row = StringBuilder()
            // Add a small margin for terminal readability
            row.append("  ") 
            for (x in 0 until bitMatrix.width) {
                if (bitMatrix.get(x, y)) {
                    // Black block (two characters wide to make it roughly square in mono fonts)
                    row.append("██")
                } else {
                    // White block
                    row.append("  ")
                }
            }
            println(row.toString())
        }
        println()
    }
}
