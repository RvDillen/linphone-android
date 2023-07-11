package org.linphone.clb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import org.linphone.core.Account;
import org.linphone.core.AccountParams;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;

import static org.linphone.LinphoneApplication.coreContext;
import static org.linphone.clb.RegisterCLB.STATE_CONNECTSTATE;

import java.util.Objects;
import java.util.concurrent.Executor;


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

        if ((Objects.equals(sipUsername, "")) || (Objects.equals(sipPassword, ""))) return;


        Core lc = coreContext.getCore();
        Account mAccount = lc.getDefaultAccount();
        if (mAccount == null) {
            Log.e("[Login Receiver] No proxy config !");
            android.util.Log.w(TAG, "[Login Receiver] No proxy config !");
            return;
        }
        AuthInfo mAuthInfo = mAccount.findAuthInfo();
        AuthInfo newAuthInfo;
        if (mAuthInfo == null) {
            // Create new Authentication
            newAuthInfo =
                    Factory.instance()
                            .createAuthInfo(sipUsername, null, sipPassword, null, null, null);

            CreateNewRegistration(sipUsername, sipPassword, lc, mAccount, mAuthInfo, newAuthInfo);
        } else {
            newAuthInfo = mAuthInfo.clone();

            UnregisterOldAccount(lc, mAccount);

            CreateNewRegistration(sipUsername, sipPassword, lc, mAccount, mAuthInfo, newAuthInfo);
        }
    }

    private void UnregisterOldAccount(Core lc, Account mAccount) {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(
                coreContext.getContext().getMainLooper());
        Runnable myRunnable = () -> {
            AccountParams accountParams = mAccount.getParams().clone();
            // Kill current connection
            accountParams.setExpires(0);
            accountParams.setPublishExpires(0);
            accountParams.setRegisterEnabled(false);
            mAccount.setParams(accountParams);
            lc.refreshRegisters();
        };
        mainHandler.post(myRunnable);

        // Wait some time before checking the unregister state.
        try {
            Thread.sleep(100);
            int i = 0;
            while(i < 50) {
                Log.d(TAG, "[Login Receiver] Unregister state " + i + ":" + mAccount.getState());
                if(mAccount.getState() != RegistrationState.Ok ) {
                    Log.i(TAG, "[Login Receiver] Unregistered successfully");
                    break;
                }
                i++;
                Thread.sleep(100L * i);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void CreateNewRegistration(String sipUsername, String sipPassword, Core lc, Account mAccount, AuthInfo mAuthInfo, AuthInfo newAuthInfo) {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(
                coreContext.getContext().getMainLooper());
        Runnable myRunnable = () -> {
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

            if(mAuthInfo != null)
                lc.removeAuthInfo(mAuthInfo);

            lc.addAuthInfo(newAuthInfo);
            mAccount.setParams(accountParams);
            lc.refreshRegisters();
        };
        mainHandler.post(myRunnable);
        /*
        boolean registered = true;
        try {
            Thread.sleep(100);
            int i = 0;
            while (i < 50) {
                Log.d(TAG, "[Login Receiver] Registration state " + i + ":" + mAccount.getState());
                if(mAccount.getState() == RegistrationState.Ok ) {
                    Log.i(TAG, "[Login Receiver] Registration successfully");
                    registered = true;
                    break;
                }
                i++;
                Thread.sleep(100 * i);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        */
        // Publish Login
        String state = "LoggedIn";
        Intent intentMessage = new Intent(STATE_CONNECTSTATE);
        intentMessage.putExtra("connectstate", state);
        Log.i("[Login Receiver] Login request, send response '" + state + "'");

        Context appContext = coreContext.getContext().getApplicationContext();
        appContext.sendBroadcast(intentMessage);
        CallStateCLB.instance().Restart();
    }
}
