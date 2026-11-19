package dev.sagron.zerotrustcamera.capture.zt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DocFiltersTest {
    static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Test
    public void grayscaleKeepsBrightness() {
        int[] out = DocFilters.grayscale(new int[] {rgb(255, 0, 0), rgb(255, 255, 255)});
        assertEquals(76, out[0] & 0xFF);
        assertEquals(out[0] & 0xFF, (out[0] >> 16) & 0xFF);
        assertEquals(0xFFFFFFFF, out[1]);
    }

    @Test
    public void enhanceStretchesAGreyPage() {
        int[] px = new int[1000];
        for (int i = 0; i < px.length; i++) px[i] = i < 900 ? rgb(170, 170, 170) : rgb(60, 60, 60);
        int[] out = DocFilters.enhance(px);
        assertEquals(255, out[0] & 0xFF); // the paper turns white
        assertEquals(0, out[999] & 0xFF); // the ink turns black
    }

    @Test
    public void blackAndWhiteIgnoresAShadow() {
        int w = 128, h = 64;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            // Left half in shadow (paper 110), right half lit (paper 220); ink is 60 darker.
            int paper = x < w / 2 ? 110 : 220;
            boolean ink = y == 32 && x % 16 < 8;
            int v = ink ? paper - 60 : paper;
            px[y * w + x] = rgb(v, v, v);
        }
        int[] out = DocFilters.blackAndWhite(px, w, h);
        // The shadowed paper stays white, and the ink on both halves turns black.
        assertEquals(0xFFFFFFFF, out[10 * w + 10]);
        assertEquals(0xFF000000, out[32 * w + 2]);
        assertEquals(0xFF000000, out[32 * w + 66]);
        assertTrue(DocFilters.blackCount(out) < w * h / 10);
    }
}
