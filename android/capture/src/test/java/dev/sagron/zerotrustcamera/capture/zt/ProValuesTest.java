package dev.sagron.zerotrustcamera.capture.zt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ProValuesTest {
    private static final long S = 1_000_000_000L;

    @Test
    public void shuttersStayInsideTheRange() {
        long[] s = ProValues.shutters(S / 1000, S / 2);
        assertEquals(S / 1000, s[0]);
        assertEquals(S / 2, s[s.length - 1]);
        assertEquals(10, s.length);
    }

    @Test
    public void longExposuresWhenTheCameraAllows() {
        long[] s = ProValues.shutters(S / 8000, 30 * S);
        assertEquals(19, s.length);
        assertEquals(30 * S, s[18]);
    }

    @Test
    public void aRangeWithNoUsualStepStillGivesOne() {
        long[] s = ProValues.shutters(S / 70, S / 65);
        assertEquals(1, s.length);
        assertEquals(S / 65, s[0]); // 1/60 is just outside, so the nearest end
    }

    @Test
    public void isoSteps() {
        assertArrayEquals(new int[] {50, 64, 80, 100}, ProValues.isos(45, 100));
        assertArrayEquals(new int[] {3200, 4000, 5000, 6400}, ProValues.isos(3200, 7000));
    }

    @Test
    public void shutterLabels() {
        assertEquals("1/250", ProValues.shutterLabel(S / 250));
        assertEquals("1/8000", ProValues.shutterLabel(S / 8000));
        assertEquals("1/2", ProValues.shutterLabel(S / 2));
        assertEquals("1s", ProValues.shutterLabel(S));
        assertEquals("30s", ProValues.shutterLabel(30 * S));
        assertEquals("1.5s", ProValues.shutterLabel(3 * S / 2));
        assertEquals("1/33", ProValues.shutterLabel(30_000_000L));
    }

    @Test
    public void focusLabels() {
        assertEquals("∞", ProValues.focusLabel(0f));
        assertEquals("2.0 m", ProValues.focusLabel(0.5f));
        assertEquals("10 cm", ProValues.focusLabel(10f));
        assertEquals("50 cm", ProValues.focusLabel(2f));
    }

    @Test
    public void nearestStep() {
        long[] s = ProValues.shutters(S / 8000, S);
        assertEquals(S / 60, s[ProValues.nearest(s, 16_000_000L)]);
        int[] iso = ProValues.isos(50, 6400);
        assertEquals(400, iso[ProValues.nearest(iso, 410)]);
    }
}
