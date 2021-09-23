package org.linphone.clb;

import android.app.Activity;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.linphone.utils.PermissionHelper;

/**
 * PermissionHelper: Request extra permission needed for CLB implementeation
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
        if (!PermissionHelper.Companion.required(context).hasRecordAudioPermission()) {
            Log.i(tag, "Microphone permission not granted");
            Toast.makeText(context, "Linphone: Microphone permission not granted", Toast.LENGTH_LONG).show();
            return;
        }

        // Check Overlay Permission
        if (! CheckOverlayPermission(context)){
            Log.i(tag, "Overlay permission not granted");
            Toast.makeText(context, "Linphone: Overlay permission not granted", Toast.LENGTH_LONG).show();
        }

        /* WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(
                            context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        */
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

}
