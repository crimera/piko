package app.morphe.extension.newx.misc;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import androidx.annotation.Nullable;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import app.morphe.extension.crimera.internal.media3.common.C;
import app.morphe.extension.crimera.internal.media3.common.DataReader;
import app.morphe.extension.crimera.internal.media3.common.Format;
import app.morphe.extension.crimera.internal.media3.common.MediaLibraryInfo;
import app.morphe.extension.crimera.internal.media3.common.util.MediaFormatUtil;
import app.morphe.extension.crimera.internal.media3.common.util.ParsableByteArray;
import app.morphe.extension.crimera.internal.media3.common.util.UnstableApi;
import app.morphe.extension.crimera.internal.media3.extractor.DefaultExtractorInput;
import app.morphe.extension.crimera.internal.media3.extractor.DiscardingTrackOutput;
import app.morphe.extension.crimera.internal.media3.extractor.Extractor;
import app.morphe.extension.crimera.internal.media3.extractor.ExtractorOutput;
import app.morphe.extension.crimera.internal.media3.extractor.PositionHolder;
import app.morphe.extension.crimera.internal.media3.extractor.SeekMap;
import app.morphe.extension.crimera.internal.media3.extractor.TrackOutput;
import app.morphe.extension.crimera.internal.media3.extractor.mp4.Mp4Extractor;

/** Bundled MP4 parsing; compressed samples go unchanged to the device's MP4 writer. */
@UnstableApi
final class Media3GifRemuxer {
    private static final int MAX_SAMPLE_BYTES = 64 * 1024 * 1024;

    interface SampleSink {
        void format(int trackId, Format format);
        void start();
        void sample(int trackId, long timeUs, int flags, ByteBuffer data);
    }

    private Media3GifRemuxer() {
    }

