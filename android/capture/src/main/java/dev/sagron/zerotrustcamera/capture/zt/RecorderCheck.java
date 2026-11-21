package dev.sagron.zerotrustcamera.capture.zt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Finds out whether the phone's video recorder ever goes back and changes a part of the file it
 * has already written. Encrypting a video while it records is only safe for parts that never
 * change again, so this is measured on a real phone before that is built.
 *
 * <p>While a video records in memory, {@link #observe} takes a checksum of every part (1 MiB) that
 * is complete and at least one whole part behind the end of the file. When the recording has
 * stopped, {@link #finish} takes them again and reports which parts are no longer the same.
 *
 * <p>Plain Java so it is tested on a computer (RecorderCheckTest). It only reads.
 */
public final class RecorderCheck {
    public static final int PART = 1 << 20;

    private final int part;
    private final List<Long> seen = new ArrayList<>();
    private final ByteBuffer buffer;

    public RecorderCheck() {
        this(PART);
    }

    RecorderCheck(int part) {
        this.part = part;
        this.buffer = ByteBuffer.allocate(part);
    }

    /** What {@link #finish} found. */
    public static final class Result {
        /** Parts that were checked. */
        public final int parts;
        /** The ones that changed after they were first seen, in order. */
        public final List<Integer> changed;

        Result(int parts, List<Integer> changed) {
            this.parts = parts;
            this.changed = changed;
        }

        /** One line for the person to send back, for example "212 parts, changed: 0". */
        public String line() {
            if (changed.isEmpty()) return parts + " parts, changed: none";
            StringBuilder s = new StringBuilder().append(parts).append(" parts, changed: ");
            int shown = Math.min(changed.size(), 12);
            for (int i = 0; i < shown; i++) {
                if (i > 0) s.append(' ');
                s.append(changed.get(i));
            }
            if (changed.size() > shown) s.append(" and ").append(changed.size() - shown).append(" more");
            return s.toString();
        }
    }

    /** Takes the checksum of parts that became settled since the last call. */
    public synchronized void observe(FileChannel file) throws IOException {
        long size = file.size();
        // A part counts once the file is a whole part past its end.
        while ((long) (seen.size() + 2) * part <= size) {
            seen.add(checksum(file, seen.size()));
        }
    }

    /** Compares every part seen with what the finished file holds. */
    public synchronized Result finish(FileChannel file) throws IOException {
        List<Integer> changed = new ArrayList<>();
        long size = file.size();
        for (int i = 0; i < seen.size(); i++) {
            if ((long) (i + 1) * part > size || checksum(file, i) != seen.get(i)) changed.add(i);
        }
        return new Result(seen.size(), changed);
    }

    private long checksum(FileChannel file, int index) throws IOException {
        buffer.clear();
        long at = (long) index * part;
        while (buffer.hasRemaining()) {
            int n = file.read(buffer, at + buffer.position());
            if (n < 0) break;
        }
        CRC32 crc = new CRC32();
        crc.update(buffer.array(), 0, buffer.position());
        return crc.getValue();
    }
}
