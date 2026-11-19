package dev.sagron.zerotrustcamera.capture.zt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.crypto.tink.subtle.X25519;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;
import org.junit.Test;

public class MediaVaultTest {

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] data(int n, long seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    private static byte[] seal(byte[] plain, byte[] pub, int chunk) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MediaVault.seal(new ByteArrayInputStream(plain), out, pub, chunk,
                X25519.generatePrivateKey(), new byte[]{1, 2, 3, 4, 5, 6, 7});
        return out.toByteArray();
    }

    private static byte[] open(byte[] sealed, byte[] priv) throws Exception {
        return MediaVault.openBytes(sealed, priv);
    }

    @Test
    public void x25519MatchesRfc7748() throws Exception {
        // RFC 7748 section 6.1
        byte[] alicePriv = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a");
        byte[] alicePub = hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a");
        byte[] bobPub = hex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f");
        assertArrayEquals(alicePub, MediaVault.publicFromPrivate(alicePriv));
        byte[] bobPriv = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb");
        assertArrayEquals(bobPub, MediaVault.publicFromPrivate(bobPriv));
        assertArrayEquals(hex(SHARED), X25519.computeSharedSecret(alicePriv, bobPub));
        assertArrayEquals(hex(SHARED), X25519.computeSharedSecret(bobPriv, alicePub));
    }

    private static final String SHARED = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742";

    @Test
    public void opensWhatItSealsAtEverySize() throws Exception {
        byte[][] kp = MediaVault.newKeyPair();
        int chunk = 64;
        int[] sizes = {0, 1, 63, 64, 65, 127, 128, 129, 64 * 5, 64 * 5 + 3};
        for (int n : sizes) {
            byte[] plain = data(n, n);
            byte[] sealed = seal(plain, kp[1], chunk);
            int chunks = Math.max(1, (n + chunk - 1) / chunk);
            assertEquals("size " + n, MediaVault.HEADER_LEN + n + chunks * MediaVault.TAG_LEN, sealed.length);
            assertArrayEquals("size " + n, plain, open(sealed, kp[0]));
        }
    }

    @Test
    public void defaultSealRoundTrips() throws Exception {
        byte[][] kp = MediaVault.newKeyPair();
        byte[] plain = data(MediaVault.DEFAULT_CHUNK_SIZE * 2 + 17, 9);
        byte[] sealed = MediaVault.sealBytes(plain, kp[1]);
        assertTrue(MediaVault.looksSealed(sealed));
        assertArrayEquals(plain, open(sealed, kp[0]));
        // a new key each time: the same photo never seals to the same bytes
        byte[] again = MediaVault.sealBytes(plain, kp[1]);
        assertTrue(!Arrays.equals(Arrays.copyOf(sealed, 64), Arrays.copyOf(again, 64)));
    }

    @Test
    public void wrongKeyFails() throws Exception {
        byte[][] kp = MediaVault.newKeyPair();
        byte[][] other = MediaVault.newKeyPair();
        byte[] sealed = seal(data(200, 1), kp[1], 64);
        try {
            open(sealed, other[0]);
            fail();
        } catch (GeneralSecurityException expected) {
        }
    }

    @Test
    public void anyChangedByteFails() throws Exception {
        byte[][] kp = MediaVault.newKeyPair();
        byte[] sealed = seal(data(150, 2), kp[1], 64);
        for (int i = 0; i < sealed.length; i++) {
            byte[] bad = sealed.clone();
            bad[i] ^= 0x01;
            try {
                open(bad, kp[0]);
                fail("byte " + i);
            } catch (GeneralSecurityException expected) {
            }
        }
    }

    @Test
    public void cutOffFileFails() throws Exception {
        byte[][] kp = MediaVault.newKeyPair();
        int chunk = 64;
        byte[] sealed = seal(data(chunk * 3, 3), kp[1], chunk);
        // Cut at a chunk boundary: what is left is whole chunks, but none is marked last.
        int[] cuts = {MediaVault.HEADER_LEN, MediaVault.HEADER_LEN + chunk + 16,
                MediaVault.HEADER_LEN + 2 * (chunk + 16), sealed.length - 1, 10};
        for (int cut : cuts) {
            byte[] bad = Arrays.copyOf(sealed, cut);
            try {
                open(bad, kp[0]);
                fail("cut " + cut);
            } catch (GeneralSecurityException expected) {
            }
        }
    }

    @Test
    public void droppedOrSwappedChunkFails() throws Exception {
        byte[][] kp = MediaVault.newKeyPair();
        int chunk = 64, ct = chunk + 16, h = MediaVault.HEADER_LEN;
        byte[] sealed = seal(data(chunk * 3 + 5, 4), kp[1], chunk);
        byte[] swapped = sealed.clone();
        System.arraycopy(sealed, h, swapped, h + ct, ct);
        System.arraycopy(sealed, h + ct, swapped, h, ct);
        byte[] dropped = new byte[sealed.length - ct];
        System.arraycopy(sealed, 0, dropped, 0, h + ct);
        System.arraycopy(sealed, h + 2 * ct, dropped, h + ct, sealed.length - h - 2 * ct);
        for (byte[] bad : new byte[][]{swapped, dropped}) {
            try {
                open(bad, kp[0]);
                fail();
            } catch (GeneralSecurityException expected) {
            }
        }
    }

    @Test
    public void readerReadsAnyRange() throws Exception {
        byte[][] kp = MediaVault.newKeyPair();
        int chunk = 64;
        byte[] plain = data(chunk * 4 + 10, 5);
        File f = File.createTempFile("vault", ".media");
        try {
            Files.write(f.toPath(), seal(plain, kp[1], chunk));
            try (MediaVault.Reader r = new MediaVault.Reader(
                    new RandomAccessFile(f, "r").getChannel(), kp[0])) {
                assertEquals(plain.length, r.size());
                Random rnd = new Random(6);
                for (int i = 0; i < 300; i++) {
                    int pos = rnd.nextInt(plain.length);
                    int len = 1 + rnd.nextInt(200);
                    byte[] buf = new byte[len + 4];
                    int n = r.read(pos, buf, 4, len);
                    int want = Math.min(len, plain.length - pos);
                    assertEquals(want, n);
                    assertArrayEquals(Arrays.copyOfRange(plain, pos, pos + want),
                            Arrays.copyOfRange(buf, 4, 4 + n));
                }
                assertEquals(-1, r.read(plain.length, new byte[4], 0, 4));
            }
        } finally {
            f.delete();
        }
    }

    @Test
    public void readerRefusesCutOffFile() throws Exception {
        byte[][] kp = MediaVault.newKeyPair();
        int chunk = 64;
        byte[] sealed = seal(data(chunk * 2, 7), kp[1], chunk);
        File f = File.createTempFile("vault", ".media");
        try {
            Files.write(f.toPath(), Arrays.copyOf(sealed, MediaVault.HEADER_LEN + chunk + 16));
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                new MediaVault.Reader(raf.getChannel(), kp[0]);
                fail();
            } catch (GeneralSecurityException expected) {
            }
        } finally {
            f.delete();
        }
    }

    /**
     * Pins the format: a phone must open what an older version sealed. If this changes, old
     * vaults stop opening.
     */
    @Test
    public void fixedVector() throws Exception {
        byte[] vaultPriv = hex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4");
        byte[] vaultPub = MediaVault.publicFromPrivate(vaultPriv);
        byte[] ephPriv = hex("4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d");
        byte[] prefix = hex("00112233445566");
        byte[] plain = "ZT Camera keeps photos private.".getBytes("UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MediaVault.seal(new ByteArrayInputStream(plain), out, vaultPub, 16, ephPriv, prefix);
        byte[] sealed = out.toByteArray();
        String digest = toHex(MessageDigest.getInstance("SHA-256").digest(sealed));
        assertEquals(VECTOR_SHA256, digest);
        assertArrayEquals(plain, open(sealed, vaultPriv));
    }

    private static final String VECTOR_SHA256 =
            "5bf4de2bc563444a96d2e61fefe9836777901f2746b097f188e26c7c730063b7";
}
