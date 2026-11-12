/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.clientversion

import app.crimera.patches.twitter.misc.extension.sharedExtensionPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption
import app.morphe.patcher.patch.stringOption
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION_CLASS_DESCRIPTOR = "$PATCHES_DESCRIPTOR/clientversion/SpoofClientVersionPatch;"

// X Chat's network stack (com.x): AppInfo, header client info and user agent provider.
private const val APP_INFO_INTERFACE = "Lcom/x/common/api/e;"
private const val CLIENT_INFO_INTERFACE = "Lcom/x/common/api/v;"
private const val USER_AGENT_INTERFACE = "Lcom/x/common/api/t;"

// The app's own version name as compiled into the code ("12.19.1-release.0").
private val APP_VERSION_NAME = Regex("""^\d+\.\d+\.\d+-release\.\d+$""")
private val USER_AGENT_VERSION = Regex("""^/\d+\.\d+\.\d+-release\.\d+ \($""")
private val VERSION_CODES = 300_000_000..399_999_999

private fun isConstString(opcode: Opcode) = opcode == Opcode.CONST_STRING || opcode == Opcode.CONST_STRING_JUMBO

@Suppress("unused")
val spoofClientVersionPatch =
    bytecodePatch(
        name = "Spoof client version",
        description = "Reports a newer app version to X Chat's servers, which refuse old versions with " +
            "\"Update Required\" (ClientRequiresUpdate). The rest of the app keeps reporting its real version, " +
            "and its user agent carries the real version code again instead of the one changed for Play Store updates.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(sharedExtensionPatch)

        val spoofedVersion by stringOption(
            key = "spoofedVersion",
            default = "12.41.0-prod.01",
            title = "Reported version name",
            description = "Version name X Chat sees instead of the app's own.",
            required = true,
        )

        val spoofedVersionCode by intOption(
            key = "spoofedVersionCode",
            default = 312410001,
            title = "Reported version code",
            description = "Version code X Chat sees. It must belong to the reported version name.",
            required = true,
        )

        execute {
            val version = spoofedVersion!!
            val versionCode = spoofedVersionCode!!
            var realVersionCode: Int? = null

            // 1. com.x AppInfo and client info: version name (and AppInfo's version code).
            var replaced = 0
            classDefForEach { classDef ->
                val appInfo = APP_INFO_INTERFACE in classDef.interfaces
                if (!appInfo && CLIENT_INFO_INTERFACE !in classDef.interfaces) return@classDefForEach
                val targets = classDef.methods.flatMap { method ->
                    method.implementation?.instructions?.mapIndexedNotNull { index, instruction ->
                        val isVersionName = isConstString(instruction.opcode) &&
                            APP_VERSION_NAME.matches(((instruction as ReferenceInstruction).reference as StringReference).string)
                        val code = (instruction as? NarrowLiteralInstruction)?.narrowLiteral
                        val isVersionCode = appInfo && method.returnType == "I" && code != null && code in VERSION_CODES
                        if (isVersionCode) realVersionCode = code
                        if (isVersionName || isVersionCode) Triple(method, index, isVersionName) else null
                    } ?: emptyList()
                }
                if (targets.isEmpty()) return@classDefForEach
                val mutableClass = mutableClassDefBy(classDef.type)
                targets.forEach { (method, index, isVersionName) ->
                    val mutableMethod = mutableClass.methods.first {
                        it.name == method.name && it.returnType == method.returnType && it.parameterTypes == method.parameterTypes
                    }
                    val register = mutableMethod.getInstruction<OneRegisterInstruction>(index).registerA
                    mutableMethod.replaceInstruction(
                        index,
                        if (isVersionName) "const-string/jumbo v$register, \"$version\"" else "const v$register, $versionCode",
                    )
                    replaced++
                }
            }
            if (replaced < 2) throw PatchException("X Chat client info not found ($replaced replacements)")
            val legacyVersionCode = realVersionCode ?: throw PatchException("App version code not found")

            // 2. com.x user agent provider: it reuses the legacy user agent, so rewrite its version there.
            var userAgents = 0
            classDefForEach { classDef ->
                if (USER_AGENT_INTERFACE !in classDef.interfaces) return@classDefForEach
                val method = classDef.methods.firstOrNull {
                    it.name == "a" && it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;"
                } ?: return@classDefForEach
                val usesLegacyUserAgent = method.implementation?.instructions?.any {
                    ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "toString"
                } == true
                if (!usesLegacyUserAgent) return@classDefForEach
                val mutableMethod = mutableClassDefBy(classDef.type).methods.first {
                    it.name == method.name && it.parameterTypes.isEmpty() && it.returnType == method.returnType
                }
                mutableMethod.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }.reversed()
                    .forEach { (index, instruction) ->
                        val register = (instruction as OneRegisterInstruction).registerA
                        mutableMethod.addInstructions(
                            index,
                            """
                            invoke-static {v$register}, $EXTENSION_CLASS_DESCRIPTOR->userAgent(Ljava/lang/String;)Ljava/lang/String;
                            move-result-object v$register
                            """,
                        )
                    }
                userAgents++
            }
            if (userAgents != 1) throw PatchException("Expected one X Chat user agent provider, found $userAgents")

            // 3. Extension values come from the options.
            mutableClassDefBy(EXTENSION_CLASS_DESCRIPTOR).methods.forEach { method ->
                when (method.name) {
                    "version" -> method.replaceInstruction(0, "const-string/jumbo v0, \"$version\"")
                    "versionCode" -> method.replaceInstruction(0, "const v0, $versionCode")
                }
            }

            // 4. Legacy user agent: report the app's real version code, not the maximum value
            //    set to disable Play Store updates.
            restoreLegacyUserAgentVersionCode(legacyVersionCode)

            // 5. Legacy client headers: X Chat requests that go through the legacy stack
            //    get the reported version too, every other request keeps the real one.
            hookLegacyClientHeaders()
        }
    }

