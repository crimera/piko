/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.customise.chatColors

import app.crimera.patches.instagram.misc.directMessage.markChatAsReadPatch.ThreadLongPressButtonActionFingerprint
import app.crimera.patches.instagram.misc.directMessage.markChatAsReadPatch.ThreadLongPressButtonStringListFingerprint
import app.crimera.patches.instagram.misc.directMessage.markChatAsReadPatch.ThreadLongPressButtonsEnumInitFingerprint
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeString
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * The keys of the theme fields the colors are written to, as the server names them. The extension
 * (ChatColors.java) uses these same keys, so a key added there has to be added here too.
 */
private val THEME_KEYS =
    listOf(
        "gradient_colors",
        "dark_gradient_colors",
        "light_theme_incoming_message_bubble_color",
        "dark_theme_incoming_message_bubble_color",
        "light_primary_text_message_from_me_color",
        "dark_primary_text_message_from_me_color",
        "light_primary_text_message_from_others_color",
        "dark_primary_text_message_from_others_color",
        "background_color",
        "dark_background_color",
        "fallback_color",
        "dark_fallback_color",
        "light_message_attribution_color",
        "dark_message_attribution_color",
        "light_theme_nav_bar_color",
        "dark_theme_nav_bar_color",
        "light_theme_nav_bar_subtitle_color",
        "dark_theme_nav_bar_subtitle_color",
        "composer_color",
        "dark_composer_color",
        "light_composer_border_color",
        "dark_composer_border_color",
        "light_input_placeholder_text_color",
        "dark_input_placeholder_text_color",
        "light_large_background_image_uri",
        "dark_large_background_image_uri",
        "light_large_background_video_uri",
        "dark_large_background_video_uri",
        "custom_background_drawable_url",
        "light_theme_background_drawable_resource_id",
        "dark_theme_background_drawable_resource_id",
    )

/**
 * The fields of a theme by the key the server sends them as. The theme writer reads a field that can be null, tests
 * it and then loads the key, so that field is the one read two instructions before the key. A number is written
 * without a test, with the key loaded right before it is read.
 */
context(context: BytecodePatchContext)
private fun themeFieldNames(idField: String): String {
    val code = ThemeJsonWriterFingerprint.method.instructions
    val names = linkedMapOf<String, String>()

    fun themeField(
        index: Int,
        opcode: Opcode,
    ) = code.getOrNull(index)
        ?.takeIf { it.opcode == opcode }
        ?.getReference<FieldReference>()
        ?.takeIf { it.definingClass == THEME_INFO_CLASS }

    code.forEachIndexed { index, instruction ->
        val isString = instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO
        if (!isString) return@forEachIndexed
        val key = instruction.getReference<StringReference>()?.string ?: return@forEachIndexed
        if (key !in THEME_KEYS) return@forEachIndexed
        val testedField =
            if (code.getOrNull(index - 1)?.opcode == Opcode.IF_EQZ) themeField(index - 2, Opcode.IGET_OBJECT) else null
        val field =
            testedField
                ?: themeField(index + 1, Opcode.IGET)
                ?: throw PatchException("Field of the chat theme key \"$key\" not found")
        names.putIfAbsent(key, field.name)
    }
    val missing = THEME_KEYS.filter { it !in names }
    if (missing.isNotEmpty()) throw PatchException("Fields of the chat theme not found: $missing")
    names["id"] = idField
    return names.entries.joinToString(";") { "${it.key}=${it.value}" }
}

/**
 * The hooks below name the parameters of a method by register, which is their index only while no wide parameter
 * (long or double, two registers each) comes before them.
 */
private fun MutableMethod.requireNoWideParameterBefore(parameterIndex: Int) {
    if (parameters.take(parameterIndex).any { it.type == "J" || it.type == "D" }) {
        throw PatchException("A wide parameter comes before parameter $parameterIndex of $name")
    }
}

/**
 * The menu of a chat picks the icon of each button in a switch on the ordinal of the button, and a button without a
 * case falls through it and gets none. The icon of the button added here is given on that way through.
 */
private fun MutableMethod.addMenuIcon() {
    val code = instructions
    val ordinalIndex =
        code.indices.singleOrNull { index ->
            code[index].getReference<MethodReference>()?.let {
                it.definingClass == "Ljava/lang/Enum;" && it.name == "ordinal"
            } == true &&
                code.getOrNull(index + 1)?.opcode == Opcode.MOVE_RESULT &&
                code.getOrNull(index + 2)?.opcode == Opcode.PACKED_SWITCH
        } ?: throw PatchException("Switch of the chat menu icons not found")
    val noIcon = code[ordinalIndex + 3]
    if (noIcon.opcode != Opcode.MOVE_OBJECT &&
        noIcon.opcode != Opcode.MOVE_OBJECT_FROM16 &&
        noIcon.opcode != Opcode.MOVE_OBJECT_16
    ) {
        throw PatchException("Chat menu buttons without an icon are not handled as expected")
    }
    val buttonRegister = code[ordinalIndex].registersUsed[0]
    val iconRegister = noIcon.registersUsed[0]
    // The icons are made into drawables with this call, so its first register is the context.
    val contextRegister =
        code.drop(ordinalIndex)
            .firstOrNull { instruction ->
                instruction.getReference<MethodReference>()?.let {
                    it.definingClass == "Landroid/content/Context;" && it.name == "getDrawable"
                } == true
            }?.registersUsed?.get(0)
            ?: throw PatchException("Context of the chat menu icons not found")
    if (buttonRegister > 15 || contextRegister > 15) {
        throw PatchException("Registers of the chat menu icons do not fit the call")
    }
    addInstructions(
        ordinalIndex + 4,
        """
        invoke-static {v$contextRegister, v$buttonRegister}, $EXTENSION_CLASS->menuIcon(Landroid/content/Context;Ljava/lang/Object;)Landroid/graphics/drawable/Drawable;
        move-result-object v$iconRegister
        """.trimIndent(),
    )
}

