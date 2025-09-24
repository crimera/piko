package app.revanced.patches.all.misc.activity.exportall

import app.revanced.patcher.patch.resourcePatch

@Suppress("unused")
val exportAllActivitiesPatch =
    resourcePatch(
        name = "Export all activities",
        description = "Makes all app activities exportable.",
        use = false,
    ) {
        execute {
            val exportedFlag = "android:exported"

            document("AndroidManifest.xml").use { document ->
                val activities = document.getElementsByTagName("activity")

                for (i in 0..activities.length) {
                    activities.item(i)?.apply {
                        val exportedAttribute = attributes.getNamedItem(exportedFlag)

                        if (exportedAttribute != null) {
                            if (exportedAttribute.nodeValue != "true") {
                                exportedAttribute.nodeValue = "true"
                            }
                        }
                        // Reason why the attribute is added in the case it does not exist:
                        // https://github.com/revanced/revanced-patches/pull/1751/files#r1141481604
                        else {
                            document
                                .createAttribute(exportedFlag)
                                .apply { value = "true" }
                                .let(attributes::setNamedItem)
                        }
                    }
                }
            }
        }
    }
