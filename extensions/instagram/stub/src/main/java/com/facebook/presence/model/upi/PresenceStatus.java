package com.facebook.presence.model.upi;

/**
 * Stub of the obfuscated Instagram enum
 * {@code com.facebook.presence.model.upi.PresenceStatus}.
 *
 * <p>The real enum declares OFFLINE, ACTIVE, IDLE and DISABLED, but its static fields are
 * renamed by R8 (A06, A03, A05, A04 on 435.0.0.37.76). Only the enum <i>names</i> survive
 * obfuscation, so the extension must look constants up with {@link #valueOf(String)} instead
 * of referencing the fields directly.
 */
public enum PresenceStatus {
    OFFLINE,
    ACTIVE,
    IDLE,
    DISABLED,
}
