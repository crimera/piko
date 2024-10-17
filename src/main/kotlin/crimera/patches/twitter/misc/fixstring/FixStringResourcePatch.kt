package crimera.patches.twitter.misc.fixstring

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import java.io.File

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

  fun replaceStringInFile(file: File) {
    val regex = Regex("""(<string\s+name="conference_default_title">)([^<]*)(<\/string>)""")
    val defaultValue = """"&#120143; Conference"""

    val content = file.readText()
    val matchResult = regex.find(content)
    matchResult?.let {
      val value = it.groups[2]?.value
      println("Matched value in ${file.name}: $value")
    } ?: run {
      println("No match found in ${file.name}.")
    }

    file.writeText(
      content.replace(regex) {
        "${it.groupValues[1]}$defaultValue${it.groupValues[3]}"
      }
    )
  }

  override fun execute(context: ResourceContext) {
    val locales = listOf("values", "values-en-rGB")

    val duration = measureExecutionTime {
      locales.forEach {
        locale ->
        val stringsFile = context["res/$locale/strings.xml"]
        if (stringsFile.exists()) {
          println("Processing $locale strings file")
          replaceStringInFile(stringsFile)
        } else {
          println("Strings file for $locale not found")
        }
      }
    }

    println(duration)
  }
}
