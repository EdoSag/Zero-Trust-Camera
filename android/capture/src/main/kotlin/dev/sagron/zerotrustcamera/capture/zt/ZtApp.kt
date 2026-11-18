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

    fun open(from: Activity) {
        from.startActivity(Intent().setClassName(from.packageName, ACTIVITY))
    }
}
