package org.linphone.clb;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {

    private static String tag = "hashHelper";

    public static String calculateHash(final String key, final String value) {
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

    private static void log(String text) {
        org.linphone.core.tools.Log.i(text);
        Log.i(tag, text);
    }
    private static void logError(String text) {
        org.linphone.core.tools.Log.i(text);
        Log.e(tag, text);
    }

}
