package app.revanced.generator

import app.revanced.patcher.PatchBundleLoader
import java.io.File

internal fun main() = PatchBundleLoader.Jar(
    File("build/libs/").listFiles { it -> it.name.endsWith(".jar") }!!.first(),
).also { loader ->
    if (loader.isEmpty()) throw IllegalStateException("No patches found")
}.let { bundle ->
    arrayOf(JsonPatchesFileGenerator()).forEach { generator -> generator.generate(bundle) }
}