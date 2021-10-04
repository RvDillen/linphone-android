package org.linphone.clb;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import androidx.annotation.RequiresApi;

public class LockHelper {

    private static final String TAG = "LockHelper";
    private static final String lockTag = "com.clb.linphone:LockTag";

    public static void LockScreen(Activity activity) {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) return;

        // Android R(+) => Add lockscreen behaviour
        android.util.Log.i(TAG, "Start over lockscreen");

        KeyguardManager kgm = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        if (kgm != null) kgm.requestDismissKeyguard(activity, null);

        activity.setShowWhenLocked(true);
        activity.setTurnScreenOn(true);
    }

    public static void UnlockScreen(Activity activity) {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) return;

        // Android R(+) => Add lockscreen behaviour
        android.util.Log.i(TAG, "Start over unlockscreen");

        activity.setShowWhenLocked(false);
        activity.setTurnScreenOn(false);
    }

    public static boolean IsScreenOn(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        boolean screenOn =
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        ? pm.isInteractive()
                        : pm.isScreenOn();
        return screenOn;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    public static boolean IsDeviceLocked(Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        boolean isLocked = km.isDeviceLocked();
        return isLocked;
    }

    public static PowerManager.WakeLock SetWakeLock(Context context) {

        PowerManager.WakeLock wakeLock = null;
        PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        int wakeUpFlags =
                PowerManager.ACQUIRE_CAUSES_WAKEUP; // | PowerManager.SCREEN_BRIGHT_WAKE_LOCK;

        wakeLock = mgr.newWakeLock(wakeUpFlags, lockTag);
        wakeLock.setReferenceCounted(true);
        wakeLock.acquire();
        return wakeLock;
    }

    public static void ReleaseWakeLock(PowerManager.WakeLock wakeLock) {
        // Release cpu lock (if present)
        if (null == wakeLock) return;

        android.util.Log.i(TAG, "ReleaseWakeLock start");
        try {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ex) {
            android.util.Log.e(TAG, "ReleaseWakeLock fails: ", ex);
        }
    }

    @SuppressWarnings("deprecation")
    public static KeyguardManager.KeyguardLock SetKeyLock(Context context) {

        try {
            android.util.Log.i(TAG, "SetKeyLock");

            KeyguardManager km =
                    (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

            KeyguardManager.KeyguardLock keyguardLock = km.newKeyguardLock(lockTag);
            keyguardLock.disableKeyguard();

            return keyguardLock;
        } catch (Exception ex) {
            android.util.Log.e(TAG, "SetKeyLock error: ", ex);
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    public static void ReleaseKeyLock(KeyguardManager.KeyguardLock keyguardLock) {
        if (null == keyguardLock) return;

        android.util.Log.i(TAG, "ReleaseKeyLock");

        keyguardLock.reenableKeyguard();
        keyguardLock = null;
    }
}
