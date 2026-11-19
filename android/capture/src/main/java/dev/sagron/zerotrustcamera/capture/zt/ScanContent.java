package dev.sagron.zerotrustcamera.capture.zt;

import java.net.IDN;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a scanned code holds, worked out on the phone: a link (with safety checks), Wi-Fi, a
 * contact, an event, a phone number, a text message, an email, a place, a two-factor secret, a
 * Zero Trust invite, or plain text. Nothing is ever opened by itself: the scan screen shows this
 * and the person picks an action.
 *
 * Plain Java, so it is tested on the computer (ScanContentTest).
 */
public final class ScanContent {
    public enum Kind { LINK, ZT_INVITE, WIFI, CONTACT, EVENT, PHONE, SMS, EMAIL, PLACE, OTP, TEXT }

    /** Reasons to look twice at a link, shown above it. */
    public enum Warning {
        NOT_ENCRYPTED,   // http://
        PUNYCODE,        // xn-- labels or non-Latin letters in the address
        SHORTENER,       // hides where it really goes
        IP_ADDRESS,      // a bare number instead of a name
        USER_INFO,       // "https://bank.com@evil.example" tricks
        LOOKALIKE,       // a well-known name inside someone else's address
        NOT_WEB,         // javascript:, data:, file:, intent: and the like
    }

    public final Kind kind;
    public final String raw;
    /** Named parts: url, host, domain, ssid, password, security, hidden, name, phone, email, ... */
    public final Map<String, String> fields;
    public final Set<Warning> warnings;

    private ScanContent(Kind kind, String raw, Map<String, String> fields, Set<Warning> warnings) {
        this.kind = kind;
        this.raw = raw;
        this.fields = Collections.unmodifiableMap(fields);
        this.warnings = Collections.unmodifiableSet(warnings);
    }

    public String get(String key) {
        return fields.get(key);
    }

    public static ScanContent parse(String raw) {
        String t = raw.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        Map<String, String> f = new LinkedHashMap<>();
        if (lower.startsWith("wifi:")) {
            parseWifi(t.substring(5), f);
            if (f.containsKey("ssid")) return new ScanContent(Kind.WIFI, raw, f, EnumSet.noneOf(Warning.class));
        }
        if (lower.startsWith("begin:vcard")) {
            parseVcard(t, f);
            return new ScanContent(Kind.CONTACT, raw, f, EnumSet.noneOf(Warning.class));
        }
        if (lower.startsWith("mecard:")) {
            parseMecard(t.substring(7), f);
            return new ScanContent(Kind.CONTACT, raw, f, EnumSet.noneOf(Warning.class));
        }
        if (lower.startsWith("begin:vevent") || (lower.startsWith("begin:vcalendar") && lower.contains("begin:vevent"))) {
            parseEvent(t, f);
            return new ScanContent(Kind.EVENT, raw, f, EnumSet.noneOf(Warning.class));
        }
        if (lower.startsWith("tel:")) {
            f.put("phone", decode(t.substring(4)));
            return new ScanContent(Kind.PHONE, raw, f, EnumSet.noneOf(Warning.class));
        }
        if (lower.startsWith("smsto:") || lower.startsWith("sms:") || lower.startsWith("mmsto:")) {
            String rest = t.substring(t.indexOf(':') + 1);
            int q = rest.indexOf('?');
            int colon = rest.indexOf(':');
            if (q >= 0) { // sms:+123?body=hi
                f.put("phone", decode(rest.substring(0, q)));
                String body = queryParam(rest.substring(q + 1), "body");
                if (body != null) f.put("body", body);
            } else if (colon >= 0) { // SMSTO:+123:hi
                f.put("phone", rest.substring(0, colon));
                f.put("body", rest.substring(colon + 1));
            } else {
                f.put("phone", rest);
            }
            return new ScanContent(Kind.SMS, raw, f, EnumSet.noneOf(Warning.class));
        }
        if (lower.startsWith("mailto:") || lower.startsWith("matmsg:")) {
            if (lower.startsWith("mailto:")) {
                String rest = t.substring(7);
                int q = rest.indexOf('?');
                f.put("email", decode(q >= 0 ? rest.substring(0, q) : rest));
                if (q >= 0) {
                    String s = queryParam(rest.substring(q + 1), "subject");
                    String b = queryParam(rest.substring(q + 1), "body");
                    if (s != null) f.put("subject", s);
                    if (b != null) f.put("body", b);
                }
            } else {
                Map<String, String> m = splitFields(t.substring(7));
                putIf(f, "email", m.get("TO"));
                putIf(f, "subject", m.get("SUB"));
                putIf(f, "body", m.get("BODY"));
            }
            return new ScanContent(Kind.EMAIL, raw, f, EnumSet.noneOf(Warning.class));
        }
        if (lower.startsWith("geo:")) {
            String rest = t.substring(4);
            int q = rest.indexOf('?');
            String coords = q >= 0 ? rest.substring(0, q) : rest;
            String[] parts = coords.split("[,;]");
            if (parts.length >= 2) {
                f.put("lat", parts[0].trim());
                f.put("lon", parts[1].trim());
            }
            if (q >= 0) putIf(f, "query", queryParam(rest.substring(q + 1), "q"));
            return new ScanContent(Kind.PLACE, raw, f, EnumSet.noneOf(Warning.class));
        }
        if (lower.startsWith("otpauth://") || lower.startsWith("otpauth-migration://")) {
            parseOtp(t, f);
            return new ScanContent(Kind.OTP, raw, f, EnumSet.noneOf(Warning.class));
        }
        if (looksLikeLink(t)) {
            String url = SCHEME.matcher(t).find() ? t : "https://" + t;
            LinkCheck check = checkLink(url);
            f.putAll(check.fields);
            String app = ztInviteApp(check);
            if (app != null) {
                f.put("app", app);
                return new ScanContent(Kind.ZT_INVITE, raw, f, check.warnings);
            }
            return new ScanContent(Kind.LINK, raw, f, check.warnings);
        }
        f.put("text", raw);
        return new ScanContent(Kind.TEXT, raw, f, EnumSet.noneOf(Warning.class));
    }

