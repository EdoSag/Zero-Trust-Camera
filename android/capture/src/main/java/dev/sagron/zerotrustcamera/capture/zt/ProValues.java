package dev.sagron.zerotrustcamera.capture.zt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pro camera's dials: the shutter speeds and ISO steps a camera can do, and how each value
 * is written. Plain Java, so it is tested on the computer (ProValuesTest).
 */
public final class ProValues {
    private ProValues() {}

    private static final long SECOND = 1_000_000_000L;

    /** Usual shutter speeds, fastest first, in nanoseconds. */
    private static final long[] SHUTTERS = {
        SECOND / 8000, SECOND / 4000, SECOND / 2000, SECOND / 1000, SECOND / 500, SECOND / 250,
        SECOND / 125, SECOND / 60, SECOND / 30, SECOND / 15, SECOND / 8, SECOND / 4, SECOND / 2,
        SECOND, 2 * SECOND, 4 * SECOND, 8 * SECOND, 15 * SECOND, 30 * SECOND,
    };

    private static final int[] ISOS = {50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800,
        1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800};

    /** The usual shutter speeds inside the camera's range (in nanoseconds), fastest first. */
    public static long[] shutters(long minNs, long maxNs) {
        List<Long> out = new ArrayList<>();
        for (long s : SHUTTERS) if (s >= minNs && s <= maxNs) out.add(s);
        if (out.isEmpty()) out.add(Math.max(minNs, Math.min(maxNs, SECOND / 60)));
        long[] arr = new long[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    /** The usual ISO steps inside the camera's range. */
    public static int[] isos(int min, int max) {
        List<Integer> out = new ArrayList<>();
        for (int s : ISOS) if (s >= min && s <= max) out.add(s);
        if (out.isEmpty()) out.add(Math.max(min, Math.min(max, 100)));
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    /** "1/250" below a second, "2s" from a second up. */
    public static String shutterLabel(long ns) {
        if (ns <= 0) return "?";
        if (ns < SECOND) {
            long n = Math.round((double) SECOND / ns);
            return "1/" + n;
        }
        double s = (double) ns / SECOND;
        if (Math.abs(s - Math.round(s)) < 0.05) return Math.round(s) + "s";
        return String.format(Locale.ROOT, "%.1fs", s);
    }

    /** Focus distance from diopters (1 / metres): infinity at 0, centimetres when close. */
    public static String focusLabel(float diopters) {
        if (diopters <= 0.01f) return "∞";
        float metres = 1f / diopters;
        if (metres < 1f) return Math.round(metres * 100f) + " cm";
        return String.format(Locale.ROOT, "%.1f m", metres);
    }

    /** Index of the step closest to [value]. */
    public static int nearest(long[] steps, long value) {
        int best = 0;
        for (int i = 1; i < steps.length; i++) {
            if (Math.abs(steps[i] - value) < Math.abs(steps[best] - value)) best = i;
        }
        return best;
    }

    public static int nearest(int[] steps, int value) {
        int best = 0;
        for (int i = 1; i < steps.length; i++) {
            if (Math.abs(steps[i] - value) < Math.abs(steps[best] - value)) best = i;
        }
        return best;
    }
}
