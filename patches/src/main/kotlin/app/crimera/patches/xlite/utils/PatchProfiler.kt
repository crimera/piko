package app.crimera.patches.xlite.utils

import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.patch.BytecodePatchContext
import java.util.Locale

private const val PROFILE_PROPERTY = "piko.xlite.profile"
private const val PROFILE_ENVIRONMENT = "PIKO_XLITE_PROFILE"
private const val PROFILE_PREFIX = "[X-Lite profile]"

internal object XLitePatchProfiler {
    private val depth = ThreadLocal.withInitial { 0 }

    val enabled: Boolean
        get() =
            System.getProperty(PROFILE_PROPERTY)?.toBooleanStrictOrNull() == true ||
                System.getenv(PROFILE_ENVIRONMENT)?.toBooleanStrictOrNull() == true

    fun <T> measure(
        category: String,
        label: String,
        block: () -> T,
    ): T {
        if (!enabled) return block()

        val currentDepth = depth.get()
        val startedAt = System.nanoTime()
        depth.set(currentDepth + 1)
        return try {
            block()
        } finally {
            depth.set(currentDepth)
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0
            val indentation = "  ".repeat(currentDepth)
            val formattedElapsedMs = String.format(Locale.ROOT, "%.3f", elapsedMs)
            println("$PROFILE_PREFIX $indentation$category | $label | $formattedElapsedMs ms")
        }
    }

    fun matches(
        label: String,
        fingerprint: Fingerprint,
        matches: Collection<Match>,
    ) {
        if (!enabled) return

        val strategy = when {
            fingerprint.classFingerprint != null -> "class-fingerprint"
            fingerprint.definingClass?.endsWith(';') == true -> "exact-class"
            fingerprint.definingClass != null -> "class-prefix"
            !fingerprint.strings.isNullOrEmpty() -> "legacy-partial-string"
            !fingerprint.filters.isNullOrEmpty() -> "filtered"
            else -> "full-dex"
        }
        val targets = matches.joinToString(limit = 4) { it.originalMethod.toString() }
        println(
            "$PROFILE_PREFIX result | $label | matches=${matches.size} | strategy=$strategy" +
                if (targets.isEmpty()) "" else " | targets=$targets",
        )
    }
}

context(_: BytecodePatchContext)
internal fun Fingerprint.profileMatchAll(label: String): List<Match> =
    XLitePatchProfiler.measure("search", label) { scopedMatchAll() }
        .also { matches -> XLitePatchProfiler.matches(label, this, matches) }

context(_: BytecodePatchContext)
internal fun Fingerprint.profileMatchAllOrNull(label: String): List<Match>? =
    XLitePatchProfiler.measure("search", label) { scopedMatchAllOrNull() }
        .also { matches -> XLitePatchProfiler.matches(label, this, matches.orEmpty()) }

internal fun <T> profilePatchTime(
    label: String,
    block: () -> T,
): T = XLitePatchProfiler.measure("function", label, block)
