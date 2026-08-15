package analysis;

import app.morphe.patcher.Fingerprint;
import app.morphe.patcher.Match;
import app.morphe.patcher.Patcher;
import app.morphe.patcher.PatcherConfig;
import app.morphe.patcher.PatcherContext;
import app.morphe.patcher.dex.BytecodeMode;
import app.morphe.patcher.dex.NoOpDexVerifier;
import app.morphe.patcher.patch.BytecodePatchContext;
import app.morphe.patcher.patch.Patch;
import app.morphe.patcher.patch.PatchLoader;
import app.morphe.patcher.resource.CpuArchitecture;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

public class ProfilePatches {
    private static boolean diagnosticRematch;

    public static class FingerprintTiming {
        public String name;
        public String patchName;
        public double searchTimeMs;
        public int matchCount;
        public String matchedClass;
        public String matchedMethod;
        public boolean hasDefiningClass;
        public boolean hasStringFilters;
        public int stringCount;
        public boolean isSlowScan;
    }

    public static class PatchTiming {
        public String name;
        public double isolatedTotalMs;
        public double isolatedSearchMs;
        public double isolatedApplyMs;
        public double bundleSequentialMs;
        public boolean success;
        public String error;
        public List<FingerprintTiming> fingerprints = new ArrayList<>();
    }

    private static final String[] ORDERED_XLITE_PATCHES = new String[]{
        "X-Lite: Remove ads",
        "X-Lite: Browse tweet object",
        "X-Lite: Share post as image",
        "X-Lite: Disable automatic timeline refresh",
        "X-Lite: Restore timeline position",
        "X-Lite: Customize inline actions",
        "X-Lite: Unlock downloads",
        "X-Lite: Hide new posts pill",
        "X-Lite: Filter posts by keyword",
        "X-Lite: Customize navigation bar items",
        "X-Lite: Hide premium upsell",
        "X-Lite: Hide compose button",
        "X-Lite: Customize drawer items",
        "X-Lite: Inline download button",
        "X-Lite: Hide Spaces bar",
        "X-Lite: Feature switch overrides",
        "X-Lite: Show sensitive media",
        "X-Lite: Dynamic color",
        "X-Lite: Customize default reply sorting",
        "X-Lite: Hide who to follow",
        "X-Lite: Hide AI-generated posts",
        "X-Lite: Customize default media tab",
        "X-Lite: Custom font",
        "X-Lite: Open canonical URLs"
    };

    public static void main(String[] args) throws Exception {
        Set<String> arguments = new HashSet<>(Arrays.asList(args));
        boolean bundleOnly = arguments.contains("--bundle-only");
        diagnosticRematch = arguments.contains("--diagnostic-rematch");
        if (arguments.contains("--profile")) {
            System.setProperty("piko.xlite.profile", "true");
        }

        File apkFile = new File(System.getProperty("user.home") + "/Downloads/twitter_12.17.3-alpha.01.apk");
        File mppFile = new File("patches/build/libs/patches-3.9.0-dev.4.mpp");
        File baselineFile = new File("baseline_patch_times.txt");

        if (!apkFile.exists()) {
            System.err.println("APK not found: " + apkFile.getAbsolutePath());
            System.exit(1);
        }
        if (!mppFile.exists()) {
            System.err.println("MPP not found: " + mppFile.getAbsolutePath());
            System.exit(1);
        }

        System.out.println("=================================================================");
        System.out.println("  X-Lite Patch Apply & Search Profiler");
        System.out.println("  Target APK: " + apkFile.getAbsolutePath());
        System.out.println("  Patch Bundle: " + mppFile.getAbsolutePath());
        System.out.println("=================================================================");

        PatchLoader.Jar loader = new PatchLoader.Jar(Collections.singleton(mppFile));
        Map<String, Patch<?>> patchMap = new LinkedHashMap<>();
        for (Patch<?> p : loader) {
            if (p.getName() != null) {
                patchMap.put(p.getName(), p);
            }
        }

        List<Patch<?>> xlitePatches = new ArrayList<>();
        for (String name : ORDERED_XLITE_PATCHES) {
            Patch<?> p = patchMap.get(name);
            if (p != null) {
                xlitePatches.add(p);
            } else {
                System.err.println("WARNING: Patch not found in bundle: " + name);
            }
        }

        System.out.println("Profiling " + xlitePatches.size() + " X-Lite patches...\n");

        // Run the bundle first in a fresh context so isolated diagnostics cannot warm static caches.
        Map<String, PatchTiming> timings = new LinkedHashMap<>();
        for (Patch<?> patch : xlitePatches) {
            PatchTiming timing = new PatchTiming();
            timing.name = patch.getName();
            timing.success = true;
            timings.put(patch.getName(), timing);
        }
        profileSequentialBundle(apkFile, xlitePatches, timings);

        if (bundleOnly) {
            System.out.println("\nBundle-only mode: baseline report was not overwritten.");
            return;
        }

        for (Patch<?> patch : xlitePatches) {
            double bundleSequentialMs = timings.get(patch.getName()).bundleSequentialMs;
            PatchTiming isolatedTiming = profileIsolatedPatch(apkFile, patch, patchMap);
            isolatedTiming.bundleSequentialMs = bundleSequentialMs;
            timings.put(patch.getName(), isolatedTiming);
        }

        writeBaselineReport(baselineFile, apkFile, mppFile, timings);

        System.out.println("\nBaseline written successfully to: " + baselineFile.getAbsolutePath());
    }

