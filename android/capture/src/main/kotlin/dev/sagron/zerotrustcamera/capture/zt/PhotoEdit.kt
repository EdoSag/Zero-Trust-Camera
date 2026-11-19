package dev.sagron.zerotrustcamera.capture.zt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.FaceDetector
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The photo editor's steps. Each one works in 0 to 1 units of the picture it is applied to, so
 * the same list draws the small preview and the full-size photo alike, and undo is dropping the
 * last step.
 */
sealed class EditOp {
    /** Quarter turns clockwise. */
    data class Rotate(val quarters: Int) : EditOp()

    data class Crop(val rect: RectF) : EditOp()

    data class Adjust(val brightness: Float, val contrast: Float, val saturation: Float, val warmth: Float) : EditOp()

    /** Areas covered with a coarse mosaic (faces, plates, screens, papers). */
    data class Hide(val rects: List<RectF>) : EditOp()

    /** Freehand lines: points are x, y pairs; width is a share of the picture's shorter side. */
    data class Draw(val strokes: List<Stroke>) : EditOp()

    data class Stroke(val points: FloatArray, val color: Int, val width: Float)
}

object PhotoEdit {
    /** Draws [ops] over [base]. [base] is never changed or recycled. */
    fun render(base: Bitmap, ops: List<EditOp>): Bitmap {
        var cur = base
        for (op in ops) {
            val next = apply(cur, op, owned = cur !== base)
            if (cur !== base && cur !== next) cur.recycle()
            cur = next
        }
        return if (cur === base) base.copy(Bitmap.Config.ARGB_8888, true) else cur
    }

    private fun apply(src: Bitmap, op: EditOp, owned: Boolean): Bitmap = when (op) {
        is EditOp.Rotate -> {
            val q = ((op.quarters % 4) + 4) % 4
            if (q == 0) src else {
                val m = Matrix().apply { postRotate(90f * q) }
                Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            }
        }
        is EditOp.Crop -> {
            val l = (op.rect.left * src.width).roundToInt().coerceIn(0, src.width - 1)
            val t = (op.rect.top * src.height).roundToInt().coerceIn(0, src.height - 1)
            val r = (op.rect.right * src.width).roundToInt().coerceIn(l + 1, src.width)
            val b = (op.rect.bottom * src.height).roundToInt().coerceIn(t + 1, src.height)
            Bitmap.createBitmap(src, l, t, r - l, b - t)
        }
        is EditOp.Adjust -> {
            if (PhotoOps.isIdentity(op.brightness, op.contrast, op.saturation, op.warmth)) src else {
                val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = filter(op) }
                Canvas(out).drawBitmap(src, 0f, 0f, paint)
                out
            }
        }
        is EditOp.Hide -> {
            val out = mutable(src, owned)
            for (r in op.rects) {
                val x0 = (r.left * out.width).toInt()
                val y0 = (r.top * out.height).toInt()
                val x1 = (r.right * out.width).roundToInt()
                val y1 = (r.bottom * out.height).roundToInt()
                val w = x1 - x0
                val h = y1 - y0
                if (w <= 0 || h <= 0) continue
                val px = IntArray(w * h)
                out.getPixels(px, 0, w, x0, y0, w, h)
                PhotoOps.mosaic(px, w, h, 0, 0, w, h)
                out.setPixels(px, 0, w, x0, y0, w, h)
            }
            out
        }
        is EditOp.Draw -> {
            val out = mutable(src, owned)
            val canvas = Canvas(out)
            for (s in op.strokes) drawStroke(canvas, s, out.width.toFloat(), out.height.toFloat())
            out
        }
    }

    fun filter(op: EditOp.Adjust) = ColorMatrixColorFilter(
        ColorMatrix(PhotoOps.adjustMatrix(op.brightness, op.contrast, op.saturation, op.warmth))
    )

    fun drawStroke(canvas: Canvas, s: EditOp.Stroke, w: Float, h: Float, offsetX: Float = 0f, offsetY: Float = 0f) {
        val pts = s.points
        if (pts.size < 2) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = s.color
            strokeWidth = max(1f, s.width * min(w, h))
        }
        if (pts.size == 2) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(offsetX + pts[0] * w, offsetY + pts[1] * h, paint.strokeWidth / 2, paint)
            return
        }
        val path = Path()
        path.moveTo(offsetX + pts[0] * w, offsetY + pts[1] * h)
        var i = 2
        while (i + 1 < pts.size) {
            path.lineTo(offsetX + pts[i] * w, offsetY + pts[i + 1] * h)
            i += 2
        }
        canvas.drawPath(path, paint)
    }

    /** [src] itself when it is a working copy that may be drawn on, otherwise a fresh copy. */
    private fun mutable(src: Bitmap, owned: Boolean): Bitmap =
        if (owned && src.isMutable && src.config == Bitmap.Config.ARGB_8888) src
        else src.copy(Bitmap.Config.ARGB_8888, true)

    /** Scales [src] so its longer side is at most [maxSide]; returns [src] when it already fits. */
    fun fit(src: Bitmap, maxSide: Int): Bitmap {
        val long = max(src.width, src.height)
        if (long <= maxSide) return src
        val s = maxSide.toFloat() / long
        return Bitmap.createScaledBitmap(src, (src.width * s).roundToInt().coerceAtLeast(1),
            (src.height * s).roundToInt().coerceAtLeast(1), true)
    }

    /**
     * Faces looking roughly at the camera, found on the phone with Android's own detector (no
     * download, no Google Play services). Boxes are 0 to 1 of [photo]. Faces in profile or very
     * small ones can be missed, so the editor also lets areas be marked by hand.
     */
    fun findFaces(photo: Bitmap): List<RectF> {
        // The detector wants RGB_565 with an even width; a smaller copy is plenty and quick.
        val scaled = fit(photo, 1024)
        val w = scaled.width and 1.inv()
        val h = scaled.height
        if (w < 2 || h < 2) return emptyList()
        val small = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        Canvas(small).drawBitmap(scaled, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        if (scaled !== photo) scaled.recycle()
        val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
        val found = try {
            FaceDetector(w, h, MAX_FACES).findFaces(small, faces)
        } catch (_: Exception) {
            0
        } finally {
            small.recycle()
        }
        val mid = android.graphics.PointF()
        return faces.take(found).mapNotNull { f ->
            if (f == null || f.confidence() < 0.3f) return@mapNotNull null
            f.getMidPoint(mid)
            PhotoOps.faceBox(mid.x, mid.y, f.eyesDistance(), w, h)?.let { RectF(it[0], it[1], it[2], it[3]) }
        }
    }

    private const val MAX_FACES = 16
}
