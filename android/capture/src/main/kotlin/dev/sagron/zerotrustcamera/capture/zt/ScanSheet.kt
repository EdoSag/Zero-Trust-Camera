package dev.sagron.zerotrustcamera.capture.zt

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.sagron.zerotrustcamera.capture.R
import dev.sagron.zerotrustcamera.capture.zt.ScanContent.Kind
import dev.sagron.zerotrustcamera.capture.zt.ScanContent.Warning

/**
 * What a scanned code holds, and what can be done with it. Nothing opens by itself: a link shows
 * its real site and any reason to look twice first, a two-factor secret is never shown, and each
 * action goes to the matching ZT app when it is installed.
 */
object ScanSheet {
    private const val ZT = "dev.sagron.zerotrust"

    fun show(activity: Activity, raw: String, onDismiss: () -> Unit): AlertDialog {
        val c = ScanContent.parse(raw)
        val ctx = activity
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(ctx, 24)
            setPadding(pad, dp(ctx, 8), pad, 0)
        }

        for (w in c.warnings) body.addView(warningView(ctx, warningText(ctx, c, w)))
        body.addView(mainText(ctx, c))

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(title(c.kind))
            .setView(body)
            .setNegativeButton(R.string.scan_close, null)
            .setOnDismissListener { onDismiss() }
            .create()

