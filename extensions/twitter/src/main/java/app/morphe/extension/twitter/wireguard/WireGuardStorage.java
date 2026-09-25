/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.wireguard;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import com.wireguard.config.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class WireGuardStorage {
    private static final String ALIAS = "piko_wireguard_config_v1";
    private static final byte VERSION = 1;
    private final Context context;
    private final AtomicFile file;

    WireGuardStorage(Context context) {
        this.context = context;
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "piko-wireguard.enc"));
    }

    boolean exists() {
        File base = file.getBaseFile();
        // Android 8/9 AtomicFile can leave only the committed .bak after an
        // interrupted replacement. openRead() restores it when load() is called.
        return base.exists() || new File(base.getPath() + ".bak").exists();
    }

    private SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            if (!create) throw new IOException("Configuration key unavailable");
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            return generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }

    void save(Config config) throws Exception {
        byte[] plaintext = config.toWgQuickString().getBytes(StandardCharsets.UTF_8);
        FileOutputStream output = null;
        try {
            if (plaintext.length > WireGuardConfig.MAX_BYTES) throw new IOException("Configuration is too large");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(true));
            cipher.updateAAD(new byte[]{VERSION});
            byte[] ciphertext = cipher.doFinal(plaintext);
            output = file.startWrite();
            output.write(VERSION);
            output.write(cipher.getIV());
            output.write(ciphertext);
            file.finishWrite(output);
        } catch (Exception error) {
            if (output != null) file.failWrite(output);
            throw error;
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    Config load() throws Exception {
        byte[] envelope;
        try (InputStream input = file.openRead()) {
            envelope = readBounded(input, WireGuardConfig.MAX_BYTES + 64);
        }
        if (envelope.length < 29 || envelope[0] != VERSION) throw new IOException("Invalid encrypted configuration");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, envelope, 1, 12));
        cipher.updateAAD(new byte[]{VERSION});
        byte[] plaintext = cipher.doFinal(envelope, 13, envelope.length - 13);
        try {
            return WireGuardConfig.parse(new String(plaintext, StandardCharsets.UTF_8), context.getPackageName());
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    void delete() throws Exception {
        // Delete the key first: even an interrupted file deletion leaves no decryptable material.
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        store.deleteEntry(ALIAS);
        file.delete();
        if (exists()) throw new IOException("Could not delete encrypted configuration");
    }

    static byte[] readBounded(InputStream input, int limit) throws IOException {
        if (input == null) throw new IOException("Unable to open document");
        // One bounded buffer avoids ByteArrayOutputStream retaining a second plaintext copy.
        byte[] buffer = new byte[limit + 1];
        try {
            int size = 0;
            while (size < buffer.length) {
                int count = input.read(buffer, size, buffer.length - size);
                if (count < 0) return Arrays.copyOf(buffer, size);
                size += count;
            }
            throw new IOException("Configuration is too large");
        } finally {
            Arrays.fill(buffer, (byte) 0);
        }
    }
}
