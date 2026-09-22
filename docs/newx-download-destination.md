# NewX download destination (feat: Downloads location)

Implements `crimera/piko-newx` issue #36: let the user choose where inline-downloaded media is
saved, and let them control the saved filename.

## What changed

The inline download pipeline used to be a two-stage bridge between two independent writers:

1. `DownloadManager.enqueue(...)` wrote a staging file into app-private external storage
   (`Android/data/<pkg>/files/Pictures|Movies/Twitter/*_tmp_<uuid>.<ext>`).
2. An `ACTION_DOWNLOAD_COMPLETE` receiver called `publishDownload(...)`, which performed a second
   MediaStore insert (`RELATIVE_PATH` + `IS_PENDING`) and then deleted the staging file.

Every reported bug class in #36 traced back to that bridge:

| Report | Root cause |
| --- | --- |
| "Downloads not happening" | `publishDownload` bailed out when `getUriForDownloadedFile(id)` was `null` — always the case for an app-private-external destination, and always after a process restart. The failure path posted a notification and never cleared the pending entry, so the same entry was retried forever and the file was discarded. |
| "Media disappears after about a week" | The only durable copy was the app-private staging file. Android/OEM storage management may reclaim that directory, and because the publish step failed the file was never gallery-indexed, so nothing survived. |
| "Duplicate downloads" | Collision detection was `mediaExists()` (blind when `READ_MEDIA_*` is denied, hence the provider-probe hack) or `pendingFileExists()`. Either could report "free" while a row existed, so MediaProvider renamed collisions to `file (1).jpg` — while DownloadManager and MediaStore raced on the same path as two distinct writers. |

The pipeline is now a **single SAF writer**. No `DownloadManager`, no MediaStore publish, no
pending-preference machinery, no fallback path.

## Behavior

### Destination model

- One Storage Access Framework **tree URI per media type**: images and videos.
- The chosen folder *is* the parent — there is no fixed subfolder. Files are created directly
  inside it with `DocumentsContract.createDocument`.
- Routing is by MIME type: `image/*` → images folder, `video/*` → videos folder. A MIME type that
  is neither is rejected (fail closed) instead of being guessed into a folder.
- Gallery visibility is now the user's choice, and the settings copy says so: a tree under
  `Pictures/` or `DCIM/` is media-scanned, a tree under `Download/` is not.

### First run

Destination selection is prompted on the **first download attempt**, not when settings are
opened. The dialog names the media kinds that still need a folder and offers a per-kind
"Choose folder" action. Cancelling aborts the download with an explicit message; it never falls
back to `Android/data`.

### Transfer scheduling

Transfers run on a small fixed thread pool, and each item's progress notification is posted at
**enqueue time** — right after `DownloadDestination.reserve(...)` succeeds, before the transfer
is scheduled. Creating the notification on the transfer thread made it wait for every download
ahead of it to finish streaming, which read as "downloads take a while to start" even though no
work was actually blocked. SAF collision resolution stays serialized by
`enqueueDownload`'s lock, so parallel transfers write to disjoint, uniquely reserved documents.
A failed first attempt now reuses the same notification for the `name=4096x4096` retry instead of
dropping it silently.

Click-time post metadata is resolved from a **single** `toString()`. Every `rawSource*` accessor
builds the post's data-class `toString`, which is large; resolving the filename tokens and author
handle independently rebuilt it many times before any storage work ran. The text is now
materialized once in `resolveAndPresent` and passed through the `...FromText` overloads.

### Conflict policy

`newx.content.inline_download_conflict` is unchanged in name and options, but re-implemented
against the SAF provider instead of a MediaStore query:

| Value | Behavior |
| --- | --- |
| `overwrite` | The existing document is reused and truncated in place at transfer time; no delete or create |
| `rename` | The new document is created with a `_1`, `_2`, … suffix |
| `skip` | The download is skipped silently, before any network work |

Collision resolution happens in `DownloadDestination.reserve(...)` *before* the transfer starts,
so a skipped download costs nothing. It deliberately does **not** list the directory:
`/children` is an unindexed full listing, so a folder holding thousands of files (a real
reported folder held 2,951) would cost a full provider round-trip of every row. Existence is
instead probed with a single-document lookup built from the folder's document id (local providers
encode the path in document ids), which answers hit or miss in constant time. All three policies
probe before creating anything: `SKIP` returns null on a hit without creating or deleting,
`RENAME` advances to the next `_N` suffix until the probe misses, and `OVERWRITE` returns the
occupant unchanged so the transfer reuses it — the write opens the existing document in place
(truncate-open) instead of deleting and re-creating it at tap time. Only when the probe misses and
the provider nevertheless renames on create — the signal that it uses opaque document ids — does
the code delete the created probe and fall back to one directory listing, so the extension never
reports success under a name the file does not have. An unknown policy value (hand-edited or
restored from a foreign backup) throws rather than silently overwriting the user's files. The
in-place write opens the document with the explicit `wt` (truncate) mode: plain `w` leaves
truncation provider-defined and has not truncated since Android 10, which would leave stale
trailing bytes when the replacement is shorter than the occupant.

