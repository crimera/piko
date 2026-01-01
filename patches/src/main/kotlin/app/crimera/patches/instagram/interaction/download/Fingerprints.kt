package app.crimera.patches.instagram.interaction.download

import app.revanced.patcher.fingerprint

internal val feedBottomSheet = fingerprint {
    strings("MediaOptionsOverflowHelper", "SHOP_SIMILAR")
		// returns("V")
		// custom { methodDef, classDef -> 
		// 	methodDef.name == "A0P" && classDef.type == "LX/B5W;" 
		// }
}

internal val dialogItemClickedMethodFingerprint = fingerprint {
    strings("Required value was null.", "media_options")
}
