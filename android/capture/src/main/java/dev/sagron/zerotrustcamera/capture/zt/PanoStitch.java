package dev.sagron.zerotrustcamera.capture.zt;

import java.util.Arrays;

/**
 * Stitches a panorama out of frames taken while the phone turns from left to right. Plain Java
 * on ARGB pixels, so it runs (and is tested) without a phone.
 *
 * Each frame is bent onto a cylinder ({@link #warp}), so turning the phone becomes a plain shift
 * to the side. The shift is first guessed from how far the phone turned, then found exactly by
 * comparing the overlap ({@link #align}). Frames are laid on one long canvas, fading into each
 * other where they overlap ({@link Canvas}). A pixel with alpha 0 is "nothing here".
 */
public final class PanoStitch {
    private PanoStitch() {}

    /** The focal length in pixels of a frame {@code width} wide seen under {@code fovDegrees}. */
    public static double focalPx(int width, double fovDegrees) {
        return (width / 2.0) / Math.tan(Math.toRadians(fovDegrees) / 2.0);
    }

    /** How wide a frame is once bent onto the cylinder. */
    public static int warpedWidth(int width, double focal) {
        return Math.max(1, (int) Math.floor(2.0 * focal * Math.atan(width / (2.0 * focal))));
    }

    /**
     * Bends a frame onto a cylinder of radius {@code focal}. The result is {@link #warpedWidth}
     * wide and as high as the frame; the corners the bend leaves empty have alpha 0.
     */
    public static int[] warp(int[] src, int w, int h, double focal) {
        int ow = warpedWidth(w, focal);
        int[] out = new int[ow * h];
        double cx = (w - 1) / 2.0, cy = (h - 1) / 2.0, ocx = (ow - 1) / 2.0;
        for (int x = 0; x < ow; x++) {
            double theta = (x - ocx) / focal;
            double sx = focal * Math.tan(theta) + cx;
            double scale = 1.0 / Math.cos(theta);
            int x0 = (int) Math.floor(sx);
            if (x0 < 0 || x0 >= w - 1) {
                if (x0 == w - 1 && sx - x0 < 1e-6) x0 = w - 2; else continue;
            }
            double fx = sx - x0;
            for (int y = 0; y < h; y++) {
                double sy = (y - cy) * scale + cy;
                int y0 = (int) Math.floor(sy);
                if (y0 < 0 || y0 > h - 1) continue;
                int y1 = Math.min(h - 1, y0 + 1);
                double fy = sy - y0;
                int a = src[y0 * w + x0], b = src[y0 * w + x0 + 1];
                int c = src[y1 * w + x0], d = src[y1 * w + x0 + 1];
                out[y * ow + x] = 0xFF000000
                        | mix(a, b, c, d, fx, fy, 16) << 16
                        | mix(a, b, c, d, fx, fy, 8) << 8
                        | mix(a, b, c, d, fx, fy, 0);
            }
        }
        return out;
    }

    private static int mix(int a, int b, int c, int d, double fx, double fy, int shift) {
        double top = ((a >> shift) & 0xFF) * (1 - fx) + ((b >> shift) & 0xFF) * fx;
        double bottom = ((c >> shift) & 0xFF) * (1 - fx) + ((d >> shift) & 0xFF) * fx;
        return (int) Math.round(top * (1 - fy) + bottom * fy);
    }

