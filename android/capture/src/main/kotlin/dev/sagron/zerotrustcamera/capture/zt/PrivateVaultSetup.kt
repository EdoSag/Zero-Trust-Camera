package dev.sagron.zerotrustcamera.capture.zt

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.sagron.zerotrustcamera.capture.CamConfig
import dev.sagron.zerotrustcamera.capture.R
import dev.sagron.zerotrustcamera.capture.ui.showIgnoringShortEdgeMode

/** Where new photos and videos go: the phone's gallery, or the encrypted Private vault. */
object PrivateVaultSetup {

    /** Asked once, on the first start: gallery or Private vault. */
    fun askWhereToSave(activity: FragmentActivity, camConfig: CamConfig) {
        MaterialAlertDialogBuilder(activity).apply {
            setTitle(R.string.vault_choose_title)
            setMessage(R.string.vault_choose_message)
            setCancelable(false)
            setNegativeButton(R.string.vault_choose_gallery) { _, _ ->
                camConfig.privateVault = false
            }
            setPositiveButton(R.string.vault_choose_private) { _, _ ->
                turnOn(activity, camConfig) {}
            }
            showIgnoringShortEdgeMode()
        }
    }

    /**
     * Turns Private mode on, setting the vault up first if needed (one confirmation with the
     * fingerprint, face or screen lock). onDone(true) once new captures go to the vault.
     */
    fun turnOn(activity: FragmentActivity, camConfig: CamConfig, onDone: (Boolean) -> Unit) {
        VaultKeys.setUp(activity) { result ->
            // The camera never needs the opened key: new captures are sealed with the public one.
            VaultSession.lock()
            val on = result == VaultKeys.Result.OK
            camConfig.privateVault = on
            when (result) {
                VaultKeys.Result.OK ->
                    toast(activity, R.string.vault_mode_on)
                VaultKeys.Result.NO_SCREEN_LOCK ->
                    toast(activity, R.string.vault_needs_screen_lock)
                VaultKeys.Result.CANCELLED -> {}
                else ->
                    toast(activity, R.string.vault_setup_failed)
            }
            onDone(on)
        }
    }

    private fun toast(activity: FragmentActivity, msg: Int) {
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
    }
}