    static void remux(File source, File destination, @Nullable StringBuilder report,
                      BooleanSupplier cancelled) throws IOException {
        MediaMuxer muxer = new MediaMuxer(destination.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try {
            FrameworkSink sink = new FrameworkSink(muxer);
            extract(source, sink, report, cancelled);
            checkCancelled(cancelled);
            // A failed extraction is never finalized or copied over the original download.
            muxer.stop();
        } finally {
            muxer.release();
        }
    }

    /** Shared production/test entry point: no platform extractor or host-app Media3 calls. */
    static void extract(File source, SampleSink sink, @Nullable StringBuilder report,
                        BooleanSupplier cancelled) throws IOException {
        checkCancelled(cancelled);
        if (report != null) {
            report.append("extractor[media3]: bundled Mp4Extractor ").append(MediaLibraryInfo.VERSION)
                    .append(", source bytes=").append(source.length()).append('\n');
        }
        Mp4Extractor extractor = new Mp4Extractor();
        BridgeOutput output = new BridgeOutput(sink, report, cancelled);
        try (RandomAccessFile inputFile = new RandomAccessFile(source, "r")) {
            long sourceBytes = inputFile.length();
            DataReader reader = (data, offset, length) -> {
                checkCancelled(cancelled);
                return inputFile.read(data, offset, length);
            };
            DefaultExtractorInput input = new DefaultExtractorInput(reader, 0, sourceBytes);
            extractor.init(output);
            PositionHolder position = new PositionHolder();
            int seeks = 0;
            while (true) {
                checkCancelled(cancelled);
                int result = extractor.read(input, position);
                if (result == Extractor.RESULT_END_OF_INPUT) break;
                if (result == Extractor.RESULT_SEEK) {
                    if (position.position < 0 || position.position > sourceBytes) {
                        throw new IOException("Extractor requested an out-of-file seek: " + position.position);
                    }
                    inputFile.seek(position.position);
                    input = new DefaultExtractorInput(reader, position.position, sourceBytes);
                    seeks++;
                }
            }
            output.finish();
            if (report != null) report.append("extractor[media3]: completed, seeks=").append(seeks).append('\n');
        } finally {
            extractor.release();
        }
    }

    private static final class FrameworkSink implements SampleSink {
        private final MediaMuxer muxer;
        private final Map<Integer, Integer> outputTracks = new LinkedHashMap<>();
        private final MediaCodec.BufferInfo sampleInfo = new MediaCodec.BufferInfo();

        FrameworkSink(MediaMuxer muxer) {
            this.muxer = muxer;
        }

        @Override
        public void format(int trackId, Format format) {
            MediaFormat platformFormat = MediaFormatUtil.createMediaFormatFromFormat(format);
            if (format.sampleMimeType != null && format.sampleMimeType.startsWith("video/")) {
                muxer.setOrientationHint(format.rotationDegrees);
            }
            outputTracks.put(trackId, muxer.addTrack(platformFormat));
        }

        @Override
        public void start() {
            muxer.start();
        }

        @Override
        public void sample(int trackId, long timeUs, int flags, ByteBuffer data) {
            Integer outputTrack = outputTracks.get(trackId);
            if (outputTrack == null) throw new IllegalStateException("Unknown muxer track " + trackId);
            int platformFlags = (flags & C.BUFFER_FLAG_KEY_FRAME) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
            sampleInfo.set(data.position(), data.remaining(), timeUs, platformFlags);
            muxer.writeSampleData(outputTrack, data, sampleInfo);
        }
    }

    private static final class BridgeOutput implements ExtractorOutput {
        private final SampleSink sink;
        @Nullable private final StringBuilder report;
        private final BooleanSupplier cancelled;
        private final Map<Integer, BridgeTrack> tracks = new LinkedHashMap<>();
        private boolean started;

        BridgeOutput(SampleSink sink, @Nullable StringBuilder report, BooleanSupplier cancelled) {
            this.sink = sink;
            this.report = report;
            this.cancelled = cancelled;
        }

        @Override
        public TrackOutput track(int id, int type) {
            if (started) throw new IllegalStateException("Track appeared after muxer start");
            if (type != C.TRACK_TYPE_VIDEO && type != C.TRACK_TYPE_AUDIO) {
                if (report != null) {
                    report.append("extractor[media3]: ignored track ").append(id)
                            .append(" type=").append(type).append('\n');
                }
                return new DiscardingTrackOutput();
            }
            BridgeTrack previous = tracks.get(id);
            if (previous != null) {
                if (previous.type != type) throw new IllegalStateException("Track type changed for " + id);
                return previous;
            }
            BridgeTrack track = new BridgeTrack(id, type, this);
            tracks.put(id, track);
            return track;
        }

        @Override
        public void endTracks() {
            if (started) throw new IllegalStateException("Duplicate endTracks");
            int videoTracks = 0;
            for (BridgeTrack track : tracks.values()) {
                if (track.format == null) throw new IllegalStateException("Track format missing: " + track.id);
                if (track.type == C.TRACK_TYPE_VIDEO) videoTracks++;
            }
            if (videoTracks != 1) throw new IllegalStateException("Expected one GIF video track, found " + videoTracks);
            sink.start();
            started = true;
            if (report != null) report.append("extractor[media3]: usable tracks=").append(tracks.size()).append('\n');
        }

        @Override
        public void seekMap(SeekMap seekMap) {
            if (report != null) report.append("extractor[media3]: durationUs=").append(seekMap.getDurationUs()).append('\n');
        }

        void finish() throws IOException {
            if (!started) throw new IOException("Media3 found no usable GIF tracks");
            for (BridgeTrack track : tracks.values()) {
                if (track.samples == 0 || track.pendingBytes != 0) {
                    throw new IOException("Incomplete track " + track.id + ": samples=" + track.samples
                            + ", pending bytes=" + track.pendingBytes);
                }
                if (report != null) {
                    report.append("remux: copied track ").append(track.id).append(" (")
                            .append(track.format.sampleMimeType).append("): ")
                            .append(track.samples).append(" samples, ").append(track.bytes)
                            .append(" bytes, PTS reorders=").append(track.presentationReorders).append('\n');
                }
            }
        }
    }

    private static final class BridgeTrack implements TrackOutput {
        final int id;
        final int type;
        final BridgeOutput output;
        Format format;
        byte[] pending = new byte[64 * 1024];
        int pendingBytes;
        long samples;
        long bytes;
        long lastPresentationUs = Long.MIN_VALUE;
        long presentationReorders;

        BridgeTrack(int id, int type, BridgeOutput output) {
            this.id = id;
            this.type = type;
            this.output = output;
        }

        @Override
        public void format(Format format) {
            checkCancelled(output.cancelled);
            if (this.format != null) {
                if (!this.format.equals(format)) throw new IllegalStateException("Track format changed: " + id);
                return;
            }
            if (output.started || format.sampleMimeType == null || format.drmInitData != null) {
                throw new IllegalStateException("Unsupported or encrypted track format: " + id);
            }
            this.format = format;
            if (output.report != null) {
                output.report.append("extractor[media3]: track ").append(id).append(": ")
                        .append(format.sampleMimeType).append(' ').append(format.width).append('x')
                        .append(format.height).append(" csdEntries=").append(format.initializationData.size())
                        .append(" rotation=").append(format.rotationDegrees).append('\n');
            }
            output.sink.format(id, format);
        }

        @Override
        public int sampleData(DataReader input, int length, boolean allowEndOfInput, int sampleDataPart)
                throws IOException {
            checkCancelled(output.cancelled);
            reserve(length, sampleDataPart);
            int read = input.read(pending, pendingBytes, length);
            if (read == C.RESULT_END_OF_INPUT) {
                if (allowEndOfInput) return C.RESULT_END_OF_INPUT;
                throw new EOFException("Truncated MP4 sample");
            }
            if (read == 0 && length > 0) throw new IOException("Sample read made no progress");
            pendingBytes += read;
            return read;
        }

        @Override
        public void sampleData(ParsableByteArray data, int length, int sampleDataPart) {
            checkCancelled(output.cancelled);
            reserve(length, sampleDataPart);
            data.readBytes(pending, pendingBytes, length);
            pendingBytes += length;
        }

        @Override
        public void sampleMetadata(long timeUs, int flags, int size, int offset, CryptoData cryptoData) {
            checkCancelled(output.cancelled);
            if (!output.started || format == null || cryptoData != null || (flags & C.BUFFER_FLAG_ENCRYPTED) != 0) {
                throw new IllegalStateException("Unprepared or encrypted sample on track " + id);
            }
            if (size <= 0 || offset < 0 || offset > pendingBytes || size > pendingBytes - offset) {
                throw new IllegalStateException("Invalid sample geometry on track " + id);
            }
            int start = pendingBytes - offset - size;
            if (start != 0) throw new IllegalStateException("Unconsumed sample bytes on track " + id);
            // Mp4Extractor already emits start-coded video samples/csd; no NAL conversion.
            output.sink.sample(id, timeUs, flags, ByteBuffer.wrap(pending, start, size));
            samples++;
            bytes += size;
            if (timeUs < lastPresentationUs) presentationReorders++;
            lastPresentationUs = timeUs;
            System.arraycopy(pending, pendingBytes - offset, pending, 0, offset);
            pendingBytes = offset;
        }

        private void reserve(int extraBytes, int sampleDataPart) {
            if (sampleDataPart != SAMPLE_DATA_PART_MAIN || extraBytes < 0
                    || extraBytes > MAX_SAMPLE_BYTES - pendingBytes) {
                throw new IllegalStateException("Unsupported or oversized sample on track " + id);
            }
            int required = pendingBytes + extraBytes;
            if (required > pending.length) {
                pending = Arrays.copyOf(pending, Math.max(required, Math.min(MAX_SAMPLE_BYTES, pending.length * 2)));
            }
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) throw new CancellationException("GIF remux cancelled");
    }
}
