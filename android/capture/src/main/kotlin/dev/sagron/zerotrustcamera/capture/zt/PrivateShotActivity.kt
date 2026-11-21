package dev.sagron.zerotrustcamera.capture.zt

import android.os.Bundle
import android.widget.Toast
import dev.sagron.zerotrustcamera.capture.R
import dev.sagron.zerotrustcamera.capture.ui.activities.MainActivity

/**
 * The camera, saving to the Private vault whatever the setting says: what the "Private shot"
 * button on the home screen widget opens. Sets the vault up first if there is none yet, and
 * closes if that is turned down, so nothing taken here can end up in the gallery.
 */
class PrivateShotActivity : MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        camConfig.privateShot = true
        // After the first frame: the screen lock prompt needs the screen to be up.
        window.decorView.post {
            if (isFinishing || isDestroyed) return@post
            PrivateVaultSetup.ensureForShot(this) { ready ->
                if (!ready) {
                    finish()
                } else if (savedInstanceState == null) {
                    Toast.makeText(this, R.string.private_shot_on, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
