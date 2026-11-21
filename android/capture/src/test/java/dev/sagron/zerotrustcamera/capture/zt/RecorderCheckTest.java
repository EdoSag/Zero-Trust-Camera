package dev.sagron.zerotrustcamera.capture.zt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class RecorderCheckTest {
    private static final int PART = 64;

    private static byte[] bytes(int n, int seed) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) (i * 31 + seed);
        return b;
    }

    private static RandomAccessFile file() throws Exception {
        File f = File.createTempFile("recorder-check", ".bin");
        f.deleteOnExit();
        return new RandomAccessFile(f, "rw");
    }

    @Test
    public void aFileThatOnlyGrowsHasNoChangedParts() throws Exception {
        try (RandomAccessFile f = file()) {
            RecorderCheck check = new RecorderCheck(PART);
            for (int i = 0; i < 10; i++) {
                f.seek(f.length());
                f.write(bytes(PART / 2 + 7, i));
                check.observe(f.getChannel());
            }
            RecorderCheck.Result r = check.finish(f.getChannel());
            // 10 writes of 39 bytes = 390 bytes: parts 0 to 4 are a whole part behind the end.
            assertEquals(5, r.parts);
            assertTrue(r.changed.isEmpty());
            assertEquals("5 parts, changed: none", r.line());
        }
    }

    @Test
    public void thePartAtTheEndIsNotCountedUntilItIsBehind() throws Exception {
        try (RandomAccessFile f = file()) {
            RecorderCheck check = new RecorderCheck(PART);
            f.write(bytes(PART * 2 - 1, 1));
            check.observe(f.getChannel());
            assertEquals(0, check.finish(f.getChannel()).parts);
            f.seek(f.length());
            f.write(1);
            check.observe(f.getChannel());
            assertEquals(1, check.finish(f.getChannel()).parts);
        }
    }

    @Test
    public void goingBackToPatchTheStartIsFound() throws Exception {
        try (RandomAccessFile f = file()) {
            RecorderCheck check = new RecorderCheck(PART);
            f.write(bytes(PART * 6, 3));
            check.observe(f.getChannel());
            // What a recorder does when it stops: the header at the start gets its sizes.
            f.seek(8);
            f.write(new byte[]{9, 9, 9, 9});
            // And one byte in the middle of part 3.
            f.seek(PART * 3 + 5);
            f.write(77);
            f.seek(f.length());
            f.write(bytes(100, 4));
            RecorderCheck.Result r = check.finish(f.getChannel());
            assertEquals(5, r.parts);
            assertEquals(Arrays.asList(0, 3), r.changed);
            assertEquals("5 parts, changed: 0 3", r.line());
        }
    }

    @Test
    public void aFileThatGotShorterCountsAsChanged() throws Exception {
        try (RandomAccessFile f = file()) {
            RecorderCheck check = new RecorderCheck(PART);
            f.write(bytes(PART * 4, 5));
            check.observe(f.getChannel());
            f.setLength(PART * 2);
            assertEquals(Arrays.asList(2), check.finish(f.getChannel()).changed);
        }
    }

    @Test
    public void manyChangedPartsAreSummedUp() throws Exception {
        try (RandomAccessFile f = file()) {
            RecorderCheck check = new RecorderCheck(PART);
            f.write(bytes(PART * 20, 6));
            check.observe(f.getChannel());
            f.seek(0);
            f.write(bytes(PART * 20, 7));
            RecorderCheck.Result r = check.finish(f.getChannel());
            assertEquals(19, r.parts);
            assertEquals(19, r.changed.size());
            assertTrue(r.line().endsWith("11 and 7 more"));
        }
    }
}