context(context: BytecodePatchContext)
private fun hookLegacyClientHeaders() {
    var hooked = 0
    context.classDefForEach { classDef ->
        classDef.methods.forEach { method ->
            if (method.parameterTypes.firstOrNull()?.toString() != "Ljava/net/URI;" || method.returnType != "V") return@forEach
            val instructions = method.implementation?.instructions?.toList() ?: return@forEach
            val strings = instructions.mapNotNull {
                if (isConstString(it.opcode)) ((it as ReferenceInstruction).reference as StringReference).string else null
            }
            if ("X-Twitter-Client-Version" !in strings || "User-Agent" !in strings) return@forEach

            val versionIndex = instructions.indexOfFirst {
                isConstString(it.opcode) &&
                    APP_VERSION_NAME.matches(((it as ReferenceInstruction).reference as StringReference).string)
            }
            val userAgentIndex = instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_VIRTUAL &&
                    ((it as ReferenceInstruction).reference as MethodReference).name == "toString"
            } + 1
            if (versionIndex < 0 || userAgentIndex <= 0 || instructions[userAgentIndex].opcode != Opcode.MOVE_RESULT_OBJECT) {
                throw PatchException("Unexpected legacy client header builder in ${classDef.type}")
            }

            val mutableMethod = context.mutableClassDefBy(classDef.type).methods.first {
                it.name == method.name && it.returnType == method.returnType && it.parameterTypes == method.parameterTypes
            }
            val uriRegister = mutableMethod.implementation!!.registerCount - mutableMethod.parameterTypes.size
            // Insert from the bottom up so earlier indices stay valid.
            listOf(
                versionIndex to (mutableMethod.getInstruction<OneRegisterInstruction>(versionIndex).registerA to "legacyVersion"),
                userAgentIndex to (mutableMethod.getInstruction<OneRegisterInstruction>(userAgentIndex).registerA to "legacyUserAgent"),
            ).sortedByDescending { it.first }.forEach { (index, value) ->
                val (register, hook) = value
                mutableMethod.addInstructions(
                    index + 1,
                    """
                    invoke-static {v$register}, $EXTENSION_CLASS_DESCRIPTOR->$hook(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$register
                    """,
                )
            }
            mutableMethod.addInstructions(
                0,
                "invoke-static {v$uriRegister}, $EXTENSION_CLASS_DESCRIPTOR->onRequest(Ljava/net/URI;)V",
            )
            hooked++
        }
    }
    if (hooked != 1) throw PatchException("Expected one legacy client header builder, found $hooked")
}

context(context: BytecodePatchContext)
private fun restoreLegacyUserAgentVersionCode(versionCode: Int) {
    var found = 0
    context.classDefForEach { classDef ->
        classDef.methods.forEach { method ->
            val instructions = method.implementation?.instructions?.toList() ?: return@forEach
            val start = instructions.indexOfFirst {
                isConstString(it.opcode) &&
                    USER_AGENT_VERSION.matches(((it as ReferenceInstruction).reference as StringReference).string)
            }
            if (start < 0) return@forEach
            val index = (start + 1 until minOf(start + 12, instructions.size)).firstOrNull {
                val field = (instructions[it] as? ReferenceInstruction)?.reference as? FieldReference
                instructions[it].opcode == Opcode.IGET &&
                    field?.definingClass == "Landroid/content/pm/PackageInfo;" && field.name == "versionCode"
            } ?: throw PatchException("User agent version code not found in ${classDef.type}")
            val mutableMethod = context.mutableClassDefBy(classDef.type).methods.first {
                it.name == method.name && it.returnType == method.returnType && it.parameterTypes == method.parameterTypes
            }
            val register = mutableMethod.getInstruction<TwoRegisterInstruction>(index).registerA
            mutableMethod.replaceInstruction(index, "const v$register, $versionCode")
            found++
        }
    }
    if (found != 1) throw PatchException("Expected one legacy user agent builder, found $found")
}
