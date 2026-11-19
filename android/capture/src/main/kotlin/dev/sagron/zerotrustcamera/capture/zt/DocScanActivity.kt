package dev.sagron.zerotrustcamera.capture.zt

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import dev.sagron.zerotrustcamera.capture.CamConfig
import dev.sagron.zerotrustcamera.capture.R
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * The document scanner: finds the page in the viewfinder (and takes it by itself once it holds
 * still, when Auto is on), lets the corners be adjusted, straightens it, applies a look (enhanced,
 * grayscale, black and white), and collects pages into a PDF. Text can be read from the pages on
 * the phone. Pages stay in memory until they are saved, shared or sent.
 *
 * In Private mode the pages are saved into the Private vault, not the phone's storage.
 */
class DocScanActivity : ZtBaseActivity() {
    private enum class Stage { CAMERA, CORNERS, PAGE, FINISH }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var io: ExecutorService
    private lateinit var analysisExecutor: ExecutorService

    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var bar: LinearLayout
    private lateinit var hint: TextView
    private lateinit var progress: ProgressBar

    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private var liveQuad: DocQuadView? = null

    private var stage = Stage.CAMERA
    private var busy = false
    private var autoCapture = true
    private var lastQuad: FloatArray? = null
    private var stableSince = 0L

    private var shot: Bitmap? = null
    private var corners: FloatArray? = null
    private var flat: Bitmap? = null
    private var filter = DocPages.Filter.ENHANCED
    private var turn = 0
    private val pages = ArrayList<ByteArray>()
    private var text: String? = null

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) showCamera() else {
            Toast.makeText(this, R.string.doc_camera_needed, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        io = Executors.newSingleThreadExecutor()
        analysisExecutor = Executors.newSingleThreadExecutor()
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        content = FrameLayout(this)
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        hint = TextView(this).apply {
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(48), dp(16), dp(16))
        }
        root.addView(hint, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(0xCC, 0, 0, 0))
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        root.addView(bar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        progress = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(progress, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showCamera()
        } else {
            askCamera.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        provider?.unbindAll()
        io.shutdown()
        analysisExecutor.shutdown()
    }

    // ---- camera ----------------------------------------------------------------------------------

    private fun showCamera() {
        stage = Stage.CAMERA
        busy = false
        lastQuad = null
        content.removeAllViews()
        val pv = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
        val quad = DocQuadView(this)
        content.addView(pv, FrameLayout.LayoutParams(MATCH, MATCH))
        content.addView(quad, FrameLayout.LayoutParams(MATCH, MATCH))
        previewView = pv
        liveQuad = quad
        hint.setText(R.string.doc_hint_find)

        bar.removeAllViews()
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val auto = MaterialSwitch(this).apply {
            setText(R.string.doc_auto)
            setTextColor(Color.WHITE)
            isChecked = autoCapture
            setOnCheckedChangeListener { _, on -> autoCapture = on }
        }
        row.addView(auto, LinearLayout.LayoutParams(0, WRAP, 1f))
        val shutter = MaterialButton(this).apply {
            setText(R.string.doc_take)
            setOnClickListener { take() }
        }
        row.addView(shutter, LinearLayout.LayoutParams(0, WRAP, 1f))
        val done = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = if (pages.isEmpty()) "" else resources.getQuantityString(R.plurals.doc_pages, pages.size, pages.size)
            isEnabled = pages.isNotEmpty()
            setTextColor(Color.WHITE)
            setOnClickListener { showFinish() }
        }
        row.addView(done, LinearLayout.LayoutParams(0, WRAP, 1f))
        bar.addView(row)
        bindCamera()
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isDestroyed || stage != Stage.CAMERA) return@addListener
            val p = future.get()
            provider = p
            val fourThree = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
            val preview = Preview.Builder().setResolutionSelector(fourThree).build()
            preview.setSurfaceProvider(previewView!!.surfaceProvider)
            val capture = ImageCapture.Builder()
                .setResolutionSelector(fourThree)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor, ::analyze)
            imageCapture = capture
            try {
                p.unbindAll()
                p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "bind", e)
                Toast.makeText(this, R.string.doc_failed, Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        try {
            val (gray, w, h) = uprightGray(image)
            val q = DocDetect.find(gray, w, h)
            main.post { onLiveQuad(q, w, h) }
        } catch (e: Exception) {
            Log.w(TAG, "analyze", e)
        } finally {
            image.close()
        }
    }

    /** The luma plane, made smaller (about 320 wide) and turned upright. */
    private fun uprightGray(image: ImageProxy): Triple<IntArray, Int, Int> {
        val plane = image.planes[0]
        val buf = plane.buffer
        val step = maxOf(1, maxOf(image.width, image.height) / 320)
        val sw = image.width / step
        val sh = image.height / step
        val small = IntArray(sw * sh)
        for (y in 0 until sh) {
            val row = y * step * plane.rowStride
            for (x in 0 until sw) {
                small[y * sw + x] = buf.get(row + x * step * plane.pixelStride).toInt() and 0xFF
            }
        }
        return when (image.imageInfo.rotationDegrees) {
            90 -> Triple(IntArray(sw * sh) { i -> val x = i % sh; val y = i / sh; small[(sh - 1 - x) * sw + y] }, sh, sw)
            180 -> Triple(IntArray(sw * sh) { i -> small[sw * sh - 1 - i] }, sw, sh)
            270 -> Triple(IntArray(sw * sh) { i -> val x = i % sh; val y = i / sh; small[x * sw + (sw - 1 - y)] }, sh, sw)
            else -> Triple(small, sw, sh)
        }
    }

    private fun onLiveQuad(q: FloatArray?, w: Int, h: Int) {
        val pv = previewView ?: return
        val quad = liveQuad ?: return
        if (stage != Stage.CAMERA || busy) return
        // FIT_CENTER: the picture is scaled to fit inside the view, centred.
        val s = minOf(pv.width / w.toFloat(), pv.height / h.toFloat())
        val dw = w * s
        val dh = h * s
        quad.imageRect = RectF((pv.width - dw) / 2, (pv.height - dh) / 2, (pv.width + dw) / 2, (pv.height + dh) / 2)
        quad.corners = q
        val now = System.currentTimeMillis()
        val prev = lastQuad
        if (q == null) {
            hint.setText(R.string.doc_hint_find)
            stableSince = 0
        } else if (prev == null || (0 until 8).any { abs(q[it] - prev[it]) > 0.02f }) {
            stableSince = now
            if (autoCapture) hint.setText(R.string.doc_hint_hold)
        } else if (autoCapture && stableSince > 0 && now - stableSince > 1200) {
            take()
        }
        lastQuad = q
    }

    private fun take() {
        val capture = imageCapture ?: return
        if (busy || stage != Stage.CAMERA) return
        busy = true
        progress.visibility = View.VISIBLE
        capture.takePicture(io, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rot = image.imageInfo.rotationDegrees
                val bmp = try {
                    val raw = image.toBitmap()
                    if (rot == 0) raw else DocPages.rotate(raw, rot).also { raw.recycle() }
                } finally {
                    image.close()
                }
                val (gray, w, h) = DocPages.smallGray(bmp)
                val found = DocDetect.find(gray, w, h)
                    ?: floatArrayOf(0.05f, 0.05f, 0.95f, 0.05f, 0.95f, 0.95f, 0.05f, 0.95f)
                main.post {
                    progress.visibility = View.GONE
                    shot?.recycle()
                    shot = bmp
                    corners = found
                    showCorners()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "take", exception)
                main.post {
                    progress.visibility = View.GONE
                    busy = false
                    Toast.makeText(this@DocScanActivity, R.string.doc_failed, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // ---- corners ---------------------------------------------------------------------------------

    private fun showCorners() {
        stage = Stage.CORNERS
        provider?.unbindAll()
        previewView = null
        liveQuad = null
        val bmp = shot ?: return showCamera()
        content.removeAllViews()
        val image = ImageView(this).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val quad = DocQuadView(this).apply {
            editable = true
            corners = this@DocScanActivity.corners
        }
        content.addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
        content.addView(quad, FrameLayout.LayoutParams(MATCH, MATCH))
        image.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val s = minOf(v.width / bmp.width.toFloat(), v.height / bmp.height.toFloat())
            val dw = bmp.width * s
            val dh = bmp.height * s
            quad.imageRect = RectF((v.width - dw) / 2, (v.height - dh) / 2, (v.width + dw) / 2, (v.height + dh) / 2)
        }
        hint.setText(R.string.doc_hint_corners)
        buttons(
            getString(R.string.doc_retake) to { showCamera() },
            getString(R.string.doc_next) to {
                corners = quad.corners
                straighten()
            },
        )
    }

    private fun straighten() {
        val bmp = shot ?: return
        val c = corners ?: return
        work({
            DocPages.straighten(bmp, c)
        }) { page ->
            flat?.recycle()
            flat = page
            turn = 0
            showPage()
        }
    }

    // ---- one page --------------------------------------------------------------------------------

    private var pageView: ImageView? = null

    private fun showPage() {
        stage = Stage.PAGE
        content.removeAllViews()
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        pageView = image
        content.addView(image, FrameLayout.LayoutParams(MATCH, MATCH).apply { bottomMargin = dp(200) })
        hint.text = ""
        bar.removeAllViews()
        val chips = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        for ((f, label) in listOf(
            DocPages.Filter.ENHANCED to R.string.doc_filter_enhanced,
            DocPages.Filter.ORIGINAL to R.string.doc_filter_original,
            DocPages.Filter.GRAYSCALE to R.string.doc_filter_gray,
            DocPages.Filter.BLACK_AND_WHITE to R.string.doc_filter_bw,
        )) {
            chips.addView(Chip(this).apply {
                setText(label)
                isCheckable = true
                isChecked = f == filter
                setOnClickListener {
                    filter = f
                    renderPage()
                }
            })
        }
        bar.addView(HorizontalScrollView(this).apply { addView(chips) })
        buttons(
            getString(R.string.doc_rotate) to {
                turn = (turn + 90) % 360
                renderPage()
            },
            getString(R.string.doc_adjust) to { showCorners() },
            clear = false,
        )
        buttons(
            getString(R.string.doc_add_page) to { keepPage { showCamera() } },
            getString(R.string.doc_done) to { keepPage { showFinish() } },
            clear = false,
        )
        renderPage()
    }

    private fun looked(): Bitmap? {
        val page = flat ?: return null
        val filtered = DocPages.apply(page, filter)
        if (turn == 0) return filtered
        return DocPages.rotate(filtered, turn).also { if (filtered !== page) filtered.recycle() }
    }

    private fun renderPage() {
        val f = filter
        val t = turn
        work({ looked() }) { bmp ->
            if (f != filter || t != turn) return@work
            pageView?.setImageBitmap(bmp)
        }
    }

    private fun keepPage(then: () -> Unit) {
        work({
            val bmp = looked() ?: return@work null
            DocPages.jpeg(bmp)
        }) { jpeg ->
            if (jpeg != null) {
                pages.add(jpeg)
                text = null
            }
            shot?.recycle()
            shot = null
            flat?.recycle()
            flat = null
            then()
        }
    }

    // ---- finish ----------------------------------------------------------------------------------

    private fun showFinish() {
        stage = Stage.FINISH
        provider?.unbindAll()
        previewView = null
        liveQuad = null
        content.removeAllViews()
        hint.text = resources.getQuantityString(R.plurals.doc_pages, pages.size, pages.size)
        val strip = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        pages.forEachIndexed { i, jpeg ->
            val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
            val thumb = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
            strip.addView(ImageView(this).apply {
                setImageBitmap(thumb)
                adjustViewBounds = true
                contentDescription = getString(R.string.doc_page_number, i + 1)
                setOnLongClickListener {
                    MaterialAlertDialogBuilder(this@DocScanActivity)
                        .setMessage(R.string.doc_delete_page)
                        .setPositiveButton(R.string.doc_delete) { _, _ ->
                            pages.removeAt(i)
                            text = null
                            if (pages.isEmpty()) showCamera() else showFinish()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }
            }, LinearLayout.LayoutParams(WRAP, dp(260)).apply { marginEnd = dp(12) })
        }
        content.addView(HorizontalScrollView(this).apply {
            setPadding(dp(16), dp(96), dp(16), 0)
            addView(strip)
        }, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        bar.removeAllViews()
        val privateMode = CamConfig.isPrivateVaultOn(this) && VaultKeys.isSetUp(this)
        buttons(
            getString(if (privateMode) R.string.doc_save_vault else R.string.doc_save_pdf) to { save(privateMode) },
            getString(R.string.doc_share_pdf) to { sharePdf() },
        )
        buttons(
            getString(R.string.doc_copy_text) to { readText { showText(it) } },
            getString(R.string.doc_send_notes) to { sendToNotes() },
            clear = false,
        )
        buttons(getString(R.string.doc_scan_more) to { showCamera() }, clear = false)
    }

    private fun pdfName() = "Scan_" + VaultTransfer.randomName() + ".pdf"

    private fun save(privateMode: Boolean) {
        val ctx = applicationContext
        val snapshot = ArrayList(pages)
        work({
            if (privateMode) {
                val pub = VaultKeys.publicKey(ctx) ?: return@work false
                val now = System.currentTimeMillis()
                snapshot.forEachIndexed { i, jpeg -> VaultStore.savePhoto(ctx, pub, jpeg, now + i) }
                true
            } else {
                val pdf = File(VaultStore.tempDir(ctx), pdfName())
                try {
                    DocPages.writePdf(snapshot, pdf)
                    DocPages.saveToDocuments(ctx, pdf, pdf.name)
                } finally {
                    VaultStore.wipe(pdf)
                }
            }
        }) { ok ->
            Toast.makeText(this, if (ok != true) R.string.doc_failed
                else if (privateMode) R.string.doc_saved_vault else R.string.doc_saved_pdf, Toast.LENGTH_LONG).show()
            if (ok == true) {
                pages.clear()
                finish()
            }
        }
    }

    private fun sharePdf() {
        val ctx = applicationContext
        val snapshot = ArrayList(pages)
        work({
            VaultTransfer.clearShared(ctx, olderThanMs = 60 * 60 * 1000L)
            File(VaultTransfer.shareDir(ctx), pdfName()).also { DocPages.writePdf(snapshot, it) }
        }) { pdf ->
            val uri = FileProvider.getUriForFile(this, "$packageName.vaultshare", pdf)
            val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(null, uri)
            startActivity(Intent.createChooser(send, null))
        }
    }

    private fun sendToNotes() {
        val ctx = applicationContext
        val snapshot = ArrayList(pages)
        readText { found ->
            work({
                VaultTransfer.clearShared(ctx, olderThanMs = 60 * 60 * 1000L)
                snapshot.map { jpeg ->
                    File(VaultTransfer.shareDir(ctx), "Scan_" + VaultTransfer.randomName() + ".jpg")
                        .also { it.writeBytes(jpeg) }
                }
            }) { files ->
                val uris = files.map { FileProvider.getUriForFile(this, "$packageName.vaultshare", it) }
                val send = (if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
                    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)))
                    .setType("image/jpeg")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                found?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
                send.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                val notes = Intent(send).setPackage(NOTES)
                startActivity(if (notes.resolveActivity(packageManager) != null) notes else Intent.createChooser(send, null))
            }
        }
    }

    /** Reads the text of every page once (kept until the pages change). */
    private fun readText(then: (String?) -> Unit) {
        text?.let { return then(it) }
        if (Ocr.languages(this) == null) {
            Toast.makeText(this, R.string.doc_no_text, Toast.LENGTH_SHORT).show()
            return then(null)
        }
        val ctx = applicationContext
        val snapshot = ArrayList(pages)
        hint.setText(R.string.doc_reading)
        work({
            snapshot.mapNotNull { jpeg ->
                val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                Ocr.read(ctx, bmp).also { bmp.recycle() }
            }.joinToString("\n\n").ifBlank { null }
        }) { found ->
            hint.text = resources.getQuantityString(R.plurals.doc_pages, pages.size, pages.size)
            text = found
            then(found)
        }
    }

    private fun showText(found: String?) {
        if (found == null) {
            Toast.makeText(this, R.string.doc_no_text, Toast.LENGTH_SHORT).show()
            return
        }
        showTextDialog(this, found)
    }

    // ---- shared bits -----------------------------------------------------------------------------

    private fun back() {
        when (stage) {
            Stage.CORNERS -> showCamera()
            Stage.PAGE -> showCorners()
            Stage.CAMERA, Stage.FINISH -> {
                if (pages.isEmpty()) {
                    finish()
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.doc_discard_title)
                        .setPositiveButton(R.string.doc_discard) { _, _ -> finish() }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
        }
    }

    private fun buttons(vararg actions: Pair<String, () -> Unit>, clear: Boolean = true) {
        if (clear) bar.removeAllViews()
        val row = LinearLayout(this)
        actions.forEachIndexed { i, (label, run) ->
            val b = if (i == actions.lastIndex) MaterialButton(this)
                else MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            b.text = label
            b.setOnClickListener { if (progress.visibility != View.VISIBLE) run() }
            row.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                if (i > 0) marginStart = dp(8)
            })
        }
        bar.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
    }

    /** Runs [job] off the main thread with the spinner up, then [done] on the main thread. */
    private fun <T> work(job: () -> T, done: (T) -> Unit) {
        progress.visibility = View.VISIBLE
        io.execute {
            val result = try {
                Result.success(job())
            } catch (e: Throwable) {
                Log.e(TAG, "work", e)
                Result.failure(e)
            }
            main.post {
                if (isDestroyed) return@post
                progress.visibility = View.GONE
                result.onSuccess(done).onFailure {
                    Toast.makeText(this, R.string.doc_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "DocScan"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val NOTES = "dev.sagron.zerotrustnotes"

        /** Read text, selectable, with a Copy button. */
        fun showTextDialog(ctx: Context, found: String) {
            val view = TextView(ctx).apply {
                text = found
                setTextIsSelectable(true)
                textSize = 16f
                val pad = (24 * ctx.resources.displayMetrics.density).toInt()
                setPadding(pad, pad / 2, pad, 0)
            }
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.doc_text_title)
                .setView(android.widget.ScrollView(ctx).apply { addView(view) })
                .setPositiveButton(R.string.doc_copy) { _, _ ->
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("text", found))
                    Toast.makeText(ctx, R.string.doc_text_copied, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.scan_close, null)
                .show()
        }

        fun open(ctx: Context) {
            ctx.startActivity(Intent(ctx, DocScanActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
