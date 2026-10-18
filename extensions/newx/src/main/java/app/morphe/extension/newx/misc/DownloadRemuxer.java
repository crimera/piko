package app.morphe.extension.newx.misc;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import app.morphe.extension.crimera.downloader.engine.Reservation;
import app.morphe.extension.crimera.downloader.engine.TransferOutcome;
import app.morphe.extension.newx.settings.NewXLogger;

/**
 * Rewrites freshly saved GIF-class MP4 downloads with the device muxer (piko-newx#122).
 *
 * <p>X serves GIFs in its {@code Twitter-vork} container, which some Samsung Gallery
 * builds reject even though other players accept it. The rewrite copies the compressed samples
 * unchanged into a standard framework-written MP4 and replaces the saved file in place. Nothing
 * is decoded and no NAL data is touched: bundled Media3 extracts compressed samples for
 * Android's MediaMuxer without depending on the host app's Media3 classes.
 *
 * <p>The new container is built in the app cache first
 * and the saved document is only truncated after that container is complete; a failure before
 * the write-back leaves the original file in place. When Enable logging is on, attempts are
 * reported to {@code Download/piko-download-log.txt} for on-device testing.
 */
public final class DownloadRemuxer {
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private final Context context;

    DownloadRemuxer(Context context) {
        this.context = context;
    }

    /** X's tweet_video CDN namespace identifies GIFs, unlike MP4 MIME or missing audio. */
    static boolean isGifMp4(@Nullable String sourceUrl, @Nullable String fileName,
                            @Nullable String mimeType) {
        if (sourceUrl == null) return false;
        boolean mp4 = "video/mp4".equalsIgnoreCase(mimeType)
                || (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".mp4"));
        if (!mp4) return false;

        try {
            URI source = new URI(sourceUrl);
            if (!("https".equalsIgnoreCase(source.getScheme())
                    || "http".equalsIgnoreCase(source.getScheme()))) return false;
            if (!"video.twimg.com".equalsIgnoreCase(source.getHost())) return false;
            String path = source.getRawPath();
            if (path == null || !path.startsWith("/tweet_video/")) return false;
            String name = path.substring("/tweet_video/".length());
            return name.length() > 4 && name.indexOf('/') < 0
                    && name.toLowerCase(Locale.ROOT).endsWith(".mp4");
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    // Serialize rewrites and diagnostic appends, but keep completion inside the transfer.
    synchronized TransferOutcome remux(Reservation reservation, BooleanSupplier cancelled) {
        StringBuilder report = NewXLogger.isLoggingEnabled() ? new StringBuilder(1024) : null;
        if (report != null) {
            report.append("\n--- ").append(DownloadDebugLog.timestamp()).append('\n')
                    .append("download: ").append(reservation.fileName())
                    .append(" (").append(reservation.mimeType()).append(")\n");
        }

        Uri documentUri = reservation.documentUri();
        TransferOutcome outcome;
        if (documentUri == null) {
            if (report != null) report.append("status: remux skipped — the saved file has no document URI\n");
            outcome = TransferOutcome.saved();
        } else {
            outcome = rewrite(documentUri, report, cancelled);
        }
        if (report != null) DownloadDebugLog.append(context, report.toString());
        return outcome;
    }

    private TransferOutcome rewrite(Uri documentUri, @Nullable StringBuilder report, BooleanSupplier cancelled) {
        long startedAt = System.currentTimeMillis();
        File temporary = null;
        boolean writeBackStarted = false;
        try {
            checkCancelled(cancelled);
            if (report != null) appendSourceSummary(documentUri, report);

            temporary = File.createTempFile("piko-remux-", ".mp4", context.getCacheDir());
            muxToTemporaryFile(documentUri, temporary, report, cancelled);
            long containerBytes = temporary.length();
            if (report != null) {
                report.append("remux: wrote ").append(containerBytes)
                        .append(" bytes of container into a temporary file\n");
            }

            checkCancelled(cancelled);
            try (InputStream input = new FileInputStream(temporary);
                 OutputStream output = openForRewrite(context.getContentResolver(), documentUri)) {
                writeBackStarted = true;
                copyIntoDocument(input, output, containerBytes, cancelled);
            }

            Long sizeOnDevice = documentSize(documentUri);
            requireMatchingSize(containerBytes, sizeOnDevice);
            if (report != null) {
                report.append("status: remuxed in ").append(System.currentTimeMillis() - startedAt)
                        .append(" ms — the saved file was replaced");
                if (sizeOnDevice == null) {
                    report.append('\n');
                } else {
                    report.append(" (verified ").append(sizeOnDevice).append(" bytes)\n");
                }
            }
            return TransferOutcome.saved();
        } catch (CancellationException exception) {
            if (report != null) report.append("status: remux cancelled — download engine will discard the document\n");
            return TransferOutcome.cancelled();
        } catch (IOException | RuntimeException throwable) {
            if (report != null) {
                report.append(writeBackStarted
                        ? "status: remux failed during write-back — download engine will discard the possibly incomplete document\n"
                        : "status: remux failed — the original file was kept unchanged\n");
                report.append("error: ").append(describe(throwable)).append('\n');
            }
            NewXLogger.printException(() -> "Download remux failed", throwable);
            return writeBackStarted ? TransferOutcome.failed(throwable) : TransferOutcome.saved();
        } finally {
            if (temporary != null && !temporary.delete()) {
                NewXLogger.printInfo(() -> "Could not delete the remux temporary file");
            }
        }
    }

    private void muxToTemporaryFile(Uri documentUri, File temporary, @Nullable StringBuilder report,
                                    BooleanSupplier cancelled) throws IOException {
        File cachedSource = copySourceToCache(documentUri, report, cancelled);
        try {
            Media3GifRemuxer.remux(cachedSource, temporary, report, cancelled);
        } finally {
            deleteCachedSource(cachedSource);
        }
    }

    private File copySourceToCache(Uri documentUri, @Nullable StringBuilder report,
                                    BooleanSupplier cancelled) throws IOException {
        File cachedSource = File.createTempFile("piko-remux-source-", ".mp4", context.getCacheDir());
        boolean copied = false;
        try {
            Long sourceBytes = documentSize(documentUri);
            try (InputStream input = context.getContentResolver().openInputStream(documentUri);
                 OutputStream output = new FileOutputStream(cachedSource)) {
                if (input == null) throw new FileNotFoundException("Cannot read the source document");
                copyIntoDocument(input, output, sourceBytes == null ? -1 : sourceBytes, cancelled);
            }
            long cachedBytes = cachedSource.length();
            if (cachedBytes == 0) throw new IOException("The cached source is empty");
            if (report != null) {
                report.append("extractor[media3]: cached ").append(cachedBytes)
                        .append(" bytes unchanged");
                report.append(sourceBytes == null ? " (source size unavailable)\n" : " (source size verified)\n");
            }
            copied = true;
            return cachedSource;
        } finally {
            if (!copied) deleteCachedSource(cachedSource);
        }
    }

    private static void deleteCachedSource(@Nullable File cachedSource) {
        if (cachedSource != null && !cachedSource.delete()) {
            NewXLogger.printInfo(() -> "Could not delete the remux source cache file");
        }
    }

    private void appendSourceSummary(Uri documentUri, StringBuilder report) {
        Long size = documentSize(documentUri);
        report.append("source: ").append(size == null ? "size unknown" : size + " bytes");
        String ftyp = readFtyp(documentUri);
        if (ftyp != null) {
            report.append(", ").append(ftyp);
        }
        report.append('\n');
    }

    static void copyIntoDocument(InputStream input, OutputStream output, long expectedBytes,
                                 BooleanSupplier cancelled) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long copied = 0;
        int read;
        while ((read = input.read(buffer)) > 0) {
            checkCancelled(cancelled);
            output.write(buffer, 0, read);
            copied += read;
        }
        if (expectedBytes >= 0 && copied != expectedBytes) {
            throw new IOException("Copied " + copied + " of " + expectedBytes + " bytes");
        }
        checkCancelled(cancelled);
    }

