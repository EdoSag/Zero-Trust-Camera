package dev.sagron.zerotrustcamera.capture.zt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import dev.sagron.zerotrustcamera.capture.zt.ScanContent.Kind;
import dev.sagron.zerotrustcamera.capture.zt.ScanContent.Warning;
import org.junit.Test;

public class ScanContentTest {
    @Test
    public void plainHttpsLinkHasNoWarnings() {
        ScanContent c = ScanContent.parse("https://www.example.com/menu?table=4");
        assertEquals(Kind.LINK, c.kind);
        assertEquals("www.example.com", c.get("host"));
        assertEquals("example.com", c.get("domain"));
        assertTrue(c.warnings.isEmpty());
    }

    @Test
    public void bareHostBecomesHttps() {
        ScanContent c = ScanContent.parse("example.org/x");
        assertEquals(Kind.LINK, c.kind);
        assertEquals("https://example.org/x", c.get("url"));
    }

    @Test
    public void httpIsNotEncrypted() {
        assertTrue(ScanContent.parse("http://example.com").warnings.contains(Warning.NOT_ENCRYPTED));
    }

    @Test
    public void userInfoTrickShowsTheRealHost() {
        ScanContent c = ScanContent.parse("https://www.leumi.co.il@evil.example/login");
        assertTrue(c.warnings.contains(Warning.USER_INFO));
        assertEquals("evil.example", c.get("host"));
    }

    @Test
    public void punycodeAndUnicodeHosts() {
        assertTrue(ScanContent.parse("https://xn--pypal-4ve.com").warnings.contains(Warning.PUNYCODE));
        ScanContent c = ScanContent.parse("https://pаypal.com"); // Cyrillic a
        assertTrue(c.warnings.contains(Warning.PUNYCODE));
        assertTrue(c.get("host").startsWith("xn--"));
    }

    @Test
    public void lookalikeDomains() {
        ScanContent c = ScanContent.parse("https://paypal-secure-login.com/verify");
        assertTrue(c.warnings.contains(Warning.LOOKALIKE));
        assertEquals("paypal", c.get("lookalike"));
        assertTrue(ScanContent.parse("https://login.paypa1.com.example.net").warnings.contains(Warning.LOOKALIKE));
        assertFalse(ScanContent.parse("https://www.paypal.com/signin").warnings.contains(Warning.LOOKALIKE));
        assertFalse(ScanContent.parse("https://online.leumi.co.il").warnings.contains(Warning.LOOKALIKE));
    }

    @Test
    public void shortenersAndIpAddresses() {
        assertTrue(ScanContent.parse("https://bit.ly/3abc").warnings.contains(Warning.SHORTENER));
        assertTrue(ScanContent.parse("http://192.168.1.1/admin").warnings.contains(Warning.IP_ADDRESS));
        assertTrue(ScanContent.parse("http://[::1]/").warnings.contains(Warning.IP_ADDRESS));
    }

    @Test
    public void otherSchemesAreNotWeb() {
        assertTrue(ScanContent.parse("javascript:alert(1)").warnings.contains(Warning.NOT_WEB));
        assertTrue(ScanContent.parse("intent://scan/#Intent;scheme=zxing;end").warnings.contains(Warning.NOT_WEB));
    }

    @Test
    public void registrableDomainKnowsSecondLevels() {
        assertEquals("leumi.co.il", ScanContent.registrableDomain("online.leumi.co.il"));
        assertEquals("example.com", ScanContent.registrableDomain("a.b.example.com"));
        assertEquals("example.com", ScanContent.registrableDomain("example.com"));
    }

    @Test
    public void ztInvite() {
        ScanContent c = ScanContent.parse("https://zerotrust.bz/join/tasks#k=abc");
        assertEquals(Kind.ZT_INVITE, c.kind);
        assertEquals("tasks", c.get("app"));
        assertEquals(Kind.LINK, ScanContent.parse("http://zerotrust.bz/join/tasks#k").kind);
        assertEquals(Kind.LINK, ScanContent.parse("https://zerotrust.bz.evil.example/join/tasks").kind);
    }

    @Test
    public void wifiWithEscapes() {
        ScanContent c = ScanContent.parse("WIFI:T:WPA;S:Home\\;Net;P:pa\\:ss\\\\1;H:true;;");
        assertEquals(Kind.WIFI, c.kind);
        assertEquals("Home;Net", c.get("ssid"));
        assertEquals("pa:ss\\1", c.get("password"));
        assertEquals("WPA", c.get("security"));
        assertEquals("true", c.get("hidden"));
        ScanContent open = ScanContent.parse("WIFI:S:Cafe;;");
        assertEquals("nopass", open.get("security"));
        assertNull(open.get("password"));
    }

