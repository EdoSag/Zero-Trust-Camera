package dev.sagron.zerotrustcamera.capture.zt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ModelInputTest {
    // The bundled efficientnet_lite0.tflite's input: uint8, scale 0.01256602, zero point 131.
    private static final float SCALE = 0.01256602f;
    private static final int ZERO = 131;

    private static int at(byte[] table, int value) {
        return table[value] & 0xff;
    }

    @Test
    public void blackMiddleAndWhiteMatchTheModel() {
        byte[] t = ModelInput.table(SCALE, ZERO);
        assertEquals(52, at(t, 0));
        assertEquals(131, at(t, 127));
        assertEquals(211, at(t, 255));
    }

    @Test
    public void brighterNeverMapsLower() {
        byte[] t = ModelInput.table(SCALE, ZERO);
        for (int v = 1; v < 256; v++) {
            assertEquals(true, at(t, v) >= at(t, v - 1));
        }
    }

    @Test
    public void aModelWithNoQuantizationGetsTheValuesAsTheyAre() {
        byte[] t = ModelInput.table(0f, 0);
        assertEquals(0, at(t, 0));
        assertEquals(200, at(t, 200));
    }

    @Test
    public void normalizedRange() {
        assertEquals(-127f / 128f, ModelInput.normalize(0), 1e-6);
        assertEquals(1f, ModelInput.normalize(255), 1e-6);
    }
}
