package org.linphone.clb;

import android.util.Log;

import org.linphone.activities.main.history.data.GroupedCallLogData;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallLog;

import java.util.ArrayList;
import java.util.List;

/**
 * CallFilter: CLB class to filter calls from CLB Hardware out of History. <br>
 *
 * <ul>
 *   <li>25-08-2020. RvD Initial version
 * </ul>
 */
public class CallFilter {

    // RemoveCallsFromHardware (4.5.2 implementation)
    public static List<GroupedCallLogData> RemoveCallsFromHardware(ArrayList<GroupedCallLogData> logs) {
        List<GroupedCallLogData> nonHardwareCalls = new ArrayList<GroupedCallLogData>();

        try {
            int size = logs.size();
            for (int i = 0; i < size; i++) {
                boolean fromHardWare = false;
                GroupedCallLogData groupLog = logs.get(i);
                CallLog log = groupLog.getLastCallLog();
                if (log.getDir() == Call.Dir.Outgoing) {
                    Address toAdress = log.getToAddress();
                    String sipUri = toAdress.asStringUriOnly().toLowerCase();
                    if (sipUri.contains("clbinfo") || sipUri.contains(("clbsessionid"))) {
                        fromHardWare = true;
                    }
                }

                if (!fromHardWare) {
                    nonHardwareCalls.add(groupLog);
                }
            }
        } catch (Exception e) {
            String messgae = e.getMessage();
            Log.e("tag", "Exception RemoveCallsFromHardware: " + e.getMessage());
        }

        return nonHardwareCalls;
    }


    // RemoveCallsFromHardware (4.2.3 implementation)
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
