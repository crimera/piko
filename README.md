# Notes
- Use [crimera/revanced-integrations](https://github.com/crimera/revanced-integrations) when building.
- It is recommended to build with the official [revanced-patch](https://github.com/revanced/revanced-patches) bundle to remove ads.

```sh
java -jar cli.jar patch \
  -b official-revanced.jar \
  -b piko.jar \
  -m integrations.apk \
  -e "Unlock downloads"
  -o out.apk input.apk
```
