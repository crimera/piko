# NewX public downloads in clone profiles

## Target and observed failure

The local deployment targets `com.twitter.android` `12.25.0-prod.01` (312250001),
using x-lite `a238d895` plus the media-refresh prerequisite and this compatibility
patch. Morphe Desktop 1.11.0 consumes `patches-3.9.0-dev.4.mpp` and the original
APKM. No APK or device log is part of this change.

Main-profile downloads worked while clone-profile downloads failed on OnePlus
ColorOS 16 / Android 16 and Xiaomi HyperOS 4 / Android 17. Prior ColorOS evidence
showed a cross-user system-download task failing before receiving any bytes.
Exact OEM exception attribution remains unresolved. Earlier app-process transfer
builds produced completed files and media-scan callbacks on both devices.

The local Xiaomi log later contained nine image HTTP 404 failures followed by
four other successful image transfers. The user could download the failed images
using another plugin. These observations do not establish deleted media. The
initial local clone transport omitted the original-size fallback already present
in the upstream inline DownloadManager path; that behavior is included here.

## Implementation contract

- The patch is disabled by default. Runtime routing remains disabled unless the
  patch replaces `isPatchApplied()`, then requires an affected manufacturer,
  SDK >= 29 and Android user 999. This is an explicit compatibility option, not
  automatic detection of a system download failure.
- The native dispatcher is resolved by its signature and DownloadManager call.
  Context access must use a public instance field on a public owner. The native
  callback must be a public interface with an unambiguous public method.
  These references are injected at patch time; extensions use Object bridges.
- Private/offline native requests retain their original path. Public requests
  preserve callbacks, request headers and the owning user's MediaStore context.
- Pending names participate in inline conflict decisions. Duplicate native
  requests receive a terminal already-pending result.
- Stream bytes are checked before publication; failure deletes only the new
  pending item. Publication detaches the file from rollback before notifications.
- Original-size image 404s on the exact X media endpoint retry 4096x4096 once.
  Other HTTP failures, unrelated hosts and video requests do not use that retry.

## Validation and draft limits

Build, extension and patch unit tests, extension assembly and resolver lint are
run for this branch. The original HTTP transport failed three of the nine new
HTTP fixtures; the repaired transport passes those fixtures. A default-off test
checks that unselected compatibility does not reach Android device routing.

The exact production APK is patched with the full selected NewX patch set,
including this opt-in patch. The PR description records resulting test counts
and the APK patch result. Local Maven bootstrap and unused JDK-import cleanup
are excluded from this functional change.

This remains a draft pending the previously failing image retest, representative
older/current/alpha target matrix, patch-selection combinations and Android
MediaStore/callback failure-path integration tests. The shared compatibility
table is not evidence that every declared target has been tested here.

Transfers depend on the host process; persistent queue recovery and orphaned
pending-item cleanup after process death are not implemented. Hidden existing
files may use provider rename suffixes, and cross-owner writes remain subject
to MediaStore permissions. Image format preservation is separate from the
original-size fallback; existing forced-JPG behavior is unchanged.
