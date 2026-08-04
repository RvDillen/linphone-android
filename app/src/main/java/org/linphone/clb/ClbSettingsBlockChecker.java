package org.linphone.clb;

import android.widget.Toast;
import android.content.Context;

import org.linphone.LinphoneApplication;
import org.linphone.core.tools.Log;

public class ClbSettingsBlockChecker {

    public static boolean AreSettingsBlocked(Context context) {
        if (LinphoneApplication.Companion.getCorePreferences().getBlockSettingsByPin() == 1) {
            Toast.makeText(context, context.getString(org.linphone.R.string.settings_blocked), Toast.LENGTH_SHORT).show();
            Log.i("[Side Menu] Settings blocked by CLB AppConfig.");

            return true;
        }
        return false;
    }
}