        fun action(label: String, emphasized: Boolean = false, run: () -> Unit) {
            val button = if (emphasized) MaterialButton(ctx)
                else MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            button.text = label
            button.setOnClickListener {
                unlockThen(activity) {
                    try {
                        run()
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(ctx, R.string.scan_no_app, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            body.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, 8) })
        }

        when (c.kind) {
            Kind.LINK -> {
                val url = c.get("url")!!
                val safe = c.warnings.isEmpty()
                if (Warning.NOT_WEB !in c.warnings) {
                    if (installed(ctx, "${ZT}browser")) {
                        action(ctx.getString(if (safe) R.string.scan_open_zt_browser else R.string.scan_open_anyway), safe) {
                            ctx.startActivity(view(url).setPackage("${ZT}browser"))
                        }
                    } else {
                        action(ctx.getString(if (safe) R.string.scan_open_browser else R.string.scan_open_anyway), safe) {
                            ctx.startActivity(Intent.createChooser(view(url), null))
                        }
                    }
                }
                copyAction(ctx, ::action, url)
            }
            Kind.ZT_INVITE -> {
                val app = c.get("app")!!
                val name = appName(app)
                val url = c.get("url")!!
                val pkg = ZT + app
                if (installed(ctx, pkg)) {
                    action(ctx.getString(R.string.scan_open_in_app, name), true) {
                        ctx.startActivity(view(url).setPackage(pkg))
                    }
                } else {
                    action(ctx.getString(R.string.scan_open_browser), true) {
                        ctx.startActivity(Intent.createChooser(view(url), null))
                    }
                }
            }
            Kind.WIFI -> {
                val password = c.get("password")
                action(ctx.getString(R.string.scan_wifi_join), true) { joinWifi(ctx, c) }
                if (password != null) {
                    action(ctx.getString(R.string.scan_copy_password)) { copy(ctx, password, sensitive = true) }
                }
            }
            Kind.CONTACT -> {
                action(ctx.getString(R.string.scan_add_contact), true) { addContact(ctx, c) }
                copyAction(ctx, ::action, raw)
            }
            Kind.EVENT -> {
                action(ctx.getString(R.string.scan_add_event), true) { addEvent(ctx, c) }
                copyAction(ctx, ::action, raw)
            }
            Kind.PHONE -> {
                val number = c.get("phone").orEmpty()
                action(ctx.getString(R.string.scan_call), true) {
                    ctx.startActivity(preferZt(ctx, Intent(Intent.ACTION_DIAL, "tel:${enc(number)}".toUri()), "phone"))
                }
                copyAction(ctx, ::action, number)
            }
            Kind.SMS -> {
                action(ctx.getString(R.string.scan_send_text), true) {
                    ctx.startActivity(Intent(Intent.ACTION_SENDTO, "smsto:${enc(c.get("phone").orEmpty())}".toUri())
                        .putExtra("sms_body", c.get("body")))
                }
                copyAction(ctx, ::action, c.get("body") ?: c.get("phone").orEmpty())
            }
            Kind.EMAIL -> {
                action(ctx.getString(R.string.scan_send_email), true) {
                    ctx.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
                        .putExtra(Intent.EXTRA_EMAIL, arrayOf(c.get("email").orEmpty()))
                        .putExtra(Intent.EXTRA_SUBJECT, c.get("subject"))
                        .putExtra(Intent.EXTRA_TEXT, c.get("body")))
                }
                copyAction(ctx, ::action, c.get("email").orEmpty())
            }
            Kind.PLACE -> {
                action(ctx.getString(R.string.scan_open_map), true) {
                    ctx.startActivity(Intent.createChooser(view(raw.trim()), null))
                }
                copyAction(ctx, ::action, listOfNotNull(c.get("lat"), c.get("lon")).joinToString(","))
            }
            Kind.OTP -> {
                // No copy and no share: the secret should go straight into an authenticator.
                if (c.get("migration") == null) {
                    action(ctx.getString(R.string.scan_otp_open), true) {
                        ctx.startActivity(Intent.createChooser(view(raw.trim()), null))
                    }
                }
            }
            Kind.TEXT -> {
                copyAction(ctx, ::action, raw)
                action(ctx.getString(R.string.scan_share)) {
                    ctx.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, raw), null))
                }
            }
        }
        dialog.show()
        return dialog
    }

    // ---- what is shown ---------------------------------------------------------------------------

    private fun title(kind: Kind) = when (kind) {
        Kind.LINK -> R.string.scan_title_link
        Kind.ZT_INVITE -> R.string.scan_title_invite
        Kind.WIFI -> R.string.scan_title_wifi
        Kind.CONTACT -> R.string.scan_title_contact
        Kind.EVENT -> R.string.scan_title_event
        Kind.PHONE -> R.string.scan_title_phone
        Kind.SMS -> R.string.scan_title_sms
        Kind.EMAIL -> R.string.scan_title_email
        Kind.PLACE -> R.string.scan_title_place
        Kind.OTP -> R.string.scan_title_otp
        Kind.TEXT -> R.string.scan_title_text
    }

    private fun warningText(ctx: Context, c: ScanContent, w: Warning): String = when (w) {
        Warning.NOT_ENCRYPTED -> ctx.getString(R.string.scan_warn_not_encrypted)
        Warning.PUNYCODE -> ctx.getString(R.string.scan_warn_punycode)
        Warning.SHORTENER -> ctx.getString(R.string.scan_warn_shortener)
        Warning.IP_ADDRESS -> ctx.getString(R.string.scan_warn_ip)
        Warning.USER_INFO -> ctx.getString(R.string.scan_warn_user_info, c.get("host").orEmpty())
        Warning.LOOKALIKE -> ctx.getString(R.string.scan_warn_lookalike, c.get("lookalike").orEmpty())
        Warning.NOT_WEB -> ctx.getString(R.string.scan_warn_not_web)
    }

    private fun warningView(ctx: Context, text: String) = TextView(ctx).apply {
        this.text = "⚠ $text"
        setTextColor(MaterialColors.getColor(ctx, android.R.attr.colorError, 0xFFB3261E.toInt()))
        setPadding(0, 0, 0, dp(ctx, 8))
    }

    private fun mainText(ctx: Context, c: ScanContent): View {
        val text = SpannableStringBuilder()
        fun line(label: Int, value: String?) {
            if (value.isNullOrBlank()) return
            if (text.isNotEmpty()) text.append('\n')
            val start = text.length
            text.append(ctx.getString(label)).append(": ")
            text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.append(value)
        }
        when (c.kind) {
            Kind.LINK, Kind.ZT_INVITE -> {
                val domain = c.get("domain")
                if (!domain.isNullOrEmpty()) {
                    // The site it really goes to, large, before the full address.
                    val start = text.length
                    text.append(domain)
                    text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text.setSpan(android.text.style.RelativeSizeSpan(1.3f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text.append("\n\n")
                }
                if (c.kind == Kind.ZT_INVITE) {
                    text.append(ctx.getString(R.string.scan_invite_message, appName(c.get("app")!!))).append("\n\n")
                }
                text.append(c.get("url"))
            }
            Kind.WIFI -> {
                line(R.string.scan_wifi_network, c.get("ssid"))
                line(R.string.scan_wifi_security, if (c.get("security") == "nopass")
                    ctx.getString(R.string.scan_wifi_open) else c.get("security"))
                if (c.get("password") != null) line(R.string.scan_wifi_password, "••••••••")
            }
            Kind.CONTACT -> {
                line(R.string.scan_field_name, c.get("name"))
                line(R.string.scan_field_phone, c.get("phone"))
                line(R.string.scan_field_email, c.get("email"))
                line(R.string.scan_field_org, c.get("org"))
                line(R.string.scan_field_address, c.get("address"))
                line(R.string.scan_field_link, c.get("url"))
            }
            Kind.EVENT -> {
                line(R.string.scan_field_title, c.get("title"))
                line(R.string.scan_field_when, whenText(ctx, c))
                line(R.string.scan_field_place, c.get("location"))
                line(R.string.scan_field_note, c.get("description"))
            }
            Kind.PHONE -> text.append(c.get("phone"))
            Kind.SMS -> {
                line(R.string.scan_field_phone, c.get("phone"))
                line(R.string.scan_field_message, c.get("body"))
            }
            Kind.EMAIL -> {
                line(R.string.scan_field_email, c.get("email"))
                line(R.string.scan_field_subject, c.get("subject"))
                line(R.string.scan_field_message, c.get("body"))
            }
            Kind.PLACE -> {
                line(R.string.scan_field_place, c.get("query"))
                text.append(if (text.isEmpty()) "" else "\n")
                    .append(listOfNotNull(c.get("lat"), c.get("lon")).joinToString(", "))
            }
            Kind.OTP -> {
                text.append(ctx.getString(R.string.scan_otp_message))
                val who = listOfNotNull(c.get("issuer"), c.get("account")).joinToString(" · ")
                if (who.isNotEmpty()) text.append("\n\n").append(who)
            }
            Kind.TEXT -> text.append(c.raw)
        }
        return TextView(ctx).apply {
            this.text = text
            setTextIsSelectable(c.kind != Kind.OTP && c.kind != Kind.WIFI)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 14
            isVerticalScrollBarEnabled = true
            movementMethod = android.text.method.ScrollingMovementMethod()
        }
    }

    private fun whenText(ctx: Context, c: ScanContent): String? {
        val start = c.get("startMs")?.toLongOrNull() ?: return c.get("start")
        val allDay = c.get("allDay") == "true"
        val flags = android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_WEEKDAY or
            (if (allDay) 0 else android.text.format.DateUtils.FORMAT_SHOW_TIME)
        val end = c.get("endMs")?.toLongOrNull() ?: start
        return android.text.format.DateUtils.formatDateRange(ctx, start, end, flags)
    }

    // ---- actions ---------------------------------------------------------------------------------

    private fun copyAction(ctx: Context, action: (String, Boolean, () -> Unit) -> Unit, text: String) {
        action(ctx.getString(R.string.scan_copy), false) { copy(ctx, text) }
    }

    private fun copy(ctx: Context, text: String, sensitive: Boolean = false) {
        val clip = ClipData.newPlainText("text", text)
        if (sensitive) {
            // Keeps the password out of the clipboard preview and keyboard suggestions.
            clip.description.extras = PersistableBundle().apply {
                // ClipDescription.EXTRA_IS_SENSITIVE (Android 13); older versions ignore it.
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, R.string.copied_text_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun joinWifi(ctx: Context, c: ScanContent) {
        val ssid = c.get("ssid")!!
        val password = c.get("password")
        val security = c.get("security")
        if (Build.VERSION.SDK_INT >= 30 && security != "WEP") {
            // Android's own "save this network?" sheet: no Wi-Fi permission needed.
            val b = WifiNetworkSuggestion.Builder().setSsid(ssid)
            if (password != null) {
                if (security == "SAE" || security == "WPA3") b.setWpa3Passphrase(password) else b.setWpa2Passphrase(password)
            }
            if (c.get("hidden") == "true") b.setIsHiddenSsid(true)
            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(b.build()))
            ctx.startActivity(intent)
        } else {
            // Older Android, or WEP: copy the password and open the Wi-Fi list.
            password?.let { copy(ctx, it, sensitive = true) }
            ctx.startActivity(Intent(Settings.Panel.ACTION_WIFI))
        }
    }

    private fun addContact(ctx: Context, c: ScanContent) {
        val phones = c.get("phone")?.split('\n').orEmpty()
        val emails = c.get("email")?.split('\n').orEmpty()
        val i = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
        c.get("name")?.let { i.putExtra(ContactsContract.Intents.Insert.NAME, it) }
        phones.getOrNull(0)?.let { i.putExtra(ContactsContract.Intents.Insert.PHONE, it) }
        phones.getOrNull(1)?.let { i.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it) }
        emails.getOrNull(0)?.let { i.putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        emails.getOrNull(1)?.let { i.putExtra(ContactsContract.Intents.Insert.SECONDARY_EMAIL, it) }
        c.get("org")?.let { i.putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
        c.get("title")?.let { i.putExtra(ContactsContract.Intents.Insert.JOB_TITLE, it) }
        c.get("address")?.let { i.putExtra(ContactsContract.Intents.Insert.POSTAL, it) }
        c.get("note")?.let { i.putExtra(ContactsContract.Intents.Insert.NOTES, it) }
        ctx.startActivity(preferZt(ctx, i, "contacts"))
    }

    private fun addEvent(ctx: Context, c: ScanContent) {
        val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        c.get("title")?.let { i.putExtra(CalendarContract.Events.TITLE, it) }
        c.get("startMs")?.toLongOrNull()?.let { i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
        c.get("endMs")?.toLongOrNull()?.let { i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
        if (c.get("allDay") == "true") i.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        c.get("location")?.let { i.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        c.get("description")?.let { i.putExtra(CalendarContract.Events.DESCRIPTION, it) }
        ctx.startActivity(preferZt(ctx, i, "calendar"))
    }

    /** The ZT app, when it is installed and takes this intent; otherwise whatever the phone uses. */
    private fun preferZt(ctx: Context, intent: Intent, app: String): Intent {
        val zt = Intent(intent).setPackage(ZT + app)
        return if (zt.resolveActivity(ctx.packageManager) != null) zt else intent
    }

    private fun installed(ctx: Context, pkg: String) = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun view(url: String) = Intent(Intent.ACTION_VIEW, url.toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE)

    private fun enc(s: String) = android.net.Uri.encode(s, "+")

    private fun appName(app: String) = app.replaceFirstChar { it.uppercase() }

    /** From the lock screen, actions ask for the phone to be unlocked first. */
    private fun unlockThen(activity: Activity, run: () -> Unit) {
        val km = activity.getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isKeyguardLocked) {
            run()
            return
        }
        km.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = run()
        })
    }

    private fun dp(ctx: Context, v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()
}
