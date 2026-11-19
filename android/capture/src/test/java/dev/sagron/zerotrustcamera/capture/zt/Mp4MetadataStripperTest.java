package dev.sagron.zerotrustcamera.capture.zt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

public class Mp4MetadataStripperTest {

    private static byte[] box(String type, byte[]... parts) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] p : parts) body.write(p);
        ByteBuffer b = ByteBuffer.allocate(8 + body.size());
        b.putInt(8 + body.size());
        b.put(type.getBytes(StandardCharsets.ISO_8859_1));
        b.put(body.toByteArray());
        return b.array();
    }

    private static byte[] text(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[]... parts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p);
        return out.toByteArray();
    }

    private static int strip(byte[] mp4, File f) throws IOException {
        Files.write(f.toPath(), mp4);
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            return Mp4MetadataStripper.strip(raf.getChannel());
        }
    }

    private static boolean contains(byte[] hay, String needle) {
        return new String(hay, StandardCharsets.ISO_8859_1).contains(needle);
    }

    @Test
    public void removesLocationModelAndXmpEverywhere() throws Exception {
        byte[] mdat = box("mdat", text("PICTURE-AND-SOUND"));
        byte[] mp4 = concat(
                box("ftyp", text("isom")),
                box("moov",
                        box("mvhd", new byte[20]),
                        box("udta", box("©xyz", text("+32.0853+034.7818/"))),
                        box("meta", text("com.apple.quicktime.location.ISO6709 +32.08+034.78")),
                        box("trak",
                                box("tkhd", new byte[20]),
                                box("udta", text("Pixel 10 Pro")),
                                box("mdia", box("mdhd", new byte[20])))),
                box("uuid", text("<x:xmpmeta GPSLatitude=32/>")),
                mdat);
        File f = File.createTempFile("strip", ".mp4");
        try {
            assertEquals(4, strip(mp4, f));
            byte[] out = Files.readAllBytes(f.toPath());
            assertEquals(mp4.length, out.length);
            assertFalse(contains(out, "+32.0853"));
            assertFalse(contains(out, "location"));
            assertFalse(contains(out, "Pixel"));
            assertFalse(contains(out, "GPSLatitude"));
            // What plays is untouched, and in the same place.
            assertTrue(contains(out, "PICTURE-AND-SOUND"));
            assertArrayEquals(mdat, java.util.Arrays.copyOfRange(out, out.length - mdat.length, out.length));
            assertTrue(contains(out, "mvhd"));
            assertTrue(contains(out, "mdhd"));
        } finally {
            f.delete();
        }
    }

    @Test
    public void aCleanFileIsLeftAlone() throws Exception {
        byte[] mp4 = concat(box("ftyp", text("isom")), box("moov", box("mvhd", new byte[20])),
                box("mdat", text("x")));
        File f = File.createTempFile("strip", ".mp4");
        try {
            assertEquals(0, strip(mp4, f));
            assertArrayEquals(mp4, Files.readAllBytes(f.toPath()));
        } finally {
            f.delete();
        }
    }

    @Test
    public void aDamagedFileStopsWithoutHarm() throws Exception {
        byte[] mp4 = concat(box("ftyp", text("isom")), new byte[]{0, 0, 0x7f, 0, 'm', 'o', 'o', 'v'});
        File f = File.createTempFile("strip", ".mp4");
        try {
            assertEquals(0, strip(mp4, f));
            assertArrayEquals(mp4, Files.readAllBytes(f.toPath()));
        } finally {
            f.delete();
        }
    }
}
