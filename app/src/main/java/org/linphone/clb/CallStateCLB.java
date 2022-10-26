package org.linphone.clb;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import org.linphone.R;
import org.linphone.activities.launcher.LauncherActivity;
import org.linphone.clb.kt.CoreContextExt;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Reason;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;

import static org.linphone.LinphoneApplication.coreContext;
import static org.linphone.core.Reason.Declined;

import androidx.annotation.RequiresApi;

/**
 * CallStateCLB: CLB class to store call state from CLB. <br>
 * Used to Prevent Linphone activities to start when activated from CLB
 *
 *  04-10-2021 RvD Upgrade to 4.5.2
 *  26-03-2020 RvD Initial version
 */
public class CallStateCLB {

    private static CallStateCLB instance;
    private static final String tag = "ClbCallState";
    private static final String STATE_SIPSTATE = "org.linphone.state.SIPSTATE";

    private String callUri = null;
    private String callUriAll = null;
    private String callState = null;
    private CoreListenerStub mListener = null;
    private Context mContext = null;
    private Core mCore = null;
    private TelephonyManager mTelephonyManager;

    private long lastHanugUp = 0;
    private boolean mCallGsmON = false;

    public static final synchronized CallStateCLB instance() {
        if (instance == null) {
            instance = new CallStateCLB();
        }
        return instance;
    }

    public boolean IsCallFromCLB() {
        return (callUri != null);
    }

    public boolean IsAnyCallFromCLB() {
        return (callUriAll != null);
    }

    public void SetCallUri(String uri) {
        uri = GetShortCallUri(uri);
        callUri = uri;
        callUriAll = uri;
    }

    public String GetCallUriAll(){
        return callUriAll;
    }


    public String GetShortCallUri(String tmpCallUri) {
        int index = tmpCallUri.indexOf("@");
        int index2 = tmpCallUri.indexOf(";");

        if (index2 != -1 && index != -1) {
            index = Math.min(index, index2);
        } else if (index2 != -1) {
            index = index2;
        }
        if (index != -1) {
            tmpCallUri = tmpCallUri.substring(0, index);
        }
        return tmpCallUri;
    }

    public void SetCallState(String state) {
        callState = state;
        if (callState != "ringing") {
            // Reset uri
            callUri = null;
        }
        if(callState == "idle") {
            callUriAll = null;
            FinishLauncher();
        }
    }

    private void FinishLauncher() {
        // A11(+): Finish loader activity.
        if (Build.VERSION.SDK_INT > Version.API29_ANDROID_10) {

            Intent intent = new Intent(coreContext.getContext(), LauncherActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_FROM_BACKGROUND);

            // Remove launcher screen
            intent.putExtra("CLB", "OnOutgoingEnded");

            coreContext.getContext().startActivity(intent);

        }
    }

    public void RegisterHangUpTime() {
        lastHanugUp = System.currentTimeMillis();
    }

    public boolean IsJustHangUp() {
        long now = System.currentTimeMillis();
        long period = now - lastHanugUp;
        //        android.util.Log.i(tag, "Periode is: " + period);
        boolean result = (period) < 5000; // (< 5 sec ago)
        return result;
    }

    public boolean getCallGsmON() {
        return mCallGsmON;
    }

    public void setCallGsmON(boolean on) {
        mCallGsmON = on;
    }

    CallStateCLB(){}

    public void Restart() {
        Context currentContext = mContext;
        Core currentCore = mCore;
        mContext = coreContext.getContext().getApplicationContext();
        if(currentContext != mContext || !callStateListenerRegistered) {
            Log.i("[Manager] Creating CallStateCLB");
            AddGsmListener();
            AddCoreListener();
        }
        else if(currentCore != coreContext.getCore()) {
            Log.i("[Manager] Restarting Core listener");
            AddCoreListener();
        }
    }

    private void AddGsmListener() {
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if(callStateListenerRegistered) {
                mTelephonyManager.unregisterTelephonyCallback(callStateListener);
                callStateListenerRegistered = false;
                Log.i("[Manager] Unregistered call state listener");
            }
            if (mContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                mTelephonyManager.registerTelephonyCallback(mContext.getMainExecutor(), callStateListener);
                callStateListenerRegistered = true;
                Log.i("[Manager] Registered call state listener");
            } else {
                Log.i("[Manager] Can't register phone state listener, permission not granted");
                return;
            }
        } else {
            if (callStateListenerRegistered) {
                mTelephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
                callStateListenerRegistered = false;
                Log.i("[Manager] Unregistered phone state listener");
            }
            mTelephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            callStateListenerRegistered = true;
            Log.i("[Manager] Registered phone state listener");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private static abstract class CallStateListener extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        @Override
        abstract public void onCallStateChanged(int state);
    }

