package dev.sagron.zerotrustcamera.capture.zt

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * Reading text from pictures, on the phone, with Tesseract. The language files ship inside the
 * app (assets/tessdata, fetched at build time by tool/fetch_tessdata.sh): nothing is downloaded
 * on the phone, and pictures never leave it. They are copied out once, because Tesseract reads from a folder.
 */
object Ocr {
    private const val TAG = "Ocr"
    private val lock = Any()

    /** The languages in the app, e.g. "eng+heb", or null when none were bundled. */
    fun languages(ctx: Context): String? {
        val files = ctx.assets.list("tessdata").orEmpty().filter { it.endsWith(".traineddata") }
        if (files.isEmpty()) return null
        // English first: it reads digits and Latin letters inside Hebrew text best.
        return files.map { it.removeSuffix(".traineddata") }.sortedBy { if (it == "eng") 0 else 1 }
            .joinToString("+")
    }

    private fun dataDir(ctx: Context): File? {
        val root = File(ctx.noBackupFilesDir, "ocr")
        val dir = File(root, "tessdata").apply { mkdirs() }
        val names = ctx.assets.list("tessdata").orEmpty().filter { it.endsWith(".traineddata") }
        if (names.isEmpty()) return null
        // Copied again after each app update, in case the files changed.
        val stamp = File(root, "stamp")
        val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime.toString()
        if (stamp.takeIf { it.exists() }?.readText() != version || names.any { !File(dir, it).exists() }) {
            dir.listFiles()?.forEach { it.delete() }
            for (name in names) {
                val part = File(dir, "$name.part")
                ctx.assets.open("tessdata/$name").use { input -> part.outputStream().use { input.copyTo(it) } }
                part.renameTo(File(dir, name))
            }
            stamp.writeText(version)
        }
        return root
    }

    /** The text in [bitmap], or null when it can't be read. Slow: never on the main thread. */
    fun read(ctx: Context, bitmap: Bitmap): String? = synchronized(lock) {
        val langs = languages(ctx) ?: return null
        val root = dataDir(ctx) ?: return null
        val tess = TessBaseAPI()
        try {
            if (!tess.init(root.path, langs)) return null
            tess.setImage(bitmap)
            tess.getUTF8Text()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "read", e)
            null
        } finally {
            tess.recycle()
        }
    }
}
