package dev.sagron.zerotrustcamera.capture.zt

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import dev.sagron.zerotrustcamera.capture.App
import dev.sagron.zerotrustcamera.capture.CamConfig

/**
 * The optional place of a photo or video taken into the Private vault. Off until turned on in the
 * vault, and only then is the location permission asked for. The place is written into the item's
 * sealed details and nowhere else: never into the picture itself, so a copy saved to the gallery
 * or shared carries none, and gallery photos never get one.
 */
object VaultLocation {
    fun granted(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Turned on, and the permission given. */
    fun allowed(ctx: Context): Boolean = CamConfig.isVaultLocationOn(ctx) && granted(ctx)

    /** Starts following the phone's place while the viewfinder shows (see MainActivity). */
    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (!allowed(ctx)) return
        try {
            (ctx.applicationContext as? App)?.requestLocationUpdates()
        } catch (e: SecurityException) {
            // The permission went away in between: no place is added.
        }
    }

    /** For the other viewfinders (Pro, Panorama): follows the place only while they save to the vault. */
    fun startForViewfinder(ctx: Context) {
        if (CamConfig.isPrivateVaultOn(ctx) && VaultKeys.isSetUp(ctx)) start(ctx)
    }

    fun stop(ctx: Context) {
        (ctx.applicationContext as? App)?.dropLocationUpdates()
    }

    /** Latitude and longitude for an item sealed now, or null: off, not allowed, or not known. */
    fun now(ctx: Context): DoubleArray? {
        if (!allowed(ctx)) return null
        val l = (ctx.applicationContext as? App)?.getLocation() ?: return null
        return doubleArrayOf(l.latitude, l.longitude)
    }
}
