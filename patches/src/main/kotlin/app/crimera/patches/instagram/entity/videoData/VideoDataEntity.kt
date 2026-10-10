/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.videoData

import app.crimera.utils.changeFirstString
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

val videoDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of Video data",
    ) {
        execute {
            val mapper = VideoVersionMapperFingerprint.matchAll(1..1).single().method
            val videoType = mapper.parameterTypes.single()
            val videoRegister = mapper.implementation!!.registerCount - 1
            val getters = listOf(
                Triple("height", "Ljava/lang/Integer;", VideoHeightExtensionFingerprint),
                Triple("width", "Ljava/lang/Integer;", VideoWidthExtensionFingerprint),
                Triple("type", "Ljava/lang/Integer;", VideoCodecExtensionFingerprint),
                Triple("url", "Ljava/lang/String;", VideoUrlExtensionFingerprint),
            )

            for ((key, returnType, extension) in getters) {
                val keyIndex = mapper.instructions.mapIndexedNotNull { index, instruction ->
                    val string = (instruction as? ReferenceInstruction)?.reference as? StringReference
                    index.takeIf { string?.string == key }
                }.singleOrNull() ?: throw PatchException("Expected one video $key mapping")
                val instruction = mapper.instructions.getOrNull(keyIndex + 1)
                val getter = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                if (instruction?.opcode != Opcode.INVOKE_INTERFACE || getter == null ||
                    getter.definingClass != videoType || getter.parameterTypes.isNotEmpty() ||
                    getter.returnType != returnType || instruction.registersUsed.singleOrNull() != videoRegister
                ) {
                    throw PatchException("Could not resolve video $key getter")
                }
                extension.changeFirstString(getter.name)
            }
        }
    }