    /** Brightness of every pixel (0 to 255), or -1 where there is nothing. */
    static short[] luma(int[] px) {
        short[] out = new short[px.length];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            out[i] = (p >>> 24) == 0 ? -1
                    : (short) ((((p >> 16) & 0xFF) * 77 + ((p >> 8) & 0xFF) * 150 + (p & 0xFF) * 29) >> 8);
        }
        return out;
    }

    /**
     * Where frame {@code b} sits relative to frame {@code a} (both {@code w} by {@code h}):
     * {dx, dy, score}, with b's top left corner at (dx, dy) in a's pixels. Looks around
     * {@code guessDx} by up to {@code rangeX} to the sides and {@code rangeY} up and down, and
     * picks the place where the overlapping parts differ least. The score is the mean difference
     * there (0 to 255); {@link Integer#MAX_VALUE} when the frames never overlap enough to tell.
     */
    public static int[] align(int[] a, int[] b, int w, int h, int guessDx, int rangeX, int rangeY) {
        short[] la = luma(a), lb = luma(b);
        // Coarse first (every 4th place, every 4th pixel), then every place near the best.
        int[] best = search(la, lb, w, h, guessDx - rangeX, guessDx + rangeX, -rangeY, rangeY, 4, 4);
        if (best[2] == Integer.MAX_VALUE) return best;
        return search(la, lb, w, h, best[0] - 4, best[0] + 4, best[1] - 4, best[1] + 4, 1, 2);
    }

    private static int[] search(short[] la, short[] lb, int w, int h,
                                int x0, int x1, int y0, int y1, int step, int sample) {
        int bestDx = 0, bestDy = 0;
        long bestScore = Long.MAX_VALUE;
        for (int dy = y0; dy <= y1; dy += step) {
            for (int dx = Math.max(1, x0); dx <= Math.min(w - 1, x1); dx += step) {
                long sum = 0;
                int n = 0;
                // b's pixel (x, y) lies on a's pixel (x + dx, y + dy).
                for (int y = Math.max(0, -dy); y < Math.min(h, h - dy); y += sample) {
                    int rowA = (y + dy) * w, rowB = y * w;
                    for (int x = 0; x < w - dx; x += sample) {
                        int pa = la[rowA + x + dx], pb = lb[rowB + x];
                        if (pa < 0 || pb < 0) continue;
                        sum += Math.abs(pa - pb);
                        n++;
                    }
                }
                // Too thin an overlap says nothing.
                if (n < 64) continue;
                long score = sum * 1000 / n;
                if (score < bestScore) {
                    bestScore = score;
                    bestDx = dx;
                    bestDy = dy;
                }
            }
        }
        return new int[] {bestDx, bestDy,
                bestScore == Long.MAX_VALUE ? Integer.MAX_VALUE : (int) (bestScore / 1000)};
    }

    /** The long picture the frames are laid on, growing to the right. */
    public static final class Canvas {
        private final int frameH;
        private final int margin;
        private final int height;
        private final int maxWidth;
        private int[] px;
        private int capacity;
        private int width;
        // Rows every frame so far has covered: what is left after trimming.
        private int top;
        private int bottom;

        /**
         * @param frameH   the height of every frame
         * @param margin   how far a frame may drift up or down
         * @param maxWidth the widest the panorama may get
         */
        public Canvas(int frameH, int margin, int maxWidth) {
            this.frameH = frameH;
            this.margin = margin;
            this.height = frameH + 2 * margin;
            this.maxWidth = maxWidth;
            this.capacity = 0;
            this.px = new int[0];
            this.top = 0;
            this.bottom = height;
        }

        public int width() {
            return width;
        }

        /** True once another frame would not fit. */
        public boolean full(int frameW) {
            return width + frameW > maxWidth;
        }

        /**
         * Lays a frame with its top left corner at ({@code x}, {@code y}); y is 0 for a frame
         * that hasn't drifted, and is kept within the margin. Where it overlaps what is already
         * there, the two fade into each other from left to right.
         */
        public void add(int[] frame, int w, int h, int x, int y) {
            if (h != frameH) throw new IllegalArgumentException("frame height");
            if (x < 0) throw new IllegalArgumentException("x");
            y = Math.max(-margin, Math.min(margin, y));
            int right = Math.min(maxWidth, x + w);
            grow(right);
            int overlap = Math.max(0, Math.min(width, right) - x);
            int rowTop = margin + y;
            for (int fy = 0; fy < h; fy++) {
                int row = (rowTop + fy) * capacity;
                for (int fx = 0; fx < right - x; fx++) {
                    int p = frame[fy * w + fx];
                    if ((p >>> 24) == 0) continue;
                    int at = row + x + fx;
                    int old = px[at];
                    if ((old >>> 24) == 0 || fx >= overlap) {
                        px[at] = p | 0xFF000000;
                    } else {
                        // 0 at the left edge of the overlap (all old), 256 at its right edge.
                        int t = (fx + 1) * 256 / (overlap + 1);
                        px[at] = 0xFF000000
                                | blend(old >> 16, p >> 16, t) << 16
                                | blend(old >> 8, p >> 8, t) << 8
                                | blend(old, p, t);
                    }
                }
            }
            width = Math.max(width, right);
            top = Math.max(top, rowTop);
            bottom = Math.min(bottom, rowTop + h);
        }

        private static int blend(int a, int b, int t) {
            return ((a & 0xFF) * (256 - t) + (b & 0xFF) * t) >> 8;
        }

        private void grow(int need) {
            if (need <= capacity) return;
            int cap = Math.min(maxWidth, Math.max(need, capacity * 2));
            int[] bigger = new int[cap * height];
            for (int r = 0; r < height; r++) {
                System.arraycopy(px, r * capacity, bigger, r * cap, width);
            }
            px = bigger;
            capacity = cap;
        }

        /** The height of {@link #result}. */
        public int resultHeight() {
            return Math.max(0, bottom - top);
        }

        /**
         * The panorama: {@link #width} by {@link #resultHeight}, trimmed to the rows every frame
         * covered, with what the bend left empty at the edges filled in black.
         */
        public int[] result() {
            int rh = resultHeight();
            int[] out = new int[width * rh];
            for (int r = 0; r < rh; r++) {
                System.arraycopy(px, (top + r) * capacity, out, r * width, width);
            }
            for (int i = 0; i < out.length; i++) out[i] |= 0xFF000000;
            return out;
        }

        public void clear() {
            Arrays.fill(px, 0);
            px = new int[0];
            capacity = 0;
            width = 0;
        }
    }

    /** Turns a frame over, left to right: a sweep to the left is stitched as one to the right. */
    public static void mirror(int[] px, int w, int h) {
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w / 2; x++) {
                int t = px[row + x];
                px[row + x] = px[row + w - 1 - x];
                px[row + w - 1 - x] = t;
            }
        }
    }
}
