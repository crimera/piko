/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.trackDataIntf

import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val trackDataIntfEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of track data interface",
    ) {
        execute {
            val trackDataFromMusicInfoMethodName = TrackDataFromMusicInfoMethodFingerprint.method.name
            GetTrackDataExtension.changeFirstString(trackDataFromMusicInfoMethodName)

            mutableClassDefBy(IMMUTABLE_PANDO_AUDIO_FILTER_INFO_CLASS_DESCRIPTOR)
                .methods
                .last {
                    it.returnType ==
                        "Lcom/facebook/pando/TreeUpdaterJNI;"
                }.apply {
                    val mappingMethodInvokeInstruction = getInstruction(indexOfFirstInstruction(Opcode.INVOKE_VIRTUAL))
                    GetMappingsExtension.changeFirstString(mappingMethodInvokeInstruction.methodExtractor().name)
                }
        }
    }
