package org.linphone.clb;

import static org.linphone.core.Reason.Declined;

import android.content.Context;
import android.widget.Toast;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Reason;

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
    private String tag = "ClbCallState";

    private String callUri = null;
    private String callState = null;
    private CoreListenerStub mListener = null;
    private Context mContext = null;

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
            // Remove Listener
            if (mListener != null) {
                Core core = LinphoneManager.getCore();
                if (core != null) {
                    core.removeListener(mListener);
                }
                mListener = null;
            }
        }
    }

    public void CheckListener(Context context) {

        if (IsCallFromCLB() == false) return;

        if (mListener != null) return;

        // Add Listener to context
        mContext = context;
        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {

                        if (state == Call.State.Error) {
                            // Convert Core message for internalization
                            Reason reason = call.getErrorInfo().getReason();
                            switch (reason) {
                                case Declined:
                                    ShowToast(mContext.getString(R.string.error_call_declined));
                                    break;
                                case NotFound:
                                    ShowToast(mContext.getString(R.string.error_user_not_found));
                                    break;
                                case NotAcceptable:
                                    ShowToast(
                                            mContext.getString(R.string.error_incompatible_media));
                                    break;
                                case Busy:
                                    ShowToast(mContext.getString(R.string.error_user_busy));
                                    break;
                                default:
                                    ShowToast(mContext.getString(R.string.error_unknown));
                                    break;
                            }
                        } else if (state == Call.State.End) {
                            // Convert Core message for internalization
                            if (call.getErrorInfo().getReason() == Declined) {
                                ShowToast(mContext.getString(R.string.error_call_declined));
                            }
                        }
                    }
                };

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
        }
    }

    private void ShowToast(String text) {
        Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
    }
}
