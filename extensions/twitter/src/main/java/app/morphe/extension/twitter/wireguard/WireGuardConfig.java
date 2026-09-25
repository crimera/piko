/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.wireguard;

import com.wireguard.config.BadConfigException;
import com.wireguard.config.Config;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

/** Validation and routing policy shared by import, editing and persisted configurations. */
public final class WireGuardConfig {
    public static final int MAX_BYTES = 256 * 1024;

    private WireGuardConfig() { }

    public static Config parse(String text, String packageName) throws IOException, BadConfigException {
        if (text.length() > MAX_BYTES) throw new IOException("Configuration is too large");
        return scope(Config.parse(new BufferedReader(new StringReader(text))), packageName);
    }

    public static Config scope(Config config, String packageName) throws BadConfigException {
        Interface original = config.getInterface();
        if (original.getAddresses().isEmpty()) {
            throw new BadConfigException(BadConfigException.Section.INTERFACE,
                    BadConfigException.Location.ADDRESS, BadConfigException.Reason.MISSING_ATTRIBUTE, null);
        }
        if (config.getPeers().isEmpty()) {
            throw new BadConfigException(BadConfigException.Section.PEER,
                    BadConfigException.Location.PUBLIC_KEY, BadConfigException.Reason.MISSING_SECTION, null);
        }
        for (Peer peer : config.getPeers()) {
            if (peer.getAllowedIps().isEmpty()) {
                throw new BadConfigException(BadConfigException.Section.PEER,
                        BadConfigException.Location.ALLOWED_IPS, BadConfigException.Reason.MISSING_ATTRIBUTE, null);
            }
        }
        // Deliberately do not copy either imported application allow/deny list.
        Interface.Builder scoped = new Interface.Builder()
                .setKeyPair(original.getKeyPair())
                .addAddresses(original.getAddresses())
                .addDnsServers(original.getDnsServers())
                .addDnsSearchDomains(original.getDnsSearchDomains())
                .includeApplication(packageName);
        if (original.getMtu().isPresent()) scoped.setMtu(original.getMtu().get());
        if (original.getListenPort().isPresent()) scoped.setListenPort(original.getListenPort().get());
        return new Config.Builder().setInterface(scoped.build()).addPeers(config.getPeers()).build();
    }

    public static String validationError(BadConfigException error) {
        // Never use getText(), getMessage(), toString() or its cause: they can contain keys.
        return error.getSection().getName() + " / " + error.getLocation().getName()
                + ": " + error.getReason().name();
    }
}
