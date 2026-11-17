package org.linphone.clb.kt

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.Core
import org.linphone.core.CoreInCallService
import org.linphone.core.tools.Log
import org.linphone.ui.main.MainActivity
import org.linphone.utils.LinphoneUtils

class CoreContextExt() {
    // CLB: Coordinate one pending request with a bounded two-second service startup hold.
    companion object {
        private const val CLB_STARTUP_GRACE_MS = 2000L
        private val startupHandler = Handler(Looper.getMainLooper())
        private var pendingAddress: String? = null
        private var activityResumed = false
        private var callDispatched = false

        @Volatile private var requestGeneration = 0

        @Volatile private var startupDeadline = 0L

        fun hasPendingOutgoingCall(): Boolean = pendingAddress != null

        fun hasClbStartupHold(): Boolean = startupDeadline > SystemClock.elapsedRealtime()

        fun onMainActivityResumed(context: Context) {
            activityResumed = true
            startPendingService(context)
        }

        fun onMainActivityPaused() {
            activityResumed = false
        }

        @JvmStatic
        fun cancelPendingOutgoingCall() {
            // CLB: Invalidate queued work so cancelled or superseded requests cannot start later.
            pendingAddress = null
            callDispatched = false
            startupDeadline = 0L
            requestGeneration++
        }

        private fun startPendingService(context: Context) {
            // CLB: Require a resumed activity and microphone permission before starting a microphone FGS.
            if (pendingAddress == null || !activityResumed || startupDeadline != 0L) return
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
            startupDeadline = SystemClock.elapsedRealtime() + CLB_STARTUP_GRACE_MS
            val generation = requestGeneration
            startupHandler.postDelayed({
                if (generation == requestGeneration && startupDeadline != 0L) {
                    Log.w("[CLB Startup] Outgoing startup grace period expired")
                    cancelPendingOutgoingCall()
                    stopUnusedService()
                }
            }, CLB_STARTUP_GRACE_MS)
            try {
                val service = coreContext.notificationsManager.getService()
                if (service != null) {
                    coreContext.notificationsManager.onInCallServiceStarted(service, true)
                } else {
                    CoreContextExt().StartCoreService(context)
                }
            } catch (exception: RuntimeException) {
                Log.e("[CLB Startup] Unable to start foreground service: $exception")
                cancelPendingOutgoingCall()
                stopUnusedService()
            }
        }

        fun onClbServiceReady() {
            // CLB: Replace service polling with a single dispatch after successful foreground promotion.
            val requestedAddress = pendingAddress ?: return
            if (callDispatched || !hasClbStartupHold()) return
            callDispatched = true
            val generation = requestGeneration
            // CLB: Core and Call operations must share the Core Thread to prevent lock-inversion ANRs.
            coreContext.postOnCoreThread { core ->
                if (generation != requestGeneration || !hasClbStartupHold()) return@postOnCoreThread
                // CLB: Build the address as Linphone 5.2 did (CoreContext.startCall(String)), so the account domain is added
                // to a uri without host (e.g. 'sip:4007;?clbsessionid=...' => 'sip:4007@<domain>?clbsessionid=...').
                // Factory.createAddress() takes '4007' as host instead, causing 'DNS resolution failed for 4007' (SD-3524).
                val address = interpretClbAddress(core, requestedAddress)
                if (address != null && core.calls.none { it.remoteAddress.weakEqual(address) }) {
                    Log.i("[Manager] Start call to $requestedAddress (${address.asStringUriOnly()})")
                    coreContext.startCall(address, null, false, null)
                }
                // CLB: End of 5.2 address handling
                val callStarted = core.callsNb > 0
                startupHandler.post {
                    if (generation == requestGeneration) {
                        cancelPendingOutgoingCall()
                        if (!callStarted) stopUnusedService()
                    }
                }
            }
        }

        // CLB: Restored from Linphone 5.2 CoreContext.startCall(String) - do NOT replace by Factory.createAddress().
        // Split off the parameters at the first ';', interpret the user part (adds the default account domain when the host
        // is missing, and prevents a duplicate host), then append the parameters again.
        private fun interpretClbAddress(core: Core, requestedAddress: String): Address? {
            val parts = requestedAddress.trim().removePrefix("sip:").split(";", limit = 2)

            var address = core.interpretUrl(parts[0], LinphoneUtils.applyInternationalPrefix())
            if (address != null && parts.size > 1) {
                address = core.interpretUrl(address.asStringUriOnly() + ";" + parts[1], false)
            }

            if (address == null) {
                Log.e("[Manager] Failed to parse address: $requestedAddress")
            }
            return address
        }
        // CLB: End of address handling restored from Linphone 5.2

        private fun stopUnusedService() {
            // CLB: Release failed startup services without stopping an active or newer call request.
            val generation = requestGeneration
            coreContext.postOnCoreThread { core ->
                if (generation == requestGeneration && core.callsNb == 0) {
                    coreContext.notificationsManager.stopClbStartupService()
                }
            }
        }
    }

    fun RequestOutgoingCall(context: Context, address: String) {
        // CLB: Coalesce pending retries and bring the activity forward before service startup.
        if (pendingAddress == address) return
        cancelPendingOutgoingCall()
        pendingAddress = address
        OnOutgoingStarted(false)
        startPendingService(context)
    }

    fun StartCoreService(context: Context) {
        val serviceIntent = Intent(Intent.ACTION_MAIN).setClass(context, CoreInCallService::class.java)
        serviceIntent.putExtra("StartForeground", true)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun OnOutgoingStarted(isJustHangup: Boolean) {
        // A11(+): Show activity briefly, otherwise microphone is blocked by android (BG-12130).
        // CLB: Launch UI on the main thread; defer microphone service startup until the activity resumes.
        coreContext.postOnMainThread {
            val intent = Intent(coreContext.context, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_FROM_BACKGROUND)

            // Show launcher screen briefly, except when device is locked with pattern/pincode, show all stuff
            var state = "OnOutgoingStarted"
            if (isJustHangup) {
                state = "OnOutgoingEnded"
            }
            if (!isDeviceLocked(coreContext.context)) {
                intent.putExtra("CLB", state)
            }

            coreContext.context.startActivity(intent)
        }
    }

    private fun isDeviceLocked(context: Context): Boolean {
        val keyguardManager =
            context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager // api 23+
        return keyguardManager.isDeviceSecure
    }

    fun IsServiceReady(): Boolean {
        return coreContext.notificationsManager.getService() != null
    }
}
