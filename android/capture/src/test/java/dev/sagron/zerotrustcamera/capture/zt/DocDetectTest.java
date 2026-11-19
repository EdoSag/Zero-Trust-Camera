package dev.sagron.zerotrustcamera.capture.zt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DocDetectTest {
    static final int W = 160, H = 120;

    /** Dark table, light convex polygon (the page), optional noise. */
    static int[] picture(int[] poly, int page, int table) {
        int[] g = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                g[y * W + x] = inside(poly, x + 0.5, y + 0.5) ? page : table;
            }
        }
        return g;
    }

    static boolean inside(int[] p, double x, double y) {
        boolean in = false;
        for (int i = 0, j = 3; i < 4; j = i++) {
            double xi = p[2 * i], yi = p[2 * i + 1], xj = p[2 * j], yj = p[2 * j + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }

    static void near(float expected, float actual) {
        assertEquals(expected, actual, 0.04f);
    }

    @Test
    public void findsAStraightPage() {
        int[] poly = {30, 20, 130, 20, 130, 100, 30, 100};
        float[] q = DocDetect.find(picture(poly, 220, 40), W, H);
        assertNotNull(q);
        near(30f / W, q[0]); near(20f / H, q[1]);
        near(130f / W, q[2]); near(20f / H, q[3]);
        near(130f / W, q[4]); near(100f / H, q[5]);
        near(30f / W, q[6]); near(100f / H, q[7]);
    }

    @Test
    public void findsATiltedPageInPerspective() {
        int[] poly = {50, 15, 140, 25, 120, 110, 25, 95};
        float[] q = DocDetect.find(picture(poly, 200, 60), W, H);
        assertNotNull(q);
        for (int i = 0; i < 8; i += 2) {
            near(poly[i] / (float) W, q[i]);
            near(poly[i + 1] / (float) H, q[i + 1]);
        }
    }

    @Test
    public void textOnThePageDoesNotMatter() {
        int[] poly = {30, 20, 130, 20, 130, 100, 30, 100};
        int[] g = picture(poly, 230, 50);
        for (int y = 30; y < 90; y += 6) for (int x = 40; x < 120; x++) if (x % 7 != 0) g[y * W + x] = 30;
        assertNotNull(DocDetect.find(g, W, H));
    }

    @Test
    public void nothingOnAPlainPicture() {
        int[] g = new int[W * H];
        java.util.Arrays.fill(g, 128);
        assertNull(DocDetect.find(g, W, H));
    }

    @Test
    public void aSmallSpotIsNotAPage() {
        int[] poly = {70, 50, 85, 50, 85, 62, 70, 62};
        assertNull(DocDetect.find(picture(poly, 240, 30), W, H));
    }

    @Test
    public void aRoundBlobIsNotAPage() {
        int[] g = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            double dx = x - 80, dy = y - 60;
            // A plus sign: its corners fill far less than the shape they span.
            boolean in = (Math.abs(dx) < 50 && Math.abs(dy) < 12) || (Math.abs(dx) < 12 && Math.abs(dy) < 50);
            g[y * W + x] = in ? 230 : 30;
        }
        assertNull(DocDetect.find(g, W, H));
    }

    @Test
    public void otsuSplitsTwoLevels() {
        int[] g = new int[100];
        for (int i = 0; i < 100; i++) g[i] = i < 50 ? 40 : 200;
        int t = DocDetect.otsu(g);
        assertTrue(t >= 40 && t < 200);
    }
}
