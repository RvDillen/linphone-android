package org.linphone.clb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import org.linphone.core.tools.Log;

/**
 * RegisterCLB: CLB class to register CLB receivers, unforunately the register in manifest does not
 * work
 *
 * <p>Created by user Robert on 29-1-19.
 */
public class RegisterCLB {

    public static final String ACTION_CALL = "org.linphone.action.CALL";
    public static final String ACTION_ENDCALL = "org.linphone.action.ENDCALL";
    public static final String STATE_SIPSTATE = "org.linphone.state.SIPSTATE";

    private BroadcastReceiver mHangupReceiver;
    private BroadcastReceiver mDirectCallReceiver;
    private IntentFilter mHangupIntentFilter;
    private IntentFilter mDirectCallIntentFilter;

    Context mContext;

    public RegisterCLB(Context c) {
        mContext = c;
    }

    public void RegisterReceivers() {
        mHangupIntentFilter = new IntentFilter(ACTION_ENDCALL);
        mHangupIntentFilter.setPriority(99999999);
        mHangupReceiver = new HangupReceiver();
        try {
            mContext.registerReceiver(mHangupReceiver, mHangupIntentFilter);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        mDirectCallIntentFilter = new IntentFilter(ACTION_CALL);
        mDirectCallIntentFilter.setPriority(99999999);
        mDirectCallReceiver = new DirectCallReceiver();
        try {
            Log.i("Register Direct call receiver");
            mContext.registerReceiver(mDirectCallReceiver, mDirectCallIntentFilter);
        } catch (IllegalArgumentException e) {
            Log.i("Register Direct call receiver fail " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void UnRegisterReceivers() {
        try {
            mContext.unregisterReceiver(mHangupReceiver);
        } catch (Exception e) {
            Log.e(e);
        }
        try {
            mContext.unregisterReceiver(mDirectCallReceiver);
        } catch (Exception e) {
            Log.e(e);
        }
    }
}
