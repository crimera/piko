# Installation
- Use [crimera/revanced-integrations](https://github.com/crimera/revanced-integrations) when building.
- It is recommended to build with the official [revanced-patch](https://github.com/revanced/revanced-patches) bundle to remove ads.

```sh
java -jar cli.jar patch \
  -b official-revanced.jar \
  -b piko.jar \
  -m integrations.apk \
  -e "Unlock downloads"
  -e "Hide Recommended Users"
  -o out.apk input.apk
```

# Patches
| Patch | Description |
|:--------:|:--------------:|
| `Google Ads Patch` | Remove Google Ads Patch |
| `Hide Promoted Trends` ||
| `Hide For You` | Hides For You tab from timeline |
| `Custom download folder` | Change the download directory for video downloads |
| `Clear tracking params` | Removes tracking parameters when sharing links |
| `No shortened URL` | Get rid of t.co short urls. |
| `Disable chirp font` ||
| `Hide FAB Menu Buttons` ||
| `Hide FAB` ||
| `Hide Community Notes` ||
| `Selectable Text` | Makes bio and username selectable |
| `Show sensitive media` ||
| `Remove view count` | Removes the view count from the bottom of tweets |
| `Enable Reader Mode` | Enables reader mode on long threads |
| `Enable Undo Posts` | Enable ability to undo posts before it gets posted |
| `Download patch` | Unlocks the ability to download videos and gifs from Twitter/X |
| `Hide Recommended Users` | Hide recommended users that pops up when you follow someone |
| `Hide Banner` | Hide new post banner |
| `Hide Live Threads` ||