    private static OutputStream openForRewrite(ContentResolver resolver, Uri documentUri)
            throws IOException {
        OutputStream output = resolver.openOutputStream(documentUri, "rwt");
        if (output == null) {
            throw new FileNotFoundException("The provider did not open " + documentUri);
        }
        return output;
    }

    static void requireMatchingSize(long expectedBytes, @Nullable Long actualBytes) throws IOException {
        if (actualBytes != null && actualBytes != expectedBytes) {
            throw new IOException("Expected " + expectedBytes + " bytes after remux, found " + actualBytes);
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) throw new CancellationException("GIF remux cancelled");
    }

    @Nullable
    private Long documentSize(Uri documentUri) {
        try (Cursor cursor = context.getContentResolver().query(
                documentUri,
                new String[]{DocumentsContract.Document.COLUMN_SIZE},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Could not read the saved file size", exception);
        }
        return null;
    }

    @Nullable
    private String readFtyp(Uri documentUri) {
        byte[] header = new byte[64];
        int length = 0;
        try (InputStream input = context.getContentResolver().openInputStream(documentUri)) {
            if (input == null) {
                return null;
            }
            while (length < header.length) {
                int read = input.read(header, length, header.length - length);
                if (read <= 0) {
                    break;
                }
                length += read;
            }
        } catch (IOException | RuntimeException exception) {
            return null;
        }
        return parseFtyp(header, length);
    }

    /** Describes an ISO-BMFF ftyp box from a file's first bytes, or null when absent. */
    @Nullable
    static String parseFtyp(byte[] header, int length) {
        if (length < 16) {
            return null;
        }
        long boxSize = readUint32(header, 0);
        if (boxSize < 16 || !isAscii(header, 4, "ftyp")) {
            return null;
        }
        StringBuilder description = new StringBuilder(64);
        description.append("ftyp=").append(ascii(header, 8, 4))
                .append(" minor=0x").append(Long.toHexString(readUint32(header, 12)));
        int available = (int) Math.min(boxSize - 16, Math.min(length, header.length) - 16);
        if (available >= 4) {
            description.append(" compatible=");
            for (int offset = 0; offset + 4 <= available; offset += 4) {
                if (offset > 0) {
                    description.append(',');
                }
                description.append(ascii(header, 16 + offset, 4));
            }
        }
        return description.toString();
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static long readUint32(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static boolean isAscii(byte[] bytes, int offset, String expected) {
        for (int index = 0; index < expected.length(); index++) {
            if ((bytes[offset + index] & 0xFF) != expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static String ascii(byte[] bytes, int offset, int size) {
        StringBuilder builder = new StringBuilder(size);
        for (int index = 0; index < size; index++) {
            char value = (char) (bytes[offset + index] & 0xFF);
            builder.append(value >= ' ' && value < 0x7F ? value : '.');
        }
        return builder.toString();
    }
}
