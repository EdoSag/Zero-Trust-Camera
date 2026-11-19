package dev.sagron.zerotrustcamera.capture.zt;

import com.google.crypto.tink.subtle.Hkdf;
import com.google.crypto.tink.subtle.X25519;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The sealed file format of the Private vault ("ZTV1").
 *
 * <p>A file is sealed to the phone's vault public key, so the camera can save a photo or a video
 * while the phone is locked, without the private key. Opening needs the private key, which only
 * exists after the person unlocks the vault.
 *
 * <pre>
 * header  = "ZTV1" | version 0x01 | eph_pub (32) | nonce_prefix (7) | chunk_size (u32 BE)
 * file key = HKDF-SHA256(ikm = X25519(eph_priv, vault_pub),
 *                        salt = eph_pub | vault_pub, info = "zt/camera/media/v1", 32 bytes)
 * chunk i = AES-256-GCM(file key, nonce = nonce_prefix | u32 BE i | last ? 1 : 0,
 *                       aad = header, plaintext of chunk_size bytes, the last one shorter)
 * </pre>
 *
 * <p>Every file has its own key (a new ephemeral key pair each time), so a nonce never repeats under
 * one key. The last-chunk flag in the nonce makes a cut-off file fail to open instead of opening
 * short (the STREAM construction). Chunk {@code i} starts at
 * {@code HEADER_LEN + i * (chunk_size + 16)}, which lets the video player read any part of a
 * sealed video without opening all of it.
 *
 * <p>Plain Java so it is tested on a computer (MediaVaultTest).
 */
public final class MediaVault {
    private MediaVault() {}

    public static final int KEY_LEN = 32;
    public static final int TAG_LEN = 16;
    public static final int HEADER_LEN = 4 + 1 + KEY_LEN + 7 + 4;
    public static final int DEFAULT_CHUNK_SIZE = 1 << 20;
    private static final int MAX_CHUNK_SIZE = 16 << 20;
    private static final byte[] MAGIC = {'Z', 'T', 'V', '1'};
    private static final byte VERSION = 1;
    private static final byte[] INFO = "zt/camera/media/v1".getBytes(StandardCharsets.UTF_8);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** A new vault key pair: {private, public}. */
    public static byte[][] newKeyPair() throws InvalidKeyException {
        byte[] priv = X25519.generatePrivateKey();
        return new byte[][]{priv, X25519.publicFromPrivate(priv)};
    }

    public static byte[] publicFromPrivate(byte[] priv) throws InvalidKeyException {
        return X25519.publicFromPrivate(priv);
    }

    /** True if the bytes start like a sealed file. */
    public static boolean looksSealed(byte[] start) {
        return start.length >= 5 && start[0] == MAGIC[0] && start[1] == MAGIC[1]
                && start[2] == MAGIC[2] && start[3] == MAGIC[3] && start[4] == VERSION;
    }

    // ---- sealing -------------------------------------------------------------------------------

    public static void seal(InputStream in, OutputStream out, byte[] vaultPub)
            throws IOException, GeneralSecurityException {
        byte[] noncePrefix = new byte[7];
        RANDOM.nextBytes(noncePrefix);
        byte[] ephPriv = X25519.generatePrivateKey();
        try {
            seal(in, out, vaultPub, DEFAULT_CHUNK_SIZE, ephPriv, noncePrefix);
        } finally {
            Arrays.fill(ephPriv, (byte) 0);
        }
    }

