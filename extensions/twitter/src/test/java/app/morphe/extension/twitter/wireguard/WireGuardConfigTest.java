/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.wireguard;

import com.wireguard.config.BadConfigException;
import com.wireguard.config.Config;
import com.wireguard.crypto.KeyPair;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.*;

public class WireGuardConfigTest {
    // Generated for each test: no real keys, credentials or endpoint are committed.
    private final String privateKey = new KeyPair().getPrivateKey().toBase64();
    private final String publicKey = new KeyPair().getPublicKey().toBase64();

    private String config(String applicationPolicy) {
        return "[Interface]\nPrivateKey = " + privateKey + "\nAddress = 10.7.0.2/32, fd00::2/128\n"
                + "DNS = 1.1.1.1, vpn.example\nMTU = 1380\nListenPort = 51820\n" + applicationPolicy
                + "\n[Peer]\nPublicKey = " + publicKey + "\nPresharedKey = " + privateKey
                + "\nEndpoint = vpn.example:51820\nAllowedIPs = 0.0.0.0/0, ::/0\nPersistentKeepalive = 25\n";
    }

    @Test public void importedAllowListCannotRouteAnotherApplication() throws Exception {
        Config result = WireGuardConfig.parse(config("IncludedApplications = another.app\n"), "renamed.x");
        assertEquals(Collections.singleton("renamed.x"), result.getInterface().getIncludedApplications());
        assertTrue(result.getInterface().getExcludedApplications().isEmpty());
    }

    @Test public void importedDenyListCannotExcludeXOrCaptureDevice() throws Exception {
        Config result = WireGuardConfig.parse(config("ExcludedApplications = com.twitter.android\n"), "com.twitter.android");
        assertEquals(Collections.singleton("com.twitter.android"), result.getInterface().getIncludedApplications());
        assertTrue(result.getInterface().getExcludedApplications().isEmpty());
    }

    @Test public void retainsInterfaceFieldsAndMultiplePeersAcrossSaveRoundTrip() throws Exception {
        String secondKey = new KeyPair().getPublicKey().toBase64();
        Config first = WireGuardConfig.parse(config("") + "\n[Peer]\nPublicKey = " + secondKey
                + "\nEndpoint = [2001:db8::1]:51821\nAllowedIPs = 192.0.2.0/24\n", "com.twitter.android");
        Config restored = WireGuardConfig.parse(first.toWgQuickString(), "com.twitter.android");
        // Upstream Interface.equals compares KeyPair object identity; compare the
        // serialization without printing secret material in assertion diagnostics.
        assertTrue("Configuration fields changed after round-trip", first.toWgQuickString().equals(restored.toWgQuickString()));
        assertEquals(2, restored.getPeers().size());
        assertEquals(2, restored.getInterface().getAddresses().size());
        assertEquals(Integer.valueOf(1380), restored.getInterface().getMtu().get());
        assertEquals(Integer.valueOf(51820), restored.getInterface().getListenPort().get());
        assertTrue(restored.getInterface().getDnsSearchDomains().contains("vpn.example"));
        assertEquals(Integer.valueOf(25), restored.getPeers().get(0).getPersistentKeepalive().get());
        assertEquals(privateKey, restored.getPeers().get(0).getPreSharedKey().get().toBase64());
    }

    @Test public void rejectsMissingAddressPeerAndRoutes() throws Exception {
        assertInvalid(config("").replace("Address = 10.7.0.2/32, fd00::2/128\n", ""));
        assertInvalid("[Interface]\nPrivateKey = " + privateKey + "\nAddress = 10.7.0.2/32\n");
        assertInvalid(config("").replace("AllowedIPs = 0.0.0.0/0, ::/0\n", ""));
    }

    @Test public void malformedKeyAndUnknownAttributesNeverAppearInDiagnostics() throws Exception {
        String secret = "sensitive-key-material-that-must-not-be-logged";
        for (String input : new String[]{config("").replace(privateKey, secret), config(secret + " = value\n")}) {
            try {
                WireGuardConfig.parse(input, "com.twitter.android");
                fail("Malformed config accepted");
            } catch (BadConfigException error) {
                String safe = WireGuardConfig.validationError(error);
                assertFalse(safe.contains(secret));
                assertFalse(safe.contains(privateKey));
                assertFalse(safe.contains(publicKey));
            }
        }
    }

    @Test public void rejectsOversizedConfigurationBeforeParsing() throws Exception {
        try {
            WireGuardConfig.parse(" ".repeat(WireGuardConfig.MAX_BYTES + 1), "com.twitter.android");
            fail("Oversized config accepted");
        } catch (IOException expected) { }
    }

    private void assertInvalid(String input) throws Exception {
        try { WireGuardConfig.parse(input, "com.twitter.android"); fail("Incomplete config accepted"); }
        catch (BadConfigException expected) { }
    }
}
