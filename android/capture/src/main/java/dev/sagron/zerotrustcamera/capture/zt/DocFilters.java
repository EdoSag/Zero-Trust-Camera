package dev.sagron.zerotrustcamera.capture.zt;

import java.util.Arrays;

/**
 * Looks for scanned pages, on ARGB pixels: enhanced colour (contrast stretched, shadows lifted),
 * grayscale, and black and white (each pixel compared with its neighbourhood, so a shadow across
 * the page doesn't turn black). Plain Java, so it is tested on the computer (DocFiltersTest).
 */
public final class DocFilters {
    private DocFilters() {}

    public static int luma(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (r * 299 + g * 587 + b * 114 + 500) / 1000;
    }

    public static int[] grayscale(int[] px) {
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int l = luma(px[i]);
            out[i] = 0xFF000000 | (l << 16) | (l << 8) | l;
        }
        return out;
    }

    /** Each channel stretched so its darkest 1% is black and its lightest 2% is white. */
    public static int[] enhance(int[] px) {
        int[] lo = new int[3], hi = new int[3];
        for (int c = 0; c < 3; c++) {
            int shift = 16 - 8 * c;
            long[] hist = new long[256];
            for (int p : px) hist[(p >> shift) & 0xFF]++;
            lo[c] = percentile(hist, px.length, 0.01);
            hi[c] = percentile(hist, px.length, 0.98);
            if (hi[c] - lo[c] < 32) { lo[c] = 0; hi[c] = 255; } // flat: leave it
        }
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int v = 0xFF000000;
            for (int c = 0; c < 3; c++) {
                int shift = 16 - 8 * c;
                int x = (px[i] >> shift) & 0xFF;
                int y = (x - lo[c]) * 255 / (hi[c] - lo[c]);
                v |= Math.max(0, Math.min(255, y)) << shift;
            }
            out[i] = v;
        }
        return out;
    }

    /**
     * Black and white: a pixel turns black when it is clearly darker than the average around it
     * (a square about a sixteenth of the page wide).
     */
    public static int[] blackAndWhite(int[] px, int w, int h) {
        int[] g = new int[px.length];
        for (int i = 0; i < px.length; i++) g[i] = luma(px[i]);
        int r = Math.max(4, Math.min(w, h) / 32);
        int[] mean = DocDetect.boxBlur(g, w, h, r);
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            out[i] = g[i] < mean[i] - 12 ? 0xFF000000 : 0xFFFFFFFF;
        }
        return out;
    }

    private static int percentile(long[] hist, long total, double q) {
        long target = (long) Math.ceil(total * q);
        long seen = 0;
        for (int i = 0; i < 256; i++) {
            seen += hist[i];
            if (seen >= target) return i;
        }
        return 255;
    }

    /** For tests: how many pixels are pure black. */
    static int blackCount(int[] px) {
        return (int) Arrays.stream(px).filter(p -> (p & 0xFFFFFF) == 0).count();
    }
}
