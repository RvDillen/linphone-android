package org.linphone.clb.kt

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import org.linphone.LinphoneApplication
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.launcher.LauncherActivity
import org.linphone.core.CoreService
import org.linphone.mediastream.Version

class CoreContextExt() {

    fun StartCoreService(context: Context) {
        val serviceIntent = Intent(Intent.ACTION_MAIN).setClass(context, CoreService::class.java)
        serviceIntent.putExtra("StartForeground", true)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun OnOutgoingStarted(isJustHangup: Boolean) {

        if (Build.VERSION.SDK_INT > Version.API28_PIE_90) {
            coreContext.notificationsManager.startForeground()
        }

        // A11(+): Show activity briefly, otherwise microphone is blocked by android (BG-12130).
        if (Build.VERSION.SDK_INT > Version.API29_ANDROID_10) {

            val intent = Intent(coreContext.context, LauncherActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_FROM_BACKGROUND)

            // Show launcher screen briefly, except when device is locked with pattern/pincode, show all stuff
            if (!isDeviceLocked(coreContext.context) && !isJustHangup)
                intent.putExtra("CLB", "OnOutgoingStarted")

            coreContext.context.startActivity(intent)
        }
    }

    private fun isDeviceLocked(context: Context): Boolean {
        val keyguardManager =
            context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager // api 23+
        return keyguardManager.isDeviceSecure
    }

    fun IsServiceReady(): Boolean {
        return LinphoneApplication.coreContext.notificationsManager.service != null
    }
}
