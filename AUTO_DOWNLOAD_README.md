# Auto-Download Stories — Implementation Notes

## What's implemented

1. **`extensions/instagram/.../patches/userprofile/ProfileMoreOption.java`**
   Toggle in the "More options" profile dialog: "Enable/Disable auto-download
   stories" for whichever profile you're viewing. Selective, not blanket.

2. **`extensions/instagram/.../utils/Pref.java`**
   `isAutoDownloadTarget()`, `addAutoDownloadTarget()`,
   `removeAutoDownloadTarget()` — manages the whitelist.

3. **`extensions/shared/library/.../crimera/SharedPref.java`**
   `getRawStringSetPref()` / `setRawStringSetPref()` helpers.

4. **`extensions/instagram/.../patches/download/AutoDownloadStories.java`**
   `checkAndDownloadFromReelItem(context, userObject, mediaObject)` — wraps
   the raw objects in piko's existing `UserData`/`MediaData` entities (these
   are already fully resolved elsewhere in the codebase, so no new reflection
   plumbing needed here), checks the whitelist, dedups by media ID, downloads
   via the existing `DownloadUtils`.

5. **`patches/.../misc/stories/autoDownload/AutoDownloadStoriesPatch.kt`**
   Hooks `ReelItem`'s own timestamp-format method (same one
   `CustomiseStoryTimestampPatch` already uses) — an instance method defined
   directly on the `ReelItem` class, so `p0` = the ReelItem itself, `p1` =
   Context. This fires automatically whenever a story is rendered on screen
   (tray thumbnail or full viewer) — no button press, no explicit "open"
   step. Extracts the owner `User` field and `Media` field by TYPE matching
   on the ReelItem class def (same technique `HandleStoryButtonPatch.kt`
   already uses for its Media field lookup), then calls
   `AutoDownloadStories.checkAndDownloadFromReelItem(...)`.

6. **`strings.xml`** — new UI strings.

## One thing to verify on your first build

`USER_CLASS = "Lcom/instagram/user/model/User;"` in
`AutoDownloadStoriesPatch.kt` is a well-known, historically stable Instagram
class name (consistent with `FRIENDSHIP_STATUS_CLASS` already used elsewhere
in this repo, same package: `com/instagram/user/model/`). I can't execute an
actual build against your target APK to 100% confirm it, so:

- If the patch build throws `"Could not find User-typed field on ReelItem"`,
  open `ReelItem` in jadx on your target APK and check what type its owner
  field actually is, then update `USER_CLASS` accordingly.
- If it builds fine, no changes needed.

Everything else should build and run as-is since it reuses infrastructure
(`MEDIA_CLASS_NAME`, `UserData`, `MediaData`, `DownloadUtils`) that's already
working elsewhere in the codebase.

## Update: Whitelist display in settings + hidden download folder

1. **Download folder renamed**: `ExtensionStrings.DEFAULT_PIKO_FOLDER` changed
   from `"Piko"` to `".Modx"` — the leading dot makes it a hidden folder on
   Android (won't show in gallery apps / most file managers by default).

2. **Whitelist section added to Piko settings → Download**:
   `ScreenBuilder.buildDownloadSection()` now shows an "Auto-download
   whitelist" entry with a summary listing all whitelisted usernames
   (`@username, @username2, ...`), or a placeholder message if empty.
   This is read-only/informational — add/remove still happens via the
   profile "More options" toggle (tap the same option again to remove).

3. `Pref.addAutoDownloadTarget()` now also takes a `username` param (stored
   separately) so the settings summary can show readable names instead of
   raw user IDs.
