package app.morphe.extension.twitter.wireguard;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static app.morphe.extension.twitter.wireguard.WireGuardHealth.Result.*;

public class WireGuardHealthTest {
    @Test public void waitsThenReportsUnconfirmedWithoutCallingPeerUnreachable() {
        assertEquals(WAITING, WireGuardHealth.evaluate(new long[]{0}, 1_000_000, 59_999));
        assertEquals(UNCONFIRMED, WireGuardHealth.evaluate(new long[]{0}, 1_000_000, 60_000));
    }

    @Test public void requiresRecentHandshakeFromEveryPeer() {
        assertEquals(VERIFIED, WireGuardHealth.evaluate(new long[]{999_000, 820_000}, 1_000_000, 200_000));
        assertEquals(UNCONFIRMED, WireGuardHealth.evaluate(new long[]{999_000, 819_999}, 1_000_000, 200_000));
        assertEquals(UNCONFIRMED, WireGuardHealth.evaluate(new long[]{999_000, 0}, 1_000_000, 200_000));
        assertEquals(UNCONFIRMED, WireGuardHealth.evaluate(new long[]{}, 1_000_000, 200_000));
    }

    @Test public void rejectsFutureTimestampAndRecoversOnFreshHandshake() {
        assertEquals(UNCONFIRMED, WireGuardHealth.evaluate(new long[]{1_000_001}, 1_000_000, 200_000));
        assertEquals(VERIFIED, WireGuardHealth.evaluate(new long[]{1_000_000}, 1_000_000, 200_000));
    }
}
