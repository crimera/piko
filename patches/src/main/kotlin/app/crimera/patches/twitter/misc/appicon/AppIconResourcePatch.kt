package app.crimera.patches.twitter.misc.appicon

import app.revanced.patcher.patch.resourcePatch
import app.revanced.util.ResourceGroup
import app.revanced.util.copyResources
import app.revanced.util.findElementByAttributeValue
import app.revanced.util.findElementByAttributeValueOrThrow

val appIconResourcePatch =
    resourcePatch {
        execute {
            copyResources(
                "twitter/settings",
                ResourceGroup(
                    "layout",
                    "fragment_icon_selector.xml",
                    "icon_item.xml",
                    "section_header.xml",
                ),
            )

            val sourceDir = "twitter/appicons"
            copyResources(
                sourceDir,
                ResourceGroup("mipmap-xxhdpi", *iconBackgroundFiles),
            )

            var iconStartCount = 0
            document("AndroidManifest.xml").use { document ->
                val applicationNode = document.getElementsByTagName("application").item(0)
                val startActivityElement =
                    applicationNode.childNodes.findElementByAttributeValueOrThrow(
                        "android:name",
                        "com.twitter.android.StartActivity",
                    )

                var insertAfter = startActivityElement

                iconNames.forEach { iconName ->
                    val xmlName = "$iconName.xml"
                    copyResources(
                        sourceDir,
                        ResourceGroup("mipmap-anydpi", xmlName),
                    )

                    val icon =
                        IconConfig(
                            name = "app.revanced.extension.twitter.appicon$iconStartCount",
                            iconResource = "@mipmap/$iconName",
                        )

                    iconStartCount++
                    // Check if this specific alias already exists
                    val existingAlias =
                        applicationNode.childNodes.findElementByAttributeValue(
                            "android:name",
                            icon.name,
                        )

                    if (existingAlias == null) {
                        val activityAlias =
                            document.createElement("activity-alias").apply {
                                setAttribute("android:enabled", "false")
                                setAttribute("android:exported", "true")
                                setAttribute("android:icon", icon.iconResource)
                                setAttribute("android:name", icon.name)
                                setAttribute("android:roundIcon", icon.iconResource)
                                setAttribute("android:targetActivity", "com.twitter.android.StartActivity")
                            }

                        // Add intent-filter
                        val intentFilter = document.createElement("intent-filter")
                        intentFilter.appendChild(
                            document.createElement("action").apply {
                                setAttribute("android:name", "android.intent.action.MAIN")
                            },
                        )
                        intentFilter.appendChild(
                            document.createElement("category").apply {
                                setAttribute("android:name", "android.intent.category.LAUNCHER")
                            },
                        )
                        activityAlias.appendChild(intentFilter)

                        // Add meta-data elements
                        activityAlias.appendChild(
                            document.createElement("meta-data").apply {
                                setAttribute("android:name", "appFamilies")
                                setAttribute("android:value", "twitter")
                            },
                        )
                        activityAlias.appendChild(
                            document.createElement("meta-data").apply {
                                setAttribute("android:name", "mainActivityAliasForAppFamily")
                                setAttribute("android:value", "true")
                            },
                        )

                        // Insert after the previous element
                        applicationNode.insertBefore(activityAlias, insertAfter.nextSibling)

                        // Update insertAfter to maintain sequential order
                        insertAfter = activityAlias
                    }
                }
            }

            val stringsDir = "$sourceDir/strings"
            copyResources(stringsDir, ResourceGroup("values", "app_icon_strings.xml"), appendPiko = true)
        }
    }