    private static PatchTiming profileIsolatedPatch(File apkFile, Patch<?> patch, Map<String, Patch<?>> allPatches) throws Exception {
        String patchName = patch.getName();
        System.out.printf("Profiling [ISOLATED] %-45s ... ", patchName);
        System.out.flush();

        File tempDir = Files.createTempDirectory("morphe-iso-").toFile();
        PatchTiming pt = new PatchTiming();
        pt.name = patchName;

        try {
            PatcherConfig config = new PatcherConfig(
                apkFile,
                tempDir,
                "com.twitter.android",
                "12.17.3-alpha.01",
                false,
                Collections.singleton(CpuArchitecture.ARM64_V8A),
                BytecodeMode.STRIP_FAST,
                NoOpDexVerifier.INSTANCE
            );

            Patcher patcher = new Patcher(config);
            Set<Patch<?>> toAdd = new HashSet<>();
            collectDependencies(patch, toAdd);
            toAdd.add(patch);
            patcher.plusAssign(toAdd);

            if (config.getResourceMode$morphe_patcher() != app.morphe.patcher.resource.ResourceMode.NONE) {
                patcher.getContext().getResourceContext$morphe_patcher().decodeResources$morphe_patcher(config.getResourceMode$morphe_patcher());
            }
            if (config.getBytecodeMode$morphe_patcher() != app.morphe.patcher.dex.BytecodeMode.NONE) {
                patcher.getContext().getBytecodeContext$morphe_patcher().decodeDexFiles$morphe_patcher();
            }
            BytecodePatchContext bpc = patcher.getContext().getBytecodeContext$morphe_patcher();

            // Execute dependencies first
            Set<Patch<?>> executed = new HashSet<>();
            for (Patch<?> dep : patch.getDependencies()) {
                executeWithDependencies(dep, patcher.getContext(), executed);
            }

            // Clear fingerprints before profiling target patch
            Fingerprint.Companion.clearFingerprints();

            // Record active fingerprints before target patch execution
            Set<Fingerprint> beforeFps = getCurrentlyRegisteredFingerprints();

            long startNanos = System.nanoTime();
            try {
                executeDirect(patch, patcher.getContext());
                long totalNanos = System.nanoTime() - startNanos;
                pt.isolatedTotalMs = totalNanos / 1e6;
                pt.success = true;
            } catch (Exception e) {
                long totalNanos = System.nanoTime() - startNanos;
                pt.isolatedTotalMs = totalNanos / 1e6;
                pt.success = false;
                Throwable cause = e;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                pt.error = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            }

            if (diagnosticRematch) {
                // Diagnostic only: this reruns fingerprints after mutation and is not part of patch execution time.
                Set<Fingerprint> afterFps = getCurrentlyRegisteredFingerprints();
                Set<Fingerprint> targetFps = new LinkedHashSet<>(afterFps);
                targetFps.removeAll(beforeFps);
                collectFingerprintMetrics(pt, bpc, targetFps);
                pt.isolatedSearchMs = pt.fingerprints.stream().mapToDouble(ft -> ft.searchTimeMs).sum();
            }

            System.out.printf("Done (Own execute: %8.2f ms | %s)%n",
                pt.isolatedTotalMs, pt.success ? "PASS" : "FAIL: " + pt.error);

            patcher.close();
        } finally {
            deleteDir(tempDir);
        }

        return pt;
    }

