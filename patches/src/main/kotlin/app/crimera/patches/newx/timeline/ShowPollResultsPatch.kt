/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.POLL_RESULTS_FORMATTER_DESCRIPTOR
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val MAP_DESCRIPTOR = "Ljava/util/Map;"

/**
 * Resolves the small poll binding-value helper rather than the Compose renderer. The helper is a
 * stable leaf across the declared NewX targets and returns the exact String consumed by the UI.
 */
private object PollBindingValueFingerprint : Fingerprint(
    definingClass = "Lcom/x/cards/impl/poll/",
    parameters = listOf("I", STRING_DESCRIPTOR, MAP_DESCRIPTOR),
    returnType = STRING_DESCRIPTOR,
    filters = listOf(string("choice")),
    custom = { method, _ -> AccessFlags.STATIC.isSet(method.accessFlags) },
)

@Suppress("unused")
val newXShowPollResultsPatch =
    bytecodePatch(
        name = "NewX: Show poll results",
        description = "Adds an option to show NewX poll results without voting.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch)

        newXToggle(
            id = "newx.content.show_poll_results",
            category = Categories.CONTENT,
            strings = settingStrings("piko_newx_show_poll_results"),
            order = 350,
            defaultValue = false,
        )

        execute {
            val matches = PollBindingValueFingerprint.scopedMatchAllOrNull().orEmpty()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX poll binding-value helper, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            val method = matches.single().method
            if (method.instructions.isEmpty()) {
                throw PatchException(
                    "NewX poll binding-value helper has no instructions: ${matches.single().originalMethod}",
                )
            }
            if (method.p0Register == 0) {
                throw PatchException(
                    "NewX poll binding-value helper has no local scratch register: ${matches.single().originalMethod}",
                )
            }

            // v0 holds the formatted label, `p0..p2` are the three helper parameters. The old plain
            // insert kept incoming labels on the original first instruction, so a path that branched
            // to it still skipped the whole block.
            method.insertHook(
                index = 0,
                relocateBranchTargets = false,
            ) {
                invokeStatic(
                    methodReference("$POLL_RESULTS_FORMATTER_DESCRIPTOR->formatLabel(ILjava/lang/String;Ljava/util/Map;)Ljava/lang/String;"),
                    method.p0Register,
                    method.p0Register + 1,
                    method.p0Register + 2,
                )
                moveResult(0, STRING_DESCRIPTOR)
                ifEqz(0, Target.Original)
                returnObject(0)
            }
        }
    }
