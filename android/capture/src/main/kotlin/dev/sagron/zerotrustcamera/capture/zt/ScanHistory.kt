package dev.sagron.zerotrustcamera.capture.zt

import android.content.Context
import android.util.Log
import dev.sagron.zerotrustcamera.capture.CamConfig
import java.util.concurrent.Executors

/**
 * Scan history: off by default. When it is on (More settings, needs the Private vault), each
 * scanned code is sealed into the vault, where only the vault's owner can read it back.
 */
object ScanHistory {
    private val io = Executors.newSingleThreadExecutor()
    private var last: String? = null
    private var lastAt = 0L

    fun isOn(ctx: Context) = CamConfig.isScanHistoryOn(ctx) && VaultKeys.isSetUp(ctx)

    fun save(ctx: Context, text: String) {
        if (!isOn(ctx)) return
        // A two-factor secret is never kept, not even in the vault: it isn't shown on the scan
        // screen either, and the history page would show and copy it.
        if (ScanContent.parse(text).kind == ScanContent.Kind.OTP) return
        val now = System.currentTimeMillis()
        // The same code held in view is read again and again: keep it once.
        if (text == last && now - lastAt < 60_000) return
        last = text
        lastAt = now
        val pub = VaultKeys.publicKey(ctx) ?: return
        io.execute {
            try {
                VaultStore.saveScan(ctx, pub, text, now)
            } catch (e: Exception) {
                Log.w("ScanHistory", "save", e)
            }
        }
    }
}