    private static void profileSequentialBundle(File apkFile, List<Patch<?>> patches, Map<String, PatchTiming> timings) throws Exception {
        System.out.println("\nProfiling [SEQUENTIAL BUNDLE] all " + patches.size() + " patches in Morphe execution order...");

        File tempDir = Files.createTempDirectory("morphe-bundle-").toFile();
        try {
            PatcherConfig config = new PatcherConfig(
                apkFile,
                tempDir,
                "com.twitter.android",
                "12.17.3-alpha.01",
                false,
                Collections.singleton(CpuArchitecture.ARM64_V8A),
                BytecodeMode.STRIP_FAST,
                NoOpDexVerifier.INSTANCE
            );

            Patcher patcher = new Patcher(config);
            Set<Patch<?>> allPatchesToAdd = new HashSet<>();
            for (Patch<?> p : patches) {
                collectDependencies(p, allPatchesToAdd);
                allPatchesToAdd.add(p);
            }
            patcher.plusAssign(allPatchesToAdd);

            long resourceDecodeStart = System.nanoTime();
            if (config.getResourceMode$morphe_patcher() != app.morphe.patcher.resource.ResourceMode.NONE) {
                patcher.getContext().getResourceContext$morphe_patcher().decodeResources$morphe_patcher(config.getResourceMode$morphe_patcher());
            }
            double resourceDecodeMs = (System.nanoTime() - resourceDecodeStart) / 1e6;

            long dexDecodeStart = System.nanoTime();
            if (config.getBytecodeMode$morphe_patcher() != app.morphe.patcher.dex.BytecodeMode.NONE) {
                patcher.getContext().getBytecodeContext$morphe_patcher().decodeDexFiles$morphe_patcher();
            }
            double dexDecodeMs = (System.nanoTime() - dexDecodeStart) / 1e6;
            System.out.printf("  Bundle resource decode: %.2f ms%n", resourceDecodeMs);
            System.out.printf("  Bundle DEX decode:      %.2f ms%n", dexDecodeMs);

            Set<Patch<?>> executed = new HashSet<>();
            double totalBundlePatchesTime = 0.0;

            for (Patch<?> p : patches) {
                long start = System.nanoTime();
                try {
                    executeWithDependenciesProfiled(
                        p, patcher.getContext(), executed, p.getName(), 0
                    );
                    long duration = System.nanoTime() - start;
                    double ms = duration / 1e6;
                    totalBundlePatchesTime += ms;
                    PatchTiming pt = timings.get(p.getName());
                    if (pt != null) {
                        pt.bundleSequentialMs = ms;
                    }
                    System.out.printf("  - %-45s : %8.2f ms%n", p.getName(), ms);
                } catch (Exception e) {
                    double ms = (System.nanoTime() - start) / 1e6;
                    totalBundlePatchesTime += ms;
                    PatchTiming pt = timings.get(p.getName());
                    if (pt != null) {
                        pt.bundleSequentialMs = ms;
                    }
                    Throwable cause = e;
                    while (cause.getCause() != null) cause = cause.getCause();
                    System.out.printf("  - %-45s : %8.2f ms [ERROR: %s]%n", p.getName(), ms, cause.getMessage());
                }
            }

            System.out.printf("  Total sequential patch execution time: %.2f ms%n", totalBundlePatchesTime);
            patcher.close();
        } finally {
            deleteDir(tempDir);
        }
    }

    private static void collectDependencies(Patch<?> patch, Set<Patch<?>> out) {
        for (Patch<?> dep : patch.getDependencies()) {
            if (out.add(dep)) {
                collectDependencies(dep, out);
            }
        }
    }

    private static void executeWithDependencies(Patch<?> patch, PatcherContext context, Set<Patch<?>> executed) throws Exception {
        for (Patch<?> dep : patch.getDependencies()) {
            if (!executed.contains(dep)) {
                executeWithDependencies(dep, context, executed);
            }
        }
        if (!executed.contains(patch)) {
            executeDirect(patch, context);
            executed.add(patch);
        }
    }

