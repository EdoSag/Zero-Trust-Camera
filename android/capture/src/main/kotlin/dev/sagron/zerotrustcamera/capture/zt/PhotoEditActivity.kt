package dev.sagron.zerotrustcamera.capture.zt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import dev.sagron.zerotrustcamera.capture.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * The photo editor: crop, rotate, adjust (brightness, contrast, colour, warmth), hide areas
 * under a mosaic (faces found on the phone, or marked by hand), and draw. It always saves a new
 * copy, re-encoded here, so the copy carries no place, time or phone details; the original is
 * never changed.
 *
 * Opened three ways:
 * - from the Private vault's viewer: the copy is sealed into the vault (only the public key is
 *   needed), and the screen can't be captured;
 * - from "Clean and share" (Edit first): the copy is shared onward;
 * - from another app's "Edit with": the copy goes to the gallery.
 */
class PhotoEditActivity : ZtBaseActivity() {
    private enum class Source { VAULT, SHARE, GALLERY }
    private enum class Tool { NONE, CROP, ADJUST, HIDE, DRAW }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var io: ExecutorService
    private lateinit var source: Source
    private lateinit var editView: PhotoEditView
    private lateinit var panel: LinearLayout
    private lateinit var tools: LinearLayout
    private lateinit var undo: MaterialButton
    private lateinit var save: MaterialButton
    private lateinit var progress: ProgressBar

