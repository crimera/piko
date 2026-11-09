/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.crimera.downloader;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import app.morphe.extension.crimera.internal.media3.common.C;
import app.morphe.extension.crimera.internal.media3.common.util.MediaFormatUtil;
import app.morphe.extension.crimera.internal.media3.common.util.UnstableApi;
import app.morphe.extension.crimera.internal.media3.muxer.BufferInfo;
import app.morphe.extension.crimera.internal.media3.muxer.FileOutputStreamSeekableMuxerOutput;
import app.morphe.extension.crimera.internal.media3.muxer.Mp4Muxer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Re-encodes the xHE-AAC track of an MP4 as AAC-LC with the platform codecs, for players that
 * cannot decode xHE-AAC. Use {@link #transcodeXheAac} on a worker thread.
 */
@UnstableApi
final class AacLcTranscoder {
    // Below this the encoder narrows its bandwidth under the 16.5 kHz the best xHE-AAC carries.
    private static final int BIT_RATE = 192_000;
    // Android's own AAC encoder (FDK), whose output lags its input by ENCODER_DELAY_FRAMES.
    private static final String ENCODER = "c2.android.aac.encoder";
    private static final int ENCODER_DELAY_FRAMES = 2048;
    // -16 LKFS, where Android and the app play xHE-AAC.
    private static final int TARGET_REFERENCE_LEVEL = 64;
    private static final int DRC_EFFECT_NONE = 0;
    private static final long TIMEOUT_US = 10_000;
    // The MPEG-4 audio object type of xHE-AAC (USAC), as its AudioSpecificConfig names it.
    private static final int OBJECT_TYPE_USAC = 42;

    private final MediaExtractor extractor;
    private final MediaCodec decoder;
    private final Mp4Muxer muxer;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private MediaCodec encoder;
    private int sampleRate;
    private int channels;
    private int frameBytes;
    private int outputTrack = -1;
    // The decoder output buffer being fed to the encoder, which can take less at a time.
    private int pcmIndex = -1;
    private ByteBuffer pcm;
    private boolean pcmEnd;
    // Counts from 0: Mp4Muxer starts a single-track file at 0 whatever the first timestamp.
    private long framesQueued;
    private int framesToSkip = ENCODER_DELAY_FRAMES;

    private AacLcTranscoder(MediaExtractor extractor, MediaCodec decoder, Mp4Muxer muxer) {
        this.extractor = extractor;
        this.decoder = decoder;
        this.muxer = muxer;
    }

    /**
     * Writes the xHE-AAC track of {@code input} to {@code output} as an audio-only AAC-LC MP4.
     * Returns false, leaving {@code output} untouched, when {@code input} has no xHE-AAC track.
     */
    static boolean transcodeXheAac(File input, File output) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        AacLcTranscoder transcoder = null;
        try {
            extractor.setDataSource(input.getAbsolutePath());
            int track = findXheAacTrack(extractor);
            if (track < 0) return false;
            extractor.selectTrack(track);
            MediaFormat format = extractor.getTrackFormat(track);
            // As Android plays it, but without the dynamics compression its default effect adds:
            // DRC only where the gain would clip.
            format.setInteger(MediaFormat.KEY_AAC_DRC_TARGET_REFERENCE_LEVEL, TARGET_REFERENCE_LEVEL);
            format.setInteger(MediaFormat.KEY_AAC_DRC_EFFECT_TYPE, DRC_EFFECT_NONE);
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            decoder.configure(format, null, null, 0);
            decoder.start();
            try (FileOutputStream stream = new FileOutputStream(output);
                 Mp4Muxer muxer = new Mp4Muxer.Builder(new FileOutputStreamSeekableMuxerOutput(stream))
                         .setAttemptStreamableOutputEnabled(false)
                         .build()) {
                transcoder = new AacLcTranscoder(extractor, decoder, muxer);
                transcoder.run();
            }
            return true;
        } finally {
            if (transcoder != null && transcoder.encoder != null) transcoder.encoder.release();
            if (decoder != null) decoder.release();
            extractor.release();
        }
    }

    private static int findXheAacTrack(MediaExtractor extractor) {
        for (int track = 0; track < extractor.getTrackCount(); track++) {
            MediaFormat format = extractor.getTrackFormat(track);
            ByteBuffer config = format.getByteBuffer("csd-0");
            if (MediaFormat.MIMETYPE_AUDIO_AAC.equals(format.getString(MediaFormat.KEY_MIME))
                    && config != null && objectType(config) == OBJECT_TYPE_USAC) {
                return track;
            }
        }
        return -1;
    }

    /** The audio object type an AudioSpecificConfig opens with: 5 bits, or 32 plus 6 more after the escape 31. */
    private static int objectType(ByteBuffer config) {
        if (config.remaining() < 2) return -1;
        int bits = (config.get(config.position()) & 0xFF) << 8 | (config.get(config.position() + 1) & 0xFF);
        int type = bits >>> 11;
        return type == 31 ? 32 + (bits >>> 5 & 0x3F) : type;
    }

    // Only the output dequeues wait: an input buffer frees up once its codec's output is taken.
    private void run() throws Exception {
        boolean inputEnd = false;
        boolean encoderInputEnd = false;
        boolean encoderEnd = false;
        while (!encoderEnd) {
            if (!inputEnd) inputEnd = feedDecoder();
            if (!encoderInputEnd) {
                if (pcm == null && !pcmEnd) takeDecoderOutput();
                if (pcm != null || pcmEnd) encoderInputEnd = feedEncoder();
            }
            if (encoder != null) encoderEnd = drainEncoder();
        }
    }

    /** Queues the next compressed sample; true once the end of the track is queued. */
    private boolean feedDecoder() {
        int index = decoder.dequeueInputBuffer(0);
        if (index < 0) return false;
        int size = extractor.readSampleData(decoder.getInputBuffer(index), 0);
        if (size < 0) {
            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return true;
        }
        decoder.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0);
        extractor.advance();
        return false;
    }

    private void takeDecoderOutput() throws IOException {
        int index = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
        // The decoder also reports a new format when only the stream's loudness metadata changed.
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && encoder != null
                && !matchesEncoder(decoder.getOutputFormat())) {
            throw new IOException("xHE-AAC sample rate or channels changed mid-track");
        }
        if (index < 0) return;
        pcmEnd = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        if (info.size == 0) {
            decoder.releaseOutputBuffer(index, false);
            return;
        }
        if (encoder == null) startEncoder(decoder.getOutputFormat());
        pcmIndex = index;
        pcm = decoder.getOutputBuffer(index);
        pcm.position(info.offset).limit(info.offset + info.size);
        // Dropping the encoder's delay from the start keeps the sound in step with the video.
        int skip = Math.min(framesToSkip, pcm.remaining() / frameBytes);
        pcm.position(pcm.position() + skip * frameBytes);
        framesToSkip -= skip;
        if (!pcm.hasRemaining()) {
            decoder.releaseOutputBuffer(index, false);
            pcm = null;
        }
    }

    private void startEncoder(MediaFormat pcmFormat) throws IOException {
        if (!isPcm16(pcmFormat)) throw new IOException("Decoder output is not 16-bit PCM");
        sampleRate = pcmFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        channels = pcmFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        frameBytes = 2 * channels;
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        try {
            encoder = MediaCodec.createByCodecName(ENCODER);
        } catch (IllegalArgumentException e) {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
    }

    private boolean matchesEncoder(MediaFormat pcmFormat) {
        return isPcm16(pcmFormat)
                && pcmFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) == sampleRate
                && pcmFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == channels;
    }

    private static boolean isPcm16(MediaFormat pcmFormat) {
        return !pcmFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
                || pcmFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_16BIT;
    }

    /** Moves as much decoded PCM as one encoder input buffer takes; true once the end is queued. */
    private boolean feedEncoder() throws IOException {
        if (encoder == null) throw new IOException("xHE-AAC track decoded to nothing");
        int index = encoder.dequeueInputBuffer(0);
        if (index < 0) return false;
        long timeUs = framesQueued * 1_000_000L / sampleRate;
        if (pcm == null) {
            encoder.queueInputBuffer(index, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return true;
        }
        ByteBuffer input = encoder.getInputBuffer(index);
        int size = Math.min(pcm.remaining(), input.remaining() / frameBytes * frameBytes);
        ByteBuffer chunk = pcm.duplicate();
        chunk.limit(chunk.position() + size);
        input.put(chunk);
        pcm.position(pcm.position() + size);
        encoder.queueInputBuffer(index, 0, size, timeUs, 0);
        framesQueued += size / frameBytes;
        if (!pcm.hasRemaining()) {
            decoder.releaseOutputBuffer(pcmIndex, false);
            pcm = null;
        }
        return false;
    }

    /** Writes everything the encoder has ready; true once it has written its last sample. */
    private boolean drainEncoder() throws Exception {
        for (long timeoutUs = TIMEOUT_US; ; timeoutUs = 0) {
            int index = encoder.dequeueOutputBuffer(info, timeoutUs);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (outputTrack < 0) {
                    outputTrack = muxer.addTrack(MediaFormatUtil.createFormatFromMediaFormat(encoder.getOutputFormat()));
                }
                continue;
            }
            if (index < 0) return false;
            if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                ByteBuffer sample = encoder.getOutputBuffer(index);
                sample.position(info.offset).limit(info.offset + info.size);
                muxer.writeSampleData(outputTrack, sample, new BufferInfo(info.presentationTimeUs, info.size, C.BUFFER_FLAG_KEY_FRAME));
            }
            encoder.releaseOutputBuffer(index, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return true;
        }
    }
}
