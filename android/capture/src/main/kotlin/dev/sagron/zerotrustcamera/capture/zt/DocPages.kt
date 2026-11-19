package dev.sagron.zerotrustcamera.capture.zt

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Straightening, filters and PDF for scanned pages. */
object DocPages {
    enum class Filter { ORIGINAL, ENHANCED, GRAYSCALE, BLACK_AND_WHITE }

    private const val MAX_SIDE = 3000

    /** The page inside [corners] (TL, TR, BR, BL; 0 to 1 of the picture), made flat and square. */
    fun straighten(src: Bitmap, corners: FloatArray): Bitmap {
        val p = FloatArray(8) { i -> corners[i] * if (i % 2 == 0) src.width else src.height }
        val top = hypot(p[2] - p[0], p[3] - p[1])
        val bottom = hypot(p[4] - p[6], p[5] - p[7])
        val left = hypot(p[6] - p[0], p[7] - p[1])
        val right = hypot(p[4] - p[2], p[5] - p[3])
        var w = max(top, bottom)
        var h = max(left, right)
        val scale = minOf(1f, MAX_SIDE / max(w, h))
        w = max(1f, w * scale)
        h = max(1f, h * scale)
        val dst = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        val m = Matrix()
        if (!m.setPolyToPoly(p, 0, dst, 0, 4)) return src.copy(Bitmap.Config.ARGB_8888, false)
        val out = Bitmap.createBitmap(w.roundToInt(), h.roundToInt(), Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    fun apply(page: Bitmap, filter: Filter): Bitmap {
        if (filter == Filter.ORIGINAL) return page
        val w = page.width
        val h = page.height
        val px = IntArray(w * h)
        page.getPixels(px, 0, w, 0, 0, w, h)
        val out = when (filter) {
            Filter.ENHANCED -> DocFilters.enhance(px)
            Filter.GRAYSCALE -> DocFilters.grayscale(px)
            Filter.BLACK_AND_WHITE -> DocFilters.blackAndWhite(px, w, h)
            Filter.ORIGINAL -> px
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    fun rotate(page: Bitmap, degrees: Int): Bitmap =
        Bitmap.createBitmap(page, 0, 0, page.width, page.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

    fun jpeg(page: Bitmap, quality: Int = 90): ByteArray =
        ByteArrayOutputStream().also { page.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    /** Gray luma of a picture scaled to about [side] pixels wide, for finding the page. */
    fun smallGray(src: Bitmap, side: Int = 320): Triple<IntArray, Int, Int> {
        val scale = side.toFloat() / max(src.width, src.height)
        val w = max(16, (src.width * scale).roundToInt())
        val h = max(16, (src.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        for (i in px.indices) px[i] = DocFilters.luma(px[i])
        return Triple(px, w, h)
    }

    /**
     * Writes the pages as one PDF, each page at A4 width (595 points) and its own height. No
     * author, app or date is written into it.
     */
    fun writePdf(pages: List<ByteArray>, out: File) {
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, jpeg ->
                val bmp = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                val pw = 595
                val ph = (595f * bmp.height / bmp.width).roundToInt().coerceAtLeast(1)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
                val m = Matrix().apply { setScale(pw.toFloat() / bmp.width, ph.toFloat() / bmp.height) }
                page.canvas.drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG))
                doc.finishPage(page)
                bmp.recycle()
            }
            FileOutputStream(out).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Copies a finished PDF into Documents/ZT Camera. Returns false if it couldn't. */
    fun saveToDocuments(ctx: Context, pdf: File, name: String): Boolean {
        val resolver = ctx.contentResolver
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/ZT Camera")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), cv) ?: return false
        return try {
            resolver.openOutputStream(uri, "w")!!.use { out -> pdf.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