    public static byte[] sealBytes(byte[] plain, byte[] vaultPub)
            throws IOException, GeneralSecurityException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(plain.length + HEADER_LEN + TAG_LEN);
        seal(new ByteArrayInputStream(plain), out, vaultPub);
        return out.toByteArray();
    }

    /** Sealing with a given ephemeral key and nonce prefix, for the fixed test vector. */
    static void seal(InputStream in, OutputStream out, byte[] vaultPub, int chunkSize,
                     byte[] ephPriv, byte[] noncePrefix)
            throws IOException, GeneralSecurityException {
        if (vaultPub.length != KEY_LEN) throw new InvalidKeyException("vault key length");
        if (noncePrefix.length != 7) throw new IllegalArgumentException("nonce prefix length");
        if (chunkSize <= 0 || chunkSize > MAX_CHUNK_SIZE) throw new IllegalArgumentException("chunk size");

        byte[] ephPub = X25519.publicFromPrivate(ephPriv);
        byte[] header = header(ephPub, noncePrefix, chunkSize);
        byte[] key = fileKey(X25519.computeSharedSecret(ephPriv, vaultPub), ephPub, vaultPub);
        try {
            out.write(header);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

            byte[] cur = new byte[chunkSize];
            byte[] next = new byte[chunkSize];
            int curLen = readFully(in, cur);
            long index = 0;
            while (true) {
                int nextLen = curLen == chunkSize ? readFully(in, next) : 0;
                boolean last = nextLen == 0;
                cipher.init(Cipher.ENCRYPT_MODE, keySpec,
                        new GCMParameterSpec(TAG_LEN * 8, nonce(noncePrefix, index, last)));
                cipher.updateAAD(header);
                out.write(cipher.doFinal(cur, 0, curLen));
                if (last) break;
                byte[] t = cur; cur = next; next = t;
                curLen = nextLen;
                index++;
                if (index > 0xFFFFFFFFL) throw new IOException("file too large");
            }
            Arrays.fill(cur, (byte) 0);
            Arrays.fill(next, (byte) 0);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    // ---- opening -------------------------------------------------------------------------------

    public static void open(InputStream in, OutputStream out, byte[] vaultPriv)
            throws IOException, GeneralSecurityException {
        byte[] header = new byte[HEADER_LEN];
        if (readFully(in, header) != HEADER_LEN) throw new AEADBadTagException("not a sealed file");
        Header h = Header.parse(header);
        byte[] key = h.key(vaultPriv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            int ctChunk = h.chunkSize + TAG_LEN;
            byte[] cur = new byte[ctChunk];
            byte[] next = new byte[ctChunk];
            int curLen = readFully(in, cur);
            if (curLen < TAG_LEN) throw new AEADBadTagException("cut off");
            long index = 0;
            while (true) {
                int nextLen = curLen == ctChunk ? readFully(in, next) : 0;
                if (nextLen > 0 && nextLen < TAG_LEN) throw new AEADBadTagException("cut off");
                boolean last = nextLen == 0;
                cipher.init(Cipher.DECRYPT_MODE, keySpec,
                        new GCMParameterSpec(TAG_LEN * 8, nonce(h.noncePrefix, index, last)));
                cipher.updateAAD(header);
                byte[] plain = cipher.doFinal(cur, 0, curLen);
                out.write(plain);
                Arrays.fill(plain, (byte) 0);
                if (last) break;
                byte[] t = cur; cur = next; next = t;
                curLen = nextLen;
                index++;
            }
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public static byte[] openBytes(byte[] sealed, byte[] vaultPriv)
            throws IOException, GeneralSecurityException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(0, sealed.length - HEADER_LEN));
        open(new ByteArrayInputStream(sealed), out, vaultPriv);
        return out.toByteArray();
    }

    /**
     * Reads any range of a sealed file without opening all of it, for the video player. Keeps
     * one opened chunk at a time. Not thread safe.
     */
    public static final class Reader implements Closeable {
        private final FileChannel channel;
        private final byte[] header;
        private final Header h;
        private final byte[] key;
        private final long chunks;
        private final long plainSize;
        private final Cipher cipher;
        private final SecretKeySpec keySpec;

        private long cachedIndex = -1;
        private byte[] cached;

        public Reader(FileChannel channel, byte[] vaultPriv) throws IOException, GeneralSecurityException {
            this.channel = channel;
            header = new byte[HEADER_LEN];
            readAt(0, header, HEADER_LEN);
            h = Header.parse(header);
            key = h.key(vaultPriv);
            long body = channel.size() - HEADER_LEN;
            long ctChunk = h.chunkSize + TAG_LEN;
            chunks = Math.max(1, (body + ctChunk - 1) / ctChunk);
            long lastCt = body - (chunks - 1) * ctChunk;
            if (lastCt < TAG_LEN) throw new AEADBadTagException("cut off");
            plainSize = body - chunks * TAG_LEN;
            cipher = Cipher.getInstance("AES/GCM/NoPadding");
            keySpec = new SecretKeySpec(key, "AES");
            // Opening the last chunk proves the file is whole.
            chunk(chunks - 1);
        }

        public long size() {
            return plainSize;
        }

        /** Like MediaDataSource.readAt: the count read, or -1 at the end. */
        public int read(long position, byte[] buffer, int offset, int size)
                throws IOException, GeneralSecurityException {
            if (position >= plainSize) return -1;
            int done = 0;
            while (done < size && position < plainSize) {
                long index = position / h.chunkSize;
                int within = (int) (position % h.chunkSize);
                byte[] plain = chunk(index);
                int n = Math.min(size - done, plain.length - within);
                if (n <= 0) break;
                System.arraycopy(plain, within, buffer, offset + done, n);
                done += n;
                position += n;
            }
            return done;
        }

        private byte[] chunk(long index) throws IOException, GeneralSecurityException {
            if (index == cachedIndex) return cached;
            long ctChunk = h.chunkSize + TAG_LEN;
            long start = HEADER_LEN + index * ctChunk;
            int len = (int) Math.min(ctChunk, channel.size() - start);
            byte[] ct = new byte[len];
            readAt(start, ct, len);
            cipher.init(Cipher.DECRYPT_MODE, keySpec,
                    new GCMParameterSpec(TAG_LEN * 8, nonce(h.noncePrefix, index, index == chunks - 1)));
            cipher.updateAAD(header);
            byte[] plain = cipher.doFinal(ct);
            if (cached != null) Arrays.fill(cached, (byte) 0);
            cached = plain;
            cachedIndex = index;
            return plain;
        }

        private void readAt(long pos, byte[] into, int len) throws IOException {
            ByteBuffer buf = ByteBuffer.wrap(into, 0, len);
            while (buf.hasRemaining()) {
                int n = channel.read(buf, pos + buf.position());
                if (n < 0) throw new EOFException();
            }
        }

        @Override
        public void close() throws IOException {
            Arrays.fill(key, (byte) 0);
            if (cached != null) Arrays.fill(cached, (byte) 0);
            channel.close();
        }
    }

    // ---- parts ---------------------------------------------------------------------------------

    private static final class Header {
        final byte[] ephPub;
        final byte[] noncePrefix;
        final int chunkSize;

        private Header(byte[] ephPub, byte[] noncePrefix, int chunkSize) {
            this.ephPub = ephPub;
            this.noncePrefix = noncePrefix;
            this.chunkSize = chunkSize;
        }

        static Header parse(byte[] header) throws AEADBadTagException {
            if (!looksSealed(header)) throw new AEADBadTagException("not a sealed file");
            ByteBuffer b = ByteBuffer.wrap(header);
            b.position(5);
            byte[] ephPub = new byte[KEY_LEN];
            b.get(ephPub);
            byte[] prefix = new byte[7];
            b.get(prefix);
            int chunk = b.getInt();
            if (chunk <= 0 || chunk > MAX_CHUNK_SIZE) throw new AEADBadTagException("chunk size");
            return new Header(ephPub, prefix, chunk);
        }

        byte[] key(byte[] vaultPriv) throws GeneralSecurityException {
            byte[] vaultPub = X25519.publicFromPrivate(vaultPriv);
            return fileKey(X25519.computeSharedSecret(vaultPriv, ephPub), ephPub, vaultPub);
        }
    }

    private static byte[] header(byte[] ephPub, byte[] noncePrefix, int chunkSize) {
        return ByteBuffer.allocate(HEADER_LEN).put(MAGIC).put(VERSION).put(ephPub).put(noncePrefix)
                .putInt(chunkSize).array();
    }

    private static byte[] fileKey(byte[] shared, byte[] ephPub, byte[] vaultPub)
            throws GeneralSecurityException {
        try {
            byte[] salt = new byte[KEY_LEN * 2];
            System.arraycopy(ephPub, 0, salt, 0, KEY_LEN);
            System.arraycopy(vaultPub, 0, salt, KEY_LEN, KEY_LEN);
            return Hkdf.computeHkdf("HMACSHA256", shared, salt, INFO, 32);
        } finally {
            Arrays.fill(shared, (byte) 0);
        }
    }

    private static byte[] nonce(byte[] prefix, long index, boolean last) {
        return ByteBuffer.allocate(12).put(prefix).putInt((int) index).put((byte) (last ? 1 : 0)).array();
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) break;
            off += n;
        }
        return off;
    }
}