    @Test
    public void meCard() {
        ScanContent c = ScanContent.parse("MECARD:N:Cohen,Dana;TEL:+972501234567;TEL:031234567;EMAIL:dana@example.com;;");
        assertEquals(Kind.CONTACT, c.kind);
        assertEquals("Dana Cohen", c.get("name"));
        assertEquals("+972501234567\n031234567", c.get("phone"));
        assertEquals("dana@example.com", c.get("email"));
    }

    @Test
    public void vCard() {
        ScanContent c = ScanContent.parse("BEGIN:VCARD\r\nVERSION:3.0\r\nN:Levi;Noa;;;\r\nTEL;TYPE=CELL:+97250\r\n 1112222\r\n"
            + "item1.EMAIL:noa@example.com\r\nORG:Acme;Lab\r\nEND:VCARD");
        assertEquals(Kind.CONTACT, c.kind);
        assertEquals("Noa Levi", c.get("name"));
        assertEquals("+972501112222", c.get("phone"));
        assertEquals("noa@example.com", c.get("email"));
        assertEquals("Acme Lab", c.get("org"));
    }

    @Test
    public void event() {
        ScanContent c = ScanContent.parse("BEGIN:VEVENT\nSUMMARY:Launch\\, party\nDTSTART:20261119T180000Z\nDTEND:20261119T200000Z\nLOCATION:Tel Aviv\nEND:VEVENT");
        assertEquals(Kind.EVENT, c.kind);
        assertEquals("Launch, party", c.get("title"));
        assertEquals("1795111200000", c.get("startMs"));
        assertEquals("1795118400000", c.get("endMs"));
        assertEquals("Tel Aviv", c.get("location"));
        ScanContent allDay = ScanContent.parse("BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Day\nDTSTART;VALUE=DATE:20261120\nEND:VEVENT\nEND:VCALENDAR");
        assertEquals(Kind.EVENT, allDay.kind);
        assertEquals("true", allDay.get("allDay"));
    }

    @Test
    public void phoneSmsEmailPlace() {
        ScanContent tel = ScanContent.parse("tel:+972-3-1234567");
        assertEquals(Kind.PHONE, tel.kind);
        assertEquals("+972-3-1234567", tel.get("phone"));
        ScanContent sms = ScanContent.parse("SMSTO:+97250123:Hello there");
        assertEquals(Kind.SMS, sms.kind);
        assertEquals("+97250123", sms.get("phone"));
        assertEquals("Hello there", sms.get("body"));
        ScanContent sms2 = ScanContent.parse("sms:+97250123?body=Hi%20you");
        assertEquals("Hi you", sms2.get("body"));
        ScanContent mail = ScanContent.parse("mailto:a@example.com?subject=Hi&body=Yo");
        assertEquals(Kind.EMAIL, mail.kind);
        assertEquals("a@example.com", mail.get("email"));
        assertEquals("Hi", mail.get("subject"));
        ScanContent mat = ScanContent.parse("MATMSG:TO:b@example.com;SUB:S;BODY:B;;");
        assertEquals("b@example.com", mat.get("email"));
        ScanContent geo = ScanContent.parse("geo:32.0853,34.7818?q=Dizengoff");
        assertEquals(Kind.PLACE, geo.kind);
        assertEquals("32.0853", geo.get("lat"));
        assertEquals("Dizengoff", geo.get("query"));
    }

    @Test
    public void otpKeepsTheSecretOutOfFields() {
        ScanContent c = ScanContent.parse("otpauth://totp/Example:dana@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example");
        assertEquals(Kind.OTP, c.kind);
        assertEquals("Example", c.get("issuer"));
        assertEquals("dana@example.com", c.get("account"));
        assertFalse(c.fields.containsValue("JBSWY3DPEHPK3PXP"));
        assertEquals("true", ScanContent.parse("otpauth-migration://offline?data=abc").get("migration"));
    }

    @Test
    public void plainText() {
        assertEquals(Kind.TEXT, ScanContent.parse("Hello world").kind);
        assertEquals(Kind.TEXT, ScanContent.parse("4006381333931").kind);
    }
}
