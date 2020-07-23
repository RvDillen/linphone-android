package org.linphone.clb;

import static org.linphone.clb.RegisterCLB.STATE_CONNECTSTATE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.core.Core;
import org.linphone.core.ProxyConfig;

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

        Core lc = LinphoneManager.getCore();
        ProxyConfig proxyConfig = lc.getDefaultProxyConfig();

        proxyConfig.edit();

        // Kill current connection
        proxyConfig.setExpires(0);
        proxyConfig.setPublishExpires(0);
        proxyConfig.refreshRegister();
        lc.refreshRegisters();

        proxyConfig.done();
        lc.refreshRegisters();

        // Publish logout state
        Intent intentMessage = new Intent(STATE_CONNECTSTATE);
        intentMessage.putExtra("connectstate", "LoggedOut");

        Context appContext = LinphoneContext.instance().getApplicationContext();
        appContext.sendBroadcast(intentMessage);
    }
}
