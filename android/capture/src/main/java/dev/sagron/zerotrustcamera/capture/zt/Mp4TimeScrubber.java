package dev.sagron.zerotrustcamera.capture.zt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Removes the recording date from a finished MP4 file.
 *
 * <p>An MP4 always carries a "created" and a "modified" time in three kinds of headers: one for
 * the whole movie ({@code mvhd}), one per track ({@code tkhd}) and one per track's media
 * ({@code mdhd}). The recorder fills them in and offers no way not to. This sets them to zero,
 * which the format defines as "unknown", and changes nothing else: no byte moves, so the file
 * plays exactly as before.
 *
 * <p>Plain Java with no Android classes, so it is tested on its own.
 */
public final class Mp4TimeScrubber {

    private Mp4TimeScrubber() {}

    // More boxes than any real recording has; a damaged file can't make this loop for long.
    private static final int MAX_BOXES = 10_000;

    private static final int MOOV = fourCc("moov");
    private static final int TRAK = fourCc("trak");
    private static final int MDIA = fourCc("mdia");
    private static final int MVHD = fourCc("mvhd");
    private static final int TKHD = fourCc("tkhd");
    private static final int MDHD = fourCc("mdhd");

    /**
     * @param in a channel that can read the file at any position
     * @param out a channel that can write the same file at any position (may be {@code in})
     * @return how many headers had their times cleared; 0 means the file is not a finished MP4
     */
    public static int scrub(FileChannel in, FileChannel out) throws IOException {
        int[] budget = {MAX_BOXES};
        return walk(in, out, 0, in.size(), budget);
    }

    private static int walk(FileChannel in, FileChannel out, long start, long end, int[] budget)
            throws IOException {
        int cleared = 0;
        long pos = start;
        ByteBuffer header = ByteBuffer.allocate(16);
        while (pos + 8 <= end && budget[0]-- > 0) {
            header.clear().limit(8);
            if (!readFully(in, header, pos)) break;
            header.flip();
            long size = header.getInt() & 0xFFFFFFFFL;
            int type = header.getInt();
            long headerSize = 8;
            if (size == 1) {
                // A 64-bit size follows the type
                header.clear().limit(8);
                if (!readFully(in, header, pos + 8)) break;
                header.flip();
                size = header.getLong();
                headerSize = 16;
            } else if (size == 0) {
                // "Up to the end of the file"
                size = end - pos;
            }
            if (size < headerSize || size > end - pos) break;

            long body = pos + headerSize;
            if (type == MOOV || type == TRAK || type == MDIA) {
                cleared += walk(in, out, body, pos + size, budget);
            } else if (type == MVHD || type == TKHD || type == MDHD) {
                if (clearTimes(in, out, body, pos + size)) cleared++;
            }
            pos += size;
        }
        return cleared;
    }

    /** The body starts with a version byte and three flag bytes, then the two times. */
    private static boolean clearTimes(FileChannel in, FileChannel out, long body, long end)
            throws IOException {
        ByteBuffer version = ByteBuffer.allocate(1);
        if (!readFully(in, version, body)) return false;
        // Version 1 stores 64-bit times, version 0 32-bit ones
        int length = version.get(0) == 1 ? 16 : 8;
        if (body + 4 + length > end) return false;
        ByteBuffer zeros = ByteBuffer.allocate(length);
        long at = body + 4;
        while (zeros.hasRemaining()) {
            at += out.write(zeros, at);
        }
        return true;
    }

    private static boolean readFully(FileChannel in, ByteBuffer into, long pos) throws IOException {
        while (into.hasRemaining()) {
            int n = in.read(into, pos);
            if (n < 0) return false;
            pos += n;
        }
        return true;
    }

    private static int fourCc(String s) {
        return (s.charAt(0) << 24) | (s.charAt(1) << 16) | (s.charAt(2) << 8) | s.charAt(3);
    }
}
