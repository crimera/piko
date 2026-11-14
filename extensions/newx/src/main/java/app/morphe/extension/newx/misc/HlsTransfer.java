/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.newx.misc;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import app.morphe.extension.crimera.downloader.engine.Reservation;
import app.morphe.extension.crimera.downloader.engine.TransferObserver;
import app.morphe.extension.crimera.downloader.engine.TransferOutcome;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;
import app.morphe.extension.newx.settings.NewXLogger;

/**
 * Downloads HLS live stream replay playlists, concatenates MPEG-TS segments,
 * and remuxes them into a standard MP4 container written to the user destination.
 */
final class HlsTransfer {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int SAMPLE_BUFFER_SIZE = 1024 * 1024;

    private HlsTransfer() {
    }

    static boolean isHlsUrl(@Nullable String url) {
        if (url == null) return false;
        return url.contains(".m3u8") || url.contains("/live_video_stream/");
    }

    static TransferOutcome download(
            Context context,
            Reservation reservation,
            DownloadRequest request,
            int id,
            TransferObserver observer,
            BooleanSupplier cancelled
    ) {
        File tempTs = null;
        File tempMp4 = null;
        try {
            checkCancelled(cancelled);

            String mediaPlaylistUrl = resolveMediaPlaylistUrl(request.url());
            List<String> segmentUrls = extractSegmentUrls(mediaPlaylistUrl);
            if (segmentUrls.isEmpty()) {
                throw new IOException("No HLS video segments found in playlist: " + mediaPlaylistUrl);
            }

            tempTs = File.createTempFile("piko-hls-", ".ts", context.getCacheDir());
            long totalBytesDownloaded = 0;
            int totalSegments = segmentUrls.size();

            try (FileOutputStream tsOut = new FileOutputStream(tempTs)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                for (int i = 0; i < totalSegments; i++) {
                    checkCancelled(cancelled);
                    String segmentUrl = segmentUrls.get(i);

                    totalBytesDownloaded += downloadSegment(segmentUrl, tsOut, buffer, cancelled);
                    // Approximate total bytes estimated based on average segment size so far
                    long avgSegmentSize = totalBytesDownloaded / (i + 1);
                    long estimatedTotal = avgSegmentSize * totalSegments;
                    observer.onProgress(totalBytesDownloaded, estimatedTotal);
                }
            }

            checkCancelled(cancelled);

            // Remux MPEG-TS into standard MP4
            tempMp4 = File.createTempFile("piko-hls-", ".mp4", context.getCacheDir());
            remuxTsToMp4(tempTs, tempMp4, cancelled);

            checkCancelled(cancelled);

            // Copy final MP4 to SAF document destination
            Uri docUri = reservation.documentUri();
            if (docUri == null) {
                throw new IOException("No document URI available on reservation");
            }

            try (InputStream in = new FileInputStream(tempMp4);
                 OutputStream out = context.getContentResolver().openOutputStream(docUri, "rwt")) {
                if (out == null) throw new IOException("Cannot open document output stream: " + docUri);
                byte[] copyBuf = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(copyBuf)) != -1) {
                    checkCancelled(cancelled);
                    out.write(copyBuf, 0, read);
                }
            }

            return TransferOutcome.saved();
        } catch (CancellationException e) {
            NewXLogger.printInfo(() -> "HLS stream download was cancelled");
            return TransferOutcome.cancelled();
        } catch (Exception e) {
            NewXLogger.printException(() -> "HLS stream download failed", e);
            return TransferOutcome.failed(e);
        } finally {
            if (tempTs != null && tempTs.exists()) tempTs.delete();
            if (tempMp4 != null && tempMp4.exists()) tempMp4.delete();
        }
    }

    private static String resolveMediaPlaylistUrl(String url) throws IOException {
        String content = BroadcastStreamResolver.fetchHttp(url, null);
        if (content == null) throw new IOException("Could not fetch playlist from " + url);

        if (content.contains("#EXT-X-STREAM-INF")) {
            // Master playlist: pick first variant URL
            String[] lines = content.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.startsWith("#EXT-X-STREAM-INF:") && i + 1 < lines.length) {
                    String next = lines[i + 1].trim();
                    if (!next.startsWith("#") && !next.isEmpty()) {
                        return URI.create(url).resolve(next).toString();
                    }
                }
            }
        }
        return url;
    }

    private static List<String> extractSegmentUrls(String mediaPlaylistUrl) throws IOException {
        String content = BroadcastStreamResolver.fetchHttp(mediaPlaylistUrl, null);
        if (content == null) throw new IOException("Could not fetch media playlist from " + mediaPlaylistUrl);

        List<String> segments = new ArrayList<>();
        URI base = URI.create(mediaPlaylistUrl);
        String[] lines = content.split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                segments.add(base.resolve(line).toString());
            }
        }
        return segments;
    }

    private static long downloadSegment(String segmentUrl, OutputStream out, byte[] buffer,
                                        BooleanSupplier cancelled) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(segmentUrl).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " for segment " + segmentUrl);
            }

            long total = 0;
            try (InputStream in = conn.getInputStream()) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    checkCancelled(cancelled);
                    out.write(buffer, 0, read);
                    total += read;
                }
            }
            return total;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void remuxTsToMp4(File sourceTs, File destMp4, BooleanSupplier cancelled) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            extractor.setDataSource(sourceTs.getAbsolutePath());
            int trackCount = extractor.getTrackCount();
            if (trackCount == 0) throw new IOException("No tracks found in MPEG-TS stream");

            muxer = new MediaMuxer(destMp4.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            Map<Integer, Integer> trackMap = new HashMap<>(trackCount);

            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"))) {
                    extractor.selectTrack(i);
                    int muxerTrack = muxer.addTrack(format);
                    trackMap.put(i, muxerTrack);
                }
            }

            if (trackMap.isEmpty()) {
                throw new IOException("No compatible audio/video tracks found in stream");
            }

            muxer.start();
            ByteBuffer buffer = ByteBuffer.allocateDirect(SAMPLE_BUFFER_SIZE);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while (!cancelled.getAsBoolean()) {
                int sampleTrack = extractor.getSampleTrackIndex();
                if (sampleTrack < 0) break;

                Integer muxTrack = trackMap.get(sampleTrack);
                if (muxTrack != null) {
                    buffer.clear();
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize >= 0) {
                        info.offset = 0;
                        info.size = sampleSize;
                        info.presentationTimeUs = extractor.getSampleTime();
                        info.flags = extractor.getSampleFlags();
                        muxer.writeSampleData(muxTrack, buffer, info);
                    }
                }
                extractor.advance();
            }

            checkCancelled(cancelled);
            muxer.stop();
        } finally {
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (Exception ignored) {
                }
            }
            extractor.release();
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("HLS download cancelled");
        }
    }
}
