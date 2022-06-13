package org.linphone.clb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.linphone.core.Account;
import org.linphone.core.AccountParams;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;

import static org.linphone.LinphoneApplication.coreContext;
import static org.linphone.clb.RegisterCLB.STATE_CONNECTSTATE;


/**
 * LoginReceiver: Starts Login from CLB Messenger. (without showing the UI)
 *
 * <ul>
 *   <li>13-07-20 rvdillen inital version
 * </ul>
 */
public class LoginReceiver extends BroadcastReceiver {
    private static final String TAG = "LoginReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        String sipUsername = "";
        if (intent.hasExtra("sipUsername")) {
            sipUsername = intent.getStringExtra("sipUsername");
        }
        String sipPassword = "";
        if (intent.hasExtra("sipPassword")) {
            sipPassword = intent.getStringExtra("sipPassword");
        }

        Log.i("[Login Receiver] Login request received from: " + sipUsername);

        if ((sipUsername == "") || (sipPassword == "")) return;

        // Default hangup behaviour (no uri or terminate uri failed)
        Core lc = coreContext.getCore();
        Account mAccount = lc.getDefaultAccount();
        if (mAccount == null) {
            Log.e("[Login Receiver] No proxy config !");
            android.util.Log.w(TAG, "[Login Receiver] No proxy config !");
            return;
        }
        AuthInfo mAuthInfo = mAccount.findAuthInfo();
        AuthInfo newAuthInfo = mAuthInfo;
        if (mAuthInfo == null) {
            // Create new Authentication
            newAuthInfo =
                    Factory.instance()
                            .createAuthInfo(sipUsername, null, sipPassword, null, null, null);
        } else {
            newAuthInfo = mAuthInfo.clone();

            AccountParams accountParams = mAccount.getParams().clone();
            // Kill current connection
            accountParams.setExpires(0);
            accountParams.setPublishExpires(0);
            accountParams.setRegisterEnabled(false);
            mAccount.setParams(accountParams);
            lc.refreshRegisters();
        }

        newAuthInfo.setUserid(sipUsername);

        newAuthInfo.setUsername(sipUsername);
        AccountParams accountParams = mAccount.getParams().clone();
        accountParams.setExpires(3600);
        accountParams.setPublishExpires(3600);

        accountParams.setRegisterEnabled(true);

        Address identity = accountParams.getIdentityAddress();
        if (identity != null) {
            identity = identity.clone();
            identity.setUsername(sipUsername);
            identity.setDisplayName(sipUsername);
        }
        accountParams.setIdentityAddress(identity);

        newAuthInfo.setHa1(null);
        newAuthInfo.setPassword(sipPassword);
        // Reset algorithm to generate correct hash depending on
        // algorithm set in next to come 401
        newAuthInfo.setAlgorithm(null);

        lc.removeAuthInfo(mAuthInfo);
        lc.addAuthInfo(newAuthInfo);
        mAccount.setParams(accountParams);
        lc.refreshRegisters();

        // Publish Login
        Intent intentMessage = new Intent(STATE_CONNECTSTATE);
        intentMessage.putExtra("connectstate", "LoggedIn");
        Log.i("[Login Receiver] Login request, send response 'LoggedIn'");

        Context appContext = coreContext.getContext().getApplicationContext();
        appContext.sendBroadcast(intentMessage);
        CallStateCLB.instance().Restart();
    }
}
