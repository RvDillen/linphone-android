package org.linphone.clb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

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
        Core lc = coreContext.getCore();
        Account account = lc.getDefaultAccount();
        AccountParams accountParams = account.getParams().clone();
        // Kill current connection
        accountParams.setExpires(0);
        accountParams.setPublishExpires(0);
        accountParams.setRegisterEnabled(false);
        account.setParams(accountParams);
        lc.refreshRegisters();

        // Publish logout state
        Intent intentMessage = new Intent(STATE_CONNECTSTATE);
        intentMessage.putExtra("connectstate", "LoggedOut");

        Context appContext = coreContext.getContext().getApplicationContext();
        appContext.sendBroadcast(intentMessage);
    }
}
