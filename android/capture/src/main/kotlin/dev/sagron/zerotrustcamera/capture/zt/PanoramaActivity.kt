package dev.sagron.zerotrustcamera.capture.zt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import dev.sagron.zerotrustcamera.capture.CamConfig
import dev.sagron.zerotrustcamera.capture.R
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil

/**
 * Panorama (beta): hold the phone upright, press Start and turn slowly to one side. A frame is
 * taken every few degrees (the phone's motion sensor says how far it has turned) and laid next
 * to the last by [PanoStitch], all on the phone. The result is encoded here, so it carries no
 * place, time or phone details; in Private mode it goes into the vault.
 */
@OptIn(ExperimentalCamera2Interop::class)
class PanoramaActivity : ZtBaseActivity(), SensorEventListener {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var preview: PreviewView
    private lateinit var hint: TextView
    private lateinit var button: MaterialButton
    private lateinit var bar: ProgressBar
    private lateinit var busy: ProgressBar

    private var provider: ProcessCameraProvider? = null
    private var sensors: SensorManager? = null

    // Field of view across the frame as the phone is held (upright), in degrees.
    private var fovAcross = 55.0

    // How far the phone has turned since Start, in radians: to the right is positive.
    @Volatile private var turned = 0.0
    private var lastAzimuth = Double.NaN
    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private val angles = FloatArray(3)

    @Volatile private var sweeping = false
    @Volatile private var saving = false

