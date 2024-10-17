package crimera.patches.twitter.misc.fixstring

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch

@Patch(
    name = "ZFix String Resource",
    description = "Test modifying string",
    compatiblePackages = [CompatiblePackage("com.twitter.android")]
)
object FixStringResourcePatch: ResourcePatch() {
    private inline fun measureExecutionTime(block: () -> Unit): Long {
        val start = System.currentTimeMillis()
        block()
        val end = System.currentTimeMillis()
        return end - start
    }

    override fun execute(context: ResourceContext) {
        val duration = measureExecutionTime {
            val strings = context["res/values/strings.xml"]
            val content = strings.readText()
            val regex = Regex("""(<string\s+name="conference_default_title">)([^<]*)(<\/string>)""")
            val matchResult = regex.find(content)
            matchResult?.let {
                val value = it.groups[1]?.value
                println("Matched value: $value")  // Output: Matched value: gamer
            } ?: run {
                println("No match found.")
            }

            val defaultValue = """"&#120143; Conference"""

            strings.writeText(
                content.replace(regex) {
                    "${it.groupValues[1]}$defaultValue${it.groupValues[3]}"
                }
            )
        }

        println(duration)
    }
}
