package org.linphone.clb;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * PermissionHelper: Request extra permission needed for CLB implementeation
 *
 * <ul>
 *   <li>04-05-21 rvdillen inital version
 * </ul>
 */
public class PermissionHelper {

    private static PermissionHelper instance;

    private String tag = "ClbPermission";

    public static final synchronized PermissionHelper instance() {
        if (instance == null) {
            instance = new PermissionHelper();
        }
        return instance;
    }

    public void CheckPermissions(Activity context) {

        List<String> permissions = new ArrayList<String>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(
                            context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (permissions.size() > 0)
            ActivityCompat.requestPermissions(context, permissions.toArray(new String[0]), 0);
    }

    public boolean CheckOverlayPermission(Activity context) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (Settings.canDrawOverlays(context)) {
            return true;
        }

        try {
            String packageName = context.getPackageName();
            Intent intent =
                    new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + packageName));
            context.startActivityForResult(intent, 1234);
        } catch (Exception ex) {
            android.util.Log.i(tag, "CheckOverlayPermission failed: " + ex.getMessage());
        }

        return true;
    }

    private PermissionHelper() {}
}
