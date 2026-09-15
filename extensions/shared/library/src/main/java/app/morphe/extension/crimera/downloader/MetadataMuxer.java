/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.crimera.downloader;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import app.morphe.extension.crimera.internal.media3.common.C;
import app.morphe.extension.crimera.internal.media3.common.Metadata;
import app.morphe.extension.crimera.internal.media3.common.util.MediaFormatUtil;
import app.morphe.extension.crimera.internal.media3.common.util.UnstableApi;
import app.morphe.extension.crimera.internal.media3.container.MdtaMetadataEntry;
import app.morphe.extension.crimera.internal.media3.container.Mp4TimestampData;
import app.morphe.extension.crimera.internal.media3.muxer.BufferInfo;
import app.morphe.extension.crimera.internal.media3.muxer.FileOutputStreamSeekableMuxerOutput;
import app.morphe.extension.crimera.internal.media3.muxer.Mp4Muxer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UnstableApi
public final class MetadataMuxer {
    private static final int INITIAL_BUFFER_SIZE = 1024 * 1024;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private MetadataMuxer() {
    }

    public static void write(File inputFile, FileOutputStream output, DownloadMetadata metadata)
            throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try (FileOutputStream outputStream = output) {
            extractor.setDataSource(inputFile.getAbsolutePath());
            try (Mp4Muxer muxer = new Mp4Muxer.Builder(
                    new FileOutputStreamSeekableMuxerOutput(outputStream)
            ).build()) {
                Map<Integer, Integer> outputTracks = addTracks(extractor, muxer);
                if (outputTracks.isEmpty()) {
                    throw new IOException("Input MP4 has no audio or video tracks");
                }

                for (Metadata.Entry entry : buildMetadataEntries(metadata)) {
                    muxer.addMetadataEntry(entry);
                }
                copySamples(extractor, muxer, outputTracks);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception | LinkageError e) {
            throw new IOException("Could not embed MP4 metadata", e);
        } finally {
            extractor.release();
        }
    }

    private static List<Metadata.Entry> buildMetadataEntries(DownloadMetadata metadata) {
        List<Metadata.Entry> entries = new ArrayList<>();
        addDescription(entries, metadata.caption);
        addTextPair(entries, "comment", "purl", metadata.postUrl);
        addTextPair(entries, "artist", "performer", metadata.performer);

        if (metadata.uploadTimestampMillis != null) {
            Instant uploadTime = Instant.ofEpochMilli(metadata.uploadTimestampMillis);
            addText(entries, "date", DATE_FORMATTER.format(uploadTime));
            addText(entries, "creation_time", uploadTime.toString());
            long mp4Time = Mp4TimestampData.unixTimeToMp4TimeSeconds(metadata.uploadTimestampMillis);
            entries.add(new Mp4TimestampData(mp4Time, mp4Time));
        }
        return entries;
    }

    private static void addDescription(List<Metadata.Entry> entries, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        addText(entries, "description", value);
        addText(entries, "longdescription", value);
        addText(entries, "synopsis", value);
    }

    private static Map<Integer, Integer> addTracks(MediaExtractor extractor, Mp4Muxer muxer)
            throws Exception {
        Map<Integer, Integer> outputTracks = new HashMap<>();
        for (int trackIndex = 0; trackIndex < extractor.getTrackCount(); trackIndex++) {
            MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);
            String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mimeType == null || (!mimeType.startsWith("audio/") && !mimeType.startsWith("video/"))) {
                continue;
            }
            extractor.selectTrack(trackIndex);
            outputTracks.put(trackIndex, muxer.addTrack(
                    MediaFormatUtil.createFormatFromMediaFormat(mediaFormat)
            ));
        }
        return outputTracks;
    }

    private static void copySamples(
            MediaExtractor extractor,
            Mp4Muxer muxer,
            Map<Integer, Integer> outputTracks
    ) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocateDirect(INITIAL_BUFFER_SIZE);
        while (true) {
            int sourceTrack = extractor.getSampleTrackIndex();
            if (sourceTrack < 0) {
                return;
            }

            long sampleSize = extractor.getSampleSize();
            if (sampleSize > Integer.MAX_VALUE) {
                throw new IOException("MP4 sample is too large");
            }
            if (sampleSize > buffer.capacity()) {
                int capacity = buffer.capacity();
                while (capacity < sampleSize && capacity <= Integer.MAX_VALUE / 2) {
                    capacity *= 2;
                }
                if (capacity < sampleSize) {
                    capacity = (int) sampleSize;
                }
                buffer = ByteBuffer.allocateDirect(capacity);
            }

            buffer.clear();
            int bytesRead = extractor.readSampleData(buffer, 0);
            if (bytesRead < 0) {
                return;
            }
            buffer.position(0);
            buffer.limit(bytesRead);
            int flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    ? C.BUFFER_FLAG_KEY_FRAME
                    : 0;
            muxer.writeSampleData(
                    outputTracks.get(sourceTrack),
                    buffer,
                    new BufferInfo(extractor.getSampleTime(), bytesRead, flags)
            );
            extractor.advance();
        }
    }

    private static void addTextPair(
            List<Metadata.Entry> entries,
            String firstKey,
            String secondKey,
            String value
    ) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        addText(entries, firstKey, value);
        addText(entries, secondKey, value);
    }

    private static void addText(List<Metadata.Entry> entries, String key, String value) {
        entries.add(new MdtaMetadataEntry(
                key,
                value.getBytes(StandardCharsets.UTF_8),
                MdtaMetadataEntry.TYPE_INDICATOR_STRING
        ));
    }
}