    // ---- links -----------------------------------------------------------------------------------

    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");
    private static final Pattern BARE_HOST = Pattern.compile("^(www\\.)?[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*\\.[a-zA-Z]{2,}(/\\S*)?$");

    private static boolean looksLikeLink(String t) {
        if (t.contains(" ") || t.contains("\n")) return false;
        Matcher m = SCHEME.matcher(t);
        if (m.find()) {
            String scheme = m.group().toLowerCase(Locale.ROOT);
            // Anything with a scheme is treated as a link, so odd schemes get the NOT_WEB warning.
            return scheme.length() > 2 || t.contains("//");
        }
        return BARE_HOST.matcher(t).matches();
    }

    private static final Set<String> SHORTENERS = new HashSet<>(Arrays.asList(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "rebrand.ly",
        "cutt.ly", "shorturl.at", "tiny.cc", "rb.gy", "t.ly", "s.id", "v.gd", "lnkd.in", "qrco.de",
        "bl.ink", "short.io", "tr.im", "x.co", "u.to", "clck.ru", "did.li", "shorturl.com"));

    /** Names people trust, which scams put inside other addresses (paypal-login.example). */
    private static final Map<String, Set<String>> BRANDS = new LinkedHashMap<>();
    static {
        brand("paypal", "paypal.com", "paypal.me");
        brand("google", "google.com", "google.co.il", "goo.gl", "googleusercontent.com", "youtube.com");
        brand("apple", "apple.com", "icloud.com");
        brand("microsoft", "microsoft.com", "live.com", "office.com", "microsoftonline.com");
        brand("amazon", "amazon.com", "amazon.co.uk", "amazon.de", "amzn.to");
        brand("facebook", "facebook.com", "fb.com", "fb.me");
        brand("instagram", "instagram.com");
        brand("whatsapp", "whatsapp.com", "wa.me");
        brand("netflix", "netflix.com");
        brand("zerotrust", "zerotrust.bz");
        brand("leumi", "leumi.co.il");
        brand("hapoalim", "bankhapoalim.co.il");
        brand("discount", "discountbank.co.il");
        brand("mizrahi", "mizrahi-tefahot.co.il");
        brand("israelpost", "israelpost.co.il");
        brand("gov.il", "gov.il");
    }

    private static void brand(String name, String... domains) {
        BRANDS.put(name, new HashSet<>(Arrays.asList(domains)));
    }

    /** Second-level names under which the real owner is one label further left. */
    private static final Set<String> SECOND_LEVEL = new HashSet<>(Arrays.asList(
        "co.il", "org.il", "net.il", "ac.il", "gov.il", "muni.il", "co.uk", "org.uk", "ac.uk",
        "gov.uk", "com.au", "net.au", "org.au", "co.nz", "co.jp", "com.br", "com.mx", "co.in",
        "co.za", "com.tr", "com.cn"));

