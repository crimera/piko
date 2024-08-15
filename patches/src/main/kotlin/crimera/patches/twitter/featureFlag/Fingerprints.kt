package crimera.patches.twitter.featureFlag

import app.revanced.patcher.Match
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import crimera.patches.twitter.misc.settings.FSTS_DESCRIPTOR

internal val featureFlagFingerprint = fingerprint {
    strings("feature_switches_configs_crashlytics_enabled")
}

internal val customAdapterFingerprint = fingerprint {
    custom { methodDef, _ ->
        methodDef.definingClass.endsWith("Lapp/revanced/integrations/twitter/settings/featureflags/CustomAdapter;") &&
                methodDef.name == "getCount"
    }
}

// RecyclerView Fingerprints
internal val getCountFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.ABSTRACT)
    returns("I")
    custom { methodDef, _ ->
        methodDef.definingClass.endsWith("Landroidx/recyclerview/widget/RecyclerView\$e;")
    }
}

internal val onCreateViewHolderFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.ABSTRACT)
    returns("Landroidx/recyclerview/widget/RecyclerView\$c0;")
    custom { methodDef, _ ->
        methodDef.definingClass.endsWith("Landroidx/recyclerview/widget/RecyclerView\$e;")
    }
}

internal val onBindViewHolderFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.ABSTRACT)
    returns("V")
    custom { methodDef, _ ->
        methodDef.definingClass.endsWith("Landroidx/recyclerview/widget/RecyclerView\$e;")
    }
}

// TODO: rewrite to match the Extensions Hook implementation
internal val featureFlagLoadFingerprint = fingerprint {
    custom { methodDef, _ ->
        methodDef.definingClass.endsWith("Lapp/revanced/integrations/twitter/patches/FeatureSwitchPatch;") &&
                methodDef.name == "load"
    }
}

fun Match.enableFeatureFlag(functionName: String) {
    featureFlagLoadFingerprint.match?.mutableMethod?.addInstruction(
        0,
        "$FSTS_DESCRIPTOR->$functionName()V"
    )
}
