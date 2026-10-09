package org.linphone.clb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.linphone.clb.kt.CoreContextExt;
import org.linphone.core.tools.Log;

// import org.linphone.mediastream.Version;

/**
 * DirectCallReceiver: Starts call from CLB Messenger. (without showing the UI)
 *
 * 04-10-21 rvdillen Linphone 4.5.2
 * 26-03-20 rvdillen Added CallStateCLB
 * 18-12-17 mvdhorst initial version
 * 03-01-18 mvdhorst Starts a call without showing the UI.
 */

public class DirectCallReceiver extends BroadcastReceiver {
    private String addressToCall;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isOrderedBroadcast()) abortBroadcast();

        if (intent.hasExtra(("uri"))) {
            addressToCall = intent.getStringExtra("uri");
            addressToCall = addressToCall.replace("%40", "@");
            addressToCall = addressToCall.replace("%3A", ":");
            if (addressToCall.startsWith("sip:")) {
                addressToCall = addressToCall.substring("sip:".length());
            }
        }

        // CLB C-Serie Uri ? => Validate if transport is defined, if not set UDP.
        String adressLower = addressToCall.toLowerCase();
        if (adressLower.contains("clbsessionid") && adressLower.contains("transport=?")) {
            addressToCall = addressToCall.replace("transport=?", "transport=udp?");
        }
        CallStateCLB.instance().SetCallUri(addressToCall);

        Log.i( "[Manager] DirectCallReceiver for: " + addressToCall + " short: " + CallStateCLB.instance().GetCallUriAll());


        CoreContextExt coreExt = new CoreContextExt();
    // CLB: Use lifecycle-gated startup instead of polling a short-lived in-call service.
        coreExt.RequestOutgoingCall(context, addressToCall);
    }
}
