package dev.sagron.zerotrustcamera

import dev.sagron.zerotrustcamera.capture.zt.ZtLocale
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * The Flutter half of ZT Camera: account, language, appearance and about, on
 * the shared Zero-Trust core. Opened from the camera's settings (the camera
 * itself is the native `capture` module, which is what the launcher starts).
 *
 * FlutterFragmentActivity, not FlutterActivity: the biometric prompt
 * (local_auth) needs a FragmentActivity.
 */
class ZtAppActivity : FlutterFragmentActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
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
                    else -> result.notImplemented()
                }
            }
    }

    private companion object {
        const val CHANNEL = "dev.sagron.zerotrustcamera/app"
    }
}
