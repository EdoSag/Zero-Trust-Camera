package dev.sagron.zerotrustcamera.capture.zt;

/**
 * The photo editor's pixel maths: the colour matrix behind the Adjust sliders, and the mosaic
 * that hides faces, plates and anything else picked. Plain Java, so it is tested on the computer
 * (PhotoOpsTest).
 */
public final class PhotoOps {
    private PhotoOps() {}

    /**
     * A 4x5 colour matrix (Android's ColorMatrix layout) for the Adjust sliders. Each value is
     * -1 to 1, and 0 leaves the photo as it is.
     */
    public static float[] adjustMatrix(float brightness, float contrast, float saturation, float warmth) {
        float s = 1f + clamp(saturation);
        // Luminance weights, as in ColorMatrix.setSaturation.
        float lr = 0.213f * (1 - s), lg = 0.715f * (1 - s), lb = 0.072f * (1 - s);
        float[][] sat = {
            {lr + s, lg, lb},
            {lr, lg + s, lb},
            {lr, lg, lb + s},
        };
        float c = clamp(contrast);
        float scale = c >= 0 ? 1f + c : 1f + c * 0.7f;
        float offset = 128f * (1f - scale) + clamp(brightness) * 80f;
        float warm = clamp(warmth) * 25f;
        float[] m = new float[20];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) m[row * 5 + col] = sat[row][col] * scale;
            m[row * 5 + 4] = offset;
        }
        m[4] += warm;
        m[14] -= warm;
        m[18] = 1f;
        return m;
    }

    public static boolean isIdentity(float brightness, float contrast, float saturation, float warmth) {
        return brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f;
    }

    /** One pixel through [m]; used by the tests and nowhere hot. */
    public static int apply(float[] m, int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int nr = channel(m, 0, r, g, b, a), ng = channel(m, 5, r, g, b, a), nb = channel(m, 10, r, g, b, a);
        int na = channel(m, 15, r, g, b, a);
        return (na << 24) | (nr << 16) | (ng << 8) | nb;
    }

    private static int channel(float[] m, int o, int r, int g, int b, int a) {
        float v = m[o] * r + m[o + 1] * g + m[o + 2] * b + m[o + 3] * a + m[o + 4];
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    /**
     * Mosaic block size for a hidden area of [w] by [h] pixels: about six blocks across its longer
     * side, so a face or a number plate can't be read back from it.
     */
    public static int blockFor(int w, int h) {
        return Math.max(4, (Math.max(w, h) + 5) / 6);
    }

    /**
     * Replaces the area x0..x1, y0..y1 (end exclusive, clamped to the picture) of ARGB [px], [w]
     * pixels wide, with blocks of their average colour.
     */
    public static void mosaic(int[] px, int w, int h, int x0, int y0, int x1, int y1) {
        x0 = Math.max(0, Math.min(w, x0));
        x1 = Math.max(0, Math.min(w, x1));
        y0 = Math.max(0, Math.min(h, y0));
        y1 = Math.max(0, Math.min(h, y1));
        if (x1 <= x0 || y1 <= y0) return;
        int block = blockFor(x1 - x0, y1 - y0);
        for (int by = y0; by < y1; by += block) {
            int ey = Math.min(y1, by + block);
            for (int bx = x0; bx < x1; bx += block) {
                int ex = Math.min(x1, bx + block);
                long sa = 0, sr = 0, sg = 0, sb = 0;
                int n = (ex - bx) * (ey - by);
                for (int y = by; y < ey; y++) {
                    for (int x = bx; x < ex; x++) {
                        int p = px[y * w + x];
                        sa += (p >>> 24) & 0xFF;
                        sr += (p >> 16) & 0xFF;
                        sg += (p >> 8) & 0xFF;
                        sb += p & 0xFF;
                    }
                }
                int avg = (int) ((sa / n) << 24 | (sr / n) << 16 | (sg / n) << 8 | (sb / n));
                for (int y = by; y < ey; y++) {
                    for (int x = bx; x < ex; x++) px[y * w + x] = avg;
                }
            }
        }
    }

    /**
     * A rectangle from two corners in 0 to 1 picture units, in order (left, top, right, bottom)
     * and inside the picture, or null when it is too small to mean anything.
     */
    public static float[] normalizedRect(float ax, float ay, float bx, float by, float minSize) {
        float l = clamp01(Math.min(ax, bx)), r = clamp01(Math.max(ax, bx));
        float t = clamp01(Math.min(ay, by)), b = clamp01(Math.max(ay, by));
        if (r - l < minSize || b - t < minSize) return null;
        return new float[] {l, t, r, b};
    }

    /**
     * Rough box around a face that android.media.FaceDetector found, from the point between the
     * eyes and the eye distance (in pixels), widened to cover hair and chin.
     */
    public static float[] faceBox(float midX, float midY, float eyes, int w, int h) {
        float l = midX - eyes * 1.3f, r = midX + eyes * 1.3f;
        float t = midY - eyes * 1.4f, b = midY + eyes * 2.1f;
        return normalizedRect(l / w, t / h, r / w, b / h, 0f);
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
