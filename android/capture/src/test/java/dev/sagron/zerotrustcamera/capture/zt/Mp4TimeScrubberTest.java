package dev.sagron.zerotrustcamera.capture.zt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

public class Mp4TimeScrubberTest {

    private static byte[] box(String type, byte[]... parts) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] p : parts) body.write(p);
        ByteBuffer b = ByteBuffer.allocate(8 + body.size());
        b.putInt(8 + body.size());
        b.put(type.getBytes(StandardCharsets.US_ASCII));
        b.put(body.toByteArray());
        return b.array();
    }

    /** A box whose size is written the long way (size field 1, then 64 bits). */
    private static byte[] bigBox(String type, byte[] body) {
        ByteBuffer b = ByteBuffer.allocate(16 + body.length);
        b.putInt(1);
        b.put(type.getBytes(StandardCharsets.US_ASCII));
        b.putLong(16 + body.length);
        b.put(body);
        return b.array();
    }

    /** version, flags, created, modified, then some other fields that must not change. */
    private static byte[] header(int version) {
        ByteBuffer b = ByteBuffer.allocate(version == 1 ? 4 + 16 + 12 : 4 + 8 + 12);
        b.put((byte) version).put(new byte[] {0, 0, 7});
        if (version == 1) {
            b.putLong(0x1122334455667788L).putLong(0x0102030405060708L);
        } else {
            b.putInt(0xE4D5C6B7).putInt(0xE4D5C6B8);
        }
        b.putInt(90000).putInt(123456).putInt(0x00010000);
        return b.array();
    }

    private static byte[] cleared(int version) {
        byte[] h = header(version);
        java.util.Arrays.fill(h, 4, version == 1 ? 20 : 12, (byte) 0);
        return h;
    }

    private static byte[] movie(boolean scrubbed, int version) throws IOException {
        byte[] h = scrubbed ? cleared(version) : header(version);
        byte[] other = box("hdlr", new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        byte[] trak = box("trak", box("tkhd", h), box("mdia", box("mdhd", h), other));
        return box("moov", box("mvhd", h), trak, trak, box("udta", other));
    }

    private static byte[] scrub(byte[] file, int expected) throws IOException {
        File f = File.createTempFile("scrub", ".mp4");
        try {
            Files.write(f.toPath(), file);
            try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
                assertEquals(expected, Mp4TimeScrubber.scrub(raf.getChannel(), raf.getChannel()));
            }
            return Files.readAllBytes(f.toPath());
        } finally {
            f.delete();
        }
    }

    private static byte[] join(byte[]... parts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p);
        return out.toByteArray();
    }

    @Test
    public void clearsEveryTimeAndNothingElse() throws IOException {
        byte[] ftyp = box("ftyp", "isommp42".getBytes(StandardCharsets.US_ASCII));
        byte[] mdat = box("mdat", new byte[300]);
        for (int version = 0; version <= 1; version++) {
            // The index (moov) can sit before or after the video data
            assertArrayEquals(
                    join(ftyp, mdat, movie(true, version)),
                    scrub(join(ftyp, mdat, movie(false, version)), 5));
            assertArrayEquals(
                    join(ftyp, movie(true, version), mdat),
                    scrub(join(ftyp, movie(false, version), mdat), 5));
        }
    }

    @Test
    public void readsLongSizes() throws IOException {
        byte[] ftyp = box("ftyp", new byte[8]);
        byte[] mdat = bigBox("mdat", new byte[64]);
        assertArrayEquals(
                join(ftyp, mdat, bigBox("moov", box("mvhd", cleared(0)))),
                scrub(join(ftyp, mdat, bigBox("moov", box("mvhd", header(0)))), 1));
    }

    @Test
    public void leavesOtherFilesAlone() throws IOException {
        byte[] notMp4 = "This is not a video at all, just some text.".getBytes(StandardCharsets.US_ASCII);
        assertArrayEquals(notMp4, scrub(notMp4, 0));

        // Cut off in the middle of the index: whatever is whole is cleared, nothing is damaged
        byte[] whole = join(box("ftyp", new byte[8]), movie(false, 0));
        byte[] cut = java.util.Arrays.copyOf(whole, whole.length - 20);
        assertEquals(cut.length, scrub(cut, 0).length);

        assertArrayEquals(new byte[0], scrub(new byte[0], 0));
    }
}
