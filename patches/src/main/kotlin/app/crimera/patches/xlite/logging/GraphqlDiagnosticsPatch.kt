package app.crimera.patches.xlite.logging

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.action
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.input
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.toggle
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val GRAPHQL_DIAGNOSTICS_CLASS = "Lapp/morphe/extension/xlite/logging/GraphqlDiagnostics"
private const val GRAPHQL_DIAGNOSTICS_DESCRIPTOR = "$GRAPHQL_DIAGNOSTICS_CLASS;"

/** Shared thrown-exception helper: `(String, Throwable) -> object result`. */
internal object GraphqlFailureFingerprint : Fingerprint(
    strings = listOf("CAUGHT AN APOLLO EXCEPTION for "),
    custom = { methodDef, _ ->
        methodDef.parameters.size == 2 &&
            methodDef.parameters[0].type == "Ljava/lang/String;" &&
            methodDef.parameters[1].type == "Ljava/lang/Throwable;" &&
            methodDef.returnType.startsWith("L")
    },
)

/** Mutation entry: `(Apollo operation, Map, GraphQL repository, Continuation) -> Object`. */
internal object GraphqlMutationRequestStartFingerprint : Fingerprint(
    strings = listOf("mutation"),
    returnType = "Ljava/lang/Object;",
    custom = { methodDef, _ ->
        methodDef.parameters.size == 4 &&
            methodDef.parameters[0].type.startsWith("Lcom/apollographql/apollo/api/") &&
            methodDef.parameters[1].type == "Ljava/util/Map;" &&
            methodDef.parameters[2].type.startsWith("Lcom/x/repositories/graphql/") &&
            methodDef.parameters[3].type == "Lkotlin/coroutines/Continuation;"
    },
)

/** Shared query execution entry: `(Apollo operation, Map, GraphQL repository, HTTP context, List, Apollo value, Continuation) -> Object`. */
internal object GraphqlSharedRequestStartFingerprint : Fingerprint(
    strings = listOf("call to 'resume' before 'invoke' with coroutine"),
    returnType = "Ljava/lang/Object;",
    custom = { methodDef, _ ->
        methodDef.parameters.size == 7 &&
            methodDef.parameters[0].type.startsWith("Lcom/apollographql/apollo/api/") &&
            methodDef.parameters[1].type == "Ljava/util/Map;" &&
            methodDef.parameters[2].type.startsWith("Lcom/x/repositories/graphql/") &&
            methodDef.parameters[3].type.startsWith("Lcom/apollographql/apollo/api/http/") &&
            methodDef.parameters[4].type == "Ljava/util/List;" &&
            methodDef.parameters[5].type.startsWith("Lcom/apollographql/apollo/api/") &&
            methodDef.parameters[6].type == "Lkotlin/coroutines/Continuation;"
    },
)

/** Shared Apollo-to-app response mapper: static `(Apollo response, Function2) -> result object`. */
internal object GraphqlResponseMapperFingerprint : Fingerprint(
    strings =
        listOf(
            "trace_id",
            "bounce_deeplink",
            "sub_error_code",
        ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    custom = { methodDef, _ ->
        methodDef.parameters.size == 2 &&
            methodDef.parameters[0].type.startsWith("Lcom/apollographql/apollo/api/") &&
            methodDef.parameters[1].type == "Lkotlin/jvm/functions/Function2;" &&
            methodDef.returnType.startsWith("Lcom/x/result/")
    },
)

/** Durable enqueue boundary: static extension with stable model/library package shapes. */
internal object PostActionsEnqueueFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    custom = { methodDef, _ ->
        methodDef.parameters.size == 7 &&
            methodDef.parameters[0].type.startsWith("Lcom/x/repositories/post/actions/") &&
            methodDef.parameters[1].type.startsWith("Lcom/x/models/") &&
            methodDef.parameters[2].type.startsWith("Lcom/x/models/") &&
            methodDef.parameters[3].type == "Ljava/lang/String;" &&
            methodDef.parameters[4].type == "Lkotlin/collections/builders/MapBuilder;" &&
            methodDef.parameters[5].type.startsWith("Lcom/x/models/") &&
            methodDef.parameters[6].type == "I"
    },
)

/** Durable completion boundary: static extension with stable package shapes. */
internal object PostActionsCompleteFingerprint : Fingerprint(
    returnType = "V",
    custom = { methodDef, _ ->
        methodDef.parameters.size == 4 &&
            methodDef.parameters[0].type.startsWith("Lcom/x/repositories/post/actions/") &&
            methodDef.parameters[1].type == "Ljava/lang/String;" &&
            methodDef.parameters[2].type.startsWith("Lcom/x/repositories/post/actions/") &&
            methodDef.parameters[3].type == "Ljava/lang/String;"
    },
)

