package app.revanced.extension.shared.fixes.slink;

public enum ResolveResult {
    // Let app handle rest of stuff
    CONTINUE,
    // Start app, to make it cache its access_token
    ACCESS_TOKEN_START,
    // Don't do anything - we started resolving
    DO_NOTHING
}
