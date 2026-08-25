package app.gridfix.android.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** A `geo:` URI any phone's camera app can scan straight into its map app. */
fun geoUri(lat: Double, lon: Double, label: String): String {
    val ll = String.format(java.util.Locale.US, "%.6f,%.6f", lat, lon)
    val name = Uri.encode(label.ifBlank { "Waypoint" })
    return "geo:$ll?q=$ll($name)"
}

private fun qrBitmap(payload: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] =
                if (matrix.get(x, y)) android.graphics.Color.BLACK
                else android.graphics.Color.WHITE
        }
    }
    Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}.getOrNull()

/**
 * Position/waypoint hand-off as a QR code. The payload is a geo: URI, so any
 * camera app on any phone can scan it into its own map — no app needed on the
 * receiving end. The MGRS caption is there to read out or copy by hand.
 */
@Composable
fun QrDialog(
    title: String,
    payload: String,
    caption: String,
    onDismiss: () -> Unit,
) {
    val bmp = remember(payload) { qrBitmap(payload, 512) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (bmp != null) {
                    // QR codes need dark-on-light to scan; keep the white card
                    // even in night mode — it is on screen only while shown.
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Position QR code",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color.White)
                            .padding(8.dp),
                    )
                } else {
                    Text("Could not build the QR code.")
                }
                Text(
                    caption,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    "Scannable with any phone camera — opens in their map app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
