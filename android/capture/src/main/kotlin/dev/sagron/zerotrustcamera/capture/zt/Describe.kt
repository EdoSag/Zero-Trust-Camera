package dev.sagron.zerotrustcamera.capture.zt

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * What is in a picture (a dog, a beach, a pizza...), worked out on the phone with a small
 * image classifier: MediaPipe's EfficientNet-Lite0 (int8), trained on ImageNet's 1,000 kinds of
 * things. The model and its labels ship inside the app (assets/vision, fetched at build time by
 * tool/fetch_vision_model.sh): nothing is downloaded on the phone, and pictures never leave it.
 * The Private vault uses it to find photos by what is in them.
 */
object Describe {
    private const val TAG = "Describe"
    private const val MODEL = "vision/efficientnet_lite0.tflite"
    private const val LABELS = "vision/labels.txt"
    private const val SIZE = 224

    /** A label is kept from this share of the model's confidence. */
    private const val KEEP = 0.2f

    /** At most this many labels for one picture. */
    private const val MAX = 3

    private val lock = Any()
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    /** What each 0-255 colour value becomes in the model's input (see [ModelInput]). */
    private var table: ByteArray? = null
    private var floats = false

    fun available(ctx: Context): Boolean =
        ctx.assets.list("vision").orEmpty().contains("efficientnet_lite0.tflite")

    private fun load(ctx: Context): Interpreter? {
        interpreter?.let { return it }
        if (!available(ctx)) return null
        val bytes = ctx.assets.open(MODEL).use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        model.put(bytes).rewind()
        labels = ctx.assets.open(LABELS).bufferedReader().readLines()
        val loaded = Interpreter(model, Interpreter.Options().setNumThreads(2))
        // Fill the input the way the model was trained: each colour normalized, then
        // quantized with the input's own scale and zero point (read from the model file).
        val input = loaded.getInputTensor(0)
        floats = input.dataType() == DataType.FLOAT32
        if (!floats) {
            val q = input.quantizationParams()
            table = ModelInput.table(q.scale, q.zeroPoint)
        }
        return loaded.also { interpreter = it }
    }

    /**
     * The English labels for what is in [bitmap], most likely first; empty when nothing is
     * clear enough, null when the model is not in the app. Slow: never on the main thread.
     */
    fun labels(ctx: Context, bitmap: Bitmap): List<String>? = synchronized(lock) {
        try {
            val model = load(ctx) ?: return null
            val input = pixels(bitmap)
            val output = Array(1) { ByteArray(labels.size) }
            model.run(input, output)
            // The output is quantized: confidence = byte / 256.
            val scores = output[0].map { (it.toInt() and 0xff) / 256f }
            scores.indices
                .filter { scores[it] >= KEEP }
                .sortedByDescending { scores[it] }
                .take(MAX)
                .map { labels[it] }
        } catch (e: Exception) {
            Log.w(TAG, "labels", e)
            null
        }
    }

    /** The middle square of [bitmap] at 224 by 224, as the RGB input the model takes. */
    private fun pixels(bitmap: Bitmap): ByteBuffer {
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(
            bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side,
        )
        val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        val argb = IntArray(SIZE * SIZE)
        scaled.getPixels(argb, 0, SIZE, 0, 0, SIZE, SIZE)
        if (scaled !== square) scaled.recycle()
        if (square !== bitmap) square.recycle()
        val perValue = if (floats) 4 else 1
        val buffer = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * perValue)
            .order(ByteOrder.nativeOrder())
        val map = table
        for (p in argb) {
            for (shift in intArrayOf(16, 8, 0)) {
                val v = p shr shift and 0xff
                when {
                    floats -> buffer.putFloat(ModelInput.normalize(v))
                    map != null -> buffer.put(map[v])
                    else -> buffer.put(v.toByte())
                }
            }
        }
        buffer.rewind()
        return buffer
    }
}

/**
 * How a colour value (0-255) becomes the picture model's input. EfficientNet-Lite was trained
 * on colours normalized as (value - 127) / 128; its int8 file then stores that number
 * quantized as round(normalized / scale) + zeroPoint. Without this, black and white reach the
 * model as 0 and 255 instead of about 52 and 211, and the labels come out worse.
 */
object ModelInput {
    private const val MEAN = 127f
    private const val STD = 128f

    @JvmStatic
    fun normalize(value: Int): Float = (value - MEAN) / STD

    /** For each colour value 0-255, the quantized byte the model takes (unsigned 0-255). */
    @JvmStatic
    fun table(scale: Float, zeroPoint: Int): ByteArray = ByteArray(256) { v ->
        if (scale == 0f) {
            v.toByte() // Not quantized: nothing to map.
        } else {
            (Math.round(normalize(v) / scale) + zeroPoint).coerceIn(0, 255).toByte()
        }
    }
}
