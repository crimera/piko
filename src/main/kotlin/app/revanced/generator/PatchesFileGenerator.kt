package app.revanced.generator

import app.revanced.patcher.PatchSet

internal interface PatchesFileGenerator {
    fun generate(patches: PatchSet)
}