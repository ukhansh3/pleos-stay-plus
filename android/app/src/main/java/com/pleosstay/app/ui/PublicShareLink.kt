package com.pleosstay.app.ui

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.net.URI

/** Public handoff links are deployment settings, never derived from the device network. */
internal fun publicHttpsLink(value: String): URI? = runCatching {
    val uri = URI(value)
    val host = uri.host.orEmpty().trimEnd('.').lowercase()
    require(uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null)
    require(Regex("[a-z0-9.-]+[.][a-z][a-z0-9-]*").matches(host))
    require(listOf(".localhost", ".local", ".internal", ".home.arpa").none(host::endsWith))
    uri
}.getOrNull()

internal fun qrBitmap(value: String): Bitmap {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
    return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (y in 0 until 512) for (x in 0 until 512) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}