    // Only touched on [worker].
    private var canvas: PanoStitch.Canvas? = null
    private var previous: IntArray? = null
    private var frameW = 0
    private var focal = 0.0
    private var lastTurn = 0.0
    private var direction = 0
    private var sensorSide = 0
    private var firstFrame: IntArray? = null
    private var widest = 0
    private var x = 0
    private var y = 0
    private var frames = 0

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) bind() else {
            Toast.makeText(this, R.string.doc_camera_needed, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (turnSensor() == null) {
            Toast.makeText(this, R.string.pano_no_sensor, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bind()
        } else {
            askCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun turnSensor(): Sensor? =
        sensors?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    override fun onResume() {
        super.onResume()
        turnSensor()?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensors?.unregisterListener(this)
        // Leaving in the middle of a sweep drops it.
        if (sweeping && !saving) {
            sweeping = false
            worker.execute { reset() }
            showIdle()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        provider?.unbindAll()
        // [worker] belongs to the app: frames still on their way must have somewhere to go.
        worker.execute { reset() }
    }

    // ---- turning -------------------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        // The phone is upright, so "which way it faces" is read with its long side as up.
        SensorManager.remapCoordinateSystem(rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
        SensorManager.getOrientation(remapped, angles)
        val azimuth = angles[0].toDouble()
        if (!lastAzimuth.isNaN()) {
            var d = azimuth - lastAzimuth
            if (d > PI) d -= 2 * PI
            if (d < -PI) d += 2 * PI
            turned += d
        }
        lastAzimuth = azimuth
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---- layout --------------------------------------------------------------------------------

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
        root.addView(preview, FrameLayout.LayoutParams(MATCH, MATCH))

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(32), dp(8), dp(4))
            setBackgroundColor(Color.argb(0x80, 0, 0, 0))
        }
        top.addView(
            MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                text = getString(android.R.string.cancel)
                setTextColor(Color.WHITE)
                setOnClickListener { finish() }
            }
        )
        hint = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(8), 0, dp(8), 0)
            text = getString(R.string.pano_hint_start)
        }
        top.addView(hint, LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(top, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
            setBackgroundColor(Color.argb(0x80, 0, 0, 0))
        }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            visibility = View.INVISIBLE
        }
        bottom.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        button = MaterialButton(this).apply {
            text = getString(R.string.pano_start)
            isEnabled = false
            setOnClickListener { if (sweeping) finishSweep() else startSweep() }
        }
        bottom.addView(button, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        root.addView(bottom, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))

        busy = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(busy, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        setContentView(root)
    }

    private fun showIdle() {
        hint.text = getString(R.string.pano_hint_start)
        button.text = getString(R.string.pano_start)
        button.isEnabled = true
        bar.visibility = View.INVISIBLE
        busy.visibility = View.GONE
    }

    // ---- camera --------------------------------------------------------------------------------

    private fun bind() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isDestroyed) return@addListener
            try {
                val p = future.get()
                provider = p
                val use = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1600, 1200),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                                )
                            )
                            .build()
                    )
                    .build()
                analysis.setAnalyzer(worker) { image -> onFrame(image) }
                p.unbindAll()
                val camera = p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, use, analysis)
                readFieldOfView(camera.cameraInfo)
                button.isEnabled = true
            } catch (e: Exception) {
                Log.e(TAG, "bind", e)
                Toast.makeText(this, R.string.pano_failed, Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** The angle the frame covers from side to side with the phone upright. */
    private fun readFieldOfView(info: androidx.camera.core.CameraInfo) {
        try {
            val c2 = Camera2CameraInfo.from(info)
            val size = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val lens = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val turnedSensor = (c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90) % 180 != 0
            if (size != null && lens != null && lens.isNotEmpty() && lens[0] > 0f) {
                // Upright, the frame's width is the sensor's short side on nearly every phone.
                val across = if (turnedSensor) size.height else size.width
                val fov = Math.toDegrees(2.0 * atan(across / (2.0 * lens[0])))
                if (fov in 20.0..120.0) fovAcross = fov
            }
        } catch (e: Exception) {
            Log.w(TAG, "field of view", e)
        }
    }

    // ---- the sweep -----------------------------------------------------------------------------

    private fun startSweep() {
        turned = 0.0
        worker.execute { reset() }
        sweeping = true
        hint.text = getString(R.string.pano_hint_sweep)
        button.text = getString(R.string.pano_done)
        bar.progress = 0
        bar.visibility = View.VISIBLE
    }

    /** On [worker]. */
    private fun reset() {
        canvas?.clear()
        canvas = null
        previous = null
        frames = 0
        direction = 0
        sensorSide = 0
        firstFrame = null
        lastTurn = 0.0
        x = 0
        y = 0
    }

    /** On [worker], for every camera frame. */
    private fun onFrame(image: ImageProxy) {
        try {
            if (!sweeping || saving) return
            val turn = turned
            if (frames > 0) {
                val moved = turn - lastTurn
                // The first move decides the side; after that only that side counts.
                if (sensorSide == 0) {
                    if (abs(moved) < stepRadians()) return
                    sensorSide = if (moved > 0) 1 else -1
                } else if (moved * sensorSide < stepRadians()) return
            }
            val frame = upright(image)
            val w = frame.width
            val h = frame.height
            val px = IntArray(w * h)
            frame.getPixels(px, 0, w, 0, 0, w, h)
            frame.recycle()

            if (frames == 0) {
                focal = PanoStitch.focalPx(w, fovAcross)
                frameW = PanoStitch.warpedWidth(w, focal)
                widest = minOf(MAX_WIDTH, ceil(2 * PI * focal).toInt() + frameW)
                canvas = PanoStitch.Canvas(h, DRIFT, widest)
                firstFrame = px
            }
            val guess = (focal * abs(turn - lastTurn)).toInt().coerceIn(1, frameW - 1)
            val rangeX = guess / 2 + 16
            if (frames == 1) {
                // Which side the phone is turning to is read from the pictures themselves: the
                // second frame fits either to the right of the first or to its left. A sweep
                // to the left is stitched turned over, and turned back at the end.
                val second = PanoStitch.warp(px, w, h, focal)
                val toRight = PanoStitch.align(previous!!, second, frameW, h, guess, rangeX, DRIFT / 2)
                val toLeft = PanoStitch.align(second, previous!!, frameW, h, guess, rangeX, DRIFT / 2)
                if (toLeft[2] < toRight[2]) {
                    direction = -1
                    val first = firstFrame!!
                    PanoStitch.mirror(first, w, h)
                    val firstBent = PanoStitch.warp(first, w, h, focal)
                    canvas?.clear()
                    canvas = PanoStitch.Canvas(h, DRIFT, widest).also { it.add(firstBent, frameW, h, 0, 0) }
                    previous = firstBent
                } else {
                    direction = 1
                }
                firstFrame = null
            }
            if (direction < 0) PanoStitch.mirror(px, w, h)
            val bent = PanoStitch.warp(px, w, h, focal)
            val c = canvas ?: return
            if (frames == 0) {
                c.add(bent, frameW, h, 0, 0)
            } else {
                val found = PanoStitch.align(previous!!, bent, frameW, h, guess, rangeX, DRIFT / 2)
                // Nothing to compare (a blank wall, the dark): trust how far the phone turned.
                val good = found[2] <= MAX_DIFFERENCE
                x += if (good) found[0] else guess
                y += if (good) found[1] else 0
                c.add(bent, frameW, h, x, y)
            }
            previous = bent
            lastTurn = turn
            frames++
            val full = c.full(frameW) || abs(turn) >= 2 * PI * 0.98
            val shown = (c.width() * 1000L / maxOf(1, (2 * PI * focal).toInt() + frameW)).toInt()
            main.post {
                if (isDestroyed) return@post
                bar.progress = shown
                if (full && sweeping && !saving) finishSweep()
            }
        } catch (e: Exception) {
            Log.e(TAG, "frame", e)
        } finally {
            image.close()
        }
    }

    private fun stepRadians() = Math.toRadians(fovAcross) * STEP_OF_VIEW

    /** The frame the right way up, scaled to [FRAME_HEIGHT] high. */
    private fun upright(image: ImageProxy): Bitmap {
        val raw = image.toBitmap()
        val turn = image.imageInfo.rotationDegrees
        val tall = if (turn % 180 == 0) raw.height else raw.width
        val scale = FRAME_HEIGHT.toFloat() / tall
        val m = Matrix().apply {
            postRotate(turn.toFloat())
            postScale(scale, scale)
        }
        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (out !== raw) raw.recycle()
        return if (out.config == Bitmap.Config.ARGB_8888) out
        else out.copy(Bitmap.Config.ARGB_8888, false).also { out.recycle() }
    }

    private fun finishSweep() {
        if (saving) return
        saving = true
        sweeping = false
        button.isEnabled = false
        bar.visibility = View.INVISIBLE
        busy.visibility = View.VISIBLE
        val ctx = applicationContext
        worker.execute {
            val c = canvas
            if (c == null || frames < 2) {
                reset()
                main.post {
                    saving = false
                    if (isDestroyed) return@post
                    showIdle()
                    Toast.makeText(this, R.string.pano_too_short, Toast.LENGTH_LONG).show()
                }
                return@execute
            }
            var vault = false
            val ok = try {
                val w = c.width()
                val h = c.resultHeight()
                val px = c.result()
                val mirrored = direction < 0
                reset()
                if (mirrored) PanoStitch.mirror(px, w, h)
                val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
                val jpeg = ByteArrayOutputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    bmp.recycle()
                    out.toByteArray()
                }
                val pub = if (CamConfig.isPrivateVaultOn(ctx) && VaultKeys.isSetUp(ctx)) VaultKeys.publicKey(ctx) else null
                try {
                    if (pub != null) {
                        vault = true
                        VaultStore.savePhoto(ctx, pub, jpeg, System.currentTimeMillis())
                    } else {
                        VaultTransfer.saveJpegToGallery(ctx, jpeg)
                    }
                } finally {
                    jpeg.fill(0)
                }
                true
            } catch (e: Throwable) {
                // Out of memory included: the sweep is dropped, the app stays up.
                Log.e(TAG, "save", e)
                reset()
                false
            }
            main.post {
                saving = false
                if (isDestroyed) return@post
                showIdle()
                Toast.makeText(this, when {
                    !ok -> R.string.pano_failed
                    vault -> R.string.pro_saved_vault
                    else -> R.string.pro_saved
                }, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "Panorama"

        /** Stitches the frames and saves the result. One for the app, never shut down. */
        private val worker: ExecutorService = Executors.newSingleThreadExecutor()
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val JPEG_QUALITY = 92

        /** Every frame is brought to this height before stitching. */
        private const val FRAME_HEIGHT = 1200

        /** How far a frame may sit above or below the first, in pixels. */
        private const val DRIFT = 80

        /** The widest a panorama may get, whatever the lens. */
        private const val MAX_WIDTH = 12000

        /** A new frame every this much of the view: two thirds of it overlaps the last. */
        private const val STEP_OF_VIEW = 0.33

        /** Frames that differ more than this (0 to 255) where they overlap weren't matched. */
        private const val MAX_DIFFERENCE = 28

        fun open(ctx: Context) {
            ctx.startActivity(Intent(ctx, PanoramaActivity::class.java))
        }
    }
}
