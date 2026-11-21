package dev.sagron.zerotrustcamera.capture.zt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.SecureRandom
import org.json.JSONObject

/**
 * The Private vault on storage, under noBackupFilesDir/vault (not in Android backups, not in the
 * gallery, not readable by other apps). Every item has a random id and three sealed files:
 * `<id>.media` (the JPEG or MP4), `<id>.thumb` (a small JPEG) and `<id>.meta` (JSON: type, size,
 * when it was taken). Albums, favourites and the trash are one sealed `state` file the vault
 * screens keep.
 *
 * Saving needs only the public key, so it works while the phone is locked. Reading needs the
 * unlocked private key (VaultSession). Each file is written to a `.part` file first and renamed,
 * so a crash never leaves half an item.
 */
object VaultStore {
    const val MIME_DNG = "image/x-adobe-dng"
    private const val TAG = "VaultStore"
    const val TYPE_PHOTO = "photo"
    const val TYPE_VIDEO = "video"
    private const val THUMB_SIDE = 384

    private val random = SecureRandom()

    fun dir(ctx: Context): File = File(ctx.noBackupFilesDir, "vault").apply { mkdirs() }

    /** Where a video is recorded before it is sealed. Emptied of leftovers on each unlock. */
    fun tempDir(ctx: Context): File = File(dir(ctx), "tmp").apply { mkdirs() }

    fun newTempFile(ctx: Context, ext: String): File = File(tempDir(ctx), newId() + ext)

    private fun newId(): String {
        val b = ByteArray(16)
        random.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun file(ctx: Context, id: String, kind: String): File {
        require(id.matches(Regex("[0-9a-f]{32}"))) { "bad id" }
        return File(dir(ctx), "$id.$kind")
    }

    // ---- saving (public key only) --------------------------------------------------------------

    /** Latitude and longitude, kept only in the item's sealed details (see VaultLocation). */
    private fun putPlace(meta: JSONObject, place: DoubleArray?) {
        if (place != null && place.size == 2) meta.put("lat", place[0]).put("lon", place[1])
    }

    /** Seals a finished photo. Returns the item id and the thumbnail for the viewfinder. */
    fun savePhoto(
        ctx: Context,
        pub: ByteArray,
        jpeg: ByteArray,
        takenAt: Long,
        imported: Boolean = false,
        mime: String = "image/jpeg",
        place: DoubleArray? = null,
    ): Pair<String, Bitmap?> {
        val id = newId()
        val thumb = photoThumbnail(jpeg)
        val meta = JSONObject()
            .put("v", 1)
            .put("type", TYPE_PHOTO)
            .put("mime", mime)
            .put("takenAt", takenAt)
            .put("size", jpeg.size)
            .put("imported", imported)
        putPlace(meta, place)
        thumb?.let { meta.put("width", it.second.first).put("height", it.second.second) }
        writeSealed(file(ctx, id, "media"), pub, ByteArrayInputStream(jpeg))
        thumb?.let { writeSealed(file(ctx, id, "thumb"), pub, ByteArrayInputStream(it.first)) }
        writeSealed(file(ctx, id, "meta"), pub, ByteArrayInputStream(meta.toString().toByteArray()))
        return id to thumb?.let { BitmapHolder.decode(it.first) }
    }

    /**
     * Seals a RAW photo (DNG) from the pro camera, with the thumbnail and size taken from the JPEG
     * shot alongside it, and wipes the plain DNG file. Returns the item id.
     */
    fun saveRaw(
        ctx: Context,
        pub: ByteArray,
        dng: File,
        jpeg: ByteArray,
        takenAt: Long,
        place: DoubleArray? = null,
    ): String {
        try {
            val id = newId()
            val thumb = photoThumbnail(jpeg)
            val meta = JSONObject()
                .put("v", 1)
                .put("type", TYPE_PHOTO)
                .put("mime", MIME_DNG)
                .put("takenAt", takenAt)
                .put("size", dng.length())
                .put("imported", false)
            putPlace(meta, place)
            thumb?.let { meta.put("width", it.second.first).put("height", it.second.second) }
            FileInputStream(dng).use { writeSealed(file(ctx, id, "media"), pub, it) }
            thumb?.let { writeSealed(file(ctx, id, "thumb"), pub, ByteArrayInputStream(it.first)) }
            writeSealed(file(ctx, id, "meta"), pub, ByteArrayInputStream(meta.toString().toByteArray()))
            return id
        } finally {
            wipe(dng)
        }
    }

    /**
     * Seals a finished recording and wipes the plain file it was recorded to. Returns the item id
     * and a thumbnail, or null if it couldn't be sealed (the plain file is wiped either way).
     */
    fun saveVideo(
        ctx: Context,
        pub: ByteArray,
        plain: File,
        takenAt: Long,
        imported: Boolean = false,
        place: DoubleArray? = null,
    ): Pair<String, Bitmap?>? {
        try {
            val id = newId()
            var frame: Bitmap? = null
            val meta = JSONObject()
                .put("v", 1)
                .put("type", TYPE_VIDEO)
                .put("mime", "video/mp4")
                .put("takenAt", takenAt)
                .put("size", plain.length())
                .put("imported", imported)
            putPlace(meta, place)
            try {
                MediaMetadataRetriever().use { r ->
                    r.setDataSource(plain.path)
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        ?.let { meta.put("durationMs", it) }
                    val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        ?.toIntOrNull() ?: 0
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    if (w != null && h != null) {
                        val turned = rot == 90 || rot == 270
                        meta.put("width", if (turned) h else w).put("height", if (turned) w else h)
                    }
                    frame = r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        THUMB_SIDE, THUMB_SIDE)
                }
            } catch (e: Exception) {
                Log.w(TAG, "video details", e)
            }
            FileInputStream(plain).use { writeSealed(file(ctx, id, "media"), pub, it) }
            frame?.let { bmp ->
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                writeSealed(file(ctx, id, "thumb"), pub, ByteArrayInputStream(out.toByteArray()))
            }
            writeSealed(file(ctx, id, "meta"), pub, ByteArrayInputStream(meta.toString().toByteArray()))
            return id to frame
        } catch (e: Exception) {
            Log.e(TAG, "saveVideo", e)
            return null
        } finally {
            wipe(plain)
        }
    }

