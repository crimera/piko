package app.morphe.extension.newx.misc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.LongConsumer;

final class MultiAppTransfer {
    private MultiAppTransfer() {}

    // Both clone profiles have reported current failures. Their provider errors differ;
    // keep ordinary users and work profiles on DownloadManager.
    static boolean useAppProcess(int uid, int sdk, String manufacturer) {
        return sdk >= 29 && uid / 100_000 == 999 &&
                ("OnePlus".equalsIgnoreCase(manufacturer) || "OPPO".equalsIgnoreCase(manufacturer)
                        || "Xiaomi".equalsIgnoreCase(manufacturer));
    }

    static String fileName(String name) {
        if (name == null || name.isBlank() || name.equals(".") || name.equals("..") ||
                name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid media filename");
        }
        return name;
    }

    static long copy(InputStream input, OutputStream output, long expected, LongConsumer progress)
            throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long count = 0;
        for (int length; (length = input.read(buffer)) != -1;) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Download interrupted");
            output.write(buffer, 0, length);
            count += length;
            progress.accept(count);
        }
        if (count == 0 || (expected >= 0 && count != expected)) {
            throw new IOException("Incomplete media: " + count + " of " + expected + " bytes");
        }
        return count;
    }
}
