/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches;


import android.content.Context;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.constants.UI;

@SuppressWarnings("unused")
public class WelcomeMessage {

    public static void openWelcomeMessage(Context context) {
        try {
            if(Pref.firstTimePiko()) {
                UI.welcomeDialogBox(context);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "openWelcomeMessage failure", ex);
        }
    }

}
