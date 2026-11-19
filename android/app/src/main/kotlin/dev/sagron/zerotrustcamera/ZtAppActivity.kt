package dev.sagron.zerotrustcamera

import android.content.Intent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import dev.sagron.zerotrustcamera.capture.zt.DocScanActivity
import dev.sagron.zerotrustcamera.capture.zt.PanoramaActivity
import dev.sagron.zerotrustcamera.capture.zt.ProCameraActivity
import dev.sagron.zerotrustcamera.capture.zt.VaultSession
import dev.sagron.zerotrustcamera.capture.zt.ZtApp
import dev.sagron.zerotrustcamera.capture.zt.ZtLocale
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * The Flutter half of ZT Camera: the Private vault, and account, language,
 * appearance and about, on the shared Zero-Trust core. Opened from the camera
 * (the camera itself is the native `capture` module, which is what the
 * launcher starts).
 *
 * FlutterFragmentActivity, not FlutterActivity: the biometric prompts
 * (local_auth, and the vault's own in VaultKeys) need a FragmentActivity.
 */
class ZtAppActivity : FlutterFragmentActivity() {
    private var vault: VaultChannel? = null

    // Which screen the camera asked for ("vault" or null for settings). Read
    // by Dart on start and on every resume (see lib/main.dart).
    private var requestedScreen: String? = null

    // Registered here, before the activity starts, as Android requires.
    private val picker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMPORT)
    ) { uris -> vault?.onPicked(uris) }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        requestedScreen = intent?.getStringExtra(ZtApp.EXTRA_SCREEN)
        super.onCreate(savedInstanceState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(ZtApp.EXTRA_SCREEN)?.let { requestedScreen = it }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        vault = VaultChannel(this, messenger) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
        MethodChannel(messenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    // The language picked in settings, for the camera screens:
                    // a code such as "he", or null to follow the phone.
                    "setLanguage" -> {
                        ZtLocale.set(applicationContext, call.argument<String>("language"))
                        result.success(null)
                    }
                    // Back to the viewfinder.
                    "close" -> {
                        finish()
                        result.success(null)
                    }
                    "openDocScanner" -> {
                        startActivity(Intent(this, DocScanActivity::class.java))
                        result.success(null)
                    }
                    "openProCamera" -> {
                        ProCameraActivity.open(this)
                        result.success(null)
                    }
                    "openPanorama" -> {
                        PanoramaActivity.open(this)
                        result.success(null)
                    }
                    "takeScreen" -> {
                        result.success(requestedScreen)
                        requestedScreen = null
                    }
                    else -> result.notImplemented()
                }
            }
    }

    override fun onResume() {
        super.onResume()
        vault?.keepOpenUntilResume = false
    }

    override fun onStop() {
        super.onStop()
        // The vault locks as soon as these screens are left, for the photo picker
        // and the share sheet too. Only the vault's own video player keeps it
        // open, and it locks the vault itself when it is left.
        if (vault?.keepOpenUntilResume != true && !isChangingConfigurations) {
            VaultSession.lock()
        }
    }

    private companion object {
        const val CHANNEL = "dev.sagron.zerotrustcamera/app"
        const val MAX_IMPORT = 100
    }
}
