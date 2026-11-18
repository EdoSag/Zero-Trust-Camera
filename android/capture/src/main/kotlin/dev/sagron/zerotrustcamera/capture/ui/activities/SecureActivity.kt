package dev.sagron.zerotrustcamera.capture.ui.activities

import android.content.SharedPreferences

interface SecureActivity {
    fun getSharedPreferences(name: String, mode: Int): SharedPreferences? = null
}
