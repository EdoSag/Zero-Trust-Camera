package dev.sagron.zerotrustcamera.capture.capturer

import java.io.FileInputStream
import java.io.FileOutputStream
import dev.sagron.zerotrustcamera.capture.zt.Mp4TimeScrubber
import android.util.Log
import android.Manifest
import android.animation.ValueAnimator
import android.content.ContentResolver
import android.content.ContentValues
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.location.Location
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.view.View
import android.webkit.MimeTypeMap
import androidx.annotation.VisibleForTesting
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import dev.sagron.zerotrustcamera.capture.App
import dev.sagron.zerotrustcamera.capture.CamConfig
import dev.sagron.zerotrustcamera.capture.CapturedItem
import dev.sagron.zerotrustcamera.capture.ITEM_TYPE_VIDEO
import dev.sagron.zerotrustcamera.capture.R
import dev.sagron.zerotrustcamera.capture.VIDEO_NAME_PREFIX
import dev.sagron.zerotrustcamera.capture.ui.activities.MainActivity
import dev.sagron.zerotrustcamera.capture.ui.activities.SecureMainActivity
import dev.sagron.zerotrustcamera.capture.ui.activities.VideoCaptureActivity
import dev.sagron.zerotrustcamera.capture.util.formatVideoDuration
import dev.sagron.zerotrustcamera.capture.util.getTreeDocumentUri
import dev.sagron.zerotrustcamera.capture.util.isForeignOutputUriAllowed
import dev.sagron.zerotrustcamera.capture.util.removePendingFlagFromUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.sagron.zerotrustcamera.capture.zt.VaultStore
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VideoCapturer(private val mActivity: MainActivity) {

    val camConfig = mActivity.camConfig

    var isRecording = false
        private set

    private val videoFileFormat = ".mp4"

    private var recording: Recording? = null

    // Invoking this abandons a start still waiting on its output or queued behind the record-start
    // sound.
    private var cancelDeferredStart: (() -> Unit)? = null

    // Recording, but still waiting on the output or the record-start sound
    val isStartPending: Boolean
        get() = cancelDeferredStart != null

    var isMuted = false
        private set

    var includeAudio: Boolean = false

    var isPaused = false
        set(value) {
            if (isRecording) {
                if (value) {
                    recording?.pause()
                    mActivity.setFlipCameraIcon(R.drawable.play, R.string.resume_recording)
                } else {
                    recording?.resume()
                    mActivity.setFlipCameraIcon(R.drawable.pause, R.string.pause_recording)
                }
            }
            field = value
        }

    private val handler = Handler(Looper.getMainLooper())

    // The output is created, opened, saved and discarded through this
    @set:VisibleForTesting
    var contentResolver: ContentResolver = mActivity.contentResolver

    // What CameraX leaves free on storage it can see: room for what is still being encoded when
    // the recording stops, and for the index the file needs to play
    @set:VisibleForTesting
    var requiredFreeStorageBytes = 50L * 1024 * 1024

    private fun updateTimerTime(timeInNanos: Long) {
        mActivity.timerView.text = formatVideoDuration(timeInNanos / 1_000_000_000)
    }

    private class RecordingContext(
        val uri: Uri,
        // Open until the recording ends: how much room is left for it is read through this
        val fileDescriptor: ParcelFileDescriptor,
        val shouldAddToGallery: Boolean,
        val isPendingMediaStoreUri: Boolean,
        // ZT Camera, Private mode: the recording goes to this file inside the app, and is sealed
        // to vaultKey and wiped as soon as it stops. It never reaches the gallery.
        val vaultTemp: File? = null,
        val vaultKey: ByteArray? = null,
        // ZT Camera, "Record in memory": the recording is held in memory, never in a file.
        // This descriptor keeps it alive until it is sealed; vaultTemp is then its /proc path.
        val memory: ParcelFileDescriptor? = null,
    ) {
        var isFinalized = false

        // The free storage is read again once the recording has grown to this many bytes
        var nextStorageCheckBytes = 0L

        var isCheckingStorage = false
    }

    // ZT Camera, "Record in memory" (beta): the recording goes into memory the system gives the
    // app (memfd), which behaves like a file but is never written to storage. Null where that
    // isn't possible (Android 10, or the system says no); the usual wiped temp file is used then.
    private fun createMemoryRecording(vaultKey: ByteArray): RecordingContext? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        var keep: ParcelFileDescriptor? = null
        var out: ParcelFileDescriptor? = null
        try {
            val fd = Os.memfd_create("zt-recording", 0)
            try {
                keep = ParcelFileDescriptor.dup(fd)
                out = ParcelFileDescriptor.dup(fd)
            } finally {
                Os.close(fd)
            }
            val path = File("/proc/self/fd/" + keep!!.fd)
            // Sealing reads it back through this path: make sure that works before recording.
            FileInputStream(path).close()
            return RecordingContext(Uri.fromFile(path), out!!, shouldAddToGallery = false,
                isPendingMediaStoreUri = false, vaultTemp = path, vaultKey = vaultKey, memory = keep)
        } catch (e: Exception) {
            Log.w("VideoCapturer", "no in-memory recording", e)
            try { keep?.close() } catch (ignored: Exception) {}
            try { out?.close() } catch (ignored: Exception) {}
            return null
        }
    }

    // The most an in-memory recording may grow to: an eighth of the phone's memory, between
    // 128 MB and 768 MB. The recording stops by itself there and is saved.
    private fun memoryLimitBytes(): Long {
        val info = ActivityManager.MemoryInfo()
        (mActivity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return (info.totalMem / 8).coerceIn(128L * 1024 * 1024, 768L * 1024 * 1024)
    }

    // Runs on outputExecutor: each step is a call into another process, MediaProvider or the app
    // the video is for, and can take seconds to answer.
    private fun createRecordingContext(
        fileName: String,
        foreignUri: Uri?,
        storageLocation: String,
        vaultKey: ByteArray?,
    ): RecordingContext? {
        if (vaultKey != null && foreignUri == null) {
            if (camConfig.recordInMemory) {
                createMemoryRecording(vaultKey)?.let { return it }
            }
            val temp = VaultStore.newTempFile(mActivity, videoFileFormat)
            val pfd = ParcelFileDescriptor.open(temp,
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or
                        ParcelFileDescriptor.MODE_TRUNCATE)
            return RecordingContext(Uri.fromFile(temp), pfd, shouldAddToGallery = false,
                isPendingMediaStoreUri = false, vaultTemp = temp, vaultKey = vaultKey)
        }

        val mimeType =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(videoFileFormat) ?: "video/mp4"

        val uri: Uri?
        var shouldAddToGallery = true
        var isPendingMediaStoreUri = false

        if (foreignUri != null) {
            check(isForeignOutputUriAllowed(mActivity, foreignUri)) { "refused output uri $foreignUri" }
            uri = foreignUri
            shouldAddToGallery = false
        } else {
            if (storageLocation == CamConfig.SettingValues.Default.STORAGE_LOCATION) {
                val contentValues = ContentValues().apply {
                    put(MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaColumns.MIME_TYPE, mimeType)
                    put(MediaColumns.RELATIVE_PATH, DEFAULT_MEDIA_STORE_CAPTURE_PATH)
                    put(MediaColumns.IS_PENDING, 1)
                }
                uri = contentResolver.insert(CamConfig.videoCollectionUri, contentValues)
                isPendingMediaStoreUri = true
            } else {
                val treeUri = Uri.parse(storageLocation)
                val treeDocumentUri = getTreeDocumentUri(treeUri)

                uri = DocumentsContract.createDocument(contentResolver, treeDocumentUri, mimeType, fileName)
            }
        }

        if (uri == null) {
            return null
        }

        return contentResolver
            .openFileDescriptor(uri, "w")
            ?.let {
                RecordingContext(uri, it, shouldAddToGallery, isPendingMediaStoreUri)
            }
    }

    private fun prepareRecording(recorder: Recorder, recordingCtx: RecordingContext): PendingRecording {
        var location: Location? = null
        if (camConfig.requireLocation) {
            location = (mActivity.applicationContext as App).getLocation()
            if (location == null) {
                mActivity.showMessage(R.string.location_unavailable)
            }
        }
        val builder = FileDescriptorOutputOptions.Builder(recordingCtx.fileDescriptor)
        builder.setLocation(location)
        if (recordingCtx.memory != null) {
            builder.setFileSizeLimit(memoryLimitBytes())
        }
        val outputOptions = builder.build()
        return recorder.prepareRecording(mActivity, outputOptions)
    }

    fun startRecording() {
        if (camConfig.camera == null) return
        val recorder = camConfig.videoCapture?.output ?: return
        if (isRecording) return
        isRecording = true

        val dateString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = VIDEO_NAME_PREFIX + dateString + videoFileFormat

        includeAudio = false

        val ctx = mActivity

        if (ctx.settingsDialog.includeAudioToggle.isChecked) {
            if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED) {
                includeAudio = true
            } else {
                ctx.restartRecordingWithMicPermission()
                isRecording = false
                return
            }
        }

        val foreignUri = if (ctx is VideoCaptureActivity && ctx.isOutputUriAvailable()) {
            ctx.outputUri
        } else {
            null
        }
        val storageLocation = camConfig.storageLocation
        val vaultKey = if (foreignUri == null) camConfig.vaultKeyForCapture() else null
        val startedAt = System.currentTimeMillis()

        beforeRecordingStarts()

        // A stop cancels the start until the recording exists, and the sound callback may fire
        // more than once; a second PendingRecording.start() throws.
        var consumed = false
        // A cancel discards the output if it exists by then, and its arrival does otherwise
        var createdCtx: RecordingContext? = null

        cancelDeferredStart = {
            consumed = true
            cancelDeferredStart = null
            createdCtx?.let { abandonOutput(it) }
            afterRecordingStops()
        }

        outputExecutor.execute {
            val recordingCtx = try {
                createRecordingContext(fileName, foreignUri, storageLocation, vaultKey)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            handler.post {
                if (consumed) {
                    recordingCtx?.let { abandonOutput(it) }
                    return@post
                }

                val pendingRecording = try {
                    recordingCtx?.let { prepareRecording(recorder, it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                if (recordingCtx == null || pendingRecording == null) {
                    recordingCtx?.let { abandonOutput(it) }
                    if (foreignUri == null && vaultKey == null) {
                        camConfig.onStorageLocationNotFound()
                    }
                    ctx.showMessage(R.string.unable_to_access_output_file)
                    cancelDeferredStart = null
                    afterRecordingStops()
                    return@post
                }

                createdCtx = recordingCtx

                if (includeAudio) {
                    pendingRecording.withAudioEnabled()
                }

                camConfig.mPlayer.playVRStartSound(handler) {
                    if (consumed) {
                        return@playVRStartSound
                    }

                    consumed = true
                    cancelDeferredStart = null

                    recording = pendingRecording.start(ctx.mainExecutor) { event ->
                        onRecordEvent(event, recordingCtx, dateString, startedAt)
                    }

                    // The Recording didn't exist yet when the mute/pause setters ran.
                    if (isMuted) {
                        recording?.mute(true)
                    }

                    if (isPaused) {
                        recording?.pause()
                    }
                }
            }
        }
    }

    private fun onRecordEvent(
        event: VideoRecordEvent,
        recordingCtx: RecordingContext,
        dateString: String,
        startedAt: Long,
    ) {
        val ctx = mActivity

        if (event is VideoRecordEvent.Start) {
            onRecordingStart()
        }

        if (event is VideoRecordEvent.Status) {
            updateTimerTime(event.recordingStats.recordedDurationNanos)
            checkFreeStorage(recordingCtx, event.recordingStats.numBytesRecorded)
        }

        if (event is VideoRecordEvent.Finalize) {
            recordingCtx.isFinalized = true

            afterRecordingStops()

            camConfig.mPlayer.playVRStopSound()

            if (event.hasError()) {
                when (event.error) {
                    VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> {
                        abandonOutput(recordingCtx)
                        ctx.showMessage(R.string.recording_too_short_to_be_saved)
                        return
                    }
                    VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED,
                    VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR,
                    VideoRecordEvent.Finalize.ERROR_UNKNOWN -> {
                        // These cut the recording off without finishing its file the way a stop
                        // does. A muxer that runs out of storage still writes the index into the
                        // room it kept for it, and an encoder that dies leaves the muxer to stop
                        // normally, so what was recorded up to then often plays.
                        saveOutput(recordingCtx, dateString, startedAt, failedWithError = event.error)
                        return
                    }
                    else -> {
                        ctx.showMessage(ctx.getString(R.string.error_during_recording, event.error))
                        // The errors left unnamed here (the camera going away, storage
                        // running out) finalize whatever was written before they hit, which
                        // is worth keeping — but only if anything was.
                        if (event.recordingStats.numBytesRecorded == 0L) {
                            abandonOutput(recordingCtx)
                            return
                        }
                    }
                }
            }

            saveOutput(recordingCtx, dateString, startedAt)
        }
    }

    // CameraX only stops a recording before it fills up its storage when it can tell what that
    // storage is, and a file descriptor doesn't tell it. A recording that fills it up ends on a
    // failed write instead, leaving a file that only plays if the muxer had kept room for its
    // index.
    private fun checkFreeStorage(recordingCtx: RecordingContext, recordedBytes: Long) {
        if (recordingCtx.isCheckingStorage || recordedBytes < recordingCtx.nextStorageCheckBytes) {
            return
        }
        // Held in memory: its size limit is set up front, and storage isn't used.
        if (recordingCtx.memory != null) {
            return
        }
        recordingCtx.isCheckingStorage = true

        // Off the main thread: MediaProvider answers for the storage behind its descriptors
        outputExecutor.execute {
            val freeBytes = freeStorageBytes(recordingCtx.fileDescriptor)

            handler.post {
                recordingCtx.isCheckingStorage = false
                if (recordingCtx.isFinalized) {
                    return@post
                }

                val spareBytes = freeBytes - requiredFreeStorageBytes
                if (spareBytes <= 0) {
                    stopRecording()
                    mActivity.showMessage(R.string.recording_stopped_storage_almost_full)
                    return@post
                }
                // Rare while there is plenty left, and closer together as it runs out
                recordingCtx.nextStorageCheckBytes = recordedBytes + spareBytes / 2
            }
        }
    }

    // A video in MediaStore stays pending, hidden from other apps and left to be deleted, until
    // MediaProvider is told to make it visible: only then is it saved. A recording cut off by an
    // error is only saved if what it left plays.
    private fun saveOutput(
        recordingCtx: RecordingContext,
        dateString: String,
        startedAt: Long,
        failedWithError: Int? = null,
    ) {
        // Off the main thread for the same reason the output is created there
        outputExecutor.execute {
            closeOutputFile(recordingCtx)

            if (failedWithError != null && !isPlayable(recordingCtx.uri)) {
                discardUnusedOutput(recordingCtx)
                handler.post {
                    mActivity.showMessage(
                        mActivity.getString(R.string.unable_to_save_video_verbose, failedWithError)
                    )
                }
                return@execute
            }

            // ZT Camera: an MP4 always carries when it was recorded. Cleared here, while the file
            // is still hidden from other apps, unless the person chose to keep the date and time.
            // A video recorded for another app never keeps it.
            val forOtherApp = mActivity is VideoCaptureActivity
            val timeCleared =
                !(forOtherApp || !camConfig.keepDateTime) || clearRecordingDate(recordingCtx.uri)

            val vaultTemp = recordingCtx.vaultTemp
            if (vaultTemp != null) {
                val sealed = VaultStore.saveVideo(
                    mActivity, recordingCtx.vaultKey!!, vaultTemp, startedAt,
                    place = dev.sagron.zerotrustcamera.capture.zt.VaultLocation.now(mActivity),
                )
                releaseMemory(recordingCtx)
                handler.post {
                    if (sealed == null) {
                        mActivity.showMessage(R.string.unable_to_save_video)
                        return@post
                    }
                    failedWithError?.let {
                        mActivity.showMessage(mActivity.getString(R.string.error_during_recording, it))
                    }
                    if (!timeCleared) {
                        mActivity.showMessage(R.string.video_time_not_cleared)
                    }
                    mActivity.showThumbnail(null, sealed.second)
                }
                return@execute
            }

            if (!timeCleared && forOtherApp) {
                // The other app was promised a file with no recording time, and its file can't
                // be read back to clear it (it gave write access only). Nothing is handed over
                // rather than a file that tells when it was made.
                emptyOutput(recordingCtx.uri)
                handler.post {
                    mActivity.showMessage(R.string.unable_to_save_video)
                }
                return@execute
            }

            val saved = !recordingCtx.isPendingMediaStoreUri || try {
                removePendingFlagFromUri(contentResolver, recordingCtx.uri)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }

            handler.post {
                if (!saved) {
                    mActivity.showMessage(R.string.unable_to_save_video)
                    return@post
                }
                failedWithError?.let {
                    mActivity.showMessage(mActivity.getString(R.string.error_during_recording, it))
                }
                if (!timeCleared) {
                    // The person's own video is kept, and they are told what it still carries.
                    mActivity.showMessage(R.string.video_time_not_cleared)
                }
                onOutputSaved(recordingCtx, dateString)
            }
        }
    }

    // Runs on outputExecutor. True when the file no longer says when it was recorded.
    private fun clearRecordingDate(uri: Uri): Boolean = try {
        contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            // The streams must not be closed on their own: that would close the descriptor
            // under the other one. Closing the descriptor (use) ends both.
            val input = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)
            Mp4TimeScrubber.scrub(input.channel, output.channel) > 0
        } ?: false
    } catch (e: Exception) {
        Log.w("VideoCapturer", "could not clear the recording date", e)
        false
    }

    // Runs on outputExecutor. Leaves another app's output file with nothing in it.
    private fun emptyOutput(uri: Uri) {
        try {
            contentResolver.openFileDescriptor(uri, "wt")?.close()
        } catch (e: Exception) {
            Log.w("VideoCapturer", "could not empty the output file", e)
        }
    }

    // Runs on outputExecutor. A file the muxer never wrote an index into can't be read at all.
    private fun isPlayable(uri: Uri): Boolean = try {
        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            MediaMetadataRetriever().use {
                it.setDataSource(pfd.fileDescriptor)
                it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null
            }
        } ?: false
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    private fun onOutputSaved(recordingCtx: RecordingContext, dateString: String) {
        val ctx = mActivity
        val uri = recordingCtx.uri

        if (recordingCtx.shouldAddToGallery) {
            val item = CapturedItem(ITEM_TYPE_VIDEO, dateString, uri)
            camConfig.updateLastCapturedItem(item)

            ctx.updateThumbnail()

            if (ctx is SecureMainActivity) {
                ctx.capturedItems.add(item)
            }
        }

        if (ctx is VideoCaptureActivity) {
            ctx.afterRecording(uri)
        }
    }

    private val dp16 = 16 * mActivity.resources.displayMetrics.density
    private val dp8 = 8 * mActivity.resources.displayMetrics.density

    // Skinned devices wrap the capture button shape in selectors and layer-lists
    private fun findGradientDrawable(drawable: Drawable?): GradientDrawable? {
        return when (drawable) {
            is GradientDrawable -> drawable
            is StateListDrawable -> findGradientDrawable(drawable.current)
            is LayerDrawable -> {
                (0 until drawable.numberOfLayers)
                    .firstNotNullOfOrNull { findGradientDrawable(drawable.getDrawable(it)) }
            }
            else -> null
        }
    }

    // If no shape can be dug out, skip the cosmetic animation rather than crash
    private fun animateCaptureButtonCorners(from: Float, to: Float) {
        val gd = findGradientDrawable(mActivity.captureButton.drawable) ?: return

        val animator = ValueAnimator.ofFloat(from, to)
        animator.setDuration(300)
            .addUpdateListener { animation ->
                gd.cornerRadius = animation.animatedValue as Float
            }
        animator.start()
    }

    private fun beforeRecordingStarts() {
        // Don't leak paused/muted state from the previous recording into this one.
        isPaused = false
        isMuted = false

        // The recording is made with the recorder and audio choice of the press, but its output
        // can take seconds to arrive: these must not rebind the camera or flip the audio meanwhile.
        mActivity.settingsDialog.videoQualitySpinner.isEnabled = false
        mActivity.settingsDialog.enableEISToggle.isEnabled = false
        mActivity.settingsDialog.waitForFocusLockSwitch.isEnabled = false
        mActivity.settingsDialog.includeAudioToggle.isEnabled = false

        mActivity.previewView.keepScreenOn = true
    }

    private fun onRecordingStart() {
        // TODO: Uncomment this once the main indicator UI gets implemented
        // mActivity.micOffIcon.visibility = View.GONE

        animateCaptureButtonCorners(dp16, dp8)

        // The user may have paused before the recording actually started.
        if (isPaused) {
            mActivity.setFlipCameraIcon(R.drawable.play, R.string.resume_recording)
        } else {
            mActivity.setFlipCameraIcon(R.drawable.pause, R.string.pause_recording)
        }
        mActivity.cancelButtonView.visibility = View.GONE

        // Only the description changes: the drawable stays the same one the corner-radius
        // animation above is holding on to, and replacing it would cut that animation short.
        mActivity.captureButton.contentDescription = mActivity.getString(R.string.stop_recording)

        if (mActivity.requiresVideoModeOnly) {
            mActivity.thirdOption.visibility = View.INVISIBLE
        }

        // While recording, the gallery button turns into a shutter for stills
        mActivity.setThirdCircleIcon(R.drawable.camera_shutter, R.string.capture)
        mActivity.tabLayout.visibility = View.INVISIBLE
        mActivity.timerView.setText(R.string.start_value_timer)
        mActivity.timerView.visibility = View.VISIBLE

        if (camConfig.includeAudio) {
            mActivity.setMuteToggleState(muted = isMuted)
            mActivity.muteToggle.visibility = View.VISIBLE
        }
    }

    private fun afterRecordingStops() {
        animateCaptureButtonCorners(dp8, dp16)

        mActivity.timerView.visibility = View.GONE
        mActivity.setFlipCameraIcon(R.drawable.flip_camera, R.string.flip_camera)
        mActivity.captureButton.contentDescription =
            mActivity.getString(R.string.start_recording)

        mActivity.settingsDialog.videoQualitySpinner.isEnabled = true
        mActivity.settingsDialog.enableEISToggle.isEnabled = true

        if (mActivity !is VideoCaptureActivity) {
            mActivity.thirdOption.visibility = View.VISIBLE
        }

        if (!mActivity.requiresVideoModeOnly) {
            mActivity.settingsDialog.waitForFocusLockSwitch.isEnabled = true
        }

        // Always restore the third-circle icon and its accessibility label to the gallery button:
        // at record start it was repurposed into an in-video shutter ("Capture") unconditionally,
        // so restoring it only for non-VideoCaptureActivity would strand a stale "Capture" label
        // there. The non-recording third-circle click opens the gallery in every activity, so
        // open_gallery is the accurate label. Cancel/tab visibility stays guarded, as those don't
        // apply to VideoCaptureActivity.
        mActivity.setThirdCircleIcon(R.drawable.option_circle, R.string.open_gallery)

        if (mActivity !is VideoCaptureActivity) {
            mActivity.cancelButtonView.visibility = View.VISIBLE
            mActivity.tabLayout.visibility = View.VISIBLE
        }

        mActivity.previewView.keepScreenOn = false

        // TODO: Uncomment this once the main indicator UI gets implemented
        // if (!mActivity.config.includeAudio)
        //   mActivity.micOffIcon.visibility = View.VISIBLE

        mActivity.settingsDialog.includeAudioToggle.isEnabled = true
        mActivity.muteToggle.visibility = View.GONE

        isRecording = false

        mActivity.forceUpdateOrientationSensor()
    }

    fun muteRecording() {
        if (!isRecording) return
        check(camConfig.includeAudio)
        isMuted = true
        recording?.mute(true)
    }

    fun unmuteRecording() {
        if (!isRecording) return
        check(camConfig.includeAudio)
        isMuted = false
        recording?.mute(false)
    }

    fun stopRecording() {
        cancelDeferredStart?.let {
            it()
            return
        }

        recording?.stop()
        recording?.close()
        recording = null
    }

    fun abandonDeferredStart() {
        cancelDeferredStart?.invoke()
    }

    // Off the main thread for the same reason the output is created there
    private fun abandonOutput(recordingCtx: RecordingContext) {
        outputExecutor.execute {
            closeOutputFile(recordingCtx)
            discardUnusedOutput(recordingCtx)
        }
    }

    // Runs on outputExecutor: the app the video is for may only take the file as done once it is
    // closed. FileDescriptorOutputOptions leaves closing it to the caller, from the start on.
    private fun closeOutputFile(recordingCtx: RecordingContext) {
        try {
            recordingCtx.fileDescriptor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Gives an in-memory recording back to the system. Nothing of it is left anywhere.
    private fun releaseMemory(recordingCtx: RecordingContext) {
        try {
            recordingCtx.memory?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun discardUnusedOutput(recordingCtx: RecordingContext) {
        recordingCtx.vaultTemp?.let {
            // Memory is simply given back; a file is overwritten first.
            if (recordingCtx.memory != null) releaseMemory(recordingCtx) else VaultStore.wipe(it)
            return
        }

        if (!recordingCtx.shouldAddToGallery) {
            return
        }

        try {
            if (recordingCtx.isPendingMediaStoreUri) {
                contentResolver.delete(recordingCtx.uri, null, null)
            } else {
                DocumentsContract.deleteDocument(contentResolver, recordingCtx.uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        // Shared across activities like ImageSaver's: a stalled MediaProvider holds one thread, not
        // one per press
        private val outputExecutor = Executors.newSingleThreadExecutor()

        /**
         * Returns once the output work queued so far is done, and has posted back what it posts
         */
        @VisibleForTesting
        fun awaitOutputWork(timeoutS: Long) {
            outputExecutor.submit {}.get(timeoutS, TimeUnit.SECONDS)
        }
    }
}

private const val STALE_PENDING_RECORDING_AGE = 60 * 60 * 1000L

// A recording that dies with its process (swipe-away from Recents, OOM kill, crash) never reaches
// the Finalize callback that clears IS_PENDING, so it leaves a half-written file that is invisible
// to the user until MediaProvider expires it a week later, and unplayable in the meantime since it
// has no moov atom. Deleting is the honest outcome. Pending rows are only visible to the app that
// owns them, so this can never reach another app's in-flight write, and the age cutoff keeps it
// clear of a recording that is still being muxed.
fun deleteStalePendingRecordings(
    context: Context,
    maxAge: Long = STALE_PENDING_RECORDING_AGE,
) {
    val selection = "${MediaColumns.IS_PENDING} = 1" +
            " AND ${MediaColumns.DISPLAY_NAME} LIKE ?" +
            " AND ${MediaColumns.DATE_ADDED} < ?"
    val cutoffSeconds = (System.currentTimeMillis() - maxAge) / 1000L
    val args = arrayOf("$VIDEO_NAME_PREFIX%", cutoffSeconds.toString())

    try {
        // Pending rows are filtered out of every operation unless they are explicitly asked for.
        @Suppress("DEPRECATION")
        val collection = MediaStore.setIncludePending(CamConfig.videoCollectionUri)
        context.contentResolver.delete(collection, selection, args)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Long.MAX_VALUE when the descriptor doesn't tell: one from a provider that serves the file itself
// reports no storage at all
private fun freeStorageBytes(fd: ParcelFileDescriptor): Long = try {
    val stat = Os.fstatvfs(fd.fileDescriptor)
    if (stat.f_blocks > 0) stat.f_bavail * stat.f_frsize else Long.MAX_VALUE
} catch (e: ErrnoException) {
    Long.MAX_VALUE
}

@Throws(Exception::class)
fun getVideoThumbnail(context: Context, uri: Uri?): Bitmap? {
    MediaMetadataRetriever().use {
        it.setDataSource(context, uri)
        return it.frameAtTime
    }
}
