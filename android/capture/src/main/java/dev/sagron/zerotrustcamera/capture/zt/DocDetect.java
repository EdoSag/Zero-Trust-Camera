package dev.sagron.zerotrustcamera.capture.zt;

/**
 * Finds a sheet of paper in a small grayscale picture, on the phone and without any library:
 * blur, split light from dark (Otsu), take the largest light region, and use its four extreme
 * points as the corners. The region must fill most of the shape those corners make, which keeps
 * odd blobs (a lamp, a white wall edge) from passing as a page.
 *
 * Plain Java, so it is tested on the computer (DocDetectTest).
 */
public final class DocDetect {
    private DocDetect() {}

    /** Smallest page, as a share of the picture. */
    static final double MIN_AREA = 0.12;
    /** The page must fill at least this much of its corner shape. */
    static final double MIN_FILL = 0.80;

    /**
     * Corners as x0,y0 (top left), x1,y1 (top right), x2,y2 (bottom right), x3,y3 (bottom left),
     * each from 0 to 1 of the picture's width or height; null when no page is found.
     *
     * @param gray one luma value (0-255) per pixel, row by row
     */
    public static float[] find(int[] gray, int w, int h) {
        if (w < 16 || h < 16 || gray.length < w * h) return null;
        int[] blurred = boxBlur(gray, w, h, 2);
        int t = otsu(blurred);
        boolean[] light = new boolean[w * h];
        for (int i = 0; i < w * h; i++) light[i] = blurred[i] > t;

        // Largest 4-connected light region.
        int[] label = new int[w * h];
        int[] queue = new int[w * h];
        int best = 0, bestSize = 0, next = 0;
        for (int start = 0; start < w * h; start++) {
            if (!light[start] || label[start] != 0) continue;
            next++;
            int head = 0, tail = 0;
            queue[tail++] = start;
            label[start] = next;
            while (head < tail) {
                int p = queue[head++];
                int x = p % w, y = p / w;
                if (x > 0) tail = visit(p - 1, next, light, label, queue, tail);
                if (x < w - 1) tail = visit(p + 1, next, light, label, queue, tail);
                if (y > 0) tail = visit(p - w, next, light, label, queue, tail);
                if (y < h - 1) tail = visit(p + w, next, light, label, queue, tail);
            }
            if (tail > bestSize) {
                bestSize = tail;
                best = next;
            }
        }
        if (bestSize < MIN_AREA * w * h || bestSize > 0.995 * w * h) return null;

        int tlS = Integer.MAX_VALUE, brS = Integer.MIN_VALUE, trD = Integer.MIN_VALUE, blD = Integer.MAX_VALUE;
        int tl = 0, br = 0, tr = 0, bl = 0;
        for (int p = 0; p < w * h; p++) {
            if (label[p] != best) continue;
            int x = p % w, y = p / w;
            int s = x + y, d = x - y;
            if (s < tlS) { tlS = s; tl = p; }
            if (s > brS) { brS = s; br = p; }
            if (d > trD) { trD = d; tr = p; }
            if (d < blD) { blD = d; bl = p; }
        }
        int[] pts = {tl % w, tl / w, tr % w, tr / w, br % w, br / w, bl % w, bl / w};
        double quad = area(pts);
        if (quad <= 0 || bestSize < MIN_FILL * quad || !convex(pts)) return null;
        if (!edgesOnPage(pts, label, best, w, h)) return null;
        float[] out = new float[8];
        for (int i = 0; i < 8; i += 2) {
            // Pixel centres, so a page filling the frame reaches 0 and 1.
            out[i] = clamp((pts[i] + 0.5f) / w);
            out[i + 1] = clamp((pts[i + 1] + 0.5f) / h);
        }
        return out;
    }

