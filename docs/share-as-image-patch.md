# Share as Image Patch - Documentation

This document serves as the single source of truth for the "Share as Image" extension in Piko. It covers the architectural design, key implementation details, settings integration, and the share menu patch.

## Overview
The "Share as Image" feature allows users to capture a tweet or a connected thread segment as a single, high-quality image. It uses a recursive view-tree search and intelligent clipping to provide a clean capture without interactive elements.

## Key File Locations
- **Logic Handler**: `extensions/twitter/src/main/java/app/morphe/extension/twitter/patches/nativeFeatures/shareImage/ShareImageHandler.java`
  - *Role*: Manages view searching, target resolution, and MediaStore integration.
- **View Utilities**: `extensions/twitter/src/main/java/app/morphe/extension/twitter/utils/ViewUtils.java`
  - *Role*: Handles Bitmap generation, clipping, and UI state management (hiding scrollbars).
- **Settings Definition**: `extensions/twitter/src/main/java/app/morphe/extension/twitter/settings/Settings.java`
  - *Role*: Defines the `SHARE_IMAGE_ENABLED` boolean setting.
- **Preference Utility**: `extensions/twitter/src/main/java/app/morphe/extension/twitter/Pref.java`
  - *Role*: Provides `enableShareImage()` to check the setting's status.
- **Settings UI**: `extensions/twitter/src/main/java/app/morphe/extension/twitter/settings/ScreenBuilder.java`
  - *Role*: Adds the "Share as Image" toggle to the Piko settings UI.
- **Share Menu Patch**: `patches/src/main/kotlin/app/crimera/patches/twitter/misc/shareMenu/nativeShareImage/NativeShareImagePatch.kt`
  - *Role*: Coordinates the bytecode injection of the feature into the Twitter app.
- **Injection Logic**: `patches/src/main/kotlin/app/crimera/patches/twitter/misc/shareMenu/hooks/Util.kt`
  - *Role*: Implements the low-level logic to inject the button and click handler into the native share menu.

## Technical Implementation

### 1. Identifying the Target
The system uses `searchViewTree` to recursively traverse the Android view hierarchy, looking for a `View` whose tag matches the target `Tweet ID`.

### 2. Intelligent Target Resolution
The `resolveCaptureTarget` method determines the optimal capture area:
- **Thread Detection**: If on a Detail Screen, it identifies the `threadContainer` (RecyclerView/ListView) and uses `computeThreadBounds` to find connected tweets.
- **Connector Matching**: It looks for `tweet_connector_top` and `tweet_connector_bottom` views to group consecutive tweets in a thread that are currently visible on screen.

### 3. Refined Clipping
- **Anchor-based Cropping**: It identifies views like `tweet_inline_actions` or `stats_container` and crops the image just above them.
- **Slop Removal**: It iteratively removes small divider views or padding at the bottom of the tweet container.
- **Scrollbar Suppression**: It temporarily disables scrollbars using `ViewUtils.setScrollbarsVisible` during the rendering phase.

### 4. Settings Integration
- The feature can be toggled via the Piko settings menu.
- The state is stored in `piko_settings` with the key `share_image_enabled`.
- `SettingsStatus.shareImage` is used to track whether the patch is active and visible in the settings.

### 5. Share Menu Patching
The patch hooks into Twitter's `com.twitter.tweet.action` package:
- **Button Injection**: Adds a new action ("ShareImage") to the collection of items displayed in the native share menu.
- **Icon & Text**: Sets the icon to `ic_vector_share` and the label to the string resource `piko_share_image_title`.
- **Click Handler**: Injects bytecode that extracts the `Context` (Activity) and the `Tweet` object from the share menu's state and passes them to `ShareImageHandler.shareAsImage`.

## Storage & Sharing
Images are stored in the `Pictures/Piko` folder using the **MediaStore API**:
- **Scoped Storage**: Compatible with Android 10+ (Q).
- **Automatic Cleanup**: Removes captures older than 24 hours.
- **Native Sharing**: Uses `Intent.createChooser` to allow users to share the generated PNG directly.

## Maintenance Notes
- **Obfuscation**: Resource IDs are resolved by name (`getResourceIdentifier`) to survive app updates.
- **Bytecode Hooks**: The share menu injection relies on locating specific Smali instructions (`SGET_OBJECT`, `INVOKE_VIRTUAL`) in Twitter's internal classes. If the share menu architecture changes, these hooks may need adjustment.
