package dev.sagron.zerotrustcamera.capture.zt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
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
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import dev.sagron.zerotrustcamera.capture.CamConfig
import dev.sagron.zerotrustcamera.capture.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The pro camera: ISO, shutter speed, manual focus and white balance by hand, and RAW (DNG) next
 * to the JPEG where the camera can do it. Photos are re-encoded here, so the JPEG carries no
 * place, time or phone details. In Private mode both go into the vault.
 */
@OptIn(ExperimentalCamera2Interop::class)
class ProCameraActivity : ZtBaseActivity() {
    private enum class Dial { ISO, SHUTTER, FOCUS, WB }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var preview: PreviewView
    private lateinit var readout: TextView
    private lateinit var dials: LinearLayout
    private lateinit var tuner: LinearLayout
    private lateinit var shutterButton: MaterialButton
    private lateinit var progress: ProgressBar
    private var rawChip: MaterialButton? = null

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    // What the camera can do.
    private var manualSensor = false
    private var rawSupported = false
    private var isoSteps = intArrayOf(100)
    private var shutterSteps = longArrayOf(1_000_000_000L / 60)
    private var minFocus = 0f

    // What is picked. Null means automatic.
    private var iso: Int? = null
    private var shutter: Long? = null
    private var focus: Float? = null
    private var wb = CaptureRequest.CONTROL_AWB_MODE_AUTO
    private var raw = false
    private var open: Dial? = null