@Suppress("unused")
val chatColorsPatch =
    bytecodePatch(
        name = "Custom chat colors",
        description =
            "Adds options to change the colors of the message bubbles and the background of chats, " +
                "with a choice for each chat between them and the theme of the chat.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)

        execute {
            // The getter compares the id of the theme with the one of the shared default theme, so the
            // first string it reads from a theme is the id.
            val idField =
                ThreadThemeGetterFingerprint.method.instructions
                    .mapNotNull { it.takeIf { it.opcode == Opcode.IGET_OBJECT }?.getReference<FieldReference>() }
                    .first { it.definingClass == THEME_INFO_CLASS && it.type == "Ljava/lang/String;" }
                    .name
            ThemeFieldsExtensionFingerprint.changeString(THEME_FIELDS_SENTINEL, themeFieldNames(idField))

            // Instagram has one theme for a chat and reads it through this method everywhere, so what it
            // returns decides the colors of the bubbles, the text, the background and the rest of the chat.
            ThreadThemeGetterFingerprint.let { getter ->
                val method = getter.method
                // The key is kept in a field that is emptied when the chat's id or members change, and the method
                // below builds it again when it is needed, so the field itself can be null.
                val threadKeyGetter =
                    getter.classDef.methods.singleOrNull { it.returnType == THREAD_KEY_CLASS && it.parameters.isEmpty() }
                        ?: throw PatchException("Chat thread key getter not found")
                val sharedDefault =
                    method.instructions
                        .mapNotNull { it.takeIf { it.opcode == Opcode.SGET_OBJECT }?.getReference<FieldReference>() }
                        .first { it.type == THEME_INFO_CLASS }
                method.requireNoWideParameterBefore(method.parameters.size)
                val locals = method.implementation!!.registerCount - (method.parameters.size + 1)
                val returns =
                    method.instructions.withIndex()
                        .filter { it.value.opcode == Opcode.RETURN_OBJECT }
                        .map { it.index to it.value.registersUsed[0] }
                if (returns.isEmpty()) throw PatchException("Chat theme getter has no return")

                for ((returnIndex, resultRegister) in returns.sortedByDescending { it.first }) {
                    val scratchRegisters = (0 until locals).filter { it != resultRegister }.take(2)
                    if (scratchRegisters.size < 2) throw PatchException("Chat theme getter has no free registers")
                    val (keyRegister, sharedRegister) = scratchRegisters
                    if (maxOf(keyRegister, sharedRegister, resultRegister) > 15) {
                        throw PatchException("Registers of the chat theme getter do not fit the call")
                    }
                    // The return is replaced, not preceded, so a jump to it runs the call as well.
                    method.replaceInstruction(
                        returnIndex,
                        "invoke-virtual {p0}, ${threadKeyGetter.definingClass}->${threadKeyGetter.name}()$THREAD_KEY_CLASS",
                    )
                    method.addInstructions(
                        returnIndex + 1,
                        """
                        move-result-object v$keyRegister
                        sget-object v$sharedRegister, ${sharedDefault.definingClass}->${sharedDefault.name}:${sharedDefault.type}
                        invoke-static {v$keyRegister, v$resultRegister, v$sharedRegister}, $EXTENSION_CLASS->resolve(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v$resultRegister
                        check-cast v$resultRegister, $THEME_INFO_CLASS
                        return-object v$resultRegister
                        """.trimIndent(),
                    )
                }
            }

            // Instagram tints the camera button of the message composer with the color of the sent text, which is
            // for text. The tint is kept in the colors Instagram makes for the composer, and where it is stored the
            // extension gives back the one it should have, so every place that draws the button gets it.
            ComposerButtonColorsFingerprint.method.apply {
                val code = instructions
                val drawableIndex =
                    code.indexOfFirst {
                        it.getReference<MethodReference>()?.let { call ->
                            call.definingClass == "Landroid/widget/ImageView;" && call.name == "getDrawable"
                        } == true
                    }
                if (drawableIndex < 0) throw PatchException("Camera button of the composer not found")
                val tintIndex =
                    (drawableIndex until code.size).firstOrNull { code[it].opcode == Opcode.IGET }
                        ?: throw PatchException("Camera tint of the composer not found")
                val tintField =
                    code[tintIndex].getReference<FieldReference>()
                        ?: throw PatchException("Camera tint of the composer is not a field")
                val composerColors =
                    mutableClassDefBy(tintField.definingClass).methods.singleOrNull { it.name == "<init>" }
                        ?: throw PatchException("Constructor of the composer colors not found")
                composerColors.apply {
                    val storeIndex =
                        instructions.indexOfFirst {
                            it.opcode == Opcode.IPUT &&
                                it.getReference<FieldReference>()?.let { field ->
                                    field.definingClass == tintField.definingClass && field.name == tintField.name
                                } == true
                        }
                    if (storeIndex < 0) throw PatchException("Camera tint is not stored by the composer colors")
                    val tintRegister = instructions[storeIndex].registersUsed[0]
                    if (tintRegister > 15) throw PatchException("Register of the camera tint does not fit the call")
                    addInstructions(
                        storeIndex,
                        """
                        invoke-static {v$tintRegister}, $EXTENSION_CLASS->composerIconColor(I)I
                        move-result v$tintRegister
                        """.trimIndent(),
                    )
                }
            }

            // The menu of a chat in the inbox gets one more button, opening the choice for that chat. The
            // menu only lists buttons Instagram already has, so one it never shows in this menu is used and
            // given its own label when the labels are made.
            ChatMenuOpenFingerprint.method.apply {
                addMenuIcon()
                val listParameterIndex = parameters.indexOfFirst { it.type == "Ljava/util/List;" }
                if (listParameterIndex < 0) throw PatchException("List parameter of $name not found")
                requireNoWideParameterBefore(listParameterIndex)
                // The register of the list is far from v0, so it is copied to the free v0 to be passed on.
                addInstructions(
                    0,
                    """
                    move-object/from16 v0, p$listParameterIndex
                    invoke-static {v0}, $EXTENSION_CLASS->menuOpening(Ljava/util/List;)Ljava/util/List;
                    move-result-object v0
                    move-object/from16 p$listParameterIndex, v0
                    """.trimIndent(),
                )
            }

            ThreadLongPressButtonStringListFingerprint.method.apply {
                val listParameterIndex = parameters.indexOfFirst { it.type == "Ljava/util/List;" }
                if (listParameterIndex < 0) throw PatchException("List parameter of $name not found")
                requireNoWideParameterBefore(listParameterIndex)
                val returns = instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }
                if (returns.isEmpty()) throw PatchException("Chat menu labels have no return")
                for (indexed in returns.sortedByDescending { it.index }) {
                    val labelsRegister = indexed.value.registersUsed[0]
                    // The loop of the labels jumps to its return, so the return is replaced, not preceded.
                    replaceInstruction(
                        indexed.index,
                        "invoke-static {v$labelsRegister, p$listParameterIndex}, " +
                            "$EXTENSION_CLASS->menuLabels([Ljava/lang/String;Ljava/util/List;)[Ljava/lang/String;",
                    )
                    addInstructions(
                        indexed.index + 1,
                        """
                        move-result-object v$labelsRegister
                        return-object v$labelsRegister
                        """.trimIndent(),
                    )
                }
            }

            ThreadLongPressButtonActionFingerprint.apply {
                val contextField = classDef.fields.first { it.type == "Landroid/content/Context;" }
                val buttonType = ThreadLongPressButtonsEnumInitFingerprint.classDef.type
                method.apply {
                    val buttonParameterIndex = parameters.indexOfFirst { it.type == buttonType } + 1
                    val keyParameterIndex = parameters.indexOfFirst { it.type == THREAD_KEY_CLASS } + 1
                    if (buttonParameterIndex == 0 || keyParameterIndex == 0) {
                        throw PatchException("Button or thread key parameter of the chat menu action not found")
                    }
                    // The code below takes v0-v3 and counts the parameters from p1, as p0 is the method's object.
                    val parameterRegisters = 1 + parameters.sumOf { if (it.type == "J" || it.type == "D") 2 else 1 }
                    val locals = implementation!!.registerCount - parameterRegisters
                    if (locals < 4) throw PatchException("Chat menu action has fewer than four free registers")
                    requireNoWideParameterBefore(maxOf(buttonParameterIndex, keyParameterIndex) - 1)
                    // Registers are fixed because the code goes first and nothing is live yet.
                    addInstructionsWithLabels(
                        0,
                        """
                        move-object/from16 v0, p0
                        iget-object v1, v0, $contextField
                        move-object/from16 v2, p$buttonParameterIndex
                        move-object/from16 v3, p$keyParameterIndex
                        invoke-static {v1, v2, v3}, $EXTENSION_CLASS->onMenuButton(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/Object;)Z
                        move-result v0
                        if-eqz v0, :piko_chat_colors
                        return-void
                        """.trimIndent(),
                        ExternalLabel("piko_chat_colors", getInstruction(0)),
                    )
                }
            }

            enableSettings("chatColors")
        }
    }
