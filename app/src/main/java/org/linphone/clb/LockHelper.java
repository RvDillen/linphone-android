package org.linphone.clb;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;

public class LockHelper {

    private static final String TAG = "LockHelper";

    public static void LockScreen(Activity activity) {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) return;

        android.util.Log.d(TAG, "Start over lockscreen");

        KeyguardManager kgm = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        if (kgm != null) kgm.requestDismissKeyguard(activity, null);

        activity.setShowWhenLocked(true);
        activity.setTurnScreenOn(true);
    }

    public static void UnlockScreen(Activity activity) {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) return;

        android.util.Log.d(TAG, "Start over unlockscreen");

        activity.setShowWhenLocked(false);
        activity.setTurnScreenOn(false);
    }
}
