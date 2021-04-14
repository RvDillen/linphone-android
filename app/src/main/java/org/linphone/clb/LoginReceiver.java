package org.linphone.clb;

import static org.linphone.clb.RegisterCLB.STATE_CONNECTSTATE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;

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

        android.util.Log.d(TAG, "Login request received from: " + sipUsername);

        if ((sipUsername == "") || (sipPassword == "")) return;

        // Default hangup behaviour (no uri or terminate uri failed)
        LinphoneContext lc2 = LinphoneContext.instance();
        Core lc = LinphoneManager.getCore();
        ProxyConfig mProxyConfig = lc.getDefaultProxyConfig();
        if (mProxyConfig == null) {
            Log.e("[Login Receiver] No proxy config !");
            android.util.Log.w(TAG, "[Login Receiver] No proxy config !");
            return;
        }
        AuthInfo mAuthInfo = mProxyConfig.findAuthInfo();

        if (mAuthInfo == null) {
            // Create new Authentication
            mAuthInfo =
                    Factory.instance()
                            .createAuthInfo(sipUsername, null, sipPassword, null, null, null);
        } else {
            ProxyConfig proxyConfig = lc.getDefaultProxyConfig();

            proxyConfig.edit();

            // Kill current connection
            proxyConfig.setExpires(0);
            proxyConfig.setPublishExpires(0);
            proxyConfig.refreshRegister();
            lc.refreshRegisters();

            proxyConfig.done();
            lc.refreshRegisters();
        }

        mAuthInfo.setUserid(sipUsername);

        mAuthInfo.setUsername(sipUsername);

        mProxyConfig.edit();
        mProxyConfig.setExpires(3600);
        mProxyConfig.setPublishExpires(3600);

        mProxyConfig.enableRegister(true);

        Address identity = mProxyConfig.getIdentityAddress();
        if (identity != null) {
            identity.setUsername(sipUsername);
            identity.setDisplayName(sipUsername);
        }
        mProxyConfig.setIdentityAddress(identity);
        mProxyConfig.done();

        mAuthInfo.setHa1(null);
        mAuthInfo.setPassword(sipPassword);
        // Reset algorithm to generate correct hash depending on
        // algorithm set in next to come 401
        mAuthInfo.setAlgorithm(null);

        lc.addAuthInfo(mAuthInfo);
        lc.refreshRegisters();

        // Publish Login
        Intent intentMessage = new Intent(STATE_CONNECTSTATE);
        intentMessage.putExtra("connectstate", "LoggedIn");
        android.util.Log.d(TAG, "Login request, send reponse 'LoggedIn'");

        Context appContext = LinphoneContext.instance().getApplicationContext();
        appContext.sendBroadcast(intentMessage);
    }
}
