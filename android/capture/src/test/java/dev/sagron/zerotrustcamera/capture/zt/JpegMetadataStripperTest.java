package dev.sagron.zerotrustcamera.capture.zt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class JpegMetadataStripperTest {
    private static byte[] segment(int marker, String body) {
        byte[] b = body.getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0xFF);
        o.write(marker);
        o.write((b.length + 2) >> 8);
        o.write((b.length + 2) & 0xFF);
        o.write(b, 0, b.length);
        return o.toByteArray();
    }

    private static byte[] join(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p, 0, p.length);
        return o.toByteArray();
    }

    private static final byte[] SOI = {(byte) 0xFF, (byte) 0xD8};
    private static final byte[] EOI = {(byte) 0xFF, (byte) 0xD9};
    // Scan data with a stuffed 0xFF00 and a restart marker, which must not end the scan.
    private static final byte[] DATA = {1, 2, (byte) 0xFF, 0, 3, (byte) 0xFF, (byte) 0xD3, 4};

    private static boolean contains(byte[] hay, String needle) {
        return new String(hay, StandardCharsets.ISO_8859_1).contains(needle);
    }

    @Test
    public void dropsExifXmpIptcCommentsAndTrailer() throws IOException {
        byte[] jfif = segment(0xE0, "JFIF\0");
        byte[] icc = segment(0xE2, "ICC_PROFILE\0colours");
        byte[] dqt = segment(0xDB, "tables");
        byte[] sos = segment(0xDA, "scan");
        byte[] in = join(SOI, jfif, segment(0xE1, "Exif\0\0GPS 32.1N"), segment(0xE1, "http://ns.adobe.com/xap/ secret"),
            icc, segment(0xED, "Photoshop 3.0 IPTC name"), segment(0xFE, "comment"), segment(0xE2, "MPF\0more"),
            dqt, sos, DATA, EOI, "SEFT trailer 32.1N".getBytes(StandardCharsets.ISO_8859_1));
        byte[] out = JpegMetadataStripper.strip(in);
        assertArrayEquals(join(SOI, jfif, icc, dqt, sos, DATA, EOI), out);
        assertFalse(contains(out, "32.1N"));
        assertFalse(contains(out, "IPTC"));
    }

    @Test
    public void keepsEveryScanOfAProgressiveJpeg() throws IOException {
        byte[] sos = segment(0xDA, "scan");
        byte[] dht = segment(0xC4, "huff");
        byte[] in = join(SOI, sos, DATA, dht, sos, DATA, EOI);
        assertArrayEquals(in, JpegMetadataStripper.strip(in));
    }

    @Test
    public void cleanJpegIsUnchanged() throws IOException {
        byte[] in = join(SOI, segment(0xDB, "t"), segment(0xC0, "frame"), segment(0xDA, "s"), DATA, EOI);
        assertArrayEquals(in, JpegMetadataStripper.strip(in));
    }

    @Test(expected = IOException.class)
    public void refusesNonJpeg() throws IOException {
        JpegMetadataStripper.strip("\u0089PNG....".getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test(expected = IOException.class)
    public void refusesTruncated() throws IOException {
        JpegMetadataStripper.strip(join(SOI, segment(0xDA, "s"), DATA));
    }

    @Test
    public void lengthsAreChecked() {
        byte[] bad = join(SOI, new byte[] {(byte) 0xFF, (byte) 0xE1, 0x7F, 0x00, 1, 2});
        boolean threw = false;
        try {
            JpegMetadataStripper.strip(bad);
        } catch (IOException e) {
            threw = true;
        }
        assertTrue(threw);
        assertEquals(2, SOI.length);
    }
}
