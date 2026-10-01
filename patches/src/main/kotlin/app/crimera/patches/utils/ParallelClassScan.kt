package app.crimera.patches.utils

import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.iface.ClassDef
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/** Same floor the patcher uses for parallel deflate: smaller heaps keep scans serial. */
private const val PARALLEL_SCAN_MIN_HEAP = 1024L * 1024 * 1024
private const val MAX_SCAN_THREADS = 6
private const val MIN_PARALLEL_SCAN_CLASSES = 2048

/**
 * Read-only whole-APK discovery scan: maps every class with [transform] and concatenates the
 * results in class order, so the output and the first thrown exception match a serial
 * `classDefForEach`.
 *
 * Only uses the stable `classDefForEach` API, so it runs on any patcher. [transform] may run on
 * several threads at once; it must not mutate shared state or call `mutableClassDefBy`. Heaps
 * below 1 GB (Morphe Manager's 512 MB floor) scan serially to keep transient garbage bounded.
 */
context(context: BytecodePatchContext)
internal fun <T> classDefFlatMap(transform: (ClassDef) -> List<T>): List<T> {
    val classes = ArrayList<ClassDef>(131_072)
    context.classDefForEach { classes += it }
    return classes.flatMapParallel(transform)
}

/**
 * Parallel [flatMap] over a snapshot of elements, with the same ordering and exception contract
 * as [classDefFlatMap]. [transform] must be safe to run concurrently.
 */
internal fun <E, T> List<E>.flatMapParallel(transform: (E) -> List<T>): List<T> {
    val items = this
    val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, MAX_SCAN_THREADS)
    if (threads == 1 ||
        items.size < MIN_PARALLEL_SCAN_CLASSES ||
        Runtime.getRuntime().maxMemory() < PARALLEL_SCAN_MIN_HEAP
    ) {
        return buildList { items.forEach { addAll(transform(it)) } }
    }

    val chunkSize = (items.size + threads * 4 - 1) / (threads * 4)
    val executor =
        Executors.newFixedThreadPool(threads) { runnable ->
            Thread(runnable, "newx-class-scan").apply { isDaemon = true }
        }
    try {
        val chunks =
            (items.indices step chunkSize).map { start ->
                executor.submit<List<T>> {
                    buildList {
                        for (i in start until minOf(start + chunkSize, items.size)) {
                            addAll(transform(items[i]))
                        }
                    }
                }
            }
        return buildList {
            chunks.forEach { chunk ->
                try {
                    addAll(chunk.get())
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        }
    } finally {
        executor.shutdownNow()
    }
}
