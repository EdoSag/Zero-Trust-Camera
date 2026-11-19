package dev.sagron.zerotrustcamera.capture.zt

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/**
 * Shows the photo being edited, fitted to the screen, and handles the touch tools: dragging the
 * crop frame, marking areas to hide (a tap on a marked area removes it), and drawing.
 */
class PhotoEditView(context: Context) : View(context) {
    enum class Mode { VIEW, CROP, HIDE, DRAW }

    var bitmap: Bitmap? = null
        set(value) {
            field = value
            layoutImage()
            invalidate()
        }

    var mode = Mode.VIEW
        set(value) {
            field = value
            invalidate()
        }

    /** Live preview of the Adjust sliders. */
    var preview: ColorFilter? = null
        set(value) {
            field = value
            photoPaint.colorFilter = value
            invalidate()
        }

    val crop = RectF(0f, 0f, 1f, 1f)
    val hidden = ArrayList<RectF>()
    val strokes = ArrayList<EditOp.Stroke>()
    var drawColor = Color.RED
    var drawWidth = 0.01f

    /** Called after any change made by touch. */
    var onChange: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val image = RectF()
    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = Color.WHITE
    }
    private val shade = Paint().apply { color = Color.argb(0xA0, 0, 0, 0) }
    private val hideFill = Paint().apply { color = Color.argb(0xB0, 0x30, 0x30, 0x30) }
    private val hideLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = Color.rgb(0x4F, 0xC3, 0xF7)
    }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    // Touch state.
    private var grab = NONE
    private var startX = 0f
    private var startY = 0f
    private val startCrop = RectF()
    private var newHide: RectF? = null
    private var points: ArrayList<Float>? = null

    fun resetTools() {
        crop.set(0f, 0f, 1f, 1f)
        hidden.clear()
        strokes.clear()
        newHide = null
        points = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutImage()
    }

    private fun layoutImage() {
        val b = bitmap
        val pad = 24 * density
        if (b == null || width == 0 || height == 0) {
            image.setEmpty()
            return
        }
        val aw = width - 2 * pad
        val ah = height - 2 * pad
        val s = min(aw / b.width, ah / b.height)
        val w = b.width * s
        val h = b.height * s
        image.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
    }

    private fun sx(nx: Float) = image.left + nx * image.width()
    private fun sy(ny: Float) = image.top + ny * image.height()
    private fun nx(x: Float) = ((x - image.left) / image.width()).coerceIn(0f, 1f)
    private fun ny(y: Float) = ((y - image.top) / image.height()).coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        if (image.isEmpty) return
        canvas.drawBitmap(b, null, image, photoPaint)
        for (r in hidden) drawHide(canvas, r)
        newHide?.let { drawHide(canvas, it) }
        for (s in strokes) PhotoEdit.drawStroke(canvas, s, image.width(), image.height(), image.left, image.top)
        points?.let {
            PhotoEdit.drawStroke(canvas, EditOp.Stroke(it.toFloatArray(), drawColor, drawWidth),
                image.width(), image.height(), image.left, image.top)
        }
        if (mode == Mode.CROP) {
            val l = sx(crop.left)
            val t = sy(crop.top)
            val r = sx(crop.right)
            val bt = sy(crop.bottom)
            canvas.drawRect(image.left, image.top, image.right, t, shade)
            canvas.drawRect(image.left, bt, image.right, image.bottom, shade)
            canvas.drawRect(image.left, t, l, bt, shade)
            canvas.drawRect(r, t, image.right, bt, shade)
            canvas.drawRect(l, t, r, bt, frame)
            for ((x, y) in listOf(l to t, r to t, r to bt, l to bt)) canvas.drawCircle(x, y, 9 * density, handle)
        }
    }

    private fun drawHide(canvas: Canvas, r: RectF) {
        val s = RectF(sx(r.left), sy(r.top), sx(r.right), sy(r.bottom))
        canvas.drawRect(s, hideFill)
        canvas.drawRect(s, hideLine)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (bitmap == null || image.isEmpty) return false
        return when (mode) {
            Mode.CROP -> cropTouch(e)
            Mode.HIDE -> hideTouch(e)
            Mode.DRAW -> drawTouch(e)
            Mode.VIEW -> false
        }
    }

    private fun cropTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = e.x
                startY = e.y
                startCrop.set(crop)
                val slop = 32 * density
                val nearL = abs(e.x - sx(crop.left)) < slop
                val nearR = abs(e.x - sx(crop.right)) < slop
                val nearT = abs(e.y - sy(crop.top)) < slop
                val nearB = abs(e.y - sy(crop.bottom)) < slop
                grab = (if (nearL) LEFT else 0) or (if (nearR && !nearL) RIGHT else 0) or
                    (if (nearT) TOP else 0) or (if (nearB && !nearT) BOTTOM else 0)
                if (grab == 0 && e.x > sx(crop.left) && e.x < sx(crop.right) && e.y > sy(crop.top) && e.y < sy(crop.bottom)) {
                    grab = MOVE
                }
                return grab != NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (grab == NONE) return false
                val dx = (e.x - startX) / image.width()
                val dy = (e.y - startY) / image.height()
                val c = RectF(startCrop)
                if (grab == MOVE) {
                    val mx = dx.coerceIn(-c.left, 1f - c.right)
                    val my = dy.coerceIn(-c.top, 1f - c.bottom)
                    c.offset(mx, my)
                } else {
                    if (grab and LEFT != 0) c.left = (c.left + dx).coerceIn(0f, c.right - MIN_CROP)
                    if (grab and RIGHT != 0) c.right = (c.right + dx).coerceIn(c.left + MIN_CROP, 1f)
                    if (grab and TOP != 0) c.top = (c.top + dy).coerceIn(0f, c.bottom - MIN_CROP)
                    if (grab and BOTTOM != 0) c.bottom = (c.bottom + dy).coerceIn(c.top + MIN_CROP, 1f)
                }
                crop.set(c)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (grab != NONE) onChange?.invoke()
                grab = NONE
                return true
            }
        }
        return false
    }

    private fun hideTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = e.x
                startY = e.y
                newHide = null
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                newHide = PhotoOps.normalizedRect(nx(startX), ny(startY), nx(e.x), ny(e.y), 0f)
                    ?.let { RectF(it[0], it[1], it[2], it[3]) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val r = PhotoOps.normalizedRect(nx(startX), ny(startY), nx(e.x), ny(e.y), MIN_HIDE)
                newHide = null
                if (r != null) {
                    hidden.add(RectF(r[0], r[1], r[2], r[3]))
                } else {
                    // A tap: take away the marked area under the finger, if any.
                    val x = nx(e.x)
                    val y = ny(e.y)
                    hidden.lastOrNull { it.contains(x, y) }?.let { hidden.remove(it) }
                }
                invalidate()
                onChange?.invoke()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                newHide = null
                invalidate()
                return true
            }
        }
        return false
    }

    private fun drawTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                points = arrayListOf(nx(e.x), ny(e.y))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val p = points ?: return false
                for (i in 0 until e.historySize) {
                    p.add(nx(e.getHistoricalX(i)))
                    p.add(ny(e.getHistoricalY(i)))
                }
                p.add(nx(e.x))
                p.add(ny(e.y))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                points?.let { strokes.add(EditOp.Stroke(it.toFloatArray(), drawColor, drawWidth)) }
                points = null
                invalidate()
                onChange?.invoke()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                points = null
                invalidate()
                return true
            }
        }
        return false
    }

    private companion object {
        const val NONE = 0
        const val LEFT = 1
        const val RIGHT = 2
        const val TOP = 4
        const val BOTTOM = 8
        const val MOVE = 16
        const val MIN_CROP = 0.05f
        const val MIN_HIDE = 0.02f
    }
}