### Filename template

`newx.content.inline_download.filename_template` is a single string setting. Default:
`{screenName}_{id}`.

| Token | Source | Notes |
| --- | --- | --- |
| `{id}` | canonical post id | For a repost this is the **original/source** post id |
| `{screenName}` | author `screenName` (or the expanded-URL mention for folded retweets) | |
| `{name}`, `{displayName}` | author display name | `{displayName}` is an alias of `{name}` |
| `{timestamp}` | canonical `timestamp`, normalized to `2026-01-31-123456` | |
| `{mediaIndex}` | 1-based index within the post's media | |
| `{ext}` | derived from the media MIME type | Placing it disables the automatic extension |

Rules:

- Literal text between tokens is kept.
- `{mediaIndex}` is **only** appended automatically when the template does not contain it and the
  post has more than one media item (`name_1.jpg`, `name_2.jpg`).
- Unknown tokens stay literal so a typo is visible instead of silently collapsing every download
  onto one name; the editor refuses to save them.
- A template must contain `{id}` or `{timestamp}`. Without one of them every post would resolve to
  the same name, so the editor rejects it as "static".
- A token that resolves to nothing contributes an empty segment rather than literal braces. If the
  whole stem is empty the pre-template scheme (`<screenName>_<postId>`) is used, so a post whose
  fields cannot be parsed still produces a usable name.
- Every rendered name is passed through the segment sanitizer: `@` prefixes are dropped, anything
  outside `[A-Za-z0-9._-]` becomes `_`, and punctuation runs are collapsed — so no rendered name
  can contain a path separator or a parent-directory component.

## Download options screen

`newx.content.inline_download.options` (`POST_ACTIONS_MEDIA` → `INLINE_DOWNLOAD`) opens a custom
screen with two sections:

1. **Folders** — "Images" and "Videos" rows, each showing the resolved path and opening the SAF
   picker. An unset row shows "Not set — tap to choose"; a row whose persisted grant is gone shows
   the path plus "Tap to give access again".
2. **Filename** — the template editor: a text field, a live preview rendered by the real renderer
   (so the preview cannot drift from production), token chips that insert at the caret, inline
   validation, and a reset-to-default button. The dialog stays open on an invalid template and
   reports the specific problem.

The conflict policy stays a normal visible settings row in the `INLINE_DOWNLOAD` group; it is a
plain three-way choice and does not need the custom screen's richer editors.

The filename template is registered as a **hidden** settings node: the registry export still
carries it through backup/restore, while the Download options screen owns the only UI, so the
value has exactly one editor.

## Settings nodes

Every persisted value is an ordinary NewX settings registry node, which is what keeps
backup/restore working — the `crimera.sharedPreference` layer used by the Instagram downloader has
no export path at all.

| Node id | Type | Visible | Purpose |
| --- | --- | --- | --- |
| `newx.content.inline_download.images_tree_uri` | `TEXT_INPUT` | no | Persisted SAF tree URI |
| `newx.content.inline_download.videos_tree_uri` | `TEXT_INPUT` | no | Persisted SAF tree URI |
| `newx.content.inline_download.images_display_path` | `TEXT_INPUT` | no | Human-readable path for summaries |
| `newx.content.inline_download.videos_display_path` | `TEXT_INPUT` | no | Human-readable path for summaries |
| `newx.content.inline_download.filename_template` | `TEXT_INPUT` | no | Template; edited only from the Download options screen |
| `newx.content.inline_download_conflict` | `SINGLE_CHOICE` | yes | Conflict policy, shown as a normal settings row |

`visible = false` nodes are still registered and still exported; the renderer only skips them for
display. `DownloadSettings.java` owns these ids so every registry read resolves against a declared
constant in `SettingsAggregateValidationTest`.

`SettingsBackupRestore.ensureAllSettingsLoaded()` calls `SettingsRegistry.load()` before touching
the lazy holders, so a download that happens before the settings screen is ever opened still sees
the same frozen registry the backup exports.

## Code map

