package org.linphone.clb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import org.linphone.clb.kt.CoreContextExt;

import org.linphone.mediastream.Version;

import static org.linphone.LinphoneApplication.coreContext;

// import org.linphone.mediastream.Version;

/**
 * DirectCallReceiver: Starts call from CLB Messenger. (without showing the UI)
 *
 * <ul>
 *   <li>26-03-20 rvdillen Added CallStateCLB
 *   <li>18-12-17 mvdhorst inital version
 * </ul>
 */
/** Created by mvdhorst on 3-1-18. Starts a call without showing the UI. */
public class DirectCallReceiver extends BroadcastReceiver {
    private static final String TAG = "DirectCallReceiver";
    private String addressToCall;

    private Handler mHandler;
    private ServiceWaitThread mServiceThread;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isOrderedBroadcast()) abortBroadcast();
        Log.i(TAG, "onReceive DirectCallReceiver ");
        /*
        LinphoneCore lc = LinphoneManager.getLc();
        LinphoneCall currentCall = lc.getCurrentCall();
        */
        if (intent.hasExtra(("uri"))) {
            addressToCall = intent.getStringExtra("uri");
            addressToCall = addressToCall.replace("%40", "@");
            addressToCall = addressToCall.replace("%3A", ":");
            if (addressToCall.startsWith("sip:")) {
                addressToCall = addressToCall.substring("sip:".length());
            }
        }
        // Forcing init of Callstate
        CallStateCLB.instance().IsCallFromCLB();

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
        // We need LinphoneService to start bluetoothManager
        if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
            Log.i(TAG, "We need LinphoneService to start bluetoothManager");

            // Audiomanager will instatiate bluetooth.
           // LinphoneManager.getAudioManager();
            // BluetoothManager.getInstance().initBluetooth();
        }

        mHandler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "Start call to " + addressToCall);
                        coreContext.startCall(addressToCall);
                        //LinphoneManager.getCallManager().newOutgoingCall(addressToCall, null);
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
