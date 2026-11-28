/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.instagram.patches.download;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

final class DashSegmentIndex {
    private static final int FTYP = 0x66747970, MOOV = 0x6d6f6f76, TRAK = 0x7472616b, TKHD = 0x746b6864,
            SIDX = 0x73696478, FREE = 0x66726565, SKIP = 0x736b6970,
            MDIA = 0x6d646961, HDLR = 0x68646c72, SOUN = 0x736f756e,
            MINF = 0x6d696e66, STBL = 0x7374626c, STSD = 0x73747364, MP4A = 0x6d703461;

    private final DataInputStream input;
    private long position;
    private int type;
    private int handler;
    private long audioRate;

    private DashSegmentIndex(InputStream stream) {
        input = new DataInputStream(stream);
    }

    /**
     * Returns the exclusive end of all direct references in a zero-based, single-track presentation.
     * The caller verifies contiguous cached files up to available and rechecks them when copying.
     */
    static long mediaEnd(InputStream stream, long available, long indexStart, long indexEnd,
                         long durationUs, boolean audioRepresentation) throws IOException {
        if (stream == null || indexStart <= 0 || indexEnd < indexStart || indexEnd >= available || durationUs <= 0) return -1;
        try {
            return new DashSegmentIndex(stream).verify(available, indexStart, indexEnd + 1, durationUs, audioRepresentation);
        } catch (Invalid | EOFException ignored) {
            return -1;
        }
    }

    private static final class Invalid extends Exception {}

    private static void require(boolean valid) throws Invalid {
        if (!valid) throw new Invalid();
    }

    private long word(long limit) throws Invalid, IOException {
        require(limit - position >= 4);
        long value = input.readInt() & 0xffffffffL;
        position += 4;
        return value;
    }

    private long box(long limit) throws Invalid, IOException {
        long start = position;
        long size = word(limit);
        type = (int) word(limit);
        if (size == 1) {
            require(limit - position >= 8);
            size = input.readLong();
            position += 8;
        }
        require(size >= position - start);
        require(size <= limit - start);
        return start + size;
    }

    private void skipTo(long end) throws Invalid, IOException {
        require(position <= end);
        while (position < end) {
            long n = input.skip(end - position);
            if (n == 0) {
                input.readByte();
                n = 1;
            }
            position += n;
        }
    }

    private long verify(long available, long start, long limit, long durationUs,
                        boolean audioRepresentation) throws Invalid, IOException {
        long end = box(start);
        require(type == FTYP);
        require(end - position >= 8);
        skipTo(end);
        long trackId = 0;
        while (position < start) {
            end = box(start);
            if (type == MOOV) {
                require(trackId == 0);
                trackId = initializationTrack(end, audioRepresentation);
            } else {
                require(type == FREE || type == SKIP);
                skipTo(end);
            }
        }
        require(trackId != 0);
        end = box(limit);
        require(type == SIDX);
        require(end == limit);
        long flags = word(end);
        require(flags == 0 || flags == 0x01000000L);
        long referenceId = word(end);
        require(referenceId == trackId);
        long timescale = word(end);
        require(timescale > 0);
        long firstTime, offset;
        if (flags == 0) {
            firstTime = word(end);
            offset = word(end);
        } else {
            require(end - position >= 16);
            firstTime = input.readLong();
            offset = input.readLong();
            position += 16;
        }
        require(firstTime == 0);
        require(offset >= 0);
        require(offset <= available - end);

        // The upper 16 bits are reserved. Each direct reference occupies exactly 12 bytes.
        long count = word(end);
        require(count > 0 && count <= 65535);
        require(end - position == 12 * count);
        byte[] entries = new byte[(int) (end - position)];
        input.readFully(entries);
        ByteBuffer index = ByteBuffer.wrap(entries);
        long mediaEnd = end + offset;
        long ticks = 0;
        while (index.hasRemaining()) {
            int size = index.getInt(); // A set high bit denotes a nested index.
            long duration = index.getInt() & 0xffffffffL;
            index.getInt(); // SAP flags
            require(size > 0 && size <= available - mediaEnd && duration > 0);
            mediaEnd += size;
            ticks += duration;
        }
        // Compare at the manifest's precision, avoiding multiplication of the full duration.
        long units = durationUs % 1000 == 0 ? 1000 : 1_000_000;
        long whole = ticks / timescale;
        require(whole <= Long.MAX_VALUE / units);
        long scaledFraction = (ticks % timescale) * units;
        long nearest = (scaledFraction + timescale / 2) / timescale;
        long expected = durationUs / (1_000_000 / units);
        long expectedFraction = expected - whole * units;
        // The manifest may express a fractional microsecond by truncation instead of rounding.
        boolean truncatedUs = units == 1_000_000 && expectedFraction == scaledFraction / timescale;
        if (expectedFraction != nearest && !truncatedUs) {
            // Audio can stop on a different sample boundary than the video defining the Period.
            // Allow at most 1024 actual samples, only when the index consumes every cached byte.
            require(audioRepresentation && handler == SOUN && mediaEnd == available && audioRate > 0
                    && withinAudioTail(durationUs, ticks, timescale, audioRate));
        }
        return mediaEnd;
    }

