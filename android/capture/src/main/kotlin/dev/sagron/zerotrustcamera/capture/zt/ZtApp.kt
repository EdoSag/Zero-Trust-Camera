package dev.sagron.zerotrustcamera.capture.zt

import android.app.Activity
import android.content.Intent

/**
 * The way from the camera into the rest of ZT Camera: account, language, appearance and about,
 * which are Flutter screens on the shared Zero-Trust core (the `app` module). Named by class name
 * because this module can't see that one.
 */
object ZtApp {
    private const val ACTIVITY = "dev.sagron.zerotrustcamera.ZtAppActivity"
    const val EXTRA_SCREEN = "zt_screen"

    fun open(from: Activity) {
        from.startActivity(Intent().setClassName(from.packageName, ACTIVITY))
    }

    /** The Private vault (it asks for the fingerprint, face or screen lock first). */
    fun openVault(from: Activity) {
        from.startActivity(Intent().setClassName(from.packageName, ACTIVITY).putExtra(EXTRA_SCREEN, "vault"))
    }
}
