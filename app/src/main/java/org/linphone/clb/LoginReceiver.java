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

        if ((sipUsername == "") || (sipPassword == "")) return;

        // Default hangup behaviour (no uri or terminate uri failed)
        LinphoneContext lc2 = LinphoneContext.instance();
        Core lc = LinphoneManager.getCore();
        ProxyConfig mainConfig = lc.getDefaultProxyConfig();
        Address mainAddress = mainConfig.getIdentityAddress();
        ProxyConfig proxyConfig = lc.getDefaultProxyConfig();

        // New Proxy
        String message = "";
        try {
            proxyConfig.edit();

            // Create new Adress
            String sipUri = "sip:" + sipUsername + "@" + mainAddress.getDomain();
            Address adress = Factory.instance().createAddress(sipUri);
            adress.setPassword(sipPassword);
            adress.setTransport(mainAddress.getTransport());
            adress.setDisplayName(sipUsername);
            proxyConfig.setIdentityAddress(adress);

            // Create new Authentication
            AuthInfo authInfo1 =
                    Factory.instance()
                            .createAuthInfo(sipUsername, null, sipPassword, null, null, null);
            authInfo1.setHa1(null);
            authInfo1.setPassword(sipPassword);
            authInfo1.setAlgorithm(null);
            lc.addAuthInfo(authInfo1);

            proxyConfig.setExpires(36000);
            proxyConfig.setPublishExpires(3600);

            proxyConfig.enableRegister(true);
            proxyConfig.done();
            proxyConfig.refreshRegister();

        } catch (Exception e) {
            message = e.getMessage();
            e.printStackTrace();
        }

        // Publish Login
        Intent intentMessage = new Intent(STATE_CONNECTSTATE);
        intentMessage.putExtra("connectstate", "LoggedIn");

        Context appContext = LinphoneContext.instance().getApplicationContext();
        appContext.sendBroadcast(intentMessage);
    }
}
