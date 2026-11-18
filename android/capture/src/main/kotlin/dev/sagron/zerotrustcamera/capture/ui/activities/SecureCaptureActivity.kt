package dev.sagron.zerotrustcamera.capture.ui.activities

import android.content.SharedPreferences
import dev.sagron.zerotrustcamera.capture.util.EphemeralSharedPrefsNamespace
import dev.sagron.zerotrustcamera.capture.util.getPrefs

class SecureCaptureActivity : CaptureActivity(), SecureActivity {
    val ephemeralPrefsNamespace = EphemeralSharedPrefsNamespace()

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return ephemeralPrefsNamespace.getPrefs(this, name, mode, cloneOriginal = true)
    }
}
