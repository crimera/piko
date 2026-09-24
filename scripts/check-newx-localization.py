"""Exercise production NewXStrings against changing Context resources on the host JVM.

Uses the actual Morphe StringRef superclass and small Android test doubles. This
checks retained references and locale selection, not Android Activity rendering.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--morphe-sources', required=True, type=Path)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
fixtures = {
    'androidx/annotation/NonNull.java': 'package androidx.annotation; public @interface NonNull {}',
    'android/content/res/Resources.java': '''package android.content.res;
public class Resources {
    public java.util.Locale locale = java.util.Locale.ENGLISH;
    public int getIdentifier(String name, String type, String pkg) { return name.equals("label") ? 1 : name.equals("number") ? 2 : 0; }
    public String getString(int id, Object... args) {
        if (id == 1) return locale.getLanguage().equals("zh") ? "中文" : "English";
        return String.format(locale, "%.1f", args);
    }
}''',
    'android/content/Context.java': '''package android.content;
public class Context {
    public final android.content.res.Resources resources = new android.content.res.Resources();
    public android.content.res.Resources getResources() { return resources; }
    public String getPackageName() { return "com.twitter.android"; }
    public String getString(int id, Object... args) { return resources.getString(id, args); }
}''',
    'android/app/Activity.java': 'package android.app; public class Activity extends android.content.Context {}',
    'app/morphe/extension/shared/ResourceUtils.java': '''package app.morphe.extension.shared;
public class ResourceUtils {
    public static boolean useActivityContextIfAvailable = true;
    public static String getString(String name) { return Utils.context.getString(Utils.context.getResources().getIdentifier(name,"string","com.twitter.android")); }
}''',
    'app/morphe/extension/shared/Utils.java': '''package app.morphe.extension.shared;
public class Utils {
    public static android.content.Context context = new android.content.Context();
    public static android.app.Activity activity;
    public static android.content.Context getContext() { return context; }
    public static android.app.Activity getActivity() { return activity; }
}''',
    'app/morphe/extension/shared/Logger.java': 'package app.morphe.extension.shared; public class Logger { public static void printException(java.util.function.Supplier<String> s) {} }',
    'LocaleAudit.java': '''import app.morphe.extension.shared.*;
import app.morphe.extension.newx.settings.NewXStrings;
public class LocaleAudit {
    private static int checks;
    private static void equal(String expected, String actual) {
        checks++; if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        StringRef named = NewXStrings.sfc("label");
        StringRef numeric = NewXStrings.forResourceId(1);
        equal("English", named.toString());
        equal("English", numeric.toString());
        Utils.context.resources.locale = java.util.Locale.SIMPLIFIED_CHINESE;
        equal("中文", named.toString());
        equal("中文", numeric.toString());
        equal("中文", NewXStrings.str("label"));
        Utils.activity = new android.app.Activity();
        Utils.activity.resources.locale = java.util.Locale.ENGLISH;
        equal("English", named.toString());
        equal("中文", NewXStrings.str(Utils.context, "label"));
        Utils.activity.resources.locale = java.util.Locale.GERMAN;
        java.util.Locale.setDefault(java.util.Locale.US);
        equal("1,5", NewXStrings.str("number", 1.5));
        ResourceUtils.useActivityContextIfAvailable = false;
        equal("中文", named.toString());
        equal("missing", NewXStrings.str("missing"));
        System.out.println("Locale-aware production resource checks passed: " + checks);
    }
}''',
}
with tempfile.TemporaryDirectory(prefix='newx-localization-') as directory:
    root = Path(directory)
    for name, text in fixtures.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
    with zipfile.ZipFile(args.morphe_sources) as archive:
        entry = next(name for name in archive.namelist() if name.endswith('/StringRef.java'))
        (root / 'app/morphe/extension/shared/StringRef.java').write_bytes(archive.read(entry))
    sources = [str(path) for path in root.rglob('*.java')]
    sources.append(str(repo / 'extensions/newx/src/main/java/app/morphe/extension/newx/settings/NewXStrings.java'))
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(root / 'classes'), *sources], check=True)
    subprocess.run(['java', '-cp', str(root / 'classes'), 'LocaleAudit'], check=True)