/** Terminal drop boundary on the named durable worker; parameter classes are release-obfuscated. */
internal object DurableWorkerDropFingerprint : Fingerprint(
    definingClass = "Lcom/x/durableactions/DurableActionWorker;",
    strings = listOf("DurableActionWorker"),
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    custom = { methodDef, _ ->
        methodDef.parameters.size == 3 &&
            methodDef.parameters[0].type.startsWith("Lcom/x/durableactions/") &&
            methodDef.parameters[1].type.startsWith("Lcom/x/durableactions/") &&
            methodDef.parameters[2].type == "Ljava/lang/String;"
    },
)

/** WorkManager entry kept by name: `doWork(Continuation) -> Object` with retry/worker literals. */
internal object DurableWorkerRetryFingerprint : Fingerprint(
    name = "doWork",
    strings =
        listOf(
            "Will retry ",
            "DurableActionWorker",
        ),
    parameters = listOf("Lkotlin/coroutines/Continuation;"),
    returnType = "Ljava/lang/Object;",
)

@Suppress("unused")
val xLiteGraphqlDiagnosticsPatch =
    bytecodePatch(
        name = "X-Lite: Log network diagnostics",
        description =
            "When enabled, records GraphQL requests, responses, and errors plus the durable-action " +
                "queue lifecycle to Download/Piko/X-Lite-GraphQL-Diagnostics.txt. An optional " +
                "operation allowlist filters request records (empty = everything). Records are " +
                "bounded, sanitized, and rotated; the master toggle defaults to off.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        xLiteSettings {
            category(Categories.ADVANCED) {
                group(Groups.DEBUG_TOOLS) {
                    group(Groups.NETWORK_LOGGING) {
                        toggle(
                            id = "xlite.advanced.debug_tools.log_network_diagnostics",
                            strings = settingStrings("piko_xlite_log_network_diagnostics"),
                            order = 400,
                            defaultValue = false,
                        )
                        input(
                            id = "xlite.advanced.debug_tools.log_network_diagnostics.operation_allowlist",
                            strings = settingStrings("piko_xlite_log_network_diagnostics_allowlist"),
                            order = 410,
                            defaultValue = "",
                        )
                        toggle(
                            id = "xlite.advanced.debug_tools.log_network_diagnostics.include_queue_lifecycle",
                            strings = settingStrings("piko_xlite_log_network_diagnostics_queue"),
                            order = 420,
                            defaultValue = true,
                        )
                        toggle(
                            id = "xlite.advanced.debug_tools.log_network_diagnostics.include_request_metadata",
                            strings = settingStrings("piko_xlite_log_network_diagnostics_metadata"),
                            order = 430,
                            defaultValue = false,
                        )
                        action(
                            id = "xlite.advanced.debug_tools.log_network_diagnostics.clear_file",
                            strings = settingStrings("piko_xlite_log_network_diagnostics_clear"),
                            order = 440,
                            handlerClassDescriptor = "$GRAPHQL_DIAGNOSTICS_CLASS\$ClearFileAction;",
                        )
                    }
                }
            }
        }

        execute {
            val failureMatches = GraphqlFailureFingerprint.matchAll().toList()
            check(failureMatches.size == 1) {
                "Expected one transport-error helper, found ${failureMatches.size}: $failureMatches"
            }
            failureMatches.single().method.addInstructions(
                0,
                """
                invoke-static {p0, p1}, $GRAPHQL_DIAGNOSTICS_DESCRIPTOR->logFailure(Ljava/lang/String;Ljava/lang/Throwable;)V
                """.trimIndent(),
            )

            val mutationMatches = GraphqlMutationRequestStartFingerprint.matchAll().toList()
            check(mutationMatches.size == 1) {
                "Expected one mutation entry, found ${mutationMatches.size}: $mutationMatches"
            }
            mutationMatches.single().method.addInstructions(
                0,
                """
                invoke-static {p1}, $GRAPHQL_DIAGNOSTICS_DESCRIPTOR->logRequestStarted(Ljava/lang/Object;)V
                """.trimIndent(),
            )

            val sharedMatches = GraphqlSharedRequestStartFingerprint.matchAll().toList()
            check(sharedMatches.size == 1) {
                "Expected one shared request entry, found ${sharedMatches.size}: $sharedMatches"
            }
            sharedMatches.single().method.addInstructions(
                0,
                """
                invoke-static {p1}, $GRAPHQL_DIAGNOSTICS_DESCRIPTOR->logRequestStarted(Ljava/lang/Object;)V
                """.trimIndent(),
            )

            val mapperMatches = GraphqlResponseMapperFingerprint.matchAll().toList()
            check(mapperMatches.size == 1) {
                "Expected one Apollo response mapper, found ${mapperMatches.size}: $mapperMatches"
            }
            val mapperMethod = mapperMatches.single().method
            val implementation = mapperMethod.implementation
                ?: throw PatchException("Response mapper has no implementation")
            val p0Register = implementation.registerCount - mapperMethod.parameterTypes.size
            mapperMethod.addInstructions(
                0,
                """
                invoke-static {v$p0Register}, $GRAPHQL_DIAGNOSTICS_DESCRIPTOR->logApolloResponse(Ljava/lang/Object;)V
                """.trimIndent(),
            )

            val returnIndexes =
                mapperMethod.instructions.withIndex()
                    .filter { (_, instruction) ->
                        instruction.opcode == Opcode.RETURN_OBJECT &&
                            (instruction as? OneRegisterInstruction)?.registerA == p0Register
                    }
                    .map { it.index }
            if (returnIndexes.size != 3) {
                throw PatchException(
                    "Expected three result return sites in the response mapper, found ${returnIndexes.size}",
                )
            }
            // Insert from the highest index down so earlier indexes stay valid.
            returnIndexes.sortedDescending().forEach { index ->
                mapperMethod.addInstructions(
                    index,
                    """
                    invoke-static {v$p0Register}, $GRAPHQL_DIAGNOSTICS_DESCRIPTOR->logAppResult(Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            val enqueueMatches = PostActionsEnqueueFingerprint.matchAll().toList()
            check(enqueueMatches.size == 1) {
                "Expected one durable enqueue method, found ${enqueueMatches.size}: $enqueueMatches"
            }
            enqueueMatches.single().method.addInstructions(
                0,
                """
                invoke-static {p2, p3}, $GRAPHQL_DIAGNOSTICS_DESCRIPTOR->logDurableQueued(Ljava/lang/Object;Ljava/lang/String;)V
                """.trimIndent(),
            )

            val completeMatches = PostActionsCompleteFingerprint.matchAll().toList()
            check(completeMatches.size == 1) {
                "Expected one durable completion method, found ${completeMatches.size}: $completeMatches"
            }
            completeMatches.single().method.addInstructions(
                0,
                """
                invoke-static {p1, p2, p3}, $GRAPHQL_DIAGNOSTICS_DESCRIPTOR->logDurableCompleted(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;)V
                """.trimIndent(),
            )

            val dropMatches = DurableWorkerDropFingerprint.matchAll().toList()
            check(dropMatches.size == 1) {
                "Expected one durable drop helper, found ${dropMatches.size}: $dropMatches"
            }
            dropMatches.single().method.addInstructions(
                0,
                """
                invoke-static {p0, p1, p2}, $GRAPHQL_DIAGNOSTICS_DESCRIPTOR->logDurableDropped(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V
                """.trimIndent(),
            )

            val workerMatches = DurableWorkerRetryFingerprint.matchAll().toList()
            check(workerMatches.size == 1) {
                "Expected one durable worker doWork, found ${workerMatches.size}: $workerMatches"
            }
            val workerMethod = workerMatches.single().method
            val retrySites =
                workerMethod.instructions.withIndex()
                    .filter { (_, instruction) ->
                        instruction.opcode == Opcode.INVOKE_INTERFACE &&
                            instruction.getReference<MethodReference>()?.let { reference ->
                                reference.definingClass.startsWith("Lcom/x/durableactions/") &&
                                    reference.parameterTypes.size == 2 &&
                                    reference.parameterTypes[0].startsWith("Lcom/x/durableactions/") &&
                                    reference.parameterTypes[1].startsWith("Lkotlin/coroutines/jvm/internal/") &&
                                    reference.returnType == "Ljava/lang/Object;"
                            } == true
                    }
                    .map { it.index }
            if (retrySites.size != 2) {
                throw PatchException(
                    "Expected two durable retry persistence sites in the worker, found ${retrySites.size}",
                )
            }
            retrySites.sortedDescending().forEach { index ->
                workerMethod.addInstructions(
                    index,
                    """
                    invoke-static {v0}, $GRAPHQL_DIAGNOSTICS_DESCRIPTOR->logDurableRetry(Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }
        }
    }