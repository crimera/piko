/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.instants

import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.INSTANTS_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val INSTANTS_DOWNLOAD_HOOK_DESCRIPTOR = "$INSTANTS_DESCRIPTOR/InstantsDownloadHook;"

/**
 * "Save Instants": records each view-once Instant so it can be re-viewed and downloaded afterwards.
 *
 * The hook goes on the constructor of the app's per-instant item, which takes that instant's Media
 * as its first argument — so every instant is caught as it is built, and the hook is handed the
 * Media rather than having to reach for it.
 */
@Suppress("unused")
val instantsDownloadPatch =
    bytecodePatch(
        name = "Save Instants",
        description = "Captures view-once Instants as you view them so you can re-view and download them later.",
    ) {
        dependsOn(settingsPatch, instantsDownloadResourcePatch, decoderEntity)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            runCatching {
                quickSnapTypes = quickSnapReferencedTypes()

                // p0 is the item being constructed, p1 its Media.
                InstantItemConstructorFingerprint.method.addInstruction(
                    0,
                    "invoke-static {p1}, $INSTANTS_DOWNLOAD_HOOK_DESCRIPTOR->noteInstantMedia(Ljava/lang/Object;)V",
                )
                enableSettings("instantsDownload")
            }.onFailure { error ->
                println(
                    "[piko] Save Instants: DISABLED for this build — could not resolve the " +
                        "quicksnap item: ${error.message}",
                )
            }
        }
    }

private fun ClassDef.referencedTypes(): Sequence<String> =
    methods
        .asSequence()
        .flatMap { it.implementation?.instructions?.asSequence() ?: emptySequence() }
        .mapNotNull { (it as? ReferenceInstruction)?.reference }
        .flatMap { reference ->
            when (reference) {
                is TypeReference -> sequenceOf(reference.type)
                is FieldReference -> sequenceOf(reference.definingClass, reference.type)
                is MethodReference ->
                    sequenceOf(reference.definingClass, reference.returnType) +
                        reference.parameterTypes.asSequence().map { it.toString() }
                else -> emptySequence()
            }
        }

/** Types touched by the classes built with a quicksnap repository — the feature's own corner. */
context(patchContext: BytecodePatchContext)
private fun quickSnapReferencedTypes(): Set<String> {
    val types = mutableSetOf<String>()
    patchContext.classDefForEach { classDef ->
        val usesRepository =
            classDef.methods.any { method ->
                method.parameterTypes.any { it.toString() == QUICK_SNAP_REPOSITORY_CLASS }
            }
        if (usesRepository) types += classDef.referencedTypes()
    }
    return types
}
