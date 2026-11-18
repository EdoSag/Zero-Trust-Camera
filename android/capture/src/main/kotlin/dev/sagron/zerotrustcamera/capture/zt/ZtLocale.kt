package dev.sagron.zerotrustcamera.capture.zt

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's language (English or Hebrew), picked in the Flutter settings and followed by the
 * camera screens.
 *
 * Android 13 and later keep a per-app language themselves, so the choice is handed to the system
 * and every screen follows it. Older versions have no such thing: the choice is kept here and
 * each activity wraps its context with it ([wrap], called by [ZtBaseActivity]).
 */
object ZtLocale {
    private const val PREFS = "zt_app"
    private const val KEY = "language"

    /** [language] is a code such as "he", or null to follow the phone. */
    fun set(context: Context, language: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (language == null) remove(KEY) else putString(KEY, language)
        }.apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val wanted =
                if (language == null) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(language)
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            // Setting it again restarts every open screen for nothing
            if (manager.applicationLocales != wanted) {
                manager.applicationLocales = wanted
            }
        }
    }

    fun get(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = get(base) ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(language)))
        return base.createConfigurationContext(config)
    }
}
