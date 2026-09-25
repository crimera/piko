package app.morphe.extension.twitter.wireguard;

/** A recent handshake proves peer communication, not general Internet availability. */
final class WireGuardHealth {
    enum Result { WAITING, VERIFIED, UNCONFIRMED }
    private static final long INITIAL_WAIT_MS = 60_000;
    private static final long RECENT_HANDSHAKE_MS = 180_000;

    static Result evaluate(long[] handshakes, long now, long tunnelAge) {
        if (handshakes.length == 0) return Result.UNCONFIRMED;
        boolean allRecent = true;
        boolean anyHandshake = false;
        for (long timestamp : handshakes) {
            anyHandshake |= timestamp > 0;
            // Future timestamps can follow a wall-clock correction; do not claim freshness.
            allRecent &= timestamp > 0 && timestamp <= now && now - timestamp <= RECENT_HANDSHAKE_MS;
        }
        if (allRecent) return Result.VERIFIED;
        if (!anyHandshake && tunnelAge >= 0 && tunnelAge < INITIAL_WAIT_MS) return Result.WAITING;
        return Result.UNCONFIRMED;
    }
}
