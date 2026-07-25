package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.XLiteSettingsCategory
import app.crimera.patches.xlite.settings.toggleSetting
import app.crimera.patches.xlite.settings.xLiteSettingsContributionPatch
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private const val CONCURRENT_HASH_MAP_DESCRIPTOR = "Ljava/util/concurrent/ConcurrentHashMap;"
private const val TIMELINE_TYPE_DESCRIPTOR = "Lcom/x/models/timelines/TimelineType;"
private const val TIMELINE_POSITION_STORE_DESCRIPTOR =
    "Lapp/morphe/extension/xlite/timeline/TimelineScrollPositionStore;"

private object XLiteScrollPositionStoreClassFingerprint : Fingerprint(
    name = "<init>",
    parameters = listOf(),
    returnType = "V",
    custom = { _, classDef ->
        val getters =
            classDef.methods.filter { method ->
                method.parameterTypes.map(CharSequence::toString) ==
                    listOf(TIMELINE_TYPE_DESCRIPTOR) &&
                    method.returnType != "V"
            }
        getters.size == 1 &&
            classDef.methods.any { method ->
                method.parameterTypes.map(CharSequence::toString) ==
                    listOf(TIMELINE_TYPE_DESCRIPTOR, getters.single().returnType) &&
                    method.returnType == "V"
            }
    },
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = CONCURRENT_HASH_MAP_DESCRIPTOR,
            ),
        ),
)

private object XLiteSaveScrollPositionFingerprint : Fingerprint(
    classFingerprint = XLiteScrollPositionStoreClassFingerprint,
    parameters = listOf(TIMELINE_TYPE_DESCRIPTOR, "L"),
    returnType = "V",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = CONCURRENT_HASH_MAP_DESCRIPTOR,
                name = "put",
                parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
                returnType = "Ljava/lang/Object;",
            ),
        ),
)

private val restoreTimelinePosition =
    toggleSetting(
        id = "xlite.timeline.restore_position",
        titleResourceName = "piko_xlite_restore_timeline_position_title",
        summaryResourceName = "piko_xlite_restore_timeline_position_summary",
        order = 150,
        defaultValue = true,
        rebootApp = true,
    )

private val restoreTimelinePositionSettingsPatch =
    xLiteSettingsContributionPatch {
        category(XLiteSettingsCategory.TIMELINE) {
            add(restoreTimelinePosition)
        }
    }

@Suppress("unused")
val restoreTimelinePositionPatch =
    bytecodePatch(
        name = "X-Lite: Restore timeline position",
        description = "Persists the timeline index and offset, then restores them after the app process restarts.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)
        dependsOn(restoreTimelinePositionSettingsPatch)

        execute {
            val storeMatches = XLiteScrollPositionStoreClassFingerprint.matchAll()
            if (storeMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite scroll-position store, found ${storeMatches.size}: " +
                        storeMatches.joinToString { it.originalMethod.toString() },
                )
            }
            storeMatches.single().method.apply {
                val returnInstruction =
                    instructions.singleOrNull { it.opcode == Opcode.RETURN_VOID }
                        ?: throw PatchException(
                            "X-Lite scroll-position store constructor return was not found",
                        )
                addInstruction(
                    returnInstruction.location.index,
                    "invoke-static {p0}, $TIMELINE_POSITION_STORE_DESCRIPTOR->restore(Ljava/lang/Object;)V",
                )
            }

            val saveMatches = XLiteSaveScrollPositionFingerprint.matchAll()
            if (saveMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite save-scroll-position method, found ${saveMatches.size}: " +
                        saveMatches.joinToString { it.originalMethod.toString() },
                )
            }
            saveMatches.single().method.addInstruction(
                0,
                "invoke-static {p1, p2}, " +
                    "$TIMELINE_POSITION_STORE_DESCRIPTOR->save(" +
                    "Ljava/lang/Enum;Ljava/lang/Object;)V",
            )
        }
    }