    private static void executeWithDependenciesProfiled(
        Patch<?> patch,
        PatcherContext context,
        Set<Patch<?>> executed,
        String path,
        int depth
    ) throws Exception {
        if (executed.contains(patch)) return;

        int dependencyIndex = 0;
        for (Patch<?> dependency : patch.getDependencies()) {
            String dependencyName = dependency.getName() != null
                ? dependency.getName()
                : "internal-dependency[" + dependencyIndex + "]";
            executeWithDependenciesProfiled(
                dependency,
                context,
                executed,
                path + " -> " + dependencyName,
                depth + 1
            );
            dependencyIndex++;
        }

        long startedAt = System.nanoTime();
        executeDirect(patch, context);
        double elapsedMs = (System.nanoTime() - startedAt) / 1e6;
        executed.add(patch);
        System.out.printf("      %s%-64s : %8.2f ms%n", "  ".repeat(depth), path, elapsedMs);
    }

    private static void executeDirect(Patch<?> patch, PatcherContext context) throws Exception {
        java.lang.reflect.Method m = Patch.class.getMethod("execute$morphe_patcher", PatcherContext.class);
        m.invoke(patch, context);
    }

    @SuppressWarnings("unchecked")
    private static Set<Fingerprint> getCurrentlyRegisteredFingerprints() {
        Set<Fingerprint> set = new LinkedHashSet<>();
        try {
            Field listField = Fingerprint.class.getDeclaredField("fingerprintList");
            listField.setAccessible(true);
            List<WeakReference<Fingerprint>> fpList = (List<WeakReference<Fingerprint>>) listField.get(null);
            for (WeakReference<Fingerprint> ref : fpList) {
                Fingerprint fp = ref.get();
                if (fp != null) {
                    set.add(fp);
                }
            }
        } catch (Exception ignored) {}
        return set;
    }

    private static void collectFingerprintMetrics(PatchTiming pt, BytecodePatchContext bpc, Set<Fingerprint> fps) {
        for (Fingerprint fp : fps) {
            FingerprintTiming ft = new FingerprintTiming();
            ft.patchName = pt.name;
            ft.name = fp.getClass().getSimpleName();
            if (ft.name.isEmpty() || ft.name.contains("$")) {
                ft.name = fp.toString();
            }

            ft.hasDefiningClass = fp.getDefiningClass() != null || fp.getClassFingerprint() != null;
            ft.stringCount = fp.getStrings() != null ? fp.getStrings().size() : 0;
            ft.hasStringFilters = ft.stringCount > 0 || (fp.getFilters() != null && !fp.getFilters().isEmpty());

            try {
                // Measure fresh match time
                fp.clearMatch();
                long t0 = System.nanoTime();
                Match m = fp.matchOrNull(bpc);
                long t1 = System.nanoTime();
                ft.searchTimeMs = (t1 - t0) / 1e6;

                if (m != null) {
                    ft.matchCount = 1;
                    ClassDef cd = m.getOriginalClassDef();
                    Method meth = m.getOriginalMethod();
                    ft.matchedClass = cd != null ? cd.getType() : null;
                    ft.matchedMethod = meth != null ? meth.getName() + meth.getParameterTypes() + meth.getReturnType() : null;
                } else {
                    // Try matchAllOrNull
                    fp.clearMatch();
                    long t0All = System.nanoTime();
                    List<Match> all = fp.matchAllOrNull(bpc);
                    long t1All = System.nanoTime();
                    if (all != null && !all.isEmpty()) {
                        ft.searchTimeMs = (t1All - t0All) / 1e6;
                        ft.matchCount = all.size();
                        ft.matchedClass = all.get(0).getOriginalClassDef() != null ? all.get(0).getOriginalClassDef().getType() : null;
                        ft.matchedMethod = all.get(0).getOriginalMethod() != null ? all.get(0).getOriginalMethod().getName() : null;
                    }
                }
            } catch (Exception ignored) {
            }

            // If no string index and no definingClass, it caused a full DEX scan
            ft.isSlowScan = !ft.hasDefiningClass && (fp.getStrings() == null || fp.getStrings().isEmpty());

            pt.fingerprints.add(ft);
        }
    }

    private static void writeBaselineReport(File file, File apkFile, File mppFile, Map<String, PatchTiming> timings) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("==================================================================================================");
            pw.println("                           X-LITE PATCH PERFORMANCE BASELINE REPORT");
            pw.println("==================================================================================================");
            pw.println("Generated At       : " + sdf.format(new Date()));
            pw.println("Target APK         : " + apkFile.getName() + " (" + apkFile.length() + " bytes)");
            pw.println("Package Target     : com.twitter.android 12.17.3-alpha.01 (arm64-v8a)");
            pw.println("Patch MPP Bundle   : " + mppFile.getName());
            pw.println("Total Patches      : " + timings.size());
            pw.println("==================================================================================================");
            pw.println();

