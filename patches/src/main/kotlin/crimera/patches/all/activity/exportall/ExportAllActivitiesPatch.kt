package crimera.patches.all.activity.exportall

import app.revanced.patcher.patch.resourcePatch

//credits - @ReVanced
@Suppress("unused")
val exportAllActivitiesPatch = resourcePatch(
    name = "Export all activities",
    description = "Makes all app activities exportable.",
    use = false,
) {
    compatibleWith("com.twitter.android")

    val EXPORTED_FLAG = "android:exported"

    execute { context ->
        context.document["AndroidManifest.xml"].use { editor ->
            val activities = editor.getElementsByTagName("activity")

            for (i in 0..activities.length) {
                activities.item(i)?.apply {
                    val exportedAttribute = attributes.getNamedItem(EXPORTED_FLAG)

                    if (exportedAttribute != null) {
                        if (exportedAttribute.nodeValue != "true") {
                            exportedAttribute.nodeValue = "true"
                        }
                    }
                    // Reason why the attribute is added in the case it does not exist:
                    // https://github.com/revanced/revanced-patches/pull/1751/files#r1141481604
                    else {
                        editor.createAttribute(EXPORTED_FLAG)
                            .apply { value = "true" }
                            .let(attributes::setNamedItem)
                    }
                }
            }
        }
    }
}