| File | Role |
| --- | --- |
| `extensions/newx/.../misc/DownloadDestination.java` | The only writer: tree resolution, MIME routing, grant re-validation, collision policy, `createDocument` + stream, retry, discard, notification |
| `extensions/newx/.../misc/DownloadFileName.java` | Template rendering, token contract, validation outcomes, sanitizer |
| `extensions/newx/.../misc/DownloadSettings.java` | Registry-backed accessors for the six settings ids |
| `extensions/newx/.../misc/DownloadOptionsFragment.java` | The Download options custom screen; the only editor for the folders and the filename template |
| `extensions/newx/.../misc/DownloadFolderPickerActivity.java` | NewX-local SAF picker (a plain `Activity`; the shared `FolderPickerActivity` is an `AppCompatActivity` and NewX ships no AppCompat runtime) |
| `extensions/newx/.../misc/InlineDownloadButton.java` | Icon/action injection, media selection, picker, long-press download-all, transfer orchestration |
| `extensions/newx/.../misc/MediaMerger.java` | Merged split-artwork saves through the same writer |
| `patches/.../newx/misc/inlineactions/InlineDownloadButtonPatch.kt` | Settings declarations, custom screen, hidden template/conflict/URI nodes |
| `patches/.../newx/utils/Constants.kt` | Fragment/activity descriptors |
| `patches/.../newx/settings/SettingsResourcePatch.kt` | Registers `DownloadFolderPickerActivity` in the manifest |
| `patches/src/main/resources/addresources/values/newx/strings.xml` | `piko_newx_download_*` strings |

### Deleted

From `InlineDownloadButton.java`: `enqueueDownload`/`enqueueFallbackDownload`/`queueDownload`
(`DownloadManager`), `registerDownloadReceiver`/`resumePendingDownloads` and the Tiramisu
receiver-flag split, the pending-preference serialization and its blocking `commit()`,
`publishDownload`, `deleteExistingMedia` and the `existingMediaSelection*` cursor helpers,
`moveLegacyDownload` and its media-scanner broadcast, `mediaExists`/`pendingFileExists`, the
Pictures/Movies collection matrix, and the hardcoded `DOWNLOAD_DIRECTORY = "Twitter"` /
`relativePath(mimeType)` pair.

## Migration

None. Previously *published* files were never referenced by preference, and the removed
pipeline's staging directory is not touched.

## Known limits

- **No transfer resume.** `DownloadManager` retry/resume across connectivity loss is gone; the
  accepted mitigation is the existing `name=orig` → `name=4096x4096` retry inside
  `DownloadDestination.save`, plus an explicit failure message and a partial-file discard instead
  of a silent drop.
- **Success is reported only by the OS download notification.** The in-app toast is reserved for
  failures ("Could not save …"), because a failed transfer cancels that notification and would
  otherwise be silent. The outcome is known synchronously, so the failure toast appears as soon
  as the transfer ends.
- **Restored grants do not transfer between devices.** A backup restores the tree URI string but
  not the persisted SAF permission. The destination is therefore re-validated against
  `getPersistedUriPermissions()` before every write, and an unusable destination re-runs the
  first-run prompt instead of failing the download.
- **Restricted roots.** Android 11+ forbids picking the root of `Download/` and the root of an SD
  card, so those trees cannot be selected; a subfolder can.
- Profile-photo and X-native/premium downloads are out of scope and keep their existing paths.

## Validation

Performed in this change:

- `./gradlew :extensions:newx:testDebugUnitTest` — 212 tests pass, including the template/render,
  validation-outcome, media-index-suffix, sanitizer, and MIME-routing contract tests.
- `./gradlew :patches:build --no-daemon` — success.
- `./gradlew :patches:test :patches:lintNewxResolvers` — `SettingsAggregateValidationTest`,
  `SettingsContributionPatchTest`, `SettingsDefinitionsTest`, `ResolverCardinalityTest`, and the
  resolver linter all pass.
- `./patch-twitter.sh apks/<apk>` for `12.27.0-prod.01`, `12.28.0-alpha.01`, and
  `12.28.0-alpha.04` — 43 patches reported `Applied` each, `NewX: Inline download button` among
  them, no warnings or failures, output `~/Downloads/piko-twitter-patched.apk`.
- The patched artifact contains `DownloadDestination`, `DownloadFileName`, `DownloadSettings`,
  `DownloadOptionsFragment`, and `DownloadFolderPickerActivity`, and
  the manifest declares `DownloadFolderPickerActivity`.

Still to confirm on a device (not performed from the patch session):

- Inline download, media-picker multi-select, Download & Merge, long-press download-all.
- Download options: folder rows and the filename editor with preview; the conflict policy as a
  normal settings row.
- Negative paths: cancel at the first-run prompt; a filename collision under each policy.
- Backup → wipe → restore → download, to prove the URIs survive and the grant re-validation
  prompt fires.