    private fun writeSealed(target: File, pub: ByteArray, input: InputStream) {
        val part = File(target.path + ".part")
        FileOutputStream(part).use { file ->
            // The buffer is flushed (not only the file under it) before the sync and rename,
            // or the end of the sealed file would be lost.
            val out = file.buffered(1 shl 16)
            MediaVault.seal(input, out, pub)
            out.flush()
            file.fd.sync()
        }
        if (!part.renameTo(target)) {
            part.delete()
            throw java.io.IOException("rename failed")
        }
    }

    // Returns the JPEG bytes and the (width, height) of the full photo.
    private fun photoThumbnail(jpeg: ByteArray): Pair<ByteArray, Pair<Int, Int>>? = try {
        var size = 0 to 0
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(jpeg))) { decoder, info, _ ->
            size = info.size.width to info.size.height
            val scale = THUMB_SIDE.toFloat() / maxOf(info.size.width, info.size.height)
            if (scale < 1f) {
                decoder.setTargetSize(
                    maxOf(1, (info.size.width * scale).toInt()),
                    maxOf(1, (info.size.height * scale).toInt())
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        out.toByteArray() to size
    } catch (e: Exception) {
        Log.w(TAG, "thumbnail", e)
        null
    }

    /** Overwrites a plain file with zeros, then deletes it. */
    fun wipe(f: File) {
        try {
            if (f.exists()) {
                RandomAccessFile(f, "rw").use { raf ->
                    val zeros = ByteArray(1 shl 16)
                    var left = raf.length()
                    raf.seek(0)
                    while (left > 0) {
                        val n = minOf(left, zeros.size.toLong()).toInt()
                        raf.write(zeros, 0, n)
                        left -= n
                    }
                    raf.fd.sync()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "wipe", e)
        }
        f.delete()
    }

    // ---- reading (unlocked) --------------------------------------------------------------------

    /** Every item's details, newest first. Items whose details can't be opened are skipped. */
    fun list(ctx: Context, priv: ByteArray): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        dir(ctx).listFiles { f -> f.name.endsWith(".meta") }?.forEach { f ->
            val id = f.name.removeSuffix(".meta")
            try {
                val json = JSONObject(String(MediaVault.openBytes(f.readBytes(), priv)))
                json.put("id", id)
                json.put("hasThumb", file(ctx, id, "thumb").exists())
                if (file(ctx, id, "media").exists()) out.add(json)
            } catch (e: Exception) {
                Log.w(TAG, "item $id", e)
            }
        }
        out.sortByDescending { it.optLong("takenAt") }
        return out
    }

    fun thumb(ctx: Context, id: String, priv: ByteArray): ByteArray? =
        file(ctx, id, "thumb").takeIf { it.exists() }?.let { MediaVault.openBytes(it.readBytes(), priv) }

    fun media(ctx: Context, id: String, priv: ByteArray): ByteArray =
        FileInputStream(file(ctx, id, "media")).use { input ->
            val out = ByteArrayOutputStream()
            MediaVault.open(input.buffered(1 shl 16), out, priv)
            out.toByteArray()
        }

    /**
     * [length] bytes of an item from [offset], opened one chunk at a time: for backing up a
     * video too large to hold in memory. Shorter only at the end of the file.
     */
    fun mediaPart(ctx: Context, id: String, priv: ByteArray, offset: Long, length: Int): ByteArray {
        require(offset >= 0 && length >= 0) { "bad range" }
        FileInputStream(file(ctx, id, "media")).use { input ->
            MediaVault.Reader(input.channel, priv).use { reader ->
                val want = minOf(length.toLong(), maxOf(0L, reader.size() - offset)).toInt()
                val out = ByteArray(want)
                var done = 0
                while (done < want) {
                    val n = reader.read(offset + done, out, done, want - done)
                    if (n <= 0) break
                    done += n
                }
                return if (done == want) out else out.copyOf(done)
            }
        }
    }

    /** Where the parts of a large video coming back from the backup are put together. */
    fun restoreFile(ctx: Context, token: String): File {
        require(token.matches(Regex("[0-9a-f]{32}"))) { "bad token" }
        return File(tempDir(ctx), "$token.restore.mp4")
    }

    /** Opens an item into a plain file (for saving or sharing). */
    fun openTo(ctx: Context, id: String, priv: ByteArray, target: File) {
        FileInputStream(file(ctx, id, "media")).use { input ->
            FileOutputStream(target).buffered(1 shl 16).use { out ->
                MediaVault.open(input.buffered(1 shl 16), out, priv)
            }
        }
    }

    fun mediaFile(ctx: Context, id: String): File = file(ctx, id, "media")

    fun meta(ctx: Context, id: String, priv: ByteArray): JSONObject =
        JSONObject(String(MediaVault.openBytes(file(ctx, id, "meta").readBytes(), priv)))

    fun delete(ctx: Context, id: String) {
        for (kind in listOf("media", "thumb", "meta")) file(ctx, id, kind).delete()
    }

    // ---- scan history ----------------------------------------------------------------------------

    /** Keeps one scanned code, sealed like everything else (works while locked). */
    fun saveScan(ctx: Context, pub: ByteArray, text: String, at: Long) {
        val json = JSONObject().put("v", 1).put("text", text).put("at", at)
        writeSealed(file(ctx, newId(), "scan"), pub, ByteArrayInputStream(json.toString().toByteArray()))
    }

    /** Every kept scan, newest first. */
    fun listScans(ctx: Context, priv: ByteArray): List<Map<String, Any>> {
        val out = ArrayList<Map<String, Any>>()
        dir(ctx).listFiles { f -> f.name.endsWith(".scan") }?.forEach { f ->
            try {
                val json = JSONObject(String(MediaVault.openBytes(f.readBytes(), priv)))
                out.add(mapOf("id" to f.name.removeSuffix(".scan"), "text" to json.getString("text"),
                    "at" to json.optLong("at")))
            } catch (e: Exception) {
                Log.w(TAG, "scan ${f.name}", e)
            }
        }
        out.sortByDescending { it["at"] as Long }
        return out
    }

    fun deleteScan(ctx: Context, id: String) {
        file(ctx, id, "scan").delete()
    }

    // ---- albums, favourites and trash ----------------------------------------------------------

    private fun stateFile(ctx: Context) = File(dir(ctx), "state")

    fun readState(ctx: Context, priv: ByteArray): String? =
        stateFile(ctx).takeIf { it.exists() }?.let { String(MediaVault.openBytes(it.readBytes(), priv)) }

    fun writeState(ctx: Context, pub: ByteArray, json: String) {
        writeSealed(stateFile(ctx), pub, ByteArrayInputStream(json.toByteArray()))
    }

    /**
     * Removes leftovers of a crash: recordings never sealed, half-written files. Only old ones, so
     * a recording still being sealed is left alone.
     */
    fun cleanUp(ctx: Context) {
        val before = System.currentTimeMillis() - 30 * 60 * 1000L
        tempDir(ctx).listFiles()?.filter { it.lastModified() < before }?.forEach { wipe(it) }
        dir(ctx).listFiles { f -> f.name.endsWith(".part") && f.lastModified() < before }
            ?.forEach { it.delete() }
    }
}

private object BitmapHolder {
    fun decode(jpeg: ByteArray): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(jpeg)))
    } catch (e: Exception) {
        null
    }
}
