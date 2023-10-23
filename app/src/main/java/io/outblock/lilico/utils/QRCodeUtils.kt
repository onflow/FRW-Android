package io.outblock.lilico.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.outblock.lilico.R


fun String.toQRBitmap(
    width: Int = 500,
    height: Int = 500,
    foregroundColor: Int = Color.BLACK,
    backgroundColor: Int = Color.WHITE,
): Bitmap? {
    val result = try {
        MultiFormatWriter().encode(this, BarcodeFormat.QR_CODE, width, height, mutableMapOf(
            EncodeHintType.CHARACTER_SET to "utf-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 2
        ))
    } catch (e: IllegalArgumentException) {
        loge(e)
        return null
    }
    val w = result.width
    val h = result.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val offset = y * w
        for (x in 0 until w) {
            pixels[offset + x] = if (result[x, y]) foregroundColor else backgroundColor
        }
    }
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, w, h)
    val logo = BitmapFactory.decodeResource(Env.getApp().resources, R.drawable.ic_launcher_fill)
    return addLogoToQRCode(bitmap, logo)
}

private fun addLogoToQRCode(qrCode: Bitmap, logo: Bitmap?): Bitmap {
    if (logo == null) {
        return qrCode
    }
    val qrCodeWidth = qrCode.width
    val qrCodeHeight = qrCode.height
    val logoWidth = logo.width
    val logoHeight = logo.height

    val scaleFactor = qrCodeWidth * 1.0f / 5 / logoWidth
    val combined = Bitmap.createBitmap(qrCodeWidth, qrCodeHeight, qrCode.config)
    val canvas = Canvas(combined)
    canvas.drawBitmap(qrCode, 0f, 0f, null)
    canvas.scale(scaleFactor, scaleFactor, qrCodeWidth / 2f, qrCodeHeight / 2f)
    val xPos = (qrCodeWidth - logoWidth) / 2f
    val yPos = (qrCodeHeight - logoHeight) / 2f
    canvas.drawBitmap(logo, xPos, yPos, null)
    canvas.save()
    canvas.restore()
    return combined
}