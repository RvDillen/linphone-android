package org.linphone.clb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Core;

/**
 * HangupReceiver Hangs up a call. Created by mvdhorst on 18-12-17.
 *
 * <ul>
 *   <li>19-06-20 rvdillen incorrect audio connection is closed (BG-10842)
 *   <li>16-10-18 rvdillen tweaked hangup current call (BG-7267)
 *   <li>10-10-18 rvdillen Activate next 'in pause call' after hangup
 *   <li>12-07-18 rvdillen Fix HangUp for listen only mode
 *   <li>01-02-18 rvdillen Add hangUp uri to hangup selected call
 *   <li>18-12-17 mvdhorst Initial version
 * </ul>
 */
public class HangupReceiver extends BroadcastReceiver {

    private String tag = "ClbHangUp";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isOrderedBroadcast()) abortBroadcast();

        // Find Sip Nr to hang up
        String uriExtra = null;
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null && extras.containsKey("uri")) {
                uriExtra = extras.getString("uri");
            }
        }
        // Sip Found, try hangup this number
        if (uriExtra != null) {
            TerminatePhoneCall(uriExtra);
            return;
        }

        // Default hangup behaviour (no uri or terminate uri failed)
        LinphoneContext lc2 = LinphoneContext.instance();
        Core lc = LinphoneManager.getCore();
        Call currentCall = lc.getCurrentCall();

        if (currentCall != null) {
            lc.terminateCall(currentCall);
        } else if (lc.isInConference()) {
            lc.terminateConference();
        } else {
            lc.terminateAllCalls();
        }
    }

    private String FormatUri(String uri) {
        uri = uri.toLowerCase();
        uri = uri.replace("%20speak", " speak"); // Listen only connection (BG-6400)
        uri = uri.replace("%21speak", "!speak"); // old way as back up)
        return uri;
    }

    private Boolean TerminatePhoneCall(String uriExtra) {

        try {
            String session = uriExtra.toLowerCase();
            int index = session.indexOf("clbsessionid");
            if (index > 0) session = session.substring(index);

            Core lc = LinphoneManager.getCore();

            // Find if HangUp Nr is current call, if so terminate
            Call currentCall = LinphoneManager.getCore().getCurrentCall();
            if (currentCall != null) {

                // Current call found
                String remAddress = GetAdressString(currentCall);
                Log.i(tag, "TerminatePhoneCall started: Remote address: " + remAddress);

                // Check if any (other) calls in pause state
                boolean anyCallInPause = false;
                Call[] calls = lc.getCalls();
                if (calls != null && calls.length > 0) {
                    for (Call call : calls) {
                        anyCallInPause = anyCallInPause || call.getState() == Call.State.Paused;
                    }
                }

                // Check if Current call has same uri as from hangup uri
                if (remAddress != null && remAddress.contains(session) == true) {

                    // Current call is from hangup uri. => terminateCall now && if no calls in pause
                    LinphoneManager.getInstance()
                            .resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
                    String msg =
                            "TerminatePhoneCall: terminate currentCall Remote address: "
                                    + remAddress;
                    Log.i(tag, msg);
                    lc.terminateCall(currentCall);

                    if (anyCallInPause) DisplayPausedCall();
                    else TryTerminateActiveCall(true);
                    return true;

                } else if (anyCallInPause == true) {
                    // Call from uri is possibly in Pause,  => find and trminate terminateCall
                    Log.i(tag, "TerminatePhoneCall: terminate call in pause");
                    Call[] calls2 = lc.getCalls();
                    if (calls2 == null || calls.length == 0) return true;

                    for (Call call : calls) {
                        String adress = GetAdressString(call);
                        if (adress != null && adress.contains(session) == true) {
                            Log.i(tag, "Terminate PhoneCall in pause: " + adress);
                            lc.terminateCall(call);
                            DisplayPausedCall();
                            return true;
                        }
                    }
                    return true;

                } else {
                    Log.i(
                            tag,
                            "TerminatePhoneCall: terminate currentCall ignored, cause already gone");
                    return true;
                }
            }

            // If HangUpNr not Equals current call, try terminate from list
            if (lc.isInConference()) {
                lc.terminateConference();
                TryTerminateActiveCall(true);
            }

            Call[] calls = lc.getCalls();
            for (Call call : calls) {

                String remAddress = GetAdressString(call);
                Log.i(tag, "TerminatePhoneCall: other call Remote address: " + remAddress);

                if (remAddress != null && remAddress.contains(session) == true) {
                    lc.terminateCall(call);
                    TryTerminateActiveCall(true);
                    return true;
                }
            }
            TryTerminateActiveCall(true);
        } catch (Exception e) {
            Log.e(tag, "Exception TerminatePhoneCall: " + e.getMessage());
        }
        return false; // uri not found
    }

    private void DisplayPausedCall() {

        Core lc = LinphoneManager.getCore();
        Call[] calls = lc.getCalls();
        if (calls == null || calls.length == 0) return;

        Call nextcall = calls[0];
        LinphoneContext.instance().DisplayCall(nextcall);
    }

    private String GetAdressString(Call call) {

        Address remoteAddress = call.getRemoteAddress();
        String remAddress = "";
        if (remoteAddress != null) {
            remAddress = remoteAddress.asString().toLowerCase();
        }
        return remAddress;
    }

    private Boolean TryTerminateActiveCall(Boolean fromHangupReceiver) {

        try {
            if (!LinphoneContext.isReady()) return false;

            LinphoneManager.getInstance()
                    .resetClassicMenuLayoutAndGoBackToCallIfStillRunning(fromHangupReceiver);

            return true;

        } catch (Exception e) {
            Log.e(tag, "Exception TryTerminateActiveCall: " + e.getMessage());
        }
        return false;
    }
}
