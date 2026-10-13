/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.fieldReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "$PATCHES_DESCRIPTOR/feed/LimitFeedToFollowingProfiles;"

private object MainFeedHeaderMapFinderFingerprint : Fingerprint(
    strings = listOf("pagination_source", "FEED_REQUEST_SENT"),
    filters = listOf(
        // Main feed request headers.
        fieldAccess(type = "Ljava/util/Map;", opcode = Opcode.IGET_OBJECT),
    ),
)

/**
 * Main feed request constructor, where the request headers are set.
 */
private fun initMainFeedRequestFingerprint(headerField: FieldReference) = Fingerprint(
    definingClass = headerField.definingClass,
    name = "<init>",
    filters = listOf(fieldAccess(reference = headerField, opcode = Opcode.IPUT_OBJECT)),
)

@Suppress("unused")
val limitFeedToFollowingProfiles =
    bytecodePatch(
        name = "Limit feed to following profiles",
        description = "Filters the home feed to display only content from profiles you follow.",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // The header field is obfuscated and there is no easy way to identify it among all the class fields,
            // so it is found from a method that uses the map.
            val headerField = MainFeedHeaderMapFinderFingerprint.instructionMatches.first().fieldReference

            initMainFeedRequestFingerprint(headerField).let {
                val getHeaderIndex = it.instructionMatches.first().index
                val paramHeaderRegister = it.instructionMatches.first().getInstruction<TwoRegisterInstruction>().registerA

                it.method.addInstructions(
                    getHeaderIndex,
                    """
                    invoke-static { v$paramHeaderRegister }, $EXTENSION_CLASS_DESCRIPTOR->setFollowingHeader(Ljava/util/Map;)Ljava/util/Map;
                    move-result-object v$paramHeaderRegister
                """,
                )
            }

            enableSettings("limitFollowingFeed")
        }
    }
