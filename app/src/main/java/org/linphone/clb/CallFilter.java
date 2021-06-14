package org.linphone.clb;

import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallLog;

/**
 * CallFilter: CLB class to filter calls from CLB Hardware out of History. <br>
 *
 * <ul>
 *   <li>25-08-2020. RvD Initial version
 * </ul>
 */
public class CallFilter {

    public static List<CallLog> RemoveCallsFromHardware(List<CallLog> logs) {

        List<CallLog> nonHardwareCalls = new ArrayList<>();

        try {
            int size = logs.size();
            for (int i = 0; i < size; i++) {
                boolean fromHardWare = false;
                CallLog log = logs.get(i);
                if (log.getDir() == Call.Dir.Outgoing) {
                    Address toAdress = log.getToAddress();
                    String sipUri = toAdress.asStringUriOnly().toLowerCase();
                    if (sipUri.contains("clbinfo") || sipUri.contains(("clbsessionid"))) {
                        fromHardWare = true;
                    }
                }
                if (!fromHardWare) {
                    nonHardwareCalls.add(log);
                }
            }
        } catch (Exception e) {
            String messgae = e.getMessage();
            Log.e("tag", "Exception RemoveCallsFromHardware: " + e.getMessage());
        }

        return nonHardwareCalls;
    }
}
