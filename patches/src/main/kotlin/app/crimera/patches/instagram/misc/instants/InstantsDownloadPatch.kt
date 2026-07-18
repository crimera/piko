/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.instants

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.INSTANTS_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeStringAt
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val INSTANTS_DOWNLOAD_HOOK_DESCRIPTOR = "$INSTANTS_DESCRIPTOR/InstantsDownloadHook;"

/** The obfuscated names InstantsDownloadHook's pull path reflects on, recovered from the target dex. */
private data class DownloadNames(
    val vmStateField: String,
    val stateItemMethod: String,
    val itemMediaField: String,
)

/** Rewrites the InstantsDownloadHook placeholder field whose seed *value* is [placeholder]. Matches
 *  on sentinel text, not index. A missing/duplicated sentinel is a hook/resolver desync — our bug —
 *  so it throws rather than degrading. */
context(patchContext: BytecodePatchContext)
private fun Fingerprint.replacePlaceholder(
    placeholder: String,
    value: String,
) {
    val hits =
        method.instructions
            .filter { it.opcode == Opcode.CONST_STRING }
            .withIndex()
            .filter { (_, insn) -> (insn as ReferenceInstruction).reference.toString() == placeholder }

    if (hits.size != 1) {
        throw PatchException(
            "InstantsDownloadHook: expected exactly one \"$placeholder\" placeholder, " +
                "found ${hits.size}. The hook's placeholder fields and instantsDownloadPatch are out of sync.",
        )
    }

    changeStringAt(hits.single().index, value)
}

/**
 * "Save Instants": passively captures each view-once Instant (quicksnap) as it's viewed, recording
 * its CDN links into a local store so they can be re-viewed and downloaded afterwards from the
 * "Saved Instants" screen (see InstantsDownloadHook / InstantsVaultActivity). Instants change on tap
 * and vanish after viewing, so capture-on-view is more reliable than a per-instant button.
 *
 * Anchor: the viewer ViewModel's current-item handler — a static `(vm)V` method that reads
 * `vm.<stateField>.getValue().<itemMethod>()` and the resulting item's User, and carries the literal
 * "-100" sentinel-id check (verified via baksmali against stock v435; see
 * piko-re/re-notes/instants-download-viewer.md). From that one method the whole
 * VM → state → item → Media chain is recovered, and the resolved VM's methods are where the capture
 * hook is injected.
 *
 * Installs all-or-nothing: unless every obfuscated name resolves, no hook, capture or toggle ships.
 * An Instagram-side miss disables only this feature, never the session. No last-known-good fallback —
 * obfuscated names rotate on every R8 run.
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
            // Step 1: recover the pull path + its injection method up front — resolution gates the
            // whole feature. Any miss installs nothing and returns.
            val (names, handler) =
                runCatching { resolveDownloadNames() }.getOrElse { error ->
                    println(
                        "[piko] Download Instants: DISABLED for this build — could not resolve the " +
                            "quicksnap viewer flow: ${error.message}",
                    )
                    return@execute
                }

            // Steps 2-4: install the capture hook, bake the names, then expose the toggle. Order
            // matters — the marker is written last and enableSettings runs only after it, so any
            // failure leaves the marker unset (capture no-ops) and the toggle never appears.
            runCatching {
                // Inject the capture hook into every non-static instance method of the viewer VM
                // (p0 = this). The anchor handler (A05) fires only on certain interactions, so —
                // like the camera feature does for its VM — cover all instance methods; whichever
                // runs while the viewer is on screen captures the current instant. Anchored on the
                // resolved VM class only (§11). Also inject the static handler itself (p0 = the VM
                // parameter) so a first-frame handler call is covered too.
                handler.addInstruction(
                    0,
                    "invoke-static {p0}, $INSTANTS_DOWNLOAD_HOOK_DESCRIPTOR->noteViewerVm(Ljava/lang/Object;)V",
                )

                val vmType = handler.definingClass
                val stashed =
                    mutableClassDefBy { it.type == vmType }.methods.filter { m ->
                        !AccessFlags.STATIC.isSet(m.accessFlags) &&
                            m.name != "<init>" &&
                            m.implementation != null
                    }.count { m ->
                        runCatching {
                            m.addInstruction(
                                0,
                                "invoke-static {p0}, $INSTANTS_DOWNLOAD_HOOK_DESCRIPTOR->noteViewerVm(Ljava/lang/Object;)V",
                            )
                        }.isSuccess
                    }

                if (stashed == 0) {
                    throw PatchException(
                        "viewer ViewModel: could not stash the live VM in any instance method.",
                    )
                }

                InstantsDownloadNamesFingerprint.apply {
                    replacePlaceholder("piko.instants.dl.vmStateField", names.vmStateField)
                    replacePlaceholder("piko.instants.dl.stateItemMethod", names.stateItemMethod)
                    replacePlaceholder("piko.instants.dl.itemMediaField", names.itemMediaField)

                    // Commit point — must stay last.
                    replacePlaceholder("piko.instants.dl.marker", "1")
                }

                enableSettings("instantsDownload")
            }.onFailure { error ->
                println(
                    "[piko] Download Instants: DISABLED for this build — hook installation failed: " +
                        "${error.message}",
                )
            }
        }
    }

/** True for the viewer VM's current-item handler: a static `(Self)V` method carrying the literal
 *  "-100" id check and a no-arg `getValue()` call. Tight enough to match only that one method. */
