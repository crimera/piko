package crimera.patches.all.activity.exportall

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.Patch

//credits - @ReVanced
@Patch(
    name = "Export all activities",
    description = "Makes all app activities exportable.",
    use = false,
)
@Suppress("unused")
object ExportAllActivitiesPatch : ResourcePatch() {
    private const val EXPORTED_FLAG = "android:exported"

    override fun execute(context: ResourceContext) {
        context.xmlEditor["AndroidManifest.xml"].use { editor ->
            val document = editor.file

            val activities = document.getElementsByTagName("activity")

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
                        document.createAttribute(EXPORTED_FLAG)
                            .apply { value = "true" }
                            .let(attributes::setNamedItem)
                    }
                }
            }
        }
    }
}