            pw.println("--------------------------------------------------------------------------------------------------");
            pw.println("1. PATCH EXECUTION TIME SUMMARY (RANKED BY ISOLATED OWN EXECUTION TIME)");
            pw.println("--------------------------------------------------------------------------------------------------");
            pw.printf("%-4s %-48s | %16s | %21s | %-6s%n",
                "#", "Patch Name", "Own execute (ms)", "Bundle incremental (ms)", "Status");
            pw.println("--------------------------------------------------------------------------------------------------");

            List<PatchTiming> sorted = new ArrayList<>(timings.values());
            sorted.sort((a, b) -> Double.compare(b.isolatedTotalMs, a.isolatedTotalMs));

            double sumIsolatedTotal = 0;
            double sumBundle = 0;

            int idx = 1;
            for (PatchTiming pt : sorted) {
                sumIsolatedTotal += pt.isolatedTotalMs;
                sumBundle += pt.bundleSequentialMs;

                pw.printf("%-4d %-48s | %16.2f | %21.2f | %s%n",
                    idx++, pt.name, pt.isolatedTotalMs, pt.bundleSequentialMs,
                    pt.success ? "PASS" : "FAIL");
            }

            pw.println("--------------------------------------------------------------------------------------------------");
            pw.printf("%-4s %-48s | %16.2f | %21.2f |%n",
                "TOT", "TOTAL (ALL 24 PATCHES)", sumIsolatedTotal, sumBundle);
            pw.println("--------------------------------------------------------------------------------------------------");
            pw.println();

            pw.println("--------------------------------------------------------------------------------------------------");
            pw.println("2. BUNDLE HOTSPOTS (> 100 ms INCREMENTAL)");
            pw.println("--------------------------------------------------------------------------------------------------");
            pw.println("Bundle incremental time includes dependencies first executed for that row.");
            sorted.stream()
                .filter(pt -> pt.bundleSequentialMs >= 100.0)
                .sorted((a, b) -> Double.compare(b.bundleSequentialMs, a.bundleSequentialMs))
                .forEach(pt -> pw.printf("• %-48s : %8.2f ms%n", pt.name, pt.bundleSequentialMs));
            pw.println();

            pw.println("--------------------------------------------------------------------------------------------------");
            pw.println("3. DETAILED PER-PATCH EXECUTION BREAKDOWN");
            pw.println("--------------------------------------------------------------------------------------------------");
            for (PatchTiming pt : timings.values()) {
                pw.println("==================================================================================================");
                pw.println("Patch: " + pt.name);
                pw.printf("Own execute: %.2f ms | Bundle incremental: %.2f ms | Status: %s%n",
                    pt.isolatedTotalMs, pt.bundleSequentialMs, pt.success ? "OK" : "ERROR: " + pt.error);
                pw.println("--------------------------------------------------------------------------------------------------");

                if (pt.fingerprints.isEmpty()) {
                    pw.println("  (No in-patch spans recorded. Add temporary PatchProfiler wrappers, or use " +
                        "--diagnostic-rematch for post-mutation fingerprint diagnostics.)");
                } else {
                    pw.printf("  %-45s | %10s | %7s | %-12s | %-30s%n",
                        "Fingerprint Name", "Search(ms)", "Matches", "Strategy", "Matched Target");
                    pw.println("  ------------------------------------------------------------------------------------------------");
                    for (FingerprintTiming ft : pt.fingerprints) {
                        String strategy = ft.hasDefiningClass ? "ClassLookup" : (ft.stringCount > 0 ? "StringIndexed" : "FULL_DEX_SCAN");
                        String target = ft.matchedClass != null ? ft.matchedClass : "None";
                        pw.printf("  %-45s | %10.2f | %7d | %-12s | %-30s%n",
                            ft.name, ft.searchTimeMs, ft.matchCount, strategy, target);
                    }
                }
                pw.println();
            }

            pw.println("==================================================================================================");
            pw.println("                                      END OF BASELINE REPORT");
            pw.println("==================================================================================================");
        }
    }

    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
