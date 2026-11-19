package dev.sagron.zerotrustcamera

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import dev.sagron.zerotrustcamera.capture.CamConfig
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
            "photo" -> unlocked(result) { priv -> VaultStore.media(ctx, call.argument<String>("id")!!, priv) }
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
            else -> result.notImplemented()
        }
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
    }
}
