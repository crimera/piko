package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.ContentResolver;
import android.media.MediaFormat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import app.morphe.extension.crimera.downloader.DownloadEngine;
import app.morphe.extension.crimera.downloader.engine.DestinationWriter;
import app.morphe.extension.crimera.downloader.engine.Reservation;
import app.morphe.extension.crimera.downloader.engine.TransferObserver;
import app.morphe.extension.crimera.downloader.engine.TransferOutcome;
import app.morphe.extension.crimera.downloader.engine.TransferRunner;
import app.morphe.extension.crimera.downloader.events.DownloadEvent;
import app.morphe.extension.crimera.downloader.events.DownloadEvents;
import app.morphe.extension.crimera.downloader.events.DownloadListener;
import app.morphe.extension.crimera.downloader.model.ConflictPolicy;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;
import app.morphe.extension.crimera.internal.media3.common.C;
import app.morphe.extension.crimera.internal.media3.common.Format;
import app.morphe.extension.crimera.internal.media3.common.util.MediaFormatUtil;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
public final class DownloadRemuxerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    /**
     * Guards the log parser against files that are not a complete ftyp header: a truncated or
     * misread download must log "unknown", never throw inside the remux lane.
     */
    @Test
    public void ftypDescriptionParsesValidHeaderAndRejectsMalformedOnes() {
        byte[] iso5 = ftypHeader("iso5", 0x200, "iso6", "mp41");
        assertEquals("ftyp=iso5 minor=0x200 compatible=iso6,mp41",
                DownloadRemuxer.parseFtyp(iso5, iso5.length));

        assertEquals("ftyp=isom minor=0x0 compatible=isom",
                DownloadRemuxer.parseFtyp(ftypHeader("isom", 0, "isom", "mp41"), 20));
        assertNull(DownloadRemuxer.parseFtyp(iso5, 11));
        assertNull(DownloadRemuxer.parseFtyp(box("free", 16), 16));
        assertNull(DownloadRemuxer.parseFtyp(box("ftyp", 4), 16));
    }

    /**
     * Ordinary MP4s (including silent videos) must remain byte-identical. Only X's GIF CDN
     * namespace may trigger remuxing, regardless of the user's filename template.
     */
    @Test
    public void remuxGateAcceptsOnlyGifMp4Sources() {
        String gif = "https://video.twimg.com/tweet_video/HTjaqUHaAAAl49C.mp4";
        assertTrue(DownloadRemuxer.isGifMp4(gif, "custom-name", "video/mp4"));
        assertTrue(DownloadRemuxer.isGifMp4(gif + "?tag=12#clip", "GIF.MP4", null));
        assertTrue(DownloadRemuxer.isGifMp4(gif, "clip.mp4", "application/octet-stream"));
        for (String source : new String[] {
                "https://video.twimg.com/ext_tw_video/123/pu/vid/avc1/640x480/clip.mp4",
                "https://video.twimg.com/amplify_video/123/vid/avc1/clip.mp4",
                "https://video.twimg.com/clip.mp4?source=/tweet_video/clip.mp4",
                "https://other.example/tweet_video/clip.mp4",
                "https://video.twimg.com.other.example/tweet_video/clip.mp4",
                "https://video.twimg.com@other.example/tweet_video/clip.mp4",
                "https://video.twimg.com/tweet_video_thumb/clip.mp4",
                "https://video.twimg.com/tweet_video/clip.webm",
                "https://video.twimg.com/tweet_video/",
                "https://video.twimg.com/tweet_video/nested/clip.mp4",
                "file://video.twimg.com/tweet_video/clip.mp4",
                "not a URL", null
        }) {
            assertFalse(String.valueOf(source), DownloadRemuxer.isGifMp4(source, "clip.mp4", "video/mp4"));
        }
        assertFalse(DownloadRemuxer.isGifMp4(gif, "photo.jpg", "image/jpeg"));
        assertFalse(DownloadRemuxer.isGifMp4(null, null, null));
    }

    @Test
    public void engineCompletesOnlyAfterRemuxAndDiscardsWriteBackFailures() {
        List<String> sequence = new ArrayList<>();
        AtomicReference<TransferOutcome> remuxOutcome = new AtomicReference<>(TransferOutcome.saved());
        FakeTransfer download = new FakeTransfer();
        NewXDownloadTransfer transfer = new NewXDownloadTransfer(download, (reservation, cancelled) -> {
            sequence.add("remux");
            assertFalse(sequence.contains("completed"));
            return remuxOutcome.get();
        });
        DownloadEvents events = new DownloadEvents(error -> { throw new AssertionError(error); });
        events.register(new DownloadListener() {
            @Override public void onCompleted(DownloadEvent.Completed event) { sequence.add("completed"); }
            @Override public void onFailed(DownloadEvent.Failed event) { sequence.add("failed"); }
            @Override public void onCancelled(DownloadEvent.Cancelled event) { sequence.add("cancelled"); }
        }, Runnable::run);
        DestinationWriter destination = new DestinationWriter() {
            @Override public Reservation reserve(Context context, DownloadRequest request) {
                return new Reservation(null, request.fileName(), request.mimeType());
            }
            @Override public void discard(Context context, Reservation reservation) { sequence.add("discard"); }
        };
        DownloadEngine engine = new DownloadEngine(null, destination, transfer, events, Runnable::run, () -> 0L);
        DownloadRequest gif = request("https://video.twimg.com/tweet_video/HTjaqUHaAAAl49C.mp4");
        engine.enqueue(gif);
        assertEquals(List.of("remux", "completed"), sequence);

        sequence.clear();
        remuxOutcome.set(TransferOutcome.failed(new IOException("write-back failed")));
        engine.enqueue(gif);
        assertEquals(List.of("remux", "discard", "failed"), sequence);

        sequence.clear();
        remuxOutcome.set(TransferOutcome.cancelled());
        engine.enqueue(gif);
        assertEquals(List.of("remux", "discard", "cancelled"), sequence);

        sequence.clear();
        engine.enqueue(request("https://video.twimg.com/ext_tw_video/123/clip.mp4"));
        assertEquals(List.of("completed"), sequence);

        sequence.clear();
        download.outcome = TransferOutcome.failed(new IOException("network failed"));
        engine.enqueue(gif);
        assertEquals(List.of("discard", "failed"), sequence);
    }

    @Test
    public void cancellationDuringRemuxDoesNotLeaveHttpCancellationForRetry() {
        FakeTransfer download = new FakeTransfer();
        AtomicReference<NewXDownloadTransfer> running = new AtomicReference<>();
        NewXDownloadTransfer transfer = new NewXDownloadTransfer(download, (reservation, cancelled) -> {
            running.get().cancel(7);
            assertTrue(cancelled.getAsBoolean());
            return TransferOutcome.saved();
        });
        running.set(transfer);
        Reservation reservation = new Reservation(null, "clip.mp4", "video/mp4");
        DownloadRequest gif = request("https://video.twimg.com/tweet_video/HTjaqUHaAAAl49C.mp4");
        assertEquals(TransferOutcome.Kind.CANCELLED, transfer.run(null, reservation, gif, 7, null).kind());
        assertEquals(0, download.cancellations);
        assertEquals(TransferOutcome.Kind.SAVED, transfer.run(null, reservation,
                request("https://video.twimg.com/ext_tw_video/123/clip.mp4"), 7, null).kind());

        transfer.cancel(7);
        assertEquals(1, download.cancellations);
        assertEquals(TransferOutcome.Kind.CANCELLED, transfer.run(null, reservation, gif, 7, null).kind());
    }

    @Test
    public void copyBackRejectsShortCopiesSizeMismatchesAndCancellation() throws IOException {
        byte[] original = {1, 2, 3};
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DownloadRemuxer.copyIntoDocument(new ByteArrayInputStream(original), output, 3, () -> false);
        assertArrayEquals(original, output.toByteArray());
        // A provider may omit size metadata; the cache retry must still copy every byte.
        output.reset();
        DownloadRemuxer.copyIntoDocument(new ByteArrayInputStream(original), output, -1, () -> false);
        assertArrayEquals(original, output.toByteArray());
        assertThrows(IOException.class, () -> DownloadRemuxer.copyIntoDocument(
                new ByteArrayInputStream(original), output, 4, () -> false));
        assertThrows(CancellationException.class, () -> DownloadRemuxer.copyIntoDocument(
                new ByteArrayInputStream(original), output, 3, () -> true));
        IOException writeFailure = new IOException("provider write failed");
        IOException actual = assertThrows(IOException.class, () -> DownloadRemuxer.copyIntoDocument(
                new ByteArrayInputStream(original), new OutputStream() {
                    @Override public void write(int value) throws IOException { throw writeFailure; }
                }, 3, () -> false));
        assertSame(writeFailure, actual);
        DownloadRemuxer.requireMatchingSize(3, 3L);
        DownloadRemuxer.requireMatchingSize(3, null);
        assertThrows(IOException.class, () -> DownloadRemuxer.requireMatchingSize(3, 4L));
    }

    @Test
    public void disabledDownloadDiagnosticsNeverAccessMediaStore() {
        AtomicBoolean storageAccessed = new AtomicBoolean();
        Context context = new ContextWrapper(null) {
            @Override public ContentResolver getContentResolver() {
                storageAccessed.set(true);
                return null;
            }
        };
        DownloadDebugLog.append(context, "must not create or append a log while logging is disabled");
        new DownloadRemuxer(context).remux(new Reservation(null, "gif.mp4", "video/mp4"), () -> false);
        assertFalse(storageAccessed.get());
    }

    /** Guards the actual parser/bridge against zero tracks, lost B-frames and ignored seek requests. */
    @Test
    public void media3ExtractsGifSamplesUnchangedAcrossMoovLayouts() throws Exception {
        RecordingSink first = new RecordingSink();
        RecordingSink last = new RecordingSink();
        Media3GifRemuxer.extract(fixture("first"), first, new StringBuilder(), () -> false);
        Media3GifRemuxer.extract(fixture("last"), last, new StringBuilder(), () -> false);
        assertEquals(9, first.samples);
        assertEquals(1, first.keyFrames);
        assertTrue(first.presentationReorders > 0);
        assertEquals(first.timestamps, last.timestamps);
        byte[] expectedPayloadHash = first.digest.digest();
        assertArrayEquals(expectedPayloadHash, last.digest.digest());
        assertEquals(first.samples, last.samples);
        RecordingSink quiet = new RecordingSink();
        Media3GifRemuxer.extract(fixture("first"), quiet, null, () -> false);
        assertEquals(first.timestamps, quiet.timestamps);
        assertArrayEquals(expectedPayloadHash, quiet.digest.digest());

        // Opt-in real incident corpus uses this same production entry point, not a probe copy.
        String extraSamples = System.getenv("PIKO_GIF_EXTRA_SAMPLES");
        if (extraSamples == null || extraSamples.isEmpty()) return;
        for (String path : extraSamples.split(File.pathSeparator)) {
            RecordingSink sink = new RecordingSink();
            StringBuilder report = new StringBuilder();
            Media3GifRemuxer.extract(new File(path), sink, report, () -> false);
            assertTrue(sink.samples > 0);
            System.out.println(path + "\n" + report);
        }
    }

    @Test
    public void media3PropagatesTruncationCancellationAndSinkFailures() throws Exception {
        File source = fixture("first");
        byte[] complete = Files.readAllBytes(source.toPath());
        File truncated = temporary.newFile();
        Files.write(truncated.toPath(), Arrays.copyOf(complete, complete.length - 30));
        assertThrows(IOException.class, () -> Media3GifRemuxer.extract(
                truncated, new RecordingSink(), new StringBuilder(), () -> false));

        AtomicBoolean cancelled = new AtomicBoolean();
        RecordingSink sink = new RecordingSink();
        sink.afterSample = () -> cancelled.set(true);
        assertThrows(CancellationException.class, () -> Media3GifRemuxer.extract(
                source, sink, new StringBuilder(), cancelled::get));
        assertEquals(1, sink.samples);

        IllegalStateException failure = new IllegalStateException("muxer rejected sample");
        RecordingSink failingSink = new RecordingSink();
        failingSink.afterSample = () -> { throw failure; };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> Media3GifRemuxer.extract(
                source, failingSink, new StringBuilder(), () -> false)));
    }

    private File fixture(String layout) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/gif-remux/bframes-moov-" + layout + ".mp4.base64")) {
            if (input == null) throw new IOException("Missing MP4 extraction fixture");
            File file = temporary.newFile();
            Files.write(file.toPath(), Base64.getMimeDecoder().decode(input.readAllBytes()));
            return file;
        }
    }

    private static final class RecordingSink implements Media3GifRemuxer.SampleSink {
        final MessageDigest digest;
        final List<Long> timestamps = new ArrayList<>();
        boolean formatted;
        boolean started;
        long samples;
        long keyFrames;
        long presentationReorders;
        long previousTimeUs = Long.MIN_VALUE;
        Runnable afterSample = () -> {};

        RecordingSink() throws Exception {
            digest = MessageDigest.getInstance("SHA-256");
        }

        @Override public void format(int id, Format format) {
            assertFalse(started);
            assertEquals("video/avc", format.sampleMimeType);
            MediaFormat platformFormat = MediaFormatUtil.createMediaFormatFromFormat(format);
            assertEquals("video/avc", platformFormat.getString(MediaFormat.KEY_MIME));
            assertTrue(platformFormat.getInteger(MediaFormat.KEY_WIDTH) > 0);
            assertTrue(platformFormat.getInteger(MediaFormat.KEY_HEIGHT) > 0);
            assertTrue(format.initializationData.size() >= 2);
            for (byte[] csd : format.initializationData) {
                assertArrayEquals(new byte[]{0, 0, 0, 1}, Arrays.copyOf(csd, 4));
            }
            formatted = true;
        }

        @Override public void start() {
            assertTrue(formatted);
            assertFalse(started);
            started = true;
        }

        @Override public void sample(int id, long timeUs, int flags, ByteBuffer data) {
            assertTrue(started);
            ByteBuffer bytes = data.duplicate();
            byte[] prefix = new byte[4];
            bytes.get(prefix);
            assertArrayEquals(new byte[]{0, 0, 0, 1}, prefix);
            digest.update(data.duplicate());
            timestamps.add(timeUs);
            samples++;
            if ((flags & C.BUFFER_FLAG_KEY_FRAME) != 0) keyFrames++;
            if (timeUs < previousTimeUs) presentationReorders++;
            previousTimeUs = timeUs;
            afterSample.run();
        }
    }

    private static final class FakeTransfer implements TransferRunner {
        TransferOutcome outcome = TransferOutcome.saved();
        int cancellations;

        @Override
        public TransferOutcome run(Context context, Reservation reservation, DownloadRequest request,
                                   int id, TransferObserver observer) {
            return outcome;
        }

        @Override public void cancel(int id) { cancellations++; }
    }

    private static DownloadRequest request(String url) {
        return new DownloadRequest(url, Collections.emptyList(), null, Collections.emptyList(),
                "clip.mp4", "video/mp4", ConflictPolicy.SKIP, null);
    }

    private static byte[] ftypHeader(String major, int minor, String... compatible) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUint32(out, 16 + 4 * compatible.length);
        writeAscii(out, "ftyp");
        writeAscii(out, major);
        writeUint32(out, minor);
        for (String brand : compatible) {
            writeAscii(out, brand);
        }
        return out.toByteArray();
    }

    private static byte[] box(String type, int size) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUint32(out, size);
        writeAscii(out, type);
        while (out.size() < 16) {
            out.write(0);
        }
        return out.toByteArray();
    }

    private static void writeUint32(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) {
        for (int index = 0; index < value.length(); index++) {
            out.write(value.charAt(index) & 0xFF);
        }
    }
}
