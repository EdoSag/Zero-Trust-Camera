package dev.sagron.zerotrustcamera.capture.zt

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Draws a page's four corners over a picture shown in [imageRect], and, when [editable], lets the
 * corners be dragged. Corners are TL, TR, BR, BL, each 0 to 1 of the picture.
 */
class DocQuadView(context: Context) : View(context) {
    var imageRect = RectF()
        set(value) {
            field = value
            invalidate()
        }

    var corners: FloatArray? = null
        set(value) {
            field = value
            invalidate()
        }

    var editable = false

    private val density = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        color = Color.rgb(0x4F, 0xC3, 0xF7)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(0x40, 0x4F, 0xC3, 0xF7)
    }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val path = Path()
    private var dragging = -1

    private fun x(i: Int) = imageRect.left + corners!![2 * i] * imageRect.width()
    private fun y(i: Int) = imageRect.top + corners!![2 * i + 1] * imageRect.height()

    override fun onDraw(canvas: Canvas) {
        if (corners == null || imageRect.isEmpty) return
        path.reset()
        path.moveTo(x(0), y(0))
        for (i in 1..3) path.lineTo(x(i), y(i))
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, line)
        if (editable) {
            for (i in 0..3) {
                canvas.drawCircle(x(i), y(i), 12 * density, handle)
                canvas.drawCircle(x(i), y(i), 12 * density, line)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val c = corners
        if (!editable || c == null || imageRect.isEmpty) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = (0..3).minByOrNull { hypot(event.x - x(it), event.y - y(it)) } ?: -1
                if (dragging >= 0 && hypot(event.x - x(dragging), event.y - y(dragging)) > 48 * density) dragging = -1
                return dragging >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging < 0) return false
                c[2 * dragging] = ((event.x - imageRect.left) / imageRect.width()).coerceIn(0f, 1f)
                c[2 * dragging + 1] = ((event.y - imageRect.top) / imageRect.height()).coerceIn(0f, 1f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = -1
                return true
            }
        }
        return false
    }
}
