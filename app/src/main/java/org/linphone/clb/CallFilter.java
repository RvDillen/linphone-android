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

                if (log.getDir() == Call.Dir.Outgoing) {

                    if (sipUri.contains("clbinfo") || sipUri.contains("clbsessionid")) {
                        hardwareCalls.add(log);
                    }
                    else if (sipUri.startsWith("sip:ext1@") || sipUri.startsWith("sip:ext2@")) {
                        // BG-14101 CLB info is lost in CallHistory SIP-URI and items become visible unintentionally.
                        // 2023-03-20 (Discussed with PO): Also filter ext1 or ext2 from CallHistory. Risk that other items use these entries is relatively small.
                        hardwareCalls.add(log);
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
