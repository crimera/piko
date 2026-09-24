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
    'androidx/annotation/Nullable.java': 'package androidx.annotation; public @interface Nullable {}',
    'app/morphe/extension/shared/settings/Setting.java': 'package app.morphe.extension.shared.settings; public class Setting<T> {}',
    'app/morphe/extension/shared/settings/BooleanSetting.java': 'package app.morphe.extension.shared.settings; public class BooleanSetting extends Setting<Boolean> {}',
    'app/morphe/extension/shared/settings/StringSetting.java': 'package app.morphe.extension.shared.settings; public class StringSetting extends Setting<String> {}',
    'app/morphe/extension/newx/settings/StringSetSetting.java': 'package app.morphe.extension.newx.settings; public class StringSetSetting extends app.morphe.extension.shared.settings.Setting<java.util.Set<String>> {}',
    'app/morphe/extension/newx/settings/SettingsRegistry.java': '''package app.morphe.extension.newx.settings;
public class SettingsRegistry {
    static java.util.List<SettingsNode.Category> nodes;
    public static java.util.List<SettingsNode.Category> catalog() { return nodes; }
}''',
    'app/morphe/extension/newx/settings/SearchLocaleAudit.java': '''package app.morphe.extension.newx.settings;
import java.util.List;
import java.util.Locale;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.StringSetting;
public class SearchLocaleAudit {
    public static int run() {
        var label = NewXStrings.sfc("label");
        var choice = new SettingsNode.SingleChoice("choice", label, label, 0,
                new StringSetting(), List.of(new SettingsNode.ChoiceOption("option", label)), true);
        var group = new SettingsNode.Group("group", label, null, null, 0, List.of(choice));
        SettingsRegistry.nodes = List.of(new SettingsNode.Category("category", label, null,
                null, 0, List.of(group)));
        Utils.context.resources.locale = Locale.ENGLISH;
        var english = SettingsSearchIndex.results();
        if (SettingsSearchMatcher.match(english, "English").size() != 1)
            throw new AssertionError("English settings search did not match");
        Utils.context.resources.locale = Locale.SIMPLIFIED_CHINESE;
        var chinese = SettingsSearchIndex.results();
        var result = chinese.get(0);
        if (!result.title.equals("中文") || !result.summary.equals("中文")
                || !result.path.equals("中文 → 中文")
                || !result.keywords.equals("中文 中文 中文 → 中文 choice 中文"))
            throw new AssertionError("Search index retained text from the previous locale: " + result.keywords);
        if (SettingsSearchMatcher.match(chinese, "中文").size() != 1
                || !SettingsSearchMatcher.match(chinese, "English").isEmpty())
            throw new AssertionError("Search matcher retained the previous locale");
        Utils.context.resources.locale = Locale.ENGLISH;
        if (SettingsSearchMatcher.match(SettingsSearchIndex.results(), "English").size() != 1)
            throw new AssertionError("Search did not return to English");
        return 4;
    }
}''',
    'android/content/res/Resources.java': '''package android.content.res;
public class Resources {
    public java.util.Locale locale = java.util.Locale.ENGLISH;
    public int getIdentifier(String name, String type, String pkg) { return name.equals("label") ? 1 : name.equals("number") ? 2 : name.equals("literal") ? 3 : 0; }
    public String getString(int id) {
        if (id == 3) return "100% complete; %1$s";
        if (id == 2) return "%.1f";
        return locale.getLanguage().equals("zh") ? "中文" : "English";
    }
    public String getString(int id, Object... args) {
        return String.format(locale, getString(id), args);
    }
}''',
    'android/content/Context.java': '''package android.content;
public class Context {
    public final android.content.res.Resources resources = new android.content.res.Resources();
    public android.content.res.Resources getResources() { return resources; }
    public String getPackageName() { return "com.twitter.android"; }
    public String getString(int id) { return resources.getString(id); }
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
        equal("100% complete; %1$s", NewXStrings.str("literal"));
        equal("100% complete; %1$s", NewXStrings.sfc("literal").toString());
        equal("%.1f", NewXStrings.forResourceId(2).toString());
        checks += app.morphe.extension.newx.settings.SearchLocaleAudit.run();
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
    for name in ('NewXStrings', 'SettingsNode', 'SettingsSearchIndex', 'SettingsSearchMatcher'):
        sources.append(str(repo / f'extensions/newx/src/main/java/app/morphe/extension/newx/settings/{name}.java'))
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(root / 'classes'), *sources], check=True)
    subprocess.run(['java', '-cp', str(root / 'classes'), 'LocaleAudit'], check=True)
