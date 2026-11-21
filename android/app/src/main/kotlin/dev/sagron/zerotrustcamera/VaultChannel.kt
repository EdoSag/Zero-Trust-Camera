package dev.sagron.zerotrustcamera

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import dev.sagron.zerotrustcamera.capture.CamConfig
import dev.sagron.zerotrustcamera.capture.zt.Ocr
import dev.sagron.zerotrustcamera.capture.zt.PhotoEditActivity
import dev.sagron.zerotrustcamera.capture.zt.VaultKeys
import dev.sagron.zerotrustcamera.capture.zt.VaultSession
import dev.sagron.zerotrustcamera.capture.zt.VaultStore
import dev.sagron.zerotrustcamera.capture.zt.VaultTransfer
import dev.sagron.zerotrustcamera.capture.zt.VaultVideoActivity
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

/**
 * The Private vault for the Flutter screens (lib/vault/). Everything that needs the private key
 * runs here, in Kotlin: Dart gets opened thumbnails and photos to show, never a key.
 */
class VaultChannel(
    private val activity: FragmentActivity,
    messenger: BinaryMessenger,
    private val pickMedia: () -> Unit,
) {
    private val channel = MethodChannel(messenger, CHANNEL)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var pendingImport: MethodChannel.Result? = null

    /**
     * True while the vault's own video player is on top: it locks the vault itself when it is
     * left. The photo picker and the share sheet get no such pass, since the vault would then
     * stay open if the phone were locked from them; neither needs it open (import needs only the
     * public key, and shared copies are made before the share sheet opens).
     */
    var keepOpenUntilResume = false

    init {
        channel.setMethodCallHandler(::onCall)
    }

    private fun onCall(call: MethodCall, result: MethodChannel.Result) {
        val ctx = activity.applicationContext
        when (call.method) {
            "status" -> result.success(mapOf(
                "setUp" to VaultKeys.isSetUp(ctx),
                "unlocked" to VaultSession.isUnlocked,
                "privateMode" to (CamConfig.isPrivateVaultOn(ctx) && VaultKeys.isSetUp(ctx)),
                "screenLock" to VaultKeys.hasScreenLock(ctx),
            ))
            "setUp" -> VaultKeys.setUp(activity) { r ->
                if (r == VaultKeys.Result.OK) CamConfig.setPrivateVaultOn(ctx, true)
                result.success(name(r))
            }
            "unlock" -> VaultKeys.unlock(activity) { r ->
                if (r == VaultKeys.Result.OK) {
                    io.execute {
                        VaultStore.cleanUp(ctx)
                        VaultTransfer.clearShared(ctx)
                    }
                }
                result.success(name(r))
            }
            "lock" -> {
                VaultSession.lock()
                result.success(null)
            }
            "setPrivateMode" -> {
                val on = call.argument<Boolean>("on") == true
                if (on && !VaultKeys.isSetUp(ctx)) {
                    result.error("not_set_up", null, null)
                } else {
                    CamConfig.setPrivateVaultOn(ctx, on)
                    result.success(null)
                }
            }
            "setSecure" -> {
                // No screenshots and a blank preview in Recents while the vault is on screen.
                if (call.argument<Boolean>("on") == true) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                result.success(null)
            }
            "list" -> unlocked(result) { priv ->
                VaultStore.list(ctx, priv).map { json ->
                    json.keys().asSequence().associateWith { k -> json.get(k) }
                }
            }
            "thumb" -> unlocked(result) { priv -> VaultStore.thumb(ctx, call.argument<String>("id")!!, priv) }
            "photo" -> unlocked(result) { priv ->
                val id = call.argument<String>("id")!!
                val bytes = VaultStore.media(ctx, id, priv)
                if (VaultStore.meta(ctx, id, priv).optString("mime") != VaultStore.MIME_DNG) bytes else {
                    // RAW photos are shown as a JPEG drawn here; Flutter can't read DNG.
                    val bmp = android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
                    ) { d, info, _ ->
                        d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        val long = maxOf(info.size.width, info.size.height)
                        if (long > 4096) {
                            val f = 4096f / long
                            d.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                        }
                    }
                    bytes.fill(0)
                    java.io.ByteArrayOutputStream().use { out ->
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                        bmp.recycle()
                        out.toByteArray()
                    }
                }
            }
            "original" -> unlocked(result) { priv ->
                // The file as sealed, for the encrypted backup (Dart encrypts it again with its
                // own key before anything leaves the phone).
                val id = call.argument<String>("id")!!
                mapOf(
                    "bytes" to VaultStore.media(ctx, id, priv),
                    "mime" to VaultStore.meta(ctx, id, priv).optString("mime", "image/jpeg"),
                )
            }
            "originalPart" -> unlocked(result) { priv ->
                // One piece of a large video, for the encrypted backup.
                VaultStore.mediaPart(
                    ctx,
                    call.argument<String>("id")!!,
                    priv,
                    call.argument<Number>("offset")!!.toLong(),
                    call.argument<Number>("length")!!.toInt(),
                )
            }
            "restorePart" -> background(result) {
                // A large video comes back in pieces, put together in the vault's temp folder
                // (app-private, wiped once sealed) like a recording.
                val bytes = call.argument<ByteArray>("bytes")!!
                try {
                    java.io.FileOutputStream(
                        VaultStore.restoreFile(ctx, call.argument<String>("token")!!), true
                    ).use { it.write(bytes) }
                } finally {
                    bytes.fill(0)
                }
                null
            }
            "restoreFinish" -> background(result) {
                val pub = VaultKeys.publicKey(ctx) ?: throw IllegalStateException("no vault")
                val f = VaultStore.restoreFile(ctx, call.argument<String>("token")!!)
                val takenAt = call.argument<Number>("takenAt")?.toLong() ?: System.currentTimeMillis()
                VaultStore.saveVideo(ctx, pub, f, takenAt)?.first
                    ?: throw java.io.IOException("video not sealed")
            }
            "restoreCancel" -> background(result) {
                VaultStore.wipe(VaultStore.restoreFile(ctx, call.argument<String>("token")!!))
                null
            }
            "restore" -> background(result) {
                val pub = VaultKeys.publicKey(ctx) ?: throw IllegalStateException("no vault")
                val bytes = call.argument<ByteArray>("bytes")!!
                val mime = call.argument<String>("mime") ?: "image/jpeg"
                val takenAt = call.argument<Number>("takenAt")?.toLong() ?: System.currentTimeMillis()
                try {
                    when {
                        call.argument<Boolean>("video") == true -> {
                            val f = VaultStore.newTempFile(ctx, ".mp4")
                            f.writeBytes(bytes)
                            VaultStore.saveVideo(ctx, pub, f, takenAt)?.first
                                ?: throw java.io.IOException("video not sealed")
                        }
                        mime == VaultStore.MIME_DNG -> {
                            val small = android.graphics.ImageDecoder.decodeBitmap(
                                android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
                            ) { d, info, _ ->
                                d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                                val long = maxOf(info.size.width, info.size.height)
                                if (long > 2048) {
                                    val f = 2048f / long
                                    d.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                                }
                            }
                            val jpeg = java.io.ByteArrayOutputStream().use { out ->
                                small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                                small.recycle()
                                out.toByteArray()
                            }
                            val f = VaultStore.newTempFile(ctx, ".dng")
                            f.writeBytes(bytes)
                            VaultStore.saveRaw(ctx, pub, f, jpeg, takenAt)
                        }
                        else -> VaultStore.savePhoto(ctx, pub, bytes, takenAt, mime = mime).first
                    }
                } finally {
                    bytes.fill(0)
                }
            }
            "readText" -> unlocked(result) { priv ->
                val bytes = VaultStore.media(ctx, call.argument<String>("id")!!, priv)
                val bmp = android.graphics.ImageDecoder.decodeBitmap(
                    android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
                ) { d, _, _ -> d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE }
                bytes.fill(0)
                Ocr.read(ctx, bmp).also { bmp.recycle() }
            }
            "editPhoto" -> {
                if (!VaultSession.isUnlocked) {
                    result.error("locked", null, null)
                    return
                }
                // The editor locks the vault itself if it is left for another app.
                keepOpenUntilResume = true
                PhotoEditActivity.openVault(activity, call.argument<String>("id")!!)
                result.success(null)
            }
            "scans" -> unlocked(result) { priv -> VaultStore.listScans(ctx, priv) }
            "deleteScans" -> background(result) {
                call.argument<List<String>>("ids")!!.forEach { VaultStore.deleteScan(ctx, it) }
                null
            }
            "scanHistory" -> result.success(CamConfig.isScanHistoryOn(ctx))
            "setScanHistory" -> {
                CamConfig.setScanHistoryOn(ctx, call.argument<Boolean>("on") == true)
                result.success(null)
            }
            "readState" -> unlocked(result) { priv -> VaultStore.readState(ctx, priv) }
            "writeState" -> background(result) {
                VaultStore.writeState(ctx, VaultKeys.publicKey(ctx)!!, call.argument<String>("json")!!)
                null
            }
            "delete" -> background(result) {
                call.argument<List<String>>("ids")!!.forEach { VaultStore.delete(ctx, it) }
                null
            }
            "saveToGallery" -> unlocked(result) { priv ->
                var saved = 0
                for (id in call.argument<List<String>>("ids")!!) {
                    try {
                        VaultTransfer.saveToGallery(ctx, id, priv)
                        saved++
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "saveToGallery", e)
                    }
                }
                saved
            }
            "share" -> unlocked(result) { priv ->
                val files = call.argument<List<String>>("ids")!!.map { VaultTransfer.cleanCopy(ctx, it, priv) }
                main.post { share(files) }
                files.size
            }
            "import" -> {
                if (pendingImport != null) {
                    result.error("busy", null, null)
                    return
                }
                pendingImport = result
                pickMedia()
            }
            "playVideo" -> {
                if (!VaultSession.isUnlocked) {
                    result.error("locked", null, null)
                    return
                }
                keepOpenUntilResume = true
                activity.startActivity(Intent(activity, VaultVideoActivity::class.java)
                    .putExtra(VaultVideoActivity.EXTRA_ID, call.argument<String>("id")))
                result.success(null)
            }
            "quickHide" -> result.success(CamConfig.isQuickHideOn(ctx))
            "setQuickHide" -> {
                CamConfig.setQuickHideOn(ctx, call.argument<Boolean>("on") == true)
                watchFaceDown()
                result.success(null)
            }
            "backupConditions" -> result.success(mapOf(
                "unmetered" to isUnmetered(ctx),
                "charging" to (ctx.getSystemService(BatteryManager::class.java)?.isCharging == true),
            ))
            "backupNotice" -> {
                showBackupNotice(
                    call.argument<String>("title") ?: "",
                    call.argument<String>("text") ?: "",
                    call.argument<Number>("done")?.toInt() ?: 0,
                    call.argument<Number>("total")?.toInt() ?: 0,
                    call.argument<Boolean>("ask") == true,
                )
                result.success(null)
            }
            "backupNoticeEnd" -> {
                cancelBackupNotice()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    // ---- quick hide ---------------------------------------------------------

    private var faceDownSamples = 0
    private var watchingFaceDown = false
    private val faceDown = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Lying screen-down, gravity pulls along -z. Two readings in a row, so a swing of
            // the hand doesn't count.
            faceDownSamples = if (event.values[2] < FACE_DOWN_Z) faceDownSamples + 1 else 0
            if (faceDownSamples >= 2 && VaultSession.isUnlocked) {
                faceDownSamples = 0
                VaultSession.lock()
                // The Flutter screens show the vault locked.
                channel.invokeMethod("locked", null)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /**
     * Starts or stops watching for the phone being put face down, to match the setting. Called
     * when these screens come to the front and when the setting changes.
     */
    fun watchFaceDown() {
        val ctx = activity.applicationContext
        val sensors = ctx.getSystemService(SensorManager::class.java) ?: return
        val want = CamConfig.isQuickHideOn(ctx)
        if (want == watchingFaceDown) return
        if (want) {
            val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            faceDownSamples = 0
            sensors.registerListener(faceDown, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            sensors.unregisterListener(faceDown)
        }
        watchingFaceDown = want
    }

    /** Stops watching (these screens left the front; the vault locks then anyway). */
    fun stopFaceDown() {
        if (!watchingFaceDown) return
        activity.applicationContext.getSystemService(SensorManager::class.java)?.unregisterListener(faceDown)
        watchingFaceDown = false
    }

    /** On Wi-Fi or another network with internet that isn't metered. Unknown counts as metered. */
    private fun isUnmetered(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    } catch (e: SecurityException) {
        false
    }

    /**
     * The backup's progress, as a quiet notification. Without the notification permission nothing
     * shows; [ask] asks for it once (right after "Back up now" was tapped).
     */
    private fun showBackupNotice(title: String, text: String, done: Int, total: Int, ask: Boolean) {
        val ctx = activity.applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ask) activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFY)
            return
        }
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(BACKUP_CHANNEL, title, NotificationManager.IMPORTANCE_LOW)
        )
        nm.notify(
            BACKUP_NOTICE,
            Notification.Builder(ctx, BACKUP_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(total, done, total <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    /** Takes the backup's notification away (also when these screens close mid-backup). */
    fun cancelBackupNotice() {
        activity.applicationContext.getSystemService(NotificationManager::class.java)?.cancel(BACKUP_NOTICE)
    }

    /** The photo picker's answer (see ZtAppActivity). */
    fun onPicked(uris: List<Uri>) {
        val result = pendingImport ?: return
        pendingImport = null
        val ctx = activity.applicationContext
        val pub = VaultKeys.publicKey(ctx)
        if (pub == null) {
            result.success(0)
            return
        }
        io.execute {
            val added = uris.count { VaultTransfer.import(ctx, pub, it) }
            main.post { result.success(added) }
        }
    }

    private fun share(files: List<Pair<java.io.File, String>>) {
        if (files.isEmpty()) return
        val uris = files.map { FileProvider.getUriForFile(activity, "${activity.packageName}.vaultshare", it.first) }
        val type = if (files.all { it.second.startsWith("image/") }) "image/*"
            else if (files.all { it.second.startsWith("video/") }) "video/*" else "*/*"
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = type
        send.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, null))
    }

    private fun <T> unlocked(result: MethodChannel.Result, work: (ByteArray) -> T) {
        if (!VaultSession.isUnlocked) {
            result.error("locked", null, null)
            return
        }
        background(result) { work(VaultSession.privateKey()) }
    }

    private fun <T> background(result: MethodChannel.Result, work: () -> T) {
        io.execute {
            try {
                val value = work()
                main.post { result.success(value) }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "vault call", e)
                main.post { result.error(if (VaultSession.isUnlocked) "failed" else "locked", e.message, null) }
            }
        }
    }

    private fun name(r: VaultKeys.Result) = when (r) {
        VaultKeys.Result.OK -> "ok"
        VaultKeys.Result.CANCELLED -> "cancelled"
        VaultKeys.Result.NO_SCREEN_LOCK -> "noScreenLock"
        VaultKeys.Result.KEY_LOST -> "keyLost"
        VaultKeys.Result.FAILED -> "failed"
    }

    companion object {
        const val CHANNEL = "dev.sagron.zerotrustcamera/vault"
        private const val TAG = "VaultChannel"
        private const val FACE_DOWN_Z = -8.5f
        private const val BACKUP_CHANNEL = "vault_backup"
        private const val BACKUP_NOTICE = 4101
        private const val REQUEST_NOTIFY = 4102
    }
}
