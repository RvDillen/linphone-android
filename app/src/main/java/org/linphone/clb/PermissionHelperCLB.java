package org.linphone.clb;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

/**
 * PermissionHelper: Request extra permission(s) needed for CLB implementation
 */
public class PermissionHelperCLB {

    private static PermissionHelperCLB instance;

    private String tag = "ClbPermission";

    public static final synchronized PermissionHelperCLB instance() {
        if (instance == null) {
            instance = new PermissionHelperCLB();
        }
        return instance;
    }

    public void CheckPermissions(Activity context) {

        // Check Microphone permission
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.i(tag, "Microphone permission not granted");
            Toast.makeText(context, "Linphone: Microphone permission not granted", Toast.LENGTH_LONG).show();
        }

        // Check Overlay Permission
        if (! CheckOverlayPermission(context)){
            Log.i(tag, "Overlay permission not granted");
            Toast.makeText(context, "Linphone: Overlay permission not granted", Toast.LENGTH_LONG).show();
            RequestOverlayPermission(context);
        }
    }

    public boolean CheckOverlayPermission(Activity context) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (Settings.canDrawOverlays(context)) {
            return true;
        }

        return false;
    }

    public void RequestOverlayPermission(Activity context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName())
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            Log.i(tag, "Request overlay permission");
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(tag, "Failed to open overlay permission settings", e);
        }
    }
}
