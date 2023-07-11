package org.linphone.clb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import org.linphone.core.Account;
import org.linphone.core.AccountParams;
import org.linphone.core.Core;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;

import static org.linphone.LinphoneApplication.coreContext;
import static org.linphone.clb.RegisterCLB.STATE_CONNECTSTATE;

/**
 * LogoutReceiver: Logout from CLB Messenger.
 *
 * <ul>
 *   <li>13-07-20 rvdillen inital version
 * </ul>
 */
public class LogoutReceiver extends BroadcastReceiver {
    private static final String TAG = "LogoutReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.i("[Logout Receiver] Logout request received");
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(
                coreContext.getContext().getMainLooper());
        Runnable myRunnable = () -> {
            Core lc = coreContext.getCore();
            Account account = lc.getDefaultAccount();
            if (account == null) {
                Log.e("[Logout Receiver] No proxy config !");
                android.util.Log.w(TAG, "[Logout Receiver] No proxy config !");
                return;
            }
            AccountParams accountParams = account.getParams().clone();
            // Kill current connection
            accountParams.setExpires(0);
            accountParams.setPublishExpires(0);
            accountParams.setRegisterEnabled(false);
            account.setParams(accountParams);
            lc.refreshRegisters();
        };
        mainHandler.post(myRunnable);

        // Publish logout state
        Intent intentMessage = new Intent(STATE_CONNECTSTATE);
        intentMessage.putExtra("connectstate", "LoggedOut");

        Context appContext = coreContext.getContext().getApplicationContext();
        appContext.sendBroadcast(intentMessage);
    }
}