    private static boolean withinAudioTail(long durationUs, long ticks, long timescale, long rate) {
        long seconds = durationUs / 1_000_000 - ticks / timescale;
        if (seconds < 0 || seconds > 1) return false;
        long gap = (seconds * 1_000_000 + durationUs % 1_000_000) * timescale
                - (ticks % timescale) * 1_000_000;
        return gap > 0 && gap <= (1024L * 1_000_000 * timescale) / rate;
    }

    private long initializationTrack(long end, boolean inspectAudio) throws Invalid, IOException {
        long trackId = 0;
        while (position < end) {
            long child = box(end);
            if (type == TRAK) {
                require(trackId == 0);
                while (position < child) {
                    long item = box(child);
                    if (type == TKHD) {
                        require(trackId == 0);
                        long version = word(item) >>> 24;
                        require(version <= 1);
                        long timestamps = version == 0 ? 8 : 16;
                        require(item - position >= timestamps);
                        skipTo(position + timestamps);
                        trackId = word(item);
                        require(trackId != 0);
                    } else if (inspectAudio && type == MDIA) {
                        require(handler == 0);
                        handler = mediaHandler(item);
                    }
                    skipTo(item);
                }
                require(trackId != 0);
            }
            skipTo(child);
        }
        require(trackId != 0);
        return trackId;
    }

    private int mediaHandler(long end) throws IOException, Invalid {
        int found = 0;
        while (position < end) {
            long child = box(end);
            if (type == HDLR) {
                require(found == 0);
                long flags = word(child);
                require(flags == 0);
                word(child); // pre_defined
                found = (int) word(child);
                require(found != 0);
            } else if (type == MINF) {
                audioRate = sampleRate(child, STBL);
            }
            skipTo(child);
        }
        require(found != 0);
        return found;
    }

    private long sampleRate(long end, int target) throws IOException, Invalid {
        long rate = 0;
        while (position < end) {
            long child = box(end);
            if (type == target) {
                if (target == STBL) {
                    rate = sampleRate(child, STSD);
                } else if (word(child) == 0 && word(child) == 1) {
                    long entry = box(child);
                    // ISO AudioSampleEntry version 0 stores a 16.16 sample rate at payload offset 24.
                    if (type == MP4A && entry == child && entry - position >= 28) {
                        skipTo(position + 8);
                        long version = word(entry) >>> 16;
                        skipTo(position + 12);
                        long fixedRate = word(entry);
                        if (version == 0 && (fixedRate & 0xffff) == 0) rate = fixedRate >>> 16;
                    }
                }
            }
            skipTo(child);
        }
        return rate;
    }
}
