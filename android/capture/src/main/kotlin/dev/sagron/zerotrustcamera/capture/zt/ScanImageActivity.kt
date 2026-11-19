package dev.sagron.zerotrustcamera.capture.zt

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.IntentCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import dev.sagron.zerotrustcamera.capture.R
import java.util.EnumMap
import java.util.concurrent.Executors

/**
 * "Scan code" in the share sheet for pictures: reads a QR code or barcode from a screenshot or
 * photo, on the phone, and shows the same result sheet as the viewfinder.
 */
class ScanImageActivity : ZtBaseActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        val uri = IntentCompat.getParcelableExtra(intent, android.content.Intent.EXTRA_STREAM, Uri::class.java)
            ?.takeIf { it.scheme == "content" }
        if (uri == null) {
            finish()
            return
        }
        val ctx = applicationContext
        io.execute {
            val text = try {
                decode(ctx.contentResolver, uri)
            } catch (e: Exception) {
                Log.w(TAG, "decode", e)
                null
            }
            main.post { show(text) }
        }
    }

    private fun show(text: String?) {
        if (isFinishing || isDestroyed) return
        if (text == null) {
            Toast.makeText(this, R.string.scan_image_none, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        ScanHistory.save(applicationContext, text)
        ScanSheet.show(this, text) { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdown()
    }

    companion object {
        private const val TAG = "ScanImage"
        private const val MAX_SIDE = 2048

        fun decode(resolver: android.content.ContentResolver, uri: Uri): String? {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = MAX_SIDE.toFloat() / maxOf(info.size.width, info.size.height)
                if (scale < 1f) {
                    d.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1))
                }
            }
            return decode(bmp).also { bmp.recycle() }
        }

        fun decode(bmp: Bitmap): String? {
            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val source = RGBLuminanceSource(bmp.width, bmp.height, pixels)
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.TRY_HARDER, true)
                put(DecodeHintType.POSSIBLE_FORMATS, BarcodeFormat.entries.toList())
            }
            val reader = MultiFormatReader().apply { setHints(hints) }
            for (s in listOf(source, source.invert())) {
                try {
                    return reader.decodeWithState(BinaryBitmap(HybridBinarizer(s))).text
                } catch (e: NotFoundException) {
                    reader.reset()
                } catch (e: com.google.zxing.ReaderException) {
                    reader.reset()
                }
            }
            return null
        }
    }
}
