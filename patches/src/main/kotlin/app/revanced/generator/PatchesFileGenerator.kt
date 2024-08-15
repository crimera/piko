package app.revanced.generator

import app.revanced.patcher.patch.Patch

internal interface PatchesFileGenerator {
    fun generate(patches: Set<Patch<*>>)
}
