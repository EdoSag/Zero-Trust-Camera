package dev.sagron.zerotrustcamera.capture.zt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Removes the tags an MP4 or MOV file carries about itself: where it was recorded (the {@code ©xyz}
 * tag, Apple's location key), the phone's make and model, the software and any XMP. They live in
 * {@code udta} (user data), {@code meta} and {@code uuid} boxes at the top of the file, in the
 * movie box and in each track. Each such box is turned into a {@code free} box of the same size: a
 * player skips it, and nothing in the file moves, so no offset needs fixing. The bytes inside are
 * overwritten with zeros, so the tags can't be read back either.
 *
 * <p>Times are not touched here: {@link Mp4TimeScrubber} does that. What a stripped file still
 * has is what plays: sound, picture and their timing. Telemetry recorded as its own track (some
 * action cameras record GPS this way) is not removed.
 *
 * <p>Plain Java so it is tested on a computer (Mp4MetadataStripperTest).
 */
public final class Mp4MetadataStripper {
    private Mp4MetadataStripper() {}

    private static final int MAX_BOXES = 10_000;

    private static final int MOOV = fourCc("moov");
    private static final int TRAK = fourCc("trak");
    private static final int UDTA = fourCc("udta");
    private static final int META = fourCc("meta");
    private static final int UUID = fourCc("uuid");
    private static final int FREE = fourCc("free");

    /**
     * @param file a channel that can read and write the file at any position
     * @return how many boxes were removed
     */
    public static int strip(FileChannel file) throws IOException {
        int[] budget = {MAX_BOXES};
        return walk(file, 0, file.size(), budget);
    }

    private static int walk(FileChannel f, long start, long end, int[] budget) throws IOException {
        int stripped = 0;
        long pos = start;
        ByteBuffer header = ByteBuffer.allocate(8);
        while (pos + 8 <= end && budget[0]-- > 0) {
            header.clear();
            if (!readFully(f, header, pos)) break;
            header.flip();
            long size = header.getInt() & 0xFFFFFFFFL;
            int type = header.getInt();
            long headerSize = 8;
            if (size == 1) {
                header.clear();
                if (!readFully(f, header, pos + 8)) break;
                header.flip();
                size = header.getLong();
                headerSize = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < headerSize || size > end - pos) break;

            if (type == MOOV || type == TRAK) {
                stripped += walk(f, pos + headerSize, pos + size, budget);
            } else if (type == UDTA || type == META || type == UUID) {
                free(f, pos, headerSize, size);
                stripped++;
            }
            pos += size;
        }
        return stripped;
    }

    private static void free(FileChannel f, long pos, long headerSize, long size) throws IOException {
        ByteBuffer type = ByteBuffer.allocate(4).putInt(FREE);
        type.flip();
        long at = pos + 4;
        while (type.hasRemaining()) at += f.write(type, at);

        ByteBuffer zeros = ByteBuffer.allocate(1 << 16);
        long body = pos + headerSize;
        long left = size - headerSize;
        while (left > 0) {
            zeros.clear().limit((int) Math.min(zeros.capacity(), left));
            int n = f.write(zeros, body);
            body += n;
            left -= n;
        }
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
