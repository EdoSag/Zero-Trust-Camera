package dev.sagron.zerotrustcamera.capture.zt

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.sagron.zerotrustcamera.capture.R
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * "Clean and share": shows up in every app's share sheet for photos and videos. It makes copies
 * with no place, time or phone details and shares those instead, or keeps the items in the
 * Private vault. The originals are never changed.
 */
class CleanShareActivity : ZtBaseActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Rotation doesn't recreate this screen (see the manifest). If Android recreates it anyway
        // (it was killed in the background), the shared items are gone: close rather than sit
        // there empty.
        if (savedInstanceState != null) {
            finish()
            return
        }
        val uris = incoming()
        if (uris.isEmpty()) {
            finish()
            return
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clean_share_title)
            .setMessage(R.string.clean_share_message)
            .setPositiveButton(R.string.clean_share_send) { _, _ -> clean(uris) }
            .setOnCancelListener { finish() }
        if (VaultKeys.isSetUp(this)) {
            dialog.setNeutralButton(R.string.clean_share_to_vault) { _, _ -> keep(uris) }
        }
        dialog.show()
    }

    private fun incoming(): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
        else -> emptyList()
    }.filter { it.scheme == "content" } // never a file path into this app's own storage

    private fun clean(uris: List<Uri>) {
        Toast.makeText(this, R.string.clean_share_working, Toast.LENGTH_SHORT).show()
        val ctx = applicationContext
        io.execute {
            // Copies from earlier shares are wiped once the app they went to has had time to read them.
            VaultTransfer.clearShared(ctx, olderThanMs = 60 * 60 * 1000L)
            val done = uris.mapNotNull { cleanOne(it) }
            main.post { share(done, uris.size - done.size) }
        }
    }

    private fun cleanOne(uri: Uri): Pair<File, String>? {
        val resolver = contentResolver
        val mime = resolver.getType(uri) ?: return null
        val base = File(VaultTransfer.shareDir(this), "ZT_" + VaultTransfer.randomName())
        return try {
            when {
                mime.startsWith("image/") -> {
                    val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
                    try {
                        MediaCleaner.cleanImage(bytes, mime, base)
                    } finally {
                        bytes.fill(0)
                    }
                }
                mime in MediaCleaner.VIDEO_TYPES -> {
                    val out = File(base.path + "." + MediaCleaner.videoExtension(mime))
                    try {
                        resolver.openInputStream(uri)!!.use { input -> FileOutputStream(out).use { input.copyTo(it) } }
                        MediaCleaner.cleanVideo(out)
                    } catch (e: Exception) {
                        VaultStore.wipe(out)
                        throw e
                    }
                    out to mime
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "clean", e)
            null
        }
    }

    private fun share(files: List<Pair<File, String>>, skipped: Int) {
        if (isFinishing || isDestroyed) return
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.clean_share_none, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (skipped > 0) {
            Toast.makeText(this, getString(R.string.clean_share_skipped, skipped), Toast.LENGTH_LONG).show()
        }
        val uris = files.map { FileProvider.getUriForFile(this, "$packageName.vaultshare", it.first) }
        val types = files.map { it.second }
        val type = when {
            types.distinct().size == 1 -> types[0]
            types.all { it.startsWith("image/") } -> "image/*"
            types.all { it.startsWith("video/") } -> "video/*"
            else -> "*/*"
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = type
        send.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, null)
            // Not back to this screen again.
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, CleanShareActivity::class.java)))
        startActivity(chooser)
        finish()
    }

    private fun keep(uris: List<Uri>) {
        val ctx = applicationContext
        val pub = VaultKeys.publicKey(ctx)
        if (pub == null) {
            finish()
            return
        }
        io.execute {
            val added = uris.count { VaultTransfer.import(ctx, pub, it) }
            main.post {
                Toast.makeText(ctx, if (added > 0) getString(R.string.clean_share_added_to_vault)
                    else getString(R.string.clean_share_none), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdown()
    }

    private companion object {
        const val TAG = "CleanShare"
    }
}
