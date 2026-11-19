package dev.sagron.zerotrustcamera.capture.zt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhotoOpsTest {
    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Test
    public void zeroAdjustLeavesPixelsAlone() {
        float[] m = PhotoOps.adjustMatrix(0, 0, 0, 0);
        for (int p : new int[] {rgb(0, 0, 0), rgb(255, 255, 255), rgb(12, 200, 99), rgb(130, 5, 250)}) {
            assertEquals(p, PhotoOps.apply(m, p));
        }
        assertTrue(PhotoOps.isIdentity(0, 0, 0, 0));
    }

    @Test
    public void brightnessLiftsAndClamps() {
        float[] m = PhotoOps.adjustMatrix(1, 0, 0, 0);
        assertEquals(rgb(180, 180, 180), PhotoOps.apply(m, rgb(100, 100, 100)));
        assertEquals(rgb(255, 255, 255), PhotoOps.apply(m, rgb(250, 250, 250)));
        float[] dark = PhotoOps.adjustMatrix(-1, 0, 0, 0);
        assertEquals(rgb(0, 0, 0), PhotoOps.apply(dark, rgb(50, 50, 50)));
    }

    @Test
    public void contrastKeepsMiddleGray() {
        float[] up = PhotoOps.adjustMatrix(0, 0.5f, 0, 0);
        assertEquals(rgb(128, 128, 128), PhotoOps.apply(up, rgb(128, 128, 128)));
        assertEquals(rgb(218, 218, 218), PhotoOps.apply(up, rgb(188, 188, 188)));
        float[] down = PhotoOps.adjustMatrix(0, -1, 0, 0);
        int flat = PhotoOps.apply(down, rgb(255, 255, 255));
        assertTrue(((flat >> 16) & 0xFF) < 255);
    }

    @Test
    public void noSaturationIsGray() {
        float[] m = PhotoOps.adjustMatrix(0, 0, -1, 0);
        int p = PhotoOps.apply(m, rgb(200, 40, 90));
        int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
        assertEquals(r, g);
        assertEquals(g, b);
    }

    @Test
    public void warmthMovesRedAndBlue() {
        float[] m = PhotoOps.adjustMatrix(0, 0, 0, 1);
        assertEquals(rgb(125, 100, 75), PhotoOps.apply(m, rgb(100, 100, 100)));
    }

    @Test
    public void alphaIsKept() {
        float[] m = PhotoOps.adjustMatrix(0.3f, 0.2f, -0.4f, 0.5f);
        assertEquals(0x80, PhotoOps.apply(m, 0x80646464) >>> 24);
    }

    @Test
    public void mosaicMakesUniformBlocksAndLeavesTheRest() {
        int w = 24, h = 12;
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) px[i] = rgb(i % 256, (i * 7) % 256, (i * 13) % 256);
        int[] before = px.clone();
        PhotoOps.mosaic(px, w, h, 0, 0, 12, 12);
        int block = PhotoOps.blockFor(12, 12);
        assertEquals(4, block);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) assertEquals(px[0], px[y * w + x]);
        }
        for (int y = 0; y < h; y++) {
            for (int x = 12; x < w; x++) assertEquals(before[y * w + x], px[y * w + x]);
        }
    }

    @Test
    public void mosaicAveragesTheBlock() {
        int[] px = {rgb(0, 0, 0), rgb(200, 100, 50), rgb(0, 0, 0), rgb(200, 100, 50)};
        PhotoOps.mosaic(px, 2, 2, 0, 0, 2, 2);
        for (int p : px) assertEquals(rgb(100, 50, 25), p);
    }

    @Test
    public void mosaicClampsToThePicture() {
        int[] px = new int[16];
        PhotoOps.mosaic(px, 4, 4, -10, -10, 99, 99);
        PhotoOps.mosaic(px, 4, 4, 3, 3, 1, 1); // empty, no crash
    }

    @Test
    public void bigAreasGetBigBlocks() {
        assertEquals(100, PhotoOps.blockFor(600, 300));
    }

    @Test
    public void rectsAreOrderedAndClamped() {
        assertArrayEquals(new float[] {0.2f, 0f, 1f, 0.5f}, PhotoOps.normalizedRect(1.4f, 0.5f, 0.2f, -0.3f, 0.01f), 1e-6f);
        assertNull(PhotoOps.normalizedRect(0.5f, 0.5f, 0.505f, 0.9f, 0.01f));
    }

    @Test
    public void faceBoxCoversTheFace() {
        float[] r = PhotoOps.faceBox(500, 400, 100, 1000, 1000);
        assertArrayEquals(new float[] {0.37f, 0.26f, 0.63f, 0.61f}, r, 1e-5f);
    }
}