    private boolean callStateListenerRegistered = false;

    private CallStateListener callStateListener = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ?
            new CallStateListener() {
                @Override
                public void onCallStateChanged(int state) {
                    CallStateChanged(state);
                }
            }
            : null;

    private PhoneStateListener phoneStateListener = (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ?
            new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String phoneNumber) {
                    CallStateChanged(state);
                }
            }
            : null;


    private void CallStateChanged(int state)
    {
        switch (state) {
            case TelephonyManager.CALL_STATE_OFFHOOK:
                Log.i("[Manager] Phone state is off hook");
                setCallGsmON(true);
                if (Build.MODEL.contains("Myco")) {
                    // CLB Always end CLB call when a normal phone call is off hook.
                    Core core = coreContext.getCore();
                    if (core != null && core.getCallsNb() > 0) {
                        Call[] calls = core.getCalls();
                        if (calls != null && calls.length > 0) {
                            // New 4.6.3  getCallsNb can return values of failed calls
                            for (Call call : calls) {
                                String address = GetAddressString(call);
                                if (callUriAll != null && address.contains(callUriAll)) {
                                    Log.i("[Manager] end call, cause a CLB call with pause is not allowed.");
                                    call.terminate();
                                }
                            }
                        }
                    }
                }
                break;
            case TelephonyManager.CALL_STATE_RINGING:
                Log.i("[Manager] Phone state is ringing");
                setCallGsmON(true);
                break;
            case TelephonyManager.CALL_STATE_IDLE:
                Log.i("[Manager] Phone state is idle");
                setCallGsmON(false);

                // CLB
                Core core = coreContext.getCore();
                if (core != null && core.getCallsNb() > 0) {
                    Call[] calls = core.getCalls();
                    if (calls != null && calls.length > 0) {

                        // New 4.5.2  getCallsNb can return values of failed calls
                        for (Call call : calls) {
                            if (call.getState() == Call.State.Paused) {
                                call.resume();
                                return;
                            }
                        }
                    }
                }
                break;
        }
    }
    private void AddCoreListener() {

        if (mListener != null) {
            try {
                mCore.removeListener(mListener);
            } catch (Exception ex) {
                Log.e("[Manager] Error in removeListener. ", ex.getMessage());
            }
        }
        mCore = coreContext.getCore();
        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {

                        try {

                            // NotifySipState (formerly in mLinphoneManager (Linphone 4.2)
                            NotifySipStateStateChanged(core, call, state, message);

                            if (IsCallFromCLB() == false)
                                return;

                            // CLB call => Notify Errors with Toast
                            if (state == Call.State.Error) {
                                DisplayErrorToastFor(call);
                            } else if (state == Call.State.End) {
                                // Convert Core message for internalization
                                if (call.getErrorInfo().getReason() == Declined) {
                                    ShowToast(mContext.getString(R.string.call_error_declined));
                                }
                            }
                        } catch (Exception ex) {
                            Log.e("[Manager] Error in Notify SIP state change. ", ex.getMessage());
                        }
                    }
                };

        Log.i("[Manager] Registering call state listener");
        mCore.addListener(mListener);
    }

    public void NotifySipStateStateChanged(
            final Core core,
            final Call call,
            final Call.State state,
            final String message) {

        String newCallState = null;
        String address = GetAddressString(call);
        Log.i("[Manager] Call state is [", state, "]" + " address: " + address);
        if (state == Call.State.IncomingReceived
                && !call.equals(core.getCurrentCall())) {
            if (call.getReplacedCall() != null) {
                // attended transfer will be accepted automatically.
                return;
            }
            newCallState = "ringing";
        }

        if ((state == Call.State.IncomingReceived || state == Call.State.IncomingEarlyMedia) && getCallGsmON()) {
            // Nothing
        } else if (state == Call.State.IncomingReceived
                && !getCallGsmON()) {
            newCallState = "ringing";
        } else if (state == Call.State.End || state == Call.State.Error) {
            if (core.getCallsNb() == 0) {
                newCallState = "idle";
                callUriAll = null;
            } else {
                newCallState = "idle";
                // CLB: Still first call in pause mode => Activate .
                Call[] calls = core.getCalls();
                boolean originalIsClb = (callUriAll != null && !callUriAll.isEmpty() && address.contains(callUriAll));
                if (calls != null && calls.length > 0) {
                    Call call1 = calls[0];
                    String address1 = GetAddressString(call1);
                    Call.State call1State = call1.getState();
                    Log.i("[Manager] call 1 state: " + call1State + " address: " + address1);
                    if (call1State == Call.State.Paused && state == Call.State.End) {
                        Handler mHandler = new Handler();
                        mHandler.postDelayed(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        Call.State call1State = call1.getState();
                                        if (call1State == Call.State.Paused) {
                                            Log.i("[Manager] call 1 resume");
                                            call1.resume();
                                            // Bring UI to front
                                            CoreContextExt coreContextExt = new CoreContextExt();
                                            coreContextExt.OnOutgoingStarted(true);
                                        }
                                    }
                                },
                                400);
                        if(!originalIsClb) {
                            newCallState = "connected";
                            address = address1;
                        }
                    } else if ((call1State == Call.State.End || call1State == Call.State.Error) && calls.length > 1) {
                        boolean call1IsClb = (callUriAll != null && !callUriAll.isEmpty() && address1.contains(callUriAll));
                        Call call2 = calls[1];
                        Call.State call2State = call2.getState();
                        String address2 = GetAddressString(call2);
                        Log.i("[Manager] call 2 state: " + call2State + " address: " + address2);
                        newCallState = "idle";
                        if (call2State == Call.State.Paused) {
                            Handler mHandler = new Handler();
                            mHandler.postDelayed(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            Call.State call2State = call2.getState();
                                            if (call2State == Call.State.Paused) {
                                                Log.i("[Manager] call 2 resume");
                                                call2.resume();
                                                // Bring UI to front
                                                CoreContextExt coreContextExt = new CoreContextExt();
                                                coreContextExt.OnOutgoingStarted(true);
                                            }
                                        }
                                    },
                                    400);
                            if(!originalIsClb && !call1IsClb) {
                                newCallState = "connected";
                                address = address2;
                            }
                        } else if (callUriAll != null && address2.contains(callUriAll) && call2State == Call.State.StreamsRunning) {
                            newCallState = "connected";
                            address = address2;
                        } else {
                        }
                    }
                    else if(callUriAll != null && address1.contains(callUriAll) && call1State == Call.State.StreamsRunning) {
                        newCallState = "connected"; // Happens when an incoming sip call is ignored.
                        address = address1;
                    }
                    else {
                        newCallState = "idle";    // New: 4.5.2:  when call fails, nr of callNB == 1 (was 0);
                    }
                } else {
                    newCallState = "idle";    // New: 4.5.2:  when call fails, nr of callNB == 1 (was 0);
                }
            }
        } else if (state == Call.State.UpdatedByRemote) {
            // If the correspondent proposes video while audio call
        } else if (state == Call.State.OutgoingInit) {
            newCallState = "ringing";
        } else if (state == Call.State.StreamsRunning) {
            newCallState = "connected";
        } else if (state == Call.State.Paused && callUriAll != null && address.contains(callUriAll)) {
            Log.i("[Manager] end call, cause a CLB call with pause is not allowed.");
            call.terminate();
        }
        Log.i("[Manager] state: " + state + " clb: " + newCallState);

        // Callstate changed? => Broadcast
        if (newCallState != null) {
            SetCallState(newCallState);

            Intent intentMessage = new Intent(STATE_SIPSTATE);
            intentMessage.putExtra("state", newCallState);
            intentMessage.putExtra("address", address);
            mContext.sendBroadcast(intentMessage);
        }
    }

    private String GetAddressString(Call call) {

        Address remoteAddress = call.getRemoteAddress();
        String remAddress = "";
        if (remoteAddress != null) {
            remAddress = remoteAddress.asStringUriOnly().toLowerCase();
        }
        return remAddress;
    }

    private void DisplayErrorToastFor (Call call) {
        // Convert Core message for internalization
        Reason reason = call.getErrorInfo().getReason();
        switch (reason) {
            case Declined:
                ShowToast(mContext.getString(R.string.call_error_declined));
                break;
            case NotFound:
                ShowToast(mContext.getString(R.string.call_error_user_not_found));
                break;
            case NotAcceptable:
                ShowToast(mContext.getString(R.string.call_error_incompatible_media_params));
                break;
            case Busy:
                ShowToast(mContext.getString(R.string.call_error_user_busy));
                break;
            default:
                ShowToast(mContext.getString(R.string.call_error_generic));
                break;

        }
    }

    private void ShowToast(String text) {
        Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
    }
}