    // What the camera is doing now, from the preview frames.
    @Volatile private var liveIso = 0
    @Volatile private var liveShutter = 0L
    private var lastReadout = 0L

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) bind() else {
            Toast.makeText(this, R.string.doc_camera_needed, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A RAW shot cut short by the app being killed leaves temp files; they are wiped here.
        val ctx = applicationContext
        saver.execute { VaultStore.cleanUp(ctx) }
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bind()
        } else {
            askCamera.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        provider?.unbindAll()
        // [saver] is not shut down: a shot still being saved must finish and wipe its temp files
        // even after this screen is closed.
    }

    // ---- layout ----------------------------------------------------------------------------------

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
        root.addView(preview, FrameLayout.LayoutParams(MATCH, MATCH))

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(32), dp(8), dp(4))
            setBackgroundColor(Color.argb(0x80, 0, 0, 0))
        }
        top.addView(textButton(getString(android.R.string.cancel)) { finish() })
        readout = TextView(this).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 14f
        }
        top.addView(readout, LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(top, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
        // The RAW button is added to [top] once the camera says it can do RAW.

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(0xCC, 0, 0, 0))
            setPadding(dp(12), dp(8), dp(12), dp(24))
        }
        tuner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bottom.addView(tuner, LinearLayout.LayoutParams(MATCH, WRAP))
        dials = LinearLayout(this)
        bottom.addView(dials, LinearLayout.LayoutParams(MATCH, WRAP))
        shutterButton = MaterialButton(this).apply {
            setText(R.string.pro_take)
            isEnabled = false
            setOnClickListener { take() }
        }
        bottom.addView(shutterButton, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        root.addView(bottom, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))

        progress = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(progress, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        setContentView(root)
        rawHolder = top
    }

    private var rawHolder: LinearLayout? = null

    private fun textButton(label: String, run: () -> Unit) =
        MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = label
            setTextColor(Color.WHITE)
            setOnClickListener { run() }
        }

    private fun refreshDials() {
        dials.removeAllViews()
        val list = buildList {
            if (manualSensor) {
                add(Dial.ISO to (iso?.let { "ISO $it" } ?: getString(R.string.pro_iso_auto)))
                add(Dial.SHUTTER to (shutter?.let { ProValues.shutterLabel(it) } ?: getString(R.string.pro_shutter_auto)))
            }
            if (minFocus > 0f) {
                add(Dial.FOCUS to (focus?.let { ProValues.focusLabel(it) } ?: getString(R.string.pro_focus_auto)))
            }
            add(Dial.WB to getString(wbLabel(wb)))
        }
        for ((dial, label) in list) {
            val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                setTextColor(if (open == dial) Color.rgb(0x4F, 0xC3, 0xF7) else Color.WHITE)
                isAllCaps = false
                setOnClickListener {
                    open = if (open == dial) null else dial
                    refreshDials()
                    refreshTuner()
                }
            }
            dials.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(4) })
        }
        rawChip?.let {
            it.text = getString(if (raw) R.string.pro_raw_on else R.string.pro_raw_off)
            it.setTextColor(if (raw) Color.rgb(0x4F, 0xC3, 0xF7) else Color.WHITE)
        }
    }

    private fun refreshTuner() {
        tuner.removeAllViews()
        val dial = open ?: return
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        when (dial) {
            Dial.ISO -> {
                val start = iso ?: isoSteps[ProValues.nearest(isoSteps, if (liveIso > 0) liveIso else 100)]
                row.addView(stepSlider(isoSteps.size, ProValues.nearest(isoSteps, start)) { i ->
                    iso = isoSteps[i]
                    if (shutter == null) shutter = shutterSteps[ProValues.nearest(shutterSteps, seedShutter())]
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                row.addView(autoButton { iso = null; shutter = null })
            }
            Dial.SHUTTER -> {
                val start = shutter ?: seedShutter()
                row.addView(stepSlider(shutterSteps.size, ProValues.nearest(shutterSteps, start)) { i ->
                    shutter = shutterSteps[i]
                    if (iso == null) iso = isoSteps[ProValues.nearest(isoSteps, if (liveIso > 0) liveIso else 100)]
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                row.addView(autoButton { iso = null; shutter = null })
            }
            Dial.FOCUS -> {
                val slider = Slider(this).apply {
                    // Left is far (infinity), right is close.
                    valueFrom = 0f
                    valueTo = minFocus
                    value = (focus ?: 0f).coerceIn(0f, minFocus)
                    setLabelFormatter { ProValues.focusLabel(it) }
                    addOnChangeListener { _, v, fromUser ->
                        if (!fromUser) return@addOnChangeListener
                        focus = v
                        applyControls()
                        refreshDials()
                    }
                }
                row.addView(slider, LinearLayout.LayoutParams(0, WRAP, 1f))
                row.addView(autoButton { focus = null })
            }
            Dial.WB -> {
                for (mode in WB_MODES) {
                    row.addView(textButton(getString(wbLabel(mode))) {
                        wb = mode
                        applyControls()
                        refreshDials()
                    }.apply {
                        isAllCaps = false
                        if (mode == wb) setTextColor(Color.rgb(0x4F, 0xC3, 0xF7))
                    })
                }
                val scroll = android.widget.HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(row)
                }
                tuner.addView(scroll, LinearLayout.LayoutParams(MATCH, WRAP))
                return
            }
        }
        tuner.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun seedShutter(): Long = if (liveShutter > 0) liveShutter else 1_000_000_000L / 60

    private fun stepSlider(count: Int, at: Int, set: (Int) -> Unit) = Slider(this).apply {
        valueFrom = 0f
        valueTo = (count - 1).coerceAtLeast(1).toFloat()
        stepSize = 1f
        value = at.coerceIn(0, count - 1).toFloat()
        isEnabled = count > 1
        setLabelFormatter { v ->
            val i = v.toInt().coerceIn(0, count - 1)
            if (open == Dial.ISO) "ISO ${isoSteps[i]}" else ProValues.shutterLabel(shutterSteps[i])
        }
        addOnChangeListener { _, v, fromUser ->
            if (!fromUser) return@addOnChangeListener
            set(v.toInt().coerceIn(0, count - 1))
            applyControls()
            refreshDials()
        }
    }

    private fun autoButton(run: () -> Unit) = textButton(getString(R.string.pro_auto)) {
        run()
        applyControls()
        refreshDials()
        refreshTuner()
    }

    private fun wbLabel(mode: Int) = when (mode) {
        CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT -> R.string.pro_wb_daylight
        CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> R.string.pro_wb_cloudy
        CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT -> R.string.pro_wb_indoor
        CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT -> R.string.pro_wb_fluorescent
        else -> R.string.pro_wb_auto
    }

    // ---- camera ----------------------------------------------------------------------------------

    private fun bind() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isDestroyed) return@addListener
            val p = try {
                future.get()
            } catch (e: Exception) {
                Log.e(TAG, "provider", e)
                finish()
                return@addListener
            }
            provider = p
            readCapabilities(p)
            rebind()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun readCapabilities(p: ProcessCameraProvider) {
        val info = try {
            p.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
        } catch (e: Exception) {
            Log.w(TAG, "no back camera", e)
            return
        }
        val c2 = Camera2CameraInfo.from(info)
        val caps = c2.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        manualSensor = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let {
            isoSteps = ProValues.isos(it.lower, it.upper)
        }
        c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let {
            // Longer than 30 seconds is left out: the preview would stand still for that long.
            shutterSteps = ProValues.shutters(it.lower, minOf(it.upper, 30_000_000_000L))
        }
        minFocus = if (manualSensor) {
            c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        } else 0f
        rawSupported = try {
            ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats
                .contains(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
        } catch (e: Exception) {
            false
        }
        if (rawSupported && rawChip == null) {
            rawChip = textButton(getString(R.string.pro_raw_off)) {
                raw = !raw
                refreshDials()
                rebind()
            }.also { rawHolder?.addView(it) }
        }
        refreshDials()
    }

    private fun rebind() {
        val p = provider ?: return
        shutterButton.isEnabled = false
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        val previewBuilder = Preview.Builder().setResolutionSelector(selector)
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { liveIso = it }
                result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { liveShutter = it }
                val now = SystemClock.uptimeMillis()
                if (now - lastReadout > 250) {
                    lastReadout = now
                    main.post { showReadout() }
                }
            }
        })
        val pv = previewBuilder.build().also { it.setSurfaceProvider(preview.surfaceProvider) }
        val captureBuilder = ImageCapture.Builder()
            .setResolutionSelector(selector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        if (raw && rawSupported) captureBuilder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
        val capture = captureBuilder.build()
        try {
            p.unbindAll()
            camera = p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, pv, capture)
            imageCapture = capture
            applyControls()
            shutterButton.isEnabled = true
        } catch (e: Exception) {
            Log.e(TAG, "bind", e)
            if (raw) {
                // The camera wouldn't take RAW with these streams after all; carry on without.
                raw = false
                rawSupported = false
                rawChip?.let { rawHolder?.removeView(it) }
                rawChip = null
                refreshDials()
                rebind()
            } else {
                Toast.makeText(this, R.string.pro_failed, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showReadout() {
        if (isDestroyed) return
        val parts = ArrayList<String>()
        if (liveIso > 0) parts.add("ISO $liveIso")
        if (liveShutter > 0) parts.add(ProValues.shutterLabel(liveShutter))
        readout.text = parts.joinToString("  ·  ")
    }

    private fun applyControls() {
        val cam = camera ?: return
        val b = CaptureRequestOptions.Builder()
        val i = iso
        val s = shutter
        if (manualSensor && i != null && s != null) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, i)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, s)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(s, MIN_FRAME_NS))
        }
        val f = focus
        if (minFocus > 0f && f != null) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, f)
        }
        b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wb)
        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build())
    }

    // ---- taking ----------------------------------------------------------------------------------

    private fun take() {
        val capture = imageCapture ?: return
        shutterButton.isEnabled = false
        progress.visibility = View.VISIBLE
        if (raw && rawSupported) takeRaw(capture) else takeJpeg(capture)
    }

    private fun takeJpeg(capture: ImageCapture) {
        capture.takePicture(saver, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val jpeg = try {
                    cleanJpeg(image)
                } catch (e: Exception) {
                    Log.e(TAG, "jpeg", e)
                    null
                } finally {
                    image.close()
                }
                if (jpeg == null) failed() else keep(jpeg, null)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "capture", exception)
                failed()
            }
        })
    }

    private fun takeRaw(capture: ImageCapture) {
        val ctx = applicationContext
        val dng = VaultStore.newTempFile(ctx, ".dng")
        val jpg = VaultStore.newTempFile(ctx, ".jpg")
        val calls = AtomicInteger()
        val done = AtomicBoolean(false)
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(dng).build(),
            ImageCapture.OutputFileOptions.Builder(jpg).build(),
            saver,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val n = calls.incrementAndGet()
                    if (dng.length() > 0 && jpg.length() > 0 && done.compareAndSet(false, true)) {
                        val jpeg = try {
                            cleanJpeg(jpg)
                        } catch (e: Exception) {
                            Log.e(TAG, "raw jpeg", e)
                            null
                        } finally {
                            VaultStore.wipe(jpg)
                        }
                        if (jpeg == null) {
                            VaultStore.wipe(dng)
                            failed()
                        } else keep(jpeg, dng)
                    } else if (n >= 2 && done.compareAndSet(false, true)) {
                        VaultStore.wipe(dng)
                        VaultStore.wipe(jpg)
                        failed()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "raw capture", exception)
                    if (done.compareAndSet(false, true)) {
                        VaultStore.wipe(dng)
                        VaultStore.wipe(jpg)
                        failed()
                    }
                }
            },
        )
    }

    /** Turned the right way up and re-encoded, so no metadata comes along. */
    private fun cleanJpeg(image: ImageProxy): ByteArray {
        val bmp = image.toBitmap()
        val turn = image.imageInfo.rotationDegrees
        val upright = if (turn == 0) bmp else {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(turn.toFloat()) }, true)
                .also { if (it !== bmp) bmp.recycle() }
        }
        return encode(upright)
    }

    private fun cleanJpeg(file: File): ByteArray {
        // ImageDecoder turns it the right way up from its own Exif.
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { d, _, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return encode(bmp)
    }

    private fun encode(bmp: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bmp.recycle()
        out.toByteArray()
    }

    /** Runs on [saver]: into the vault in Private mode, otherwise the gallery. */
    private fun keep(jpeg: ByteArray, dng: File?) {
        val ctx = applicationContext
        val now = System.currentTimeMillis()
        val pub = if (CamConfig.isPrivateVaultOn(ctx) && VaultKeys.isSetUp(ctx)) VaultKeys.publicKey(ctx) else null
        val ok = try {
            if (pub != null) {
                // A place only if the viewfinder learned one in the last minutes.
                val place = VaultLocation.now(ctx)
                VaultStore.savePhoto(ctx, pub, jpeg, now, place = place)
                dng?.let { VaultStore.saveRaw(ctx, pub, it, jpeg, now, place) }
            } else {
                VaultTransfer.saveJpegToGallery(ctx, jpeg)
                dng?.let { VaultTransfer.saveDngToGallery(ctx, it) }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "keep", e)
            false
        } finally {
            jpeg.fill(0)
            dng?.let { VaultStore.wipe(it) }
        }
        main.post {
            if (isDestroyed) return@post
            progress.visibility = View.GONE
            shutterButton.isEnabled = true
            Toast.makeText(this, when {
                !ok -> R.string.pro_failed
                pub != null -> R.string.pro_saved_vault
                else -> R.string.pro_saved
            }, Toast.LENGTH_SHORT).show()
        }
    }

    private fun failed() {
        main.post {
            if (isDestroyed) return@post
            progress.visibility = View.GONE
            shutterButton.isEnabled = true
            Toast.makeText(this, R.string.pro_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "ProCamera"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val JPEG_QUALITY = 95
        private const val MIN_FRAME_NS = 33_333_333L

        /**
         * Takes the capture callbacks and does the saving. It belongs to the app, not the screen,
         * so a shot still being written when the screen closes is still sealed (or copied) and
         * its plain temp files wiped.
         */
        private val saver: ExecutorService = Executors.newSingleThreadExecutor()
        private val WB_MODES = intArrayOf(
            CaptureRequest.CONTROL_AWB_MODE_AUTO,
            CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
            CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
            CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
            CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
        )

        fun open(ctx: Context) {
            ctx.startActivity(Intent(ctx, ProCameraActivity::class.java))
        }
    }
}
