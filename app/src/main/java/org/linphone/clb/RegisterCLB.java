package org.linphone.clb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.linphone.core.tools.Log;

/**
 * RegisterCLB: CLB class to register CLB receivers, unfortunately the register in manifest does not
 * work
 *
 * <ul>
 *   <li>13-07-20 rvdillen Add Login/Logout receiver
 *   <li>19-01-19 rvdillen Initial version
 * </ul>
 */
public class RegisterCLB {

    public static final String STATE_SIPSTATE = "org.linphone.state.SIPSTATE";
    public static final String ACTION_CALL = "org.linphone.action.CALL";
    public static final String ACTION_ENDCALL = "org.linphone.action.ENDCALL";

    public static final String STATE_CONNECTSTATE = "org.linphone.state.CONNECTSTATE";
    public static final String ACTION_LOGIN = "org.linphone.action.LOGIN";
    public static final String ACTION_LOGOUT = "org.linphone.action.LOGOUT";

    private static final String TAG = "RegisterCLB";

    private static boolean isRegistered = false;

    private BroadcastReceiver mHangupReceiver;
    private BroadcastReceiver mDirectCallReceiver;
    private BroadcastReceiver mLoginReceiver;
    private BroadcastReceiver mLogoutReceiver;

    Context mContext;

    public RegisterCLB(Context c) {
        mContext = c;
    }

    public void RegisterReceivers() {
        HandlerThread broadcastHandlerThread = new HandlerThread("BroadcastRegisterThread");
        broadcastHandlerThread.start();
        Looper looper = broadcastHandlerThread.getLooper();
        Handler broadcastHandler = new Handler(looper);
        if (isRegistered) return;

        Log.i("[Manager] Registering receivers");
        // ACTION_ENDCALL
        IntentFilter mHangupIntentFilter = new IntentFilter(ACTION_ENDCALL);
        mHangupReceiver = new HangupReceiver();
        RegisterReceiver(mHangupIntentFilter, mHangupReceiver, "Register Hangup receiver", null);

        // ACTION_CALL
        IntentFilter mDirectCallIntentFilter = new IntentFilter(ACTION_CALL);
        mDirectCallReceiver = new DirectCallReceiver();
        RegisterReceiver(
                mDirectCallIntentFilter, mDirectCallReceiver, "Register Direct call receiver", null);

        // ACTION_LOGIN
        IntentFilter mLoginIntentFilter = new IntentFilter(ACTION_LOGIN);
        mLoginReceiver = new LoginReceiver();
        RegisterReceiver(mLoginIntentFilter, mLoginReceiver, "Register Login receiver", broadcastHandler);

        // ACTION_LOGOUT
        IntentFilter mLogoutIntentFilter = new IntentFilter(ACTION_LOGOUT);
        mLogoutReceiver = new LogoutReceiver();
        RegisterReceiver(mLogoutIntentFilter, mLogoutReceiver, "Register Logout receiver", broadcastHandler);

        isRegistered = true;
    }

    public void UnRegisterReceivers() {

        // Skipped unregister (BG-12261)
        // https://stackoverflow.com/questions/45728334/register-and-unregister-the-broadcastreceiver-in-application-class

        UnRegisterReceiver(mHangupReceiver, "Unregister HangupReceiver");
        UnRegisterReceiver(mDirectCallReceiver, "Unregister DirectCallReceiver");

        UnRegisterReceiver(mLoginReceiver, "Unregister LoginReceiver");
        UnRegisterReceiver(mLogoutReceiver, "Unregister LogoutReceiver");
        isRegistered = false;
    }

    private void RegisterReceiver(
            IntentFilter ifilter, BroadcastReceiver bcReceiver, String message, @Nullable Handler handler) {

        try {
            ifilter.setPriority(99999999);
            android.util.Log.i(TAG, message);
            if(handler == null)
                mContext.registerReceiver(bcReceiver, ifilter);
            else
                mContext.registerReceiver(bcReceiver, ifilter, null, handler);
        } catch (IllegalArgumentException e) {
            android.util.Log.e(TAG, "Failure of " + message + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void UnRegisterReceiver(BroadcastReceiver brReceiver, String message) {
        try {
            Log.i(message);
            mContext.unregisterReceiver(brReceiver);
        } catch (Exception e) {
            Log.e(e);
        }
    }
}
