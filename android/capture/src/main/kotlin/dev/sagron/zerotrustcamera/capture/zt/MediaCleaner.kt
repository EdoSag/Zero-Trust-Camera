package dev.sagron.zerotrustcamera.capture.zt

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import androidxc.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Makes clean copies: no place, time, phone or other personal details. Used for what leaves the
 * Private vault and for "Clean and share" from other apps.
 */
object MediaCleaner {
    private const val TAG = "MediaCleaner"

    val VIDEO_TYPES = setOf("video/mp4", "video/quicktime", "video/3gpp")

    /**
     * Writes a clean copy of a picture to [base] plus the right extension and returns the file and
     * its type. A JPEG keeps its pixels as they are (only the details are cut out, see
     * JpegMetadataStripper); any other picture is redrawn, which carries nothing over.
     */
    fun cleanImage(bytes: ByteArray, mime: String, base: File): Pair<File, String> {
        if (mime == "image/jpeg" || mime == "image/jpg") {
            try {
                val orientation = ExifInterface(ByteArrayInputStream(bytes))
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val clean = JpegMetadataStripper.strip(bytes)
                val out = File(base.path + ".jpg")
                FileOutputStream(out).use { it.write(clean) }
                clean.fill(0)
                // Orientation was in the Exif that was cut out; without it the photo shows sideways.
                if (orientation != ExifInterface.ORIENTATION_NORMAL &&
                    orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                    val exif = ExifInterface(out)
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    exif.saveAttributes()
                }
                return out to "image/jpeg"
            } catch (e: IOException) {
                Log.w(TAG, "JPEG not walkable, redrawing it", e)
            }
        }
        // ImageDecoder turns the picture the right way up itself.
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { d, _, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        // PNG stays PNG so see-through parts stay see-through.
        val png = mime == "image/png"
        val out = File(base.path + if (png) ".png" else ".jpg")
        FileOutputStream(out).use {
            bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it)
        }
        bmp.recycle()
        return out to (if (png) "image/png" else "image/jpeg")
    }

    /** Clears a video's recording times and its tag boxes (place, phone, app), in place. */
    fun cleanVideo(file: File) {
        RandomAccessFile(file, "rw").use { raf ->
            Mp4TimeScrubber.scrub(raf.channel, raf.channel)
            Mp4MetadataStripper.strip(raf.channel)
            raf.fd.sync()
        }
    }

    fun videoExtension(mime: String) = when (mime) {
        "video/quicktime" -> "mov"
        "video/3gpp" -> "3gp"
        else -> "mp4"
    }
}