    /**
     * A page's edges run along the region's border: points just inside each edge (pulled a little
     * towards the middle) must be on the page. A cross or a diamond-shaped blob fails here.
     */
    private static boolean edgesOnPage(int[] p, int[] label, int id, int w, int h) {
        double cx = (p[0] + p[2] + p[4] + p[6]) / 4.0, cy = (p[1] + p[3] + p[5] + p[7]) / 4.0;
        int samples = 0, on = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            for (int k = 1; k < 20; k++) {
                double t = k / 20.0;
                double x = p[2 * i] + (p[2 * j] - p[2 * i]) * t;
                double y = p[2 * i + 1] + (p[2 * j + 1] - p[2 * i + 1]) * t;
                x += (cx - x) * 0.06;
                y += (cy - y) * 0.06;
                int xi = (int) Math.round(x), yi = (int) Math.round(y);
                if (xi < 0 || yi < 0 || xi >= w || yi >= h) continue;
                samples++;
                if (label[yi * w + xi] == id) on++;
            }
        }
        return samples > 0 && on >= 0.9 * samples;
    }

    private static int visit(int q, int id, boolean[] light, int[] label, int[] queue, int tail) {
        if (light[q] && label[q] == 0) {
            label[q] = id;
            queue[tail++] = q;
        }
        return tail;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /** Shoelace area of the corner shape, positive for clockwise corners on screen. */
    static double area(int[] p) {
        double a = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            a += (double) p[2 * i] * p[2 * j + 1] - (double) p[2 * j] * p[2 * i + 1];
        }
        return a / 2;
    }

    static boolean convex(int[] p) {
        int sign = 0;
        for (int i = 0; i < 4; i++) {
            int ax = p[2 * ((i + 1) % 4)] - p[2 * i], ay = p[2 * ((i + 1) % 4) + 1] - p[2 * i + 1];
            int bx = p[2 * ((i + 2) % 4)] - p[2 * ((i + 1) % 4)], by = p[2 * ((i + 2) % 4) + 1] - p[2 * ((i + 1) % 4) + 1];
            long cross = (long) ax * by - (long) ay * bx;
            if (cross == 0) continue;
            int s = cross > 0 ? 1 : -1;
            if (sign == 0) sign = s;
            else if (s != sign) return false;
        }
        return sign != 0;
    }

    /** Mean of the (2r+1) square around each pixel, through a summed-area table. */
    static int[] boxBlur(int[] g, int w, int h, int r) {
        long[] sum = new long[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            long row = 0;
            for (int x = 0; x < w; x++) {
                row += g[y * w + x];
                sum[(y + 1) * (w + 1) + x + 1] = sum[y * (w + 1) + x + 1] + row;
            }
        }
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - r), y1 = Math.min(h, y + r + 1);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - r), x1 = Math.min(w, x + r + 1);
                long s = sum[y1 * (w + 1) + x1] - sum[y0 * (w + 1) + x1] - sum[y1 * (w + 1) + x0] + sum[y0 * (w + 1) + x0];
                out[y * w + x] = (int) (s / ((long) (x1 - x0) * (y1 - y0)));
            }
        }
        return out;
    }

    /** The level that best splits the picture into light and dark (Otsu's method). */
    static int otsu(int[] g) {
        long[] hist = new long[256];
        for (int v : g) hist[Math.max(0, Math.min(255, v))]++;
        long total = g.length;
        double sumAll = 0;
        for (int i = 0; i < 256; i++) sumAll += i * (double) hist[i];
        double sumB = 0, best = -1;
        long wB = 0;
        int t = 127;
        for (int i = 0; i < 256; i++) {
            wB += hist[i];
            if (wB == 0) continue;
            long wF = total - wB;
            if (wF == 0) break;
            sumB += i * (double) hist[i];
            double mB = sumB / wB, mF = (sumAll - sumB) / wF;
            double between = (double) wB * wF * (mB - mF) * (mB - mF);
            if (between > best) {
                best = between;
                t = i;
            }
        }
        return t;
    }
}