    private var vaultId: String? = null
    private var uri: Uri? = null
    private var takenAt = 0L
    private var full: Bitmap? = null
    private var small: Bitmap? = null
    private val ops = ArrayList<EditOp>()
    private var tool = Tool.NONE
    private var adjust = EditOp.Adjust(0f, 0f, 0f, 0f)
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        io = Executors.newSingleThreadExecutor()
        val internal = intent.getStringExtra(EXTRA_TOKEN) == token
        val id = intent.getStringExtra(EXTRA_VAULT_ID)
        val data = intent.data?.takeIf { it.scheme == "content" && it.authority?.startsWith(packageName) != true }
        source = when {
            internal && id != null -> Source.VAULT
            internal && intent.getBooleanExtra(EXTRA_SHARE, false) && data != null -> Source.SHARE
            intent.action == Intent.ACTION_EDIT && data != null -> Source.GALLERY
            else -> {
                finish()
                return
            }
        }
        if (source == Source.VAULT) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (!VaultSession.isUnlocked) {
                finish()
                return
            }
            vaultId = id
        } else {
            uri = data
        }
        buildUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        load()
    }

    // ---- layout ----------------------------------------------------------------------------------

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        editView = PhotoEditView(this).apply { drawColor = DRAW_COLOURS[0] }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(32), dp(8), dp(4))
        }
        val close = textButton(getString(android.R.string.cancel)) { back() }
        undo = textButton(getString(R.string.edit_undo)) { undoLast() }
        save = MaterialButton(this).apply {
            setText(R.string.edit_save)
            setOnClickListener { saveCopy() }
        }
        top.addView(close)
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(undo)
        top.addView(save)
        column.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))
        column.addView(editView, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(0xCC, 0, 0, 0))
            setPadding(dp(12), dp(8), dp(12), dp(24))
        }
        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bottom.addView(panel, LinearLayout.LayoutParams(MATCH, WRAP))
        tools = LinearLayout(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        tools.addView(textButton(getString(R.string.edit_crop)) { startTool(Tool.CROP) })
        tools.addView(textButton(getString(R.string.edit_rotate)) { rotate() })
        tools.addView(textButton(getString(R.string.edit_adjust)) { startTool(Tool.ADJUST) })
        tools.addView(textButton(getString(R.string.edit_hide)) { startTool(Tool.HIDE) })
        tools.addView(textButton(getString(R.string.edit_draw)) { startTool(Tool.DRAW) })
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(tools)
        }
        bottom.addView(scroll, LinearLayout.LayoutParams(MATCH, WRAP))
        column.addView(bottom, LinearLayout.LayoutParams(MATCH, WRAP))

        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        progress = ProgressBar(this)
        root.addView(progress, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        setContentView(root)
        refreshButtons()
    }

    private fun textButton(label: String, run: () -> Unit) =
        MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = label
            setTextColor(Color.WHITE)
            setOnClickListener { if (!busy()) run() }
        }

    private fun busy() = progress.visibility == View.VISIBLE

    private fun refreshButtons() {
        val loaded = small != null
        undo.isEnabled = loaded && ops.isNotEmpty() && tool == Tool.NONE
        save.isEnabled = loaded && ops.isNotEmpty() && tool == Tool.NONE && !saving
        tools.visibility = if (tool == Tool.NONE) View.VISIBLE else View.GONE
    }

    // ---- loading ---------------------------------------------------------------------------------

    private fun load() {
        val ctx = applicationContext
        val vid = vaultId
        val from = uri
        work({
            val decoded = if (vid != null) {
                val priv = VaultSession.privateKey()
                takenAt = try {
                    VaultStore.meta(ctx, vid, priv).optLong("takenAt", 0L)
                } catch (_: Exception) {
                    0L
                }
                val bytes = VaultStore.media(ctx, vid, priv)
                try {
                    decode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
                } finally {
                    bytes.fill(0)
                }
            } else {
                decode(ImageDecoder.createSource(contentResolver, from!!))
            }
            val side = max(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
            decoded to PhotoEdit.fit(decoded, side.coerceIn(1080, PREVIEW_MAX))
        }) { (big, preview) ->
            full = big
            small = preview
            refreshPreview()
        }
    }

    /** Decodes with the photo's own rotation applied, at most [MAX_SIDE] on its longer side. */
    private fun decode(src: ImageDecoder.Source): Bitmap = ImageDecoder.decodeBitmap(src) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val w = info.size.width
        val h = info.size.height
        val long = max(w, h)
        if (long > MAX_SIDE) {
            val s = MAX_SIDE.toFloat() / long
            d.setTargetSize((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1))
        }
    }

    private fun refreshPreview() {
        val base = small ?: return
        val steps = ArrayList(ops)
        work({ PhotoEdit.render(base, steps) }) { shown ->
            val old = editView.bitmap
            editView.bitmap = shown
            if (old != null && old !== shown && old !== small) old.recycle()
            refreshButtons()
        }
    }

    // ---- tools -----------------------------------------------------------------------------------

    private fun startTool(t: Tool) {
        if (small == null) return
        tool = t
        editView.resetTools()
        panel.removeAllViews()
        when (t) {
            Tool.CROP -> {
                editView.mode = PhotoEditView.Mode.CROP
                hint(R.string.edit_crop_hint)
            }
            Tool.ADJUST -> {
                editView.mode = PhotoEditView.Mode.VIEW
                adjust = EditOp.Adjust(0f, 0f, 0f, 0f)
                slider(R.string.edit_brightness) { adjust = adjust.copy(brightness = it) }
                slider(R.string.edit_contrast) { adjust = adjust.copy(contrast = it) }
                slider(R.string.edit_saturation) { adjust = adjust.copy(saturation = it) }
                slider(R.string.edit_warmth) { adjust = adjust.copy(warmth = it) }
            }
            Tool.HIDE -> {
                editView.mode = PhotoEditView.Mode.HIDE
                hint(R.string.edit_hide_hint)
                val faces = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    setText(R.string.edit_find_faces)
                    setTextColor(Color.WHITE)
                    setOnClickListener { if (!busy()) findFaces() }
                }
                panel.addView(faces, LinearLayout.LayoutParams(WRAP, WRAP))
            }
            Tool.DRAW -> {
                editView.mode = PhotoEditView.Mode.DRAW
                val colours = LinearLayout(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
                val rings = ArrayList<android.graphics.drawable.GradientDrawable>()
                fun ring(d: android.graphics.drawable.GradientDrawable, c: Int) =
                    d.setStroke(dp(3), if (c == editView.drawColor) Color.WHITE else Color.DKGRAY)
                for (c in DRAW_COLOURS) {
                    val dot = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(c)
                    }
                    ring(dot, c)
                    rings.add(dot)
                    val swatch = View(this).apply {
                        background = dot
                        contentDescription = String.format("#%06X", c and 0xFFFFFF)
                        setOnClickListener {
                            // Changing colour keeps what is already drawn.
                            editView.drawColor = c
                            rings.forEachIndexed { i, d -> ring(d, DRAW_COLOURS[i]) }
                        }
                    }
                    colours.addView(swatch, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
                }
                panel.addView(colours, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            Tool.NONE -> {}
        }
        val row = LinearLayout(this).apply { gravity = Gravity.END }
        row.addView(textButton(getString(android.R.string.cancel)) { endTool(false) })
        row.addView(MaterialButton(this).apply {
            setText(R.string.edit_apply)
            setOnClickListener { if (!busy()) endTool(true) }
        })
        panel.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        refreshButtons()
    }

    private fun hint(text: Int) {
        panel.addView(TextView(this).apply {
            setText(text)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        })
    }

    private fun slider(label: Int, set: (Float) -> Unit) {
        panel.addView(TextView(this).apply {
            setText(label)
            setTextColor(Color.WHITE)
        })
        panel.addView(Slider(this).apply {
            valueFrom = -1f
            valueTo = 1f
            value = 0f
            addOnChangeListener { _, v, _ ->
                set(v)
                editView.preview = PhotoEdit.filter(adjust)
            }
        }, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun endTool(apply: Boolean) {
        if (apply) {
            when (tool) {
                Tool.CROP -> editView.crop.takeIf { it.width() < 0.999f || it.height() < 0.999f }
                    ?.let { ops.add(EditOp.Crop(RectF(it))) }
                Tool.ADJUST -> if (!PhotoOps.isIdentity(adjust.brightness, adjust.contrast, adjust.saturation, adjust.warmth)) {
                    ops.add(adjust)
                }
                Tool.HIDE -> if (editView.hidden.isNotEmpty()) ops.add(EditOp.Hide(editView.hidden.map { RectF(it) }))
                Tool.DRAW -> if (editView.strokes.isNotEmpty()) ops.add(EditOp.Draw(ArrayList(editView.strokes)))
                Tool.NONE -> {}
            }
        }
        tool = Tool.NONE
        editView.mode = PhotoEditView.Mode.VIEW
        editView.preview = null
        editView.resetTools()
        panel.removeAllViews()
        if (apply) refreshPreview() else refreshButtons()
    }

    private fun rotate() {
        if (small == null) return
        val last = ops.lastOrNull()
        if (last is EditOp.Rotate) {
            ops[ops.lastIndex] = EditOp.Rotate((last.quarters + 1) % 4)
            if ((last.quarters + 1) % 4 == 0) ops.removeAt(ops.lastIndex)
        } else {
            ops.add(EditOp.Rotate(1))
        }
        refreshPreview()
    }

    private fun undoLast() {
        if (ops.isEmpty()) return
        ops.removeAt(ops.lastIndex)
        refreshPreview()
    }

    private fun findFaces() {
        val shown = editView.bitmap ?: return
        work({ PhotoEdit.findFaces(shown) }) { faces ->
            if (faces.isEmpty()) {
                Toast.makeText(this, R.string.edit_faces_none, Toast.LENGTH_LONG).show()
            } else {
                editView.hidden.addAll(faces)
                editView.invalidate()
                Toast.makeText(this, resources.getQuantityString(R.plurals.edit_faces_found, faces.size, faces.size),
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- saving ----------------------------------------------------------------------------------

    private fun saveCopy() {
        val big = full ?: return
        if (ops.isEmpty() || saving) return
        saving = true
        refreshButtons()
        val steps = ArrayList(ops)
        val ctx = applicationContext
        val src = source
        val when_ = if (takenAt > 0) takenAt else System.currentTimeMillis()
        work({
            val out = PhotoEdit.render(big, steps)
            val jpeg = ByteArrayOutputStream().use { bos ->
                out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bos)
                bos.toByteArray()
            }
            out.recycle()
            try {
                when (src) {
                    Source.VAULT -> {
                        val pub = VaultKeys.publicKey(ctx) ?: throw IllegalStateException("no vault")
                        VaultStore.savePhoto(ctx, pub, jpeg, when_)
                        null
                    }
                    Source.GALLERY -> {
                        VaultTransfer.saveJpegToGallery(ctx, jpeg)
                        null
                    }
                    Source.SHARE -> {
                        VaultTransfer.clearShared(ctx, olderThanMs = 60 * 60 * 1000L)
                        val f = File(VaultTransfer.shareDir(ctx), "ZT_" + VaultTransfer.randomName() + ".jpg")
                        f.writeBytes(jpeg)
                        f
                    }
                }
            } finally {
                jpeg.fill(0)
            }
        }, failed = {
            saving = false
            refreshButtons()
        }) { file ->
            when (src) {
                Source.VAULT -> Toast.makeText(this, R.string.edit_saved_vault, Toast.LENGTH_LONG).show()
                Source.GALLERY -> Toast.makeText(this, R.string.edit_saved_gallery, Toast.LENGTH_LONG).show()
                Source.SHARE -> if (file != null) share(file)
            }
            finish()
        }
    }

    private fun share(file: File) {
        val u = FileProvider.getUriForFile(this, "$packageName.vaultshare", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, u)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri(null, u)
        startActivity(Intent.createChooser(send, null).putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(
            ComponentName(this, CleanShareActivity::class.java),
            ComponentName(this, PhotoEditActivity::class.java),
        )))
    }

    // ---- leaving ---------------------------------------------------------------------------------

    private fun back() {
        when {
            tool != Tool.NONE -> endTool(false)
            ops.isEmpty() || saving -> finish()
            else -> com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.edit_discard_title)
                .setPositiveButton(R.string.edit_discard) { _, _ -> finish() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onStop() {
        super.onStop()
        // A vault photo doesn't stay open behind other apps or a locked screen: the vault locks
        // and this screen closes. A copy already being saved still finishes (it needs only the
        // public key).
        if (::source.isInitialized && source == Source.VAULT && !isChangingConfigurations && !isFinishing) {
            VaultSession.lock()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::io.isInitialized) io.shutdown()
        full = null
        small = null
    }

    /** Runs [job] off the main thread with the spinner up, then [done] on the main thread. */
    private fun <T> work(job: () -> T, failed: () -> Unit = {}, done: (T) -> Unit) {
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
                    Toast.makeText(this, R.string.edit_failed, Toast.LENGTH_LONG).show()
                    failed()
                    if (small == null) finish()
                }
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "PhotoEdit"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val EXTRA_VAULT_ID = "vaultId"
        private const val EXTRA_SHARE = "share"
        private const val EXTRA_TOKEN = "token"
        private const val MAX_SIDE = 4096
        private const val PREVIEW_MAX = 1600
        private const val JPEG_QUALITY = 92
        private val DRAW_COLOURS = intArrayOf(
            Color.rgb(0xF4, 0x43, 0x36), Color.rgb(0xFF, 0xEB, 0x3B), Color.rgb(0x4C, 0xAF, 0x50),
            Color.rgb(0x21, 0x96, 0xF3), Color.WHITE, Color.BLACK,
        )

        /**
         * Proves a launch came from this app: other apps can open the editor only through "Edit
         * with" on their own photo, never on a vault item or to share one onward.
         */
        private val token: String = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }

        /** Edits a vault photo; the vault must be unlocked. */
        fun openVault(ctx: Context, id: String) {
            ctx.startActivity(Intent(ctx, PhotoEditActivity::class.java)
                .putExtra(EXTRA_VAULT_ID, id)
                .putExtra(EXTRA_TOKEN, token))
        }

        /** "Clean and share", Edit first: edits a shared photo, then shares the copy onward. */
        fun openForShare(ctx: Context, uri: Uri) {
            ctx.startActivity(Intent(ctx, PhotoEditActivity::class.java)
                .setData(uri)
                .putExtra(EXTRA_SHARE, true)
                .putExtra(EXTRA_TOKEN, token)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }
}
