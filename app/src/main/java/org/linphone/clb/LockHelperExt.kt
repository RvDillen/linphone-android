package org.linphone.clb

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.os.PowerManager

/**
 * LockHelperExt: Add possibility to start Linphone over android lockscreen (with pattern)
 *
 *   15-08-21 rvdillen inital version
 */
class LockHelperExt(activity: Activity) {

    var keyguardLock: KeyguardManager.KeyguardLock? = null
    var wakeLock: PowerManager.WakeLock? = null
    var activity: Activity = activity

    fun lockScreen() {
        org.linphone.clb.LockHelper.LockScreen(activity)
    }

    fun setWakeLocks() {
        // CLB startUp A11
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            if (LockHelper.IsScreenOn(activity) == false) {
                wakeLock = LockHelper.SetWakeLock(activity)
            }
            if (LockHelper.IsDeviceLocked(activity)) {
                keyguardLock = LockHelper.SetKeyLock(activity)
            }
        }
    }

    fun unlockScreen() {
        LockHelper.UnlockScreen(activity)
        if (wakeLock != null) {
            LockHelper.ReleaseWakeLock(wakeLock)
        }
        if (keyguardLock != null) {
            LockHelper.ReleaseKeyLock(keyguardLock)
        }
    }
}
