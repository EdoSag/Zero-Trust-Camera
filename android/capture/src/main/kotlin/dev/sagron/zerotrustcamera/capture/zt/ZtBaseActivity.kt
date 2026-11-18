package dev.sagron.zerotrustcamera.capture.zt

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

/** Every camera screen: shows itself in the language picked in the app (see [ZtLocale]). */
open class ZtBaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ZtLocale.wrap(newBase))
    }
}
