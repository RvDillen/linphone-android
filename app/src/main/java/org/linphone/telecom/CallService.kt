package org.linphone.telecom

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.telecom.Call
import android.telecom.InCallService
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log

class CallService : InCallService() {
    private val binder: IBinder = LocalBinder()

    class LocalBinder : Binder() {
        val service: CallService
            get() = this.service
    }

    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        Log.i("[CallService] OnCallAdded")
        val number = call?.details?.handle.toString().removePrefix("tel:")
        coreContext.startCall(number)
    }

    override fun onCallRemoved(call: Call?) {
        Log.i("[CallService] Call removed")
        super.onCallRemoved(call)
    }

    override fun onBind(intent: Intent?): IBinder? {
        // return super.onBind(intent);
        Log.i("[CallService] BIND")
        if (intent?.action == "DirectBind") {
            return binder
        }
        return super.onBind(intent)
    }
}
