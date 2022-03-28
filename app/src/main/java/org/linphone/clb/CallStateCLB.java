package org.linphone.clb;

import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import org.linphone.R;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Reason;
import org.linphone.core.tools.Log;

import static org.linphone.LinphoneApplication.coreContext;
import static org.linphone.core.Reason.Declined;

import java.util.ArrayList;
import java.util.List;

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
    private List<String> lastCallUris = new ArrayList<>();
    private String callState = null;
    private CoreListenerStub mListener = null;
    private Context mContext = null;
    private TelephonyManager mTelephonyManager;
    private PhoneStateListener mPhoneStateListener;

    private long lastHanugUp = 0;
    private boolean mCallGsmON = false;

    public static final synchronized CallStateCLB instance() {
        if (instance == null) {
            instance = new CallStateCLB();
        }
        return instance;
    }

    public boolean IsCallFromCLB(String tmpCallUri) {
        if (tmpCallUri.startsWith("sip:")) {
            tmpCallUri = tmpCallUri.substring("sip:".length());
        }
        int index = tmpCallUri.indexOf("@");
        int index2 = tmpCallUri.indexOf(";");

        if(index != -1){
            index = Math.min(index, index2);
            tmpCallUri = tmpCallUri.substring(0, index);
        }
        for (int i = 0; i < lastCallUris.size(); i++) {
            String lastCallUri = lastCallUris.get(i);
            //do something with i
            if (lastCallUri.contains(tmpCallUri))
                return true;
        }
        return false;
    }

    public boolean IsCallFromCLB() {
        return (callUri != null);
    }

    public void SetCallUri(String uri) {
        callUri = uri;
        lastCallUris.add((callUri));
    }

    public void SetCallState(String state) {
        callState = state;
        if (callState != "ringing") {
            // Reset uri
            callUri = null;
        }
        if(callState == "idle"){
            lastCallUris.clear();
        }
    }

    public void RegisterHangUpTime() {
        lastHanugUp = System.currentTimeMillis();
    }

    public boolean IsJustHangUp() {
        long now = System.currentTimeMillis();
        long period = now - lastHanugUp;
        //        android.util.Log.i(tag, "Period is: " + period);
        boolean result = (period) < 5000; // (< 5 sec ago)
        return result;
    }

    public boolean getCallGsmON() {
        return mCallGsmON;
    }

    public void setCallGsmON(boolean on) {
        mCallGsmON = on;
    }

    CallStateCLB(){

        mContext = coreContext.getContext().getApplicationContext();

        AddGsmListener();

        AddCoreListener();
    }

    private void AddGsmListener() {

        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mPhoneStateListener =
                new PhoneStateListener() {
                    @Override
                    public void onCallStateChanged(int state, String phoneNumber) {
                        switch (state) {
                            case TelephonyManager.CALL_STATE_OFFHOOK:
                                Log.i("[Manager] Phone state is off hook");
                                setCallGsmON(true);
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
                };

        Log.i("[Manager] Registering phone state listener");
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }


    private void AddCoreListener(){

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {

                        // NotifySipState (formerly in mLinphoneManager (Linphone 4.2)
                        NotifySipStateStateChanged(core, call, state, message);

                        if (IsCallFromCLB() == false)
                            return;

                        // CLB call => Notify Errors with Toast
                        if (state == Call.State.Error) {
                            DisplayErrorToastFor(call);
                        }
                        else if (state == Call.State.End) {
                            // Convert Core message for internalization
                            if (call.getErrorInfo().getReason() == Declined) {
                                ShowToast(mContext.getString(R.string.call_error_declined));
                            }
                        }
                    }
                };

        coreContext.getCore().addListener(mListener);
    }

    public void NotifySipStateStateChanged(
            final Core core,
            final Call call,
            final Call.State state,
            final String message) {

        Log.i("[Manager] Call state is [", state, "]");
        String newCallState = null;
        String address = GetAddressString(call);
        String newCallState1 = null;
        String address1 = null;
        Boolean fromClb = IsCallFromCLB(address);
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
            } else {
                // CLB: Still first call in pause mode => Activate .
                Call[] calls = core.getCalls();
                if (calls != null && calls.length > 0) {
                    address1 = address;
                    if(fromClb) {
                        newCallState1 = "idle";
                    } else {
                        newCallState1 = "idle_inactive";
                    }
                    Call call1 = calls[0];
                    address = GetAddressString(call1);
                    Boolean secondCallFromClb = IsCallFromCLB(address);
                    Call.State call1State = call1.getState();
                    if (call1State == Call.State.Paused) {
                        call1.resume();
                        if(!fromClb && secondCallFromClb) {
                            newCallState = "connected";
                        } else {
                            newCallState = "connected_inactive";
                        }
                    } else if (call1State == Call.State.End || call1State == Call.State.Error) {
                        if (calls.length > 1) {
                           call1 = calls[1];
                            call1State = call1.getState();
                            if ((call1State == Call.State.End || call1State == Call.State.Error) && secondCallFromClb) {
                                newCallState = "idle";
                            } else {
                                newCallState = "idle_inactive";
                            }
                        }
                        //newCallState = "idle";    // New: 4.5.2:  when call fails, nr of callNB == 1 (was 0);
                    }
                }
            }
        } else if (state == Call.State.UpdatedByRemote) {
            // If the correspondent proposes video while audio call
        } else if (state == Call.State.OutgoingInit && fromClb) {
            newCallState = "ringing";
        } else if (state == Call.State.StreamsRunning && fromClb) {
            newCallState = "connected";
        } else if (state == Call.State.Paused && fromClb) {
            newCallState = "paused";
        }

        android.util.Log.i("CLBState", "state: " + state + " clb: " + newCallState);

        if(newCallState1 != null) {

            Intent intentMessage = new Intent(STATE_SIPSTATE);
            intentMessage.putExtra("state", newCallState1);
            intentMessage.putExtra("address", address1);
            mContext.sendBroadcast(intentMessage);
        }
        
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
