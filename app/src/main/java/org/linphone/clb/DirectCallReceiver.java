package org.linphone.clb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import org.linphone.clb.kt.CoreContextExt;

import static org.linphone.LinphoneApplication.coreContext;

// import org.linphone.mediastream.Version;

/**
 * DirectCallReceiver: Starts call from CLB Messenger. (without showing the UI)
 *
 * 04-10-21 rvdillen Linphone 4.5.2
 * 26-03-20 rvdillen Added CallStateCLB
 * 18-12-17 mvdhorst inital version
 * 03-01-18 mvdhorst Starts a call without showing the UI.
 */

public class DirectCallReceiver extends BroadcastReceiver {
    private static final String TAG = "DirectCallReceiver";
    private String addressToCall;

    private Handler mHandler;
    private ServiceWaitThread mServiceThread;

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
        Log.i(TAG, "onReceive DirectCallReceiver for: " + addressToCall);

        // CLB C-Serie Uri ? => Validate if transport is defined, if not set UDP.
        String adressLower = addressToCall.toLowerCase();
        if (adressLower.contains("clbsessionid") && adressLower.contains("transport=?")) {
            addressToCall = addressToCall.replace("transport=?", "transport=udp?");
        }

        CallStateCLB.instance().SetCallUri(addressToCall);

        mHandler = new Handler();
        CoreContextExt coreExt = new CoreContextExt();

        if (coreExt.IsServiceReady()) {
            onServiceReady();
        } else {
            Log.i(TAG, "Start linphone as foreground");

            // start linphone as foreground service
            coreExt.StartCoreService(context);

            mServiceThread = new ServiceWaitThread();
            mServiceThread.start();

        }
    }

    protected void onServiceReady() {

        mHandler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "Start call to " + addressToCall);
                        coreContext.startCall(addressToCall);
                    }
                },
                100);
    }

    private class ServiceWaitThread extends Thread {
        public void run() {

            CoreContextExt coreExt = new CoreContextExt();
            while (!coreExt.IsServiceReady()) {
                try {
                    sleep(30);
                } catch (InterruptedException e) {
                    throw new RuntimeException("waiting thread sleep() has been interrupted");
                }
            }
            mHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            onServiceReady();
                        }
                    });
            mServiceThread = null;
        }
    }
}
