package dev.sagron.zerotrustcamera.capture.zt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class PanoStitchTest {
    private static final int SCENE_W = 900, SCENE_H = 260, W = 240, H = 200;

    /** A scene with smooth, never-repeating detail. */
    private static int[] scene() {
        Random r = new Random(7);
        double[] ph = new double[12];
        for (int i = 0; i < ph.length; i++) ph[i] = r.nextDouble() * 6.28;
        int[] px = new int[SCENE_W * SCENE_H];
        for (int y = 0; y < SCENE_H; y++) {
            for (int x = 0; x < SCENE_W; x++) {
                double v = 0;
                for (int k = 0; k < 6; k++) {
                    v += Math.sin(x * (0.013 + 0.017 * k) + ph[k]) * Math.cos(y * (0.011 + 0.019 * k) + ph[k + 6]);
                }
                int g = (int) Math.max(0, Math.min(255, 128 + v * 40));
                px[y * SCENE_W + x] = 0xFF000000 | g << 16 | ((g * 3 / 4) & 0xFF) << 8 | (255 - g);
            }
        }
        return px;
    }

    private static int[] cut(int[] scene, int x, int y) {
        int[] out = new int[W * H];
        for (int r = 0; r < H; r++) System.arraycopy(scene, (y + r) * SCENE_W + x, out, r * W, W);
        return out;
    }

    private static int diff(int a, int b) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF))
                + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                + Math.abs((a & 0xFF) - (b & 0xFF));
    }

    @Test
    public void focalFromFieldOfView() {
        assertEquals(500.0, PanoStitch.focalPx(1000, 90), 1e-6);
        assertTrue(PanoStitch.warpedWidth(1000, 500) < 1000);
        // A very long lens bends next to nothing.
        assertTrue(PanoStitch.warpedWidth(1000, 1e7) >= 999);
    }

    @Test
    public void warpKeepsTheMiddleAndEmptiesTheCorners() {
        int[] src = cut(scene(), 100, 20);
        double f = PanoStitch.focalPx(W, 70);
        int ow = PanoStitch.warpedWidth(W, f);
        int[] out = PanoStitch.warp(src, W, H, f);
        assertEquals(ow * H, out.length);
        int mid = out[(H / 2) * ow + ow / 2];
        assertTrue(diff(mid, src[(H / 2) * W + W / 2]) < 24);
        // Top corner: the bend reaches outside the frame there.
        assertEquals(0, out[0] >>> 24);
        // The middle column is full height.
        assertEquals(0xFF, out[ow / 2] >>> 24);
    }

    @Test
    public void alignFindsTheShiftEvenWithAPoorGuess() {
        int[] s = scene();
        int[] a = cut(s, 100, 30), b = cut(s, 100 + 83, 30 - 7);
        int[] r = PanoStitch.align(a, b, W, H, 70, 30, 12);
        assertEquals(83, r[0]);
        assertEquals(-7, r[1]);
        assertTrue("score " + r[2], r[2] <= 1);
    }

    @Test
    public void alignSaysWhenFramesShareNothing() {
        int[] a = new int[W * H], b = new int[W * H];
        int[] r = PanoStitch.align(a, b, W, H, 80, 20, 8);
        assertEquals(Integer.MAX_VALUE, r[2]);
    }

    @Test
    public void stitchedSweepMatchesTheScene() {
        int[] s = scene();
        int[] xs = {60, 141, 229, 300, 388, 470, 551};
        int[] ys = {30, 27, 33, 30, 24, 31, 30};
        PanoStitch.Canvas canvas = new PanoStitch.Canvas(H, 12, 5000);
        int[] prev = cut(s, xs[0], ys[0]);
        canvas.add(prev, W, H, 0, 0);
        int x = 0, y = 0;
        for (int i = 1; i < xs.length; i++) {
            int[] cur = cut(s, xs[i], ys[i]);
            int[] r = PanoStitch.align(prev, cur, W, H, 80, 24, 12);
            x += r[0];
            y += r[1];
            canvas.add(cur, W, H, x, y);
            prev = cur;
        }
        assertEquals(xs[xs.length - 1] - xs[0] + W, canvas.width());
        // Rows every frame covered: the drift spans 24..33, so 9 rows are trimmed.
        assertEquals(H - 9, canvas.resultHeight());
        int[] out = canvas.result();
        int top = 33;
        long total = 0;
        for (int r = 0; r < canvas.resultHeight(); r++) {
            for (int c = 0; c < canvas.width(); c++) {
                total += diff(out[r * canvas.width() + c], s[(top + r) * SCENE_W + xs[0] + c]);
            }
        }
        assertEquals(0, total);
    }

    @Test
    public void canvasStopsAtItsWidestAndFades() {
        PanoStitch.Canvas canvas = new PanoStitch.Canvas(2, 0, 5);
        int[] black = {0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000};
        int[] white = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};
        canvas.add(black, 3, 2, 0, 0);
        assertTrue(!canvas.full(2));
        canvas.add(white, 3, 2, 1, 0);
        assertEquals(4, canvas.width());
        int[] out = canvas.result();
        // Old on the left, a fade across the two shared pixels, new on the right.
        assertEquals(0xFF000000, out[0]);
        int g1 = out[1] & 0xFF, g2 = out[2] & 0xFF;
        assertTrue(g1 > 0 && g1 < g2 && g2 < 255);
        assertEquals(0xFFFFFFFF, out[3]);
        assertTrue(canvas.full(2));
        canvas.add(white, 3, 2, 4, 0);
        assertEquals(5, canvas.width());
    }

    @Test
    public void mirrorTurnsAFrameOver() {
        int[] px = {1, 2, 3, 4, 5, 6};
        PanoStitch.mirror(px, 3, 2);
        org.junit.Assert.assertArrayEquals(new int[] {3, 2, 1, 6, 5, 4}, px);
    }
}
