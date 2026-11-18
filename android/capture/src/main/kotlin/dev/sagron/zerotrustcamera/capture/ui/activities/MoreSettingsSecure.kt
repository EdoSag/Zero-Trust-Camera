package dev.sagron.zerotrustcamera.capture.ui.activities

import android.os.Bundle
import dev.sagron.zerotrustcamera.capture.AutoFinishOnSleep

class MoreSettingsSecure : MoreSettings() {

    private val autoFinisher = AutoFinishOnSleep(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoFinisher.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoFinisher.stop()
    }
}
