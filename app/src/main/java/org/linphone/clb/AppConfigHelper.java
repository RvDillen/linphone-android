package org.linphone.clb;

import android.content.Context;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import org.linphone.core.CorePreferences;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AppConfigHelper {

    private final String tag = "acf";
    // LinphonePreferencesCLB internally uses an InputStream to parse/execute XML settings
    // An extra public method accepting an InputStream directly could come in handy.
    // This AppConfigParser should take the settings bundle, extract the string values
    // convert them to an InputStream and call the LinphonePreferencesCLB class to apply the settings
    private final String linphoneRc_key = "Linphonerc";
    private final String linphoneRcXml_key = "LinphonercXml";

    private Context _context;
    private CorePreferences _corePreferences;

    private Bundle _bundle;
    private String _rcString;
    private String _rcXmlString;
    private String _rcHash;
    private String _rcXmlHash;


    public AppConfigHelper(Context context, CorePreferences corePreferences) {
        _context = context;
        _corePreferences = corePreferences;
    }

    public boolean checkAppConfig() {

        RestrictionsManager rm = (RestrictionsManager)_context.getSystemService(Context.RESTRICTIONS_SERVICE);
        _bundle = rm.getApplicationRestrictions();

        // For testing!!!
        if (_bundle.isEmpty() && true) {
            _bundle.putString(linphoneRc_key, "test value for linphone rc");
            _bundle.putString(linphoneRcXml_key, "some test value for linphone rc xml");
        }

        // Do parse, even if bundle is empty so internal variables get correct values
        parseConfiguration(_bundle);

        return linphoneRcHasChanges() || linphoneRcXmlHasChanges();
    }

    public boolean linphoneRcHasChanges() {
        // Compare stored hash against calculated hash
        try {
            String storedHash = getHash(linphoneRc_key);
            return _rcHash != storedHash;
        } catch (Exception ex) {
            Log.e(tag, "Exception: " + ex.getMessage());
        }
        return false;
    }

    public boolean linphoneRcXmlHasChanges() {
        try {
            String storedHash = getHash(linphoneRcXml_key);

            if (!_rcXmlString.isEmpty())
                return _rcXmlHash != storedHash;
        } catch (Exception ex) {
            Log.e(tag, "Exception: " + ex.getMessage());
        }
        return false;
    }

    public String getLinphoneRc() {
        return _rcString;
    }

    public String getLinphoneRcXml() {
        if (linphoneRcXmlHasChanges())
            return _rcXmlString;
        return "";
    }

    public void storeHashes() {
        if (linphoneRcHasChanges())
            storeHash(linphoneRc_key);

        if (linphoneRcXmlHasChanges())
            storeHash(linphoneRcXml_key);
    }



    private String calculateHash(final String key, final String value) {
        String hash = key + value;

        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Log.e(tag, "MD5 algorithm not available for calculating hash");
            return "";
        }

        digest.update(hash.getBytes());

        byte messageDigest[] = digest.digest();

        StringBuilder hexString = new StringBuilder();
        for (byte b : messageDigest) {
            String h = Integer.toHexString(0xFF & b);
            while(h.length() < 2) {
                h = "0" + h;
            }
            hexString.append(h);
        }
        return hexString.toString();
    }

    private void parseConfiguration(Bundle bundle) {

        // Intended use: Full configuration, missing values will NOT be configured
        if (bundle.containsKey(linphoneRc_key)) {
            _rcString = bundle.getString(linphoneRc_key);
        } else {
            _rcString = "";
        }
        _rcHash = calculateHash(linphoneRc_key, _rcString);

        // Partial configuration, missing values will fall back to defaults
        if (bundle.containsKey(linphoneRcXml_key)) {
            _rcXmlString = bundle.getString(linphoneRcXml_key);
        } else {
            _rcXmlString = "";
        }
        _rcXmlHash = calculateHash(linphoneRcXml_key, _rcXmlString);
    }

    private void storeHash(String key) {
        if (key == linphoneRc_key) {
            _corePreferences.setLinphoneRcHash(_rcHash);
        }
        if (key == linphoneRcXml_key) {
            _corePreferences.setLinphoneRcXmlHash(_rcXmlHash);
        }
    }

    private String getHash(String key) {
        String hash = null;
        if (key == linphoneRc_key)
            hash = _corePreferences.getLinphoneRcHash();
        else if (key == linphoneRcXml_key)
            hash = _corePreferences.getLinphoneRcXmlHash();

        return hash == null ? "" : hash;
    }

}
