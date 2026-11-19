package dev.sagron.zerotrustcamera.capture.zt

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidxc.exifinterface.media.ExifInterface
import dev.sagron.zerotrustcamera.capture.CamConfig
import dev.sagron.zerotrustcamera.capture.MetadataPolicy
import dev.sagron.zerotrustcamera.capture.applyMetadataPolicy
import dev.sagron.zerotrustcamera.capture.capturer.DEFAULT_MEDIA_STORE_CAPTURE_PATH
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Date

/**
 * Moving things in and out of the Private vault: import from the gallery, save to the gallery and
 * share. What leaves the vault is cleaned first: a photo keeps no Exif at all (no place, time or
 * phone), a video has its recording times cleared.
 */
object VaultTransfer {
    private const val TAG = "VaultTransfer"

    /** Plain files waiting to be shared, under the cache (see the FileProvider paths). */
    fun shareDir(ctx: Context): File = File(ctx.cacheDir, "vault-share").apply { mkdirs() }

    /** Wipes plain copies left from earlier shares. */
    fun clearShared(ctx: Context) {
        shareDir(ctx).listFiles()?.forEach { VaultStore.wipe(it) }
    }

    /** A cleaned plain copy of an item, for sharing or saving. Returns the file and its type. */
    fun cleanCopy(ctx: Context, id: String, priv: ByteArray, into: File = shareDir(ctx)): Pair<File, String> {
        val meta = VaultStore.meta(ctx, id, priv)
        val mime = meta.optString("mime", "image/jpeg")
        val isVideo = meta.optString("type") == VaultStore.TYPE_VIDEO
        // The name says nothing about when it was taken: a random one, not the capture time.
        val ext = if (isVideo) "mp4" else "jpg"
        val name = (if (isVideo) "VID_" else "IMG_") + randomName() + "." + ext
        val out = File(into, name)
        if (isVideo) {
            VaultStore.openTo(ctx, id, priv, out)
            RandomAccessFile(out, "rw").use { raf ->
                Mp4TimeScrubber.scrub(raf.channel, raf.channel)
            }
            return out to "video/mp4"
        }
        if (mime == "image/jpeg") {
            VaultStore.openTo(ctx, id, priv, out)
            val exif = ExifInterface(out)
            exif.applyMetadataPolicy(MetadataPolicy.NOTHING, Date())
            exif.saveAttributes()
        } else {
            // Any other picture (HEIC, PNG, WebP from an import) is redrawn as a JPEG, which
            // carries nothing over.
            val bytes = VaultStore.media(ctx, id, priv)
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)))
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bytes.fill(0)
        }
        return out to "image/jpeg"
    }

    private val random = java.security.SecureRandom()

    private fun randomName(): String {
        val b = ByteArray(6)
        random.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    /** Saves a cleaned copy into the phone's gallery (DCIM/Camera). */
    fun saveToGallery(ctx: Context, id: String, priv: ByteArray) {
        val temp = File(VaultStore.tempDir(ctx), "export").apply { mkdirs() }
        val (file, mime) = cleanCopy(ctx, id, priv, temp)
        try {
            val collection = if (mime.startsWith("video/")) CamConfig.videoCollectionUri
                else CamConfig.imageCollectionUri
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, DEFAULT_MEDIA_STORE_CAPTURE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(collection, cv) ?: throw java.io.IOException("no gallery row")
            try {
                resolver.openOutputStream(uri, "w")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } finally {
            VaultStore.wipe(file)
        }
    }

    /** Seals a photo or video picked from the gallery into the vault. True if it was added. */
    fun import(ctx: Context, pub: ByteArray, uri: Uri): Boolean {
        val resolver = ctx.contentResolver
        val mime = resolver.getType(uri) ?: ""
        val takenAt = takenAt(ctx, uri) ?: System.currentTimeMillis()
        // Only videos the export cleaner understands (MP4 and its relatives); a WebM or MKV would
        // leave the vault with its details still in it.
        if (mime.startsWith("video/") && mime !in IMPORT_VIDEO_TYPES) return false
        if (!mime.startsWith("video/") && !mime.startsWith("image/")) return false
        return try {
            if (mime.startsWith("video/")) {
                val temp = VaultStore.newTempFile(ctx, ".mp4")
                resolver.openInputStream(uri)!!.use { input ->
                    FileOutputStream(temp).use { input.copyTo(it) }
                }
                VaultStore.saveVideo(ctx, pub, temp, takenAt, imported = true) != null
            } else {
                val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
                VaultStore.savePhoto(ctx, pub, bytes, takenAt, imported = true,
                    mime = mime.ifEmpty { "image/jpeg" })
                bytes.fill(0)
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "import", e)
            false
        }
    }

    private val IMPORT_VIDEO_TYPES = setOf("video/mp4", "video/quicktime", "video/3gpp")

    private fun takenAt(ctx: Context, uri: Uri): Long? = try {
        ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null }
    } catch (e: Exception) {
        null
    }
}
