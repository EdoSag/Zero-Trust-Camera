package dev.sagron.zerotrustcamera.capture.zt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Takes everything personal out of a JPEG without redrawing it: every APP1 to APP15 segment (Exif
 * with place, time and phone, XMP, IPTC, maker notes, Photoshop data) and comments are dropped,
 * and so is anything after the end of the picture (some phones hide whole extra files there).
 * Kept: the JFIF header, the colour profile (an APP2 ICC_PROFILE segment, which says nothing about
 * the person) and the picture itself, so the photo looks exactly the same. Orientation is put
 * back afterwards by the caller (see CleanShare), since it lives in the Exif that is dropped.
 *
 * Plain Java, so it is tested on the computer (JpegMetadataStripperTest).
 */
public final class JpegMetadataStripper {
    private JpegMetadataStripper() {}

    private static final byte[] ICC = "ICC_PROFILE\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    /** The cleaned JPEG. Throws if [jpeg] is not a JPEG this can walk. */
    public static byte[] strip(byte[] jpeg) throws IOException {
        if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            throw new IOException("not a JPEG");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length);
        out.write(0xFF);
        out.write(0xD8);
        int pos = 2;
        while (true) {
            if (pos + 1 >= jpeg.length) throw new IOException("ends early");
            if ((jpeg[pos] & 0xFF) != 0xFF) throw new IOException("bad marker at " + pos);
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xFF) { // fill byte
                pos++;
                continue;
            }
            if (marker == 0xD9) { // end of picture with no scan: keep as is
                out.write(0xFF);
                out.write(0xD9);
                return out.toByteArray();
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { // markers with no length
                out.write(jpeg, pos, 2);
                pos += 2;
                continue;
            }
            if (pos + 3 >= jpeg.length) throw new IOException("ends early");
            int len = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
            int end = pos + 2 + len;
            if (len < 2 || end > jpeg.length) throw new IOException("bad length at " + pos);
            boolean drop = marker == 0xFE // comment
                || (marker >= 0xE1 && marker <= 0xEF && !(marker == 0xE2 && startsWith(jpeg, pos + 4, end, ICC)));
            if (!drop) out.write(jpeg, pos, end - pos);
            pos = end;
            if (marker == 0xDA) {
                // Picture data. Inside it, 0xFF is followed only by 0x00 (stuffing) or a restart
                // marker; any other marker ends this scan (a progressive JPEG has several).
                int start = pos;
                while (pos + 1 < jpeg.length) {
                    if ((jpeg[pos] & 0xFF) == 0xFF) {
                        int next = jpeg[pos + 1] & 0xFF;
                        if (next != 0x00 && !(next >= 0xD0 && next <= 0xD7) && next != 0xFF) break;
                    }
                    pos++;
                }
                if (pos + 1 >= jpeg.length) throw new IOException("no end of picture");
                out.write(jpeg, start, pos - start);
                if ((jpeg[pos + 1] & 0xFF) == 0xD9) {
                    out.write(0xFF);
                    out.write(0xD9);
                    return out.toByteArray(); // whatever follows is left out
                }
            }
        }
    }

    private static boolean startsWith(byte[] b, int from, int end, byte[] prefix) {
        if (end - from < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (b[from + i] != prefix[i]) return false;
        return true;
    }
}
