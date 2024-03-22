package org.linphone.clb;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;
import android.util.Log;

import org.linphone.core.CorePreferences;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

public class AppConfigHelper {

    private final String tag = "AppConfigHelper";
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

        _rcString = "";
        _rcHash = null;
        _rcXmlString = "";
        _rcXmlHash = null;
    }

    public void checkAppConfig() {

        log("Checking RestrictionsManager for settings.");

        RestrictionsManager rm = (RestrictionsManager)_context.getSystemService(Context.RESTRICTIONS_SERVICE);
        _bundle = rm.getApplicationRestrictions();

        // For testing!!!
        /*
        if (_bundle.isEmpty() && true) {
            _bundle.putString(linphoneRc_key, "test value for linphone rc");
            _bundle.putString(linphoneRcXml_key, "some test value for linphone rc xml");
        }
        */

        // Do parse! Even when bundle is empty, so internal variables get correct values
        parseConfiguration(_bundle);
    }

    public boolean linphoneRcHasChanges() {
        // Compare stored hash against calculated hash
        try {
            String storedHash = getHash(linphoneRc_key);

            log("Rc hash compare ["+storedHash+"] with ["+_rcHash+"]");

            if (_rcHash != null) {
                boolean areEqual = _rcHash.equals(storedHash);

                String hasChanges = areEqual ? "no" : "yes";
                log("linphoneRc has changes: " + hasChanges);

                return !areEqual;
            }

        } catch (Exception ex) {
            logError("Exception: " + ex.getMessage());
        }
        return false;
    }

    public boolean linphoneRcXmlHasChanges(String config) {
        try {
            String storedHash = getHash(linphoneRcXml_key);
            boolean areEqual = true;

            if (! _rcXmlString.isEmpty()) {
                log("RcXml Hash compare ["+storedHash+"] with ["+_rcXmlHash+"]");

                areEqual = _rcXmlHash.equals(storedHash);

                String hasChanges = areEqual ? "no": "yes";
                log("linphoneRcXml has changes: " + hasChanges);

            } else if (config != null && !config.isEmpty()) {
                String fileHash = getHash(config);
                log("RcXml passed as string. Compare ["+storedHash+"] with ["+fileHash+"]");

                areEqual = _rcXmlHash.equals(fileHash);

                String hasChanges = areEqual ? "no" : "yes";
                log("linphoneRcXml has changes: " + hasChanges);

            } else {
                log("linphoneRcXml is empty, ignoring configuration.");
            }

            return ( ! areEqual);

        } catch (Exception ex) {
            logError("Exception: " + ex.getMessage());
        }
        return false;
    }

    public String getLinphoneRc() {
        return parseLinphoneRc(_rcString);
    }

    public String getLinphoneRcXml() {
        return _rcXmlString;
    }

    public void storeRcHash() {
        if (linphoneRcHasChanges()) {
            storeHash(linphoneRc_key);
        }
    }

    public void storeRcXmlHash() {
        if (linphoneRcXmlHasChanges(null)) {
            storeHash(linphoneRcXml_key);
        }
    }


    private String calculateHash(final String key, final String value) {
        String hash = key + value;

        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            logError("MD5 algorithm not available for calculating hash. Abort!");
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

        log("calculateHash("+key+"): " + hexString.toString());
        return hexString.toString();
    }

    private void parseConfiguration(Bundle bundle) {

        Set<String> keys = bundle.keySet();
        int i=0;

        for (String key : keys) {
            log("Key("+i+"): " + key);
            log(key + " : " + bundle.getString(key));
            i++;
        }

        // Intended use: Full configuration, missing values will NOT be configured
        if (bundle.containsKey(linphoneRc_key)) {
            log("parsing linphoneRc config");
            _rcString = bundle.getString(linphoneRc_key);
            log("_rcString: " + _rcString);
            _rcHash = calculateHash(linphoneRc_key, _rcString);
        } else {
            log("Bundle does not contain linphoneRc key. Return empty string.");
            _rcString = "";
        }

        // Intended use: Partial configuration, missing values will fall back to defaults
        if (bundle.containsKey(linphoneRcXml_key)) {
            log("parsing linphoneRc XML config");
            _rcXmlString = bundle.getString(linphoneRcXml_key);
            log("_rcXmlString: " + _rcXmlString);
            _rcXmlHash = calculateHash(linphoneRcXml_key, _rcXmlString);
        } else {
            log("Bundle does not contain linphoneRcXml key. Return empty string.");
            _rcXmlString = "";
        }
    }

    private void storeHash(String key) {
        log("Try store hash for key: " + key);
        try {
            if (key.equals(linphoneRc_key)) {
                log("Storing hash for (" + key + "): " + _rcHash);
                _corePreferences.setLinphoneRcHash(_rcHash);
            }
            if (key.equals(linphoneRcXml_key)) {
                log("Storing hash for (" + key + "): " + _rcXmlHash);
                _corePreferences.setLinphoneRcXmlHash(_rcXmlHash);
            }
        } catch (Exception ex) {
            log("storeHash() Exception: " + ex.getMessage());
        }
    }

    private String getHash(String key) {
        String hash = null;
        if (key.equals(linphoneRc_key))
            hash = _corePreferences.getLinphoneRcHash();
        else if (key.equals(linphoneRcXml_key))
            hash = _corePreferences.getLinphoneRcXmlHash();

        log("Return hash for ("+key+"): " + hash);
        return hash == null ? "" : hash;
    }

    private void log(String text) {
        org.linphone.core.tools.Log.i(text);
        Log.i(tag, text);
    }
    private void logError(String text) {
        org.linphone.core.tools.Log.i(text);
        Log.e(tag, text);
    }

    /*
    linphoneRc is expected to contain a *.ini file format. The MDM (GoogleWorkspace) returns one long string without \r\n
    parseLinphoneRc re-injects line-endings at the desired locations so Linphone will correctly parse the string's contents.

    The entire string is loaded into a StringBuilder. The string is parsed back to front.
    Each time a change is made, a smaller substring is used from the StringBuilder's content, until no more changes are made.
    The resulting output is returned as string
    */
    public static String parseLinphoneRc(String rcString) {

        rcString = rcString.trim();

        int subLength = rcString.length();
        boolean hasChange = true;

        StringBuilder bob = new StringBuilder(rcString.substring(0, subLength));
        String subString = "";

        while (hasChange) {

            hasChange = false;

            // Take substring
            subString = bob.substring(0, subLength);

            // Search through entire rcString
            // Look for '=' or ']'
            // If ']' is found, '[' must be found WITHOUT ANY SPACES between them and ']' should be followed by a 'space'
            //      Replace 'space' after ']' with 'line feed'
            // If '=' is found,  search backwards until a 'space' is encountered.
            //      Replace 'space' with 'line feed'

            int endBraceIndex = subString.lastIndexOf("]");
            int equalsIndex = subString.lastIndexOf("=");


            if (endBraceIndex < equalsIndex) {

                if (equalsIndex != -1) {
                    // Parse 'equals':
                    // Search backwards until a space is encountered
                    int preSpaceIndex = equalsIndex;
                    while (subString.charAt(preSpaceIndex) != ' ' && preSpaceIndex >= 0)
                        preSpaceIndex--;

                    // Spaces inside a 'header' are NOT allowed. And header MUST be followed by a space
                    if (preSpaceIndex >= 0)
                        bob.replace(preSpaceIndex, preSpaceIndex + 1, "\r\n");

                    // For now, just skip
                    subLength = equalsIndex - 1;
                    hasChange = true;
                }

            } else if (endBraceIndex >= 0) {
                // Parse 'brace':
                // Find previous (opposing) brace
                int startBraceIndex = subString.lastIndexOf("[");
                String header = subString.substring(startBraceIndex, endBraceIndex+1);

                // Spaces inside a 'header' are NOT allowed. And header MUST be followed by a space
                if (!header.contains(" ") && subString.charAt(endBraceIndex + 1) == ' ') {
                    bob.replace(endBraceIndex + 1, endBraceIndex + 2, "\r\n");
                }
                // Also, replace a space _BEFORE_ the header if there is one (note that header could at the start of the file/string)
                if (startBraceIndex > 0 && subString.charAt(startBraceIndex-1) == ' ') {
                    bob.replace(startBraceIndex-1, startBraceIndex, "\r\n");
                    subLength = startBraceIndex -1;
                } else {
                    subLength = startBraceIndex;
                }
                hasChange = true;
            }
        }
        return bob.toString();
    }
}
