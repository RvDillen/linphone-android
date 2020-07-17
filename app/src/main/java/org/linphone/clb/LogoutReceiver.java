package org.linphone.clb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.core.Address;
import org.linphone.core.Core;
import org.linphone.core.ProxyConfig;

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

        String sipUsername = "";
        String sipPassword = "";

        Core lc = LinphoneManager.getCore();
        ProxyConfig proxyConfig = lc.getDefaultProxyConfig();
        Address address = proxyConfig.getIdentityAddress();

        proxyConfig.edit();
        proxyConfig.setExpires(0);
        proxyConfig.setPublishExpires(0);
        proxyConfig.refreshRegister();
        lc.refreshRegisters();

        address.setUsername(sipUsername);
        address.setPassword(sipPassword);
        proxyConfig.setIdentityAddress(address);
        proxyConfig.done();

        lc.refreshRegisters();

        /* Remove all authentications
                AuthInfo[] authInfos = lc.getAuthInfoList();
                for (AuthInfo authInfo : authInfos) {
                    lc.removeAuthInfo(authInfo);
                }
                lc.clearAllAuthInfo();
        */
        Intent intentMessage = new Intent(STATE_CONNECTSTATE);
        intentMessage.putExtra("connectstate", "LoggedOut");

        Context appContext = LinphoneContext.instance().getApplicationContext();
        appContext.sendBroadcast(intentMessage);
    }
}
