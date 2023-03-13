package org.linphone.clb;

import android.util.Log;


import org.linphone.activities.main.history.data.GroupedCallLogData;
import org.linphone.core.*;


import java.util.ArrayList;
import java.util.List;

/**
 * CallFilter: CLB class to filter calls from CLB Hardware out of History. <br>
 *
 * 04-10-2021 RvD Upgrade to 4.5.2
 * 25-08-2020 RvD Initial version
 */
public class CallFilter {

    // RemoveCallsFromHardware (4.5.2 implementation)
    public static List<GroupedCallLogData> RemoveCallsFromHardware(ArrayList<GroupedCallLogData> logs) {
        List<GroupedCallLogData> nonHardwareCalls = new ArrayList<GroupedCallLogData>();

        try {
            int size = logs.size();
            for (int i = 0; i < size; i++) {
                boolean fromHardware = false;
                GroupedCallLogData groupLog = logs.get(i);
                CallLog log = groupLog.getLastCallLog();
                if (log.getDir() == Call.Dir.Outgoing) {
                    Address toAddress = log.getToAddress();
                    String sipUri = toAddress.asStringUriOnly().toLowerCase();
                    if (sipUri.contains("clbinfo") || sipUri.contains("clbsessionid")) {
                        fromHardware = true;
                    }
                }

                if (!fromHardware) {
                    nonHardwareCalls.add(groupLog);
                }
            }
        } catch (Exception e) {
            String message = e.getMessage();
            Log.e("CallFilter", "Exception RemoveCallsFromHardware: " + message);
        }

        return nonHardwareCalls;
    }

    public static List<CallLog> CallsFromHardwareToRemove_5_0_3(CallLog[] logs) {
        ArrayList<CallLog> hardwareCalls = new ArrayList<CallLog>();

        try {
            int size = logs.length;
            for (int i = 0; i < size; i++) {
                CallLog log = logs[i];
                String sipUri = log.getToAddress().asStringUriOnly().toLowerCase();
                String sipMsg = "SipUri from Call Log: " + sipUri;
                Log.d("CallFilter", sipMsg);

                if (log.getDir() == Call.Dir.Outgoing) {
                    Address toAddress = log.getToAddress();
                    //String sipUri = toAddress.asStringUriOnly().toLowerCase();
                    if (sipUri.contains("clbinfo") || sipUri.contains("clbsessionid")) {
                        hardwareCalls.add(log);
                    }
                    else if (sipUri.contains("ext")) {
                        Log.d("CallFilter", "Call log modification skipped for: '" + sipUri + "' (No CLB info found).");
                    }
                }
            }
        } catch (Exception e) {
            String message = e.getMessage();
            Log.e("CallFilter", "Exception RemoveCallsFromHardware: " + message);
        }

        return hardwareCalls;
    }
}
