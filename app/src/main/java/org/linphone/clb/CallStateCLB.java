package org.linphone.clb;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import org.linphone.R;
import org.linphone.activities.launcher.LauncherActivity;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Reason;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;

import static org.linphone.LinphoneApplication.coreContext;
import static org.linphone.core.Reason.Declined;

/**
 * CallStateCLB: CLB class to store call state from CLB. <br>
 * Used to Prevent Linphone activities to start when activated from CLB
 *
 * <ul>
 *   <li>26-03-2020. RvD Initial version
 * </ul>
 */
public class CallStateCLB {

    private static CallStateCLB instance;
    private static final String tag = "ClbCallState";
    private static final String STATE_SIPSTATE = "org.linphone.state.SIPSTATE";

    private String callUri = null;
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

    public boolean IsCallFromCLB() {
        return (callUri != null);
    }

    public void SetCallUri(String uri) {
        callUri = uri;
    }

    public void SetCallState(String state) {
        callState = state;
        if (callState != "ringing") {
            // Reset uri
            callUri = null;
        }
    }

    public boolean IsBusyWithCall(String uri){

        // TODO make more stable implementaton
        if (callState == null)
            return false;

        return  (callState.equals("ringing") || callState.equals("connected")) &&  (uri.equals(callUri));
    }

    public void OnOutgoingStarted(){
        // A11(+): Show notification before
        if (Build.VERSION.SDK_INT > Version.API29_ANDROID_10) {
            // LinphonePreferences.instance().setServiceNotificationVisibility(true);
            coreContext.getNotificationsManager().startForeground();
        }

        // A11(+): Show activity briefly, otherwise microphone is blocked by android (BG-12130).
        if (Build.VERSION.SDK_INT > Version.API29_ANDROID_10 && IsJustHangUp() == false) {
            Intent intent = new Intent(mContext, LauncherActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
            mContext.startActivity(intent);
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

    CallStateCLB(){

        mContext = coreContext.getContext().getApplicationContext();

        AddGsmListerner();

        AddCoreListerner();
    }

    private void AddGsmListerner() {

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
                                        if (calls[0].getState() == Call.State.Paused) {
                                            calls[0].resume();
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


    private void AddCoreListerner(){

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
        if (state == Call.State.IncomingReceived
                && !call.equals(core.getCurrentCall())) {
            if (call.getReplacedCall() != null) {
                // attended transfer will be accepted automatically.
                return;
            }
            newCallState = "ringing";
        }

        if ((state == Call.State.IncomingReceived || state == Call.State.IncomingEarlyMedia)  && getCallGsmON()) {
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
                    Call call1 = calls[0];
                    Call.State call1State = call1.getState();
                    if (call1State == Call.State.Paused) {
                        call1.resume();
                        newCallState = "connected";
                    } else if (call1State == Call.State.End || call1State == Call.State.Error) {
                        newCallState = "idle";    // New: 4.5.2:  when call fails, nr of callNB == 1 (was 0);
                    }
                }
            }
        } else if (state == Call.State.UpdatedByRemote) {
            // If the correspondent proposes video while audio call
        } else if (state == Call.State.OutgoingInit) {
            newCallState = "ringing";
        } else if (state == Call.State.StreamsRunning) {
            newCallState = "connected";
        }

        android.util.Log.i("CLBState", "state: " + state + " clb: " + newCallState);

        // Callstate changed? => Broadcast
        if (newCallState != null) {
            SetCallState(newCallState);

            Intent intentMessage = new Intent(STATE_SIPSTATE);
            intentMessage.putExtra("state", newCallState);
            mContext.sendBroadcast(intentMessage);
        }
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
