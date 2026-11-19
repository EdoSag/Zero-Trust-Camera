package dev.sagron.zerotrustcamera.capture.zt

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import dev.sagron.zerotrustcamera.capture.R
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Private vault's key pair (see MediaVault for the file format).
 *
 * The public key is kept as it is, so the camera seals new photos and videos while the phone is
 * locked. The private key is kept encrypted by an Android Keystore key that only works after the
 * person confirms with their fingerprint, face or screen lock, so it never sits on storage in a
 * form that can be read. After an unlock it lives in this process's memory only (VaultSession),
 * never in Dart and never on disk.
 *
 * The Keystore key is not dropped when a fingerprint is added. It is dropped by Android if the
 * screen lock is removed, and then the vault can't be opened again: the setup dialog says so.
 */
object VaultKeys {
    private const val TAG = "VaultKeys"
    private const val PREFS = "zt_vault_keys"
    private const val PUB = "pub"
    private const val WRAPPED = "wrapped"
    private const val IV = "iv"
    private const val ALIAS = "zt_camera_vault"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    enum class Result { OK, CANCELLED, NO_SCREEN_LOCK, KEY_LOST, FAILED }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSetUp(ctx: Context): Boolean = prefs(ctx).let { it.contains(PUB) && it.contains(WRAPPED) }

    /** What new captures are sealed to; null until the vault is set up. Needs no unlock. */
    fun publicKey(ctx: Context): ByteArray? =
        prefs(ctx).getString(PUB, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    fun hasScreenLock(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java).isDeviceSecure

    /** Makes a new vault key pair, after the person confirms it's them. */
    fun setUp(activity: FragmentActivity, onDone: (Result) -> Unit) {
        if (!hasScreenLock(activity)) {
            onDone(Result.NO_SCREEN_LOCK)
            return
        }
        if (isSetUp(activity)) {
            onDone(Result.OK)
            return
        }
        val wrapKey = try {
            newWrapKey()
        } catch (e: Exception) {
            Log.e(TAG, "keystore key", e)
            onDone(Result.FAILED)
            return
        }
        authenticate(
            activity, activity.getString(R.string.vault_setup_prompt_title),
            cipherFor = { Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrapKey) } },
        ) { result, cipher ->
            if (result != Result.OK || cipher == null) {
                onDone(result)
                return@authenticate
            }
            try {
                val pair = MediaVault.newKeyPair()
                val wrapped = cipher.doFinal(pair[0])
                prefs(activity).edit()
                    .putString(PUB, Base64.encodeToString(pair[1], Base64.NO_WRAP))
                    .putString(WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                    .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .commit()
                VaultSession.open(pair[0])
                onDone(Result.OK)
            } catch (e: Exception) {
                Log.e(TAG, "setUp", e)
                onDone(Result.FAILED)
            }
        }
    }

    /** Opens the vault for this process until VaultSession.lock(). */
    fun unlock(activity: FragmentActivity, onDone: (Result) -> Unit) {
        if (VaultSession.isUnlocked) {
            onDone(Result.OK)
            return
        }
        val p = prefs(activity)
        val wrapped = p.getString(WRAPPED, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val iv = p.getString(IV, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (wrapped == null || iv == null) {
            onDone(Result.FAILED)
            return
        }
        if (!hasScreenLock(activity)) {
            onDone(Result.KEY_LOST)
            return
        }
        authenticate(
            activity, activity.getString(R.string.vault_unlock_prompt_title),
            cipherFor = {
                val key = keyStore().getKey(ALIAS, null) as SecretKey?
                    ?: throw KeyPermanentlyInvalidatedException()
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                }
            },
        ) { result, cipher ->
            if (result != Result.OK || cipher == null) {
                onDone(result)
                return@authenticate
            }
            try {
                val priv = cipher.doFinal(wrapped)
                VaultSession.open(priv)
                onDone(Result.OK)
            } catch (e: KeyPermanentlyInvalidatedException) {
                onDone(Result.KEY_LOST)
            } catch (e: Exception) {
                Log.e(TAG, "unlock", e)
                onDone(Result.FAILED)
            }
        }
    }

    // On Android 11 and newer the key is unlocked for one use by the prompt itself (a
    // CryptoObject), with a fingerprint, face or the screen lock. Android 10 can't pair the screen
    // lock with a CryptoObject, so there the prompt opens the key for a few seconds instead.
    private fun authenticate(
        activity: FragmentActivity,
        title: String,
        cipherFor: () -> Cipher,
        onDone: (Result, Cipher?) -> Unit,
    ) {
        val perUse = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val firstCipher: Cipher? = if (perUse) {
            try {
                cipherFor()
            } catch (e: KeyPermanentlyInvalidatedException) {
                onDone(Result.KEY_LOST, null)
                return
            } catch (e: Exception) {
                Log.e(TAG, "cipher", e)
                onDone(Result.FAILED, null)
                return
            }
        } else null

        val prompt = BiometricPrompt(activity, activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (perUse) {
                        onDone(Result.OK, result.cryptoObject?.cipher)
                        return
                    }
                    try {
                        onDone(Result.OK, cipherFor())
                    } catch (e: KeyPermanentlyInvalidatedException) {
                        onDone(Result.KEY_LOST, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "cipher", e)
                        onDone(Result.FAILED, null)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                    onDone(if (cancelled) Result.CANCELLED else Result.FAILED, null)
                }
            })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(activity.getString(R.string.vault_prompt_subtitle))
            .setAllowedAuthenticators(
                if (perUse) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL
            )
            .build()

        if (firstCipher != null) {
            prompt.authenticate(info, BiometricPrompt.CryptoObject(firstCipher))
        } else {
            prompt.authenticate(info)
        }
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun newWrapKey(): SecretKey {
        keyStore().deleteEntry(ALIAS)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // A new fingerprint must not lock the person out of their own photos.
            .setInvalidatedByBiometricEnrollment(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(15)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }
}

/** The opened vault key, in this process's memory only, until lock(). */
object VaultSession {
    @Volatile
    private var key: ByteArray? = null

    val isUnlocked: Boolean get() = key != null

    fun open(privateKey: ByteArray) {
        key?.fill(0)
        key = privateKey
    }

    /** The private key, or IllegalStateException while locked. */
    fun privateKey(): ByteArray = key ?: throw IllegalStateException("vault locked")

    fun lock() {
        key?.fill(0)
        key = null
    }
}