private fun isViewerHandler(
    classType: String,
    m: Method,
): Boolean {
    if (!AccessFlags.STATIC.isSet(m.accessFlags)) return false
    if (m.returnType != "V") return false
    val params = m.parameterTypes
    if (params.size != 1 || params[0].toString() != classType) return false
    val insns = m.implementation?.instructions?.toList() ?: return false

    val hasMarker =
        insns.any {
            it.opcode == Opcode.CONST_STRING &&
                (it as ReferenceInstruction).reference.toString() == "-100"
        }
    if (!hasMarker) return false

    return insns.any {
        it.opcode == Opcode.INVOKE_INTERFACE &&
            ((it as ReferenceInstruction).reference as? MethodReference)?.let { r ->
                r.name == "getValue" && r.parameterTypes.isEmpty()
            } == true
    }
}

/**
 * Recovers the obfuscated names the download path needs, from the target dex only, plus the method to
 * inject the live-VM capture hook into.
 *
 * From the anchor handler we read: the VM state field (the iget feeding getValue), the state's
 * item-getter (the no-arg object invoke on the getValue result), and the item's Media field. Register
 * dataflow is verified at each step, so R8 reordering doesn't mislead us. Throws on any miss.
 */
context(patchContext: BytecodePatchContext)
private fun resolveDownloadNames(): Pair<DownloadNames, MutableMethod> {
    val vmClass =
        patchContext.mutableClassDefBy { cd -> cd.methods.any { isViewerHandler(cd.type, it) } }
    val handler = vmClass.methods.first { isViewerHandler(vmClass.type, it) }
    val insns = handler.instructions.toList()

    fun destReg(i: Int) = (insns[i] as OneRegisterInstruction).registerA

    // getValue() call on the state-flow field's value.
    val getValueIdx =
        insns.indices.first { i ->
            insns[i].opcode == Opcode.INVOKE_INTERFACE &&
                ((insns[i] as ReferenceInstruction).reference as? MethodReference)?.let {
                    it.name == "getValue" && it.parameterTypes.isEmpty()
                } == true
        }
    val stateHolderReg = insns[getValueIdx].registersUsed.first()

    // VM state field = the nearest preceding iget-object that wrote the getValue receiver, defined on
    // the VM itself.
    val vmStateField =
        (getValueIdx - 1 downTo 0).firstNotNullOfOrNull { i ->
            if (!insns[i].opcode.name.startsWith("iget-object", ignoreCase = true)) return@firstNotNullOfOrNull null
            if (destReg(i) != stateHolderReg) return@firstNotNullOfOrNull null
            val fr = (insns[i] as ReferenceInstruction).reference as FieldReference
            if (fr.definingClass != vmClass.type) return@firstNotNullOfOrNull null
            fr.name
        } ?: throw PatchException("no VM field feeds the viewer state getValue() call.")

    // The getValue result register, then the no-arg object-returning invoke on it = the item getter.
    val stateResultReg =
        (getValueIdx + 1 until insns.size).firstOrNull { insns[it].opcode == Opcode.MOVE_RESULT_OBJECT }
            ?.let { destReg(it) }
            ?: throw PatchException("viewer state getValue() result is never captured.")

    val itemGetter =
        (getValueIdx + 1 until insns.size).firstNotNullOfOrNull { i ->
            if (insns[i].opcode != Opcode.INVOKE_VIRTUAL) return@firstNotNullOfOrNull null
            if (insns[i].registersUsed.firstOrNull() != stateResultReg) return@firstNotNullOfOrNull null
            val mr = (insns[i] as ReferenceInstruction).reference as MethodReference
            if (mr.parameterTypes.isNotEmpty() || !mr.returnType.startsWith("L")) return@firstNotNullOfOrNull null
            mr
        } ?: throw PatchException("no no-arg item getter called on the viewer state.")

    val itemType = itemGetter.returnType

    // Media field on the item — must be unambiguous, or we'd read the wrong one.
    val mediaFields =
        patchContext.mutableClassDefBy { it.type == itemType }.fields.filter { it.type == MEDIA_CLASS_NAME }
    val itemMediaField =
        when (mediaFields.size) {
            1 -> mediaFields.single().name
            else -> throw PatchException(
                "expected exactly one $MEDIA_CLASS_NAME field on the viewer item ($itemType), " +
                    "found ${mediaFields.size}.",
            )
        }

    return DownloadNames(
        vmStateField = vmStateField,
        stateItemMethod = itemGetter.name,
        itemMediaField = itemMediaField,
    ) to handler
}