    static final class LinkCheck {
        final Map<String, String> fields = new LinkedHashMap<>();
        final Set<Warning> warnings = EnumSet.noneOf(Warning.class);
        String host = "";
        String domain = "";
        String path = "";
    }

    static LinkCheck checkLink(String url) {
        // Browsers read a backslash in a web address as a slash, and drop tabs and line breaks:
        // check the address the way the browser will read it.
        url = url.replaceAll("[\\t\\n\\r]", "");
        String lowerScheme = url.toLowerCase(Locale.ROOT);
        if (lowerScheme.startsWith("http:") || lowerScheme.startsWith("https:")) url = url.replace('\\', '/');
        LinkCheck c = new LinkCheck();
        c.fields.put("url", url);
        String scheme = url.substring(0, Math.max(0, url.indexOf(':'))).toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            c.warnings.add(Warning.NOT_WEB);
            return c;
        }
        if (scheme.equals("http")) c.warnings.add(Warning.NOT_ENCRYPTED);

        // Pull the authority out by hand: java.net.URI refuses some of the tricks worth catching.
        int start = url.indexOf("//") + 2;
        int end = start;
        while (end < url.length() && "/?#".indexOf(url.charAt(end)) < 0) end++;
        String authority = url.substring(start, end);
        c.path = url.substring(end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            c.warnings.add(Warning.USER_INFO);
            authority = authority.substring(at + 1);
        }
        String host = authority;
        if (host.startsWith("[")) { // IPv6
            c.warnings.add(Warning.IP_ADDRESS);
            int close = host.indexOf(']');
            c.host = close > 0 ? host.substring(0, close + 1) : host;
            c.domain = c.host;
            c.fields.put("host", c.host);
            c.fields.put("domain", c.domain);
            return c;
        }
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);

        boolean nonAscii = false;
        for (int i = 0; i < host.length(); i++) if (host.charAt(i) > 127) nonAscii = true;
        String ascii;
        try {
            ascii = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            ascii = host.toLowerCase(Locale.ROOT);
        }
        if (nonAscii || ascii.startsWith("xn--") || ascii.contains(".xn--")) c.warnings.add(Warning.PUNYCODE);
        c.host = ascii;

        if (ascii.matches("[0-9.]+") || ascii.matches("0x[0-9a-f]+")) {
            c.warnings.add(Warning.IP_ADDRESS);
            c.domain = ascii;
        } else {
            c.domain = registrableDomain(ascii);
        }
        if (SHORTENERS.contains(c.domain)) c.warnings.add(Warning.SHORTENER);

        // A brand's name in the address, on a domain that isn't the brand's.
        String flat = ascii.replace("-", "").replace("0", "o").replace("1", "l");
        for (Map.Entry<String, Set<String>> b : BRANDS.entrySet()) {
            String name = b.getKey().replace(".", "");
            if (!flat.replace(".", "").contains(name)) continue;
            boolean real = false;
            for (String d : b.getValue()) if (ascii.equals(d) || ascii.endsWith("." + d)) real = true;
            if (!real) {
                c.warnings.add(Warning.LOOKALIKE);
                c.fields.put("lookalike", b.getKey());
                break;
            }
        }
        c.fields.put("host", c.host);
        c.fields.put("domain", c.domain);
        return c;
    }

    static String registrableDomain(String host) {
        String[] labels = host.split("\\.");
        if (labels.length <= 2) return host;
        String lastTwo = labels[labels.length - 2] + "." + labels[labels.length - 1];
        int keep = SECOND_LEVEL.contains(lastTwo) ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, labels.length - keep); i < labels.length; i++) {
            if (sb.length() > 0) sb.append('.');
            sb.append(labels[i]);
        }
        return sb.toString();
    }

    private static final Pattern JOIN = Pattern.compile("^/join/([a-z]+)/?$");

    /** "tasks" for https://zerotrust.bz/join/tasks#..., else null. */
    private static String ztInviteApp(LinkCheck c) {
        if (!c.warnings.isEmpty()) return null;
        if (!c.host.equals("zerotrust.bz") && !c.host.equals("www.zerotrust.bz")) return null;
        String path = c.path;
        int cut = path.length();
        for (char ch : new char[] {'?', '#'}) {
            int i = path.indexOf(ch);
            if (i >= 0 && i < cut) cut = i;
        }
        Matcher m = JOIN.matcher(path.substring(0, cut));
        return m.matches() ? m.group(1) : null;
    }

    // ---- Wi-Fi, contacts, events, two-factor ------------------------------------------------------

    /** Splits "K:v;K2:v2;;" with backslash escapes, as Wi-Fi and MeCard codes use. */
    static Map<String, String> splitFields(String s) {
        Map<String, String> out = new LinkedHashMap<>();
        StringBuilder key = new StringBuilder();
        StringBuilder val = new StringBuilder();
        boolean inValue = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                (inValue ? val : key).append(s.charAt(++i));
            } else if (!inValue && ch == ':') {
                inValue = true;
            } else if (ch == ';') {
                if (key.length() > 0) {
                    String k = key.toString().trim().toUpperCase(Locale.ROOT);
                    // MeCard repeats TEL and EMAIL: keep them all, one per line.
                    out.merge(k, val.toString(), (a, b) -> a + "\n" + b);
                }
                key.setLength(0);
                val.setLength(0);
                inValue = false;
            } else {
                (inValue ? val : key).append(ch);
            }
        }
        if (key.length() > 0) out.merge(key.toString().trim().toUpperCase(Locale.ROOT), val.toString(), (a, b) -> a + "\n" + b);
        return out;
    }

    private static void parseWifi(String s, Map<String, String> f) {
        Map<String, String> m = splitFields(s);
        putIf(f, "ssid", m.get("S"));
        String type = m.get("T");
        f.put("security", type == null || type.isEmpty() ? "nopass" : type.toUpperCase(Locale.ROOT));
        putIf(f, "password", m.get("P"));
        if ("true".equalsIgnoreCase(m.get("H"))) f.put("hidden", "true");
    }

    private static void parseMecard(String s, Map<String, String> f) {
        Map<String, String> m = splitFields(s);
        String n = m.get("N");
        if (n != null) {
            // MeCard writes "Last,First".
            int comma = n.indexOf(',');
            f.put("name", comma >= 0 ? (n.substring(comma + 1) + " " + n.substring(0, comma)).trim() : n);
        }
        putIf(f, "phone", m.get("TEL"));
        putIf(f, "email", m.get("EMAIL"));
        putIf(f, "org", m.get("ORG"));
        putIf(f, "url", m.get("URL"));
        putIf(f, "address", m.get("ADR"));
        putIf(f, "note", m.get("NOTE"));
    }

    private static void parseVcard(String s, Map<String, String> f) {
        String n = null;
        for (String line : unfold(s)) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String name = line.substring(0, colon);
            int semi = name.indexOf(';');
            String prop = (semi >= 0 ? name.substring(0, semi) : name).toUpperCase(Locale.ROOT);
            int dot = prop.indexOf('.'); // "item1.TEL"
            if (dot >= 0) prop = prop.substring(dot + 1);
            String value = unescape(line.substring(colon + 1));
            switch (prop) {
                case "FN": f.put("name", value); break;
                case "N": n = value; break;
                case "TEL": append(f, "phone", value.startsWith("tel:") ? value.substring(4) : value); break;
                case "EMAIL": append(f, "email", value); break;
                case "ORG": f.put("org", value.replace(';', ' ').trim()); break;
                case "TITLE": f.put("title", value); break;
                case "URL": f.put("url", value); break;
                case "ADR": f.put("address", value.replace(';', ' ').trim().replaceAll(" +", " ")); break;
                case "NOTE": f.put("note", value); break;
                default: break;
            }
        }
        if (!f.containsKey("name") && n != null) {
            String[] p = n.split(";", -1);
            String given = p.length > 1 ? p[1] : "";
            f.put("name", (given + " " + p[0]).trim());
        }
    }

    private static void parseEvent(String s, Map<String, String> f) {
        boolean inEvent = false;
        for (String line : unfold(s)) {
            String up = line.toUpperCase(Locale.ROOT);
            if (up.startsWith("BEGIN:VEVENT")) { inEvent = true; continue; }
            if (up.startsWith("END:VEVENT")) break;
            if (!inEvent && !s.toLowerCase(Locale.ROOT).startsWith("begin:vevent")) continue;
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String name = line.substring(0, colon);
            int semi = name.indexOf(';');
            String prop = (semi >= 0 ? name.substring(0, semi) : name).toUpperCase(Locale.ROOT);
            String zone = semi >= 0 ? param(name.substring(semi + 1), "TZID") : null;
            String value = unescape(line.substring(colon + 1));
            switch (prop) {
                case "SUMMARY": f.put("title", value); break;
                case "DTSTART": f.put("start", value); putIf(f, "startMs", iCalTime(value, zone)); break;
                case "DTEND": f.put("end", value); putIf(f, "endMs", iCalTime(value, zone)); break;
                case "LOCATION": f.put("location", value); break;
                case "DESCRIPTION": f.put("description", value); break;
                default: break;
            }
        }
        String start = f.get("start");
        if (start != null && start.length() == 8) f.put("allDay", "true");
    }

    /** A parameter such as TZID out of "TZID=Asia/Jerusalem;VALUE=DATE-TIME". */
    private static String param(String params, String key) {
        for (String p : params.split(";")) {
            int eq = p.indexOf('=');
            if (eq > 0 && p.substring(0, eq).trim().equalsIgnoreCase(key)) {
                return p.substring(eq + 1).trim().replace("\"", "");
            }
        }
        return null;
    }

    static String iCalTime(String v) {
        return iCalTime(v, null);
    }

    /**
     * 20261119T180000Z, 20261119T180000 in [zone] (an IANA name from TZID, or the phone's time
     * when there is none or it is unknown) or 20261119 (all day), in ms as text.
     */
    static String iCalTime(String v, String zone) {
        Matcher m = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})(?:T(\\d{2})(\\d{2})(\\d{2})?(Z)?)?$").matcher(v.trim());
        if (!m.matches()) return null;
        java.util.TimeZone tz = java.util.TimeZone.getDefault();
        if (m.group(7) != null) {
            tz = java.util.TimeZone.getTimeZone("UTC");
        } else if (zone != null && m.group(4) != null) {
            java.util.TimeZone named = java.util.TimeZone.getTimeZone(zone);
            // getTimeZone answers GMT for a name it doesn't know: keep the phone's time then.
            if (!named.getID().equals("GMT") || zone.equalsIgnoreCase("GMT")) tz = named;
        }
        java.util.Calendar cal = java.util.Calendar.getInstance(tz);
        cal.clear();
        cal.set(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)),
            m.group(4) != null ? Integer.parseInt(m.group(4)) : 0,
            m.group(5) != null ? Integer.parseInt(m.group(5)) : 0,
            m.group(6) != null ? Integer.parseInt(m.group(6)) : 0);
        return Long.toString(cal.getTimeInMillis());
    }

    private static void parseOtp(String s, Map<String, String> f) {
        if (s.toLowerCase(Locale.ROOT).startsWith("otpauth-migration://")) {
            f.put("migration", "true");
            return;
        }
        try {
            URI u = new URI(s);
            putIf(f, "type", u.getHost());
            String label = u.getPath() == null ? "" : u.getPath().replaceFirst("^/", "");
            int colon = label.indexOf(':');
            if (colon >= 0) {
                f.put("issuer", label.substring(0, colon).trim());
                f.put("account", label.substring(colon + 1).trim());
            } else if (!label.isEmpty()) {
                f.put("account", label);
            }
            String issuer = u.getRawQuery() == null ? null : queryParam(u.getRawQuery(), "issuer");
            if (issuer != null) f.put("issuer", issuer);
        } catch (Exception e) {
            // Unreadable: still a secret, still kept off the screen.
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static List<String> unfold(String s) {
        List<String> out = new ArrayList<>();
        for (String line : s.split("\r?\n")) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && !out.isEmpty()) {
                out.set(out.size() - 1, out.get(out.size() - 1) + line.substring(1));
            } else if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static String unescape(String v) {
        return v.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\");
    }

    private static void append(Map<String, String> f, String k, String v) {
        f.merge(k, v, (a, b) -> a + "\n" + b);
    }

    private static void putIf(Map<String, String> f, String k, String v) {
        if (v != null && !v.isEmpty()) f.put(k, v);
    }

    private static String queryParam(String query, String name) {
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            String k = eq >= 0 ? part.substring(0, eq) : part;
            if (k.equalsIgnoreCase(name)) return decode(eq >= 0 ? part.substring(eq + 1) : "");
        }
        return null;
    }

    private static String decode(String s) {
        try {
            return java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
