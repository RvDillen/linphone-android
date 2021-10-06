package org.linphone.clb;

import android.content.Context;
import android.os.Build;

import org.linphone.LinphoneApplication;
import org.linphone.core.Config;
import org.linphone.core.tools.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * LinphonePreferencesCLB: CLB class to overwrite settings when starting up app.<br>
 * Including:
 *
 * <ul>
 *   <li>Overwrite settings using ini-file in download-folder
 *   <li>Overwrite settings using xml-file in download-folder
 * </ul>
 *
 * <ul>
 *   <li>15-04-20 rvdillen Settings of .XML is not imported correctly in Linphone(BG-10060)
 *   <li>xx-04-20 rvdillen Update to Linphone 4.2
 *   <li>29-01-19 rvdillen inital version
 * </ul>
 *
 * Created by user Robert on 29-1-19.
 */
public class LinphonePreferencesCLB {

    private static LinphonePreferencesCLB instance;

    private String tag = "ClbConfig";
    private String basePath = "";
    private List<String> dataPaths = new ArrayList<String>();
    private List<String> logLines = new ArrayList<String>();

    public static final synchronized LinphonePreferencesCLB instance() {
        if (instance == null) {
            instance = new LinphonePreferencesCLB();
        }
        return instance;
    }

    private LinphonePreferencesCLB() {}

    public boolean UpdateFromLinphoneRcData(String linphonercText, String configPath) {

        LogLine(" ** Start linphonerc changed => update settings **");

        // Not found local linphonerc? => Notify
        File linphonerc = new File(configPath);
        if (linphonerc.exists() == false)
            LogLine("linphonerc file not present: " + configPath);

        // Overwrite settings
        return HandleRcChanges(linphonercText, linphonerc);
    }

    /* ExportLinphoneRcFile
     * Export content of current config
     */
    public void ExportLinphoneRcFile(Context context) {

        Config config = LinphoneApplication.corePreferences.getConfig();
        String export = "\n\n" + config.dump();

        LogLine(" ** export config Linphonerc **");
        largeLog(export);

        WriteLogLines();
    }

    /* HandleLocalRcFile
     * Found local linphonerc file, copy over existing (in app files location)
     */
    private boolean HandleRcChanges(String linphonercText, File linphonerc) {

        String origin = linphonerc.getAbsolutePath();
        LogLine("Linphone RC data:");
        largeLog(linphonercText);

        boolean result = false;

        try {
            byte[] buffer = linphonercText.getBytes();

            File targetFile = new File(linphonerc.getAbsolutePath());
            OutputStream outStream = new FileOutputStream(targetFile);
            outStream.write(buffer);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)   {
                android.os.FileUtils.closeQuietly(outStream);
            }

            LogLine("Linphone RC data succesfull imported");
            LogLine("");
            result = true;

        } catch (FileNotFoundException e) {
            LogException("Error: Import Linphone RC data failed, not found: ", e);
        } catch (IOException e) {
            LogException("Error: Import Linphone RC data failed, IO: ", e);
        } catch (Exception e) {
            LogException("Error: Import Linphone RC data failed: ", e);
        } finally {
        }
        return result;
    }


    public boolean UpdateFromLinphoneXmlData(String linphonercXml, Config config) {

        LogLine(" ** Start linphonerc Xml changed => update settings **");

        // Overwrite settings
        return HandleXmlChanges(linphonercXml, config);
    }


    /* HandleLocalXmlFile
     * Found local linphonerc XML file, copy values over existing Config(ini)
     */
    private boolean HandleXmlChanges(String linphoneXml, Config config) {

        LogLine("Update Linphonerd from XML");
        boolean result = false;
        if (config == null) {
            LogLine("HandleLocal Xml data: config is null");
            return result;
        }

        // Read xml data
        InputStream stream = null;
        XmlPullParserFactory xmlFactoryObject = null;
        try {

            // Open Xml Parser from file
            stream = new ByteArrayInputStream(linphoneXml.getBytes());
            xmlFactoryObject = XmlPullParserFactory.newInstance();
            XmlPullParser myParser = xmlFactoryObject.newPullParser();
            myParser.setInput(stream, null);

            // Proces
            LogLine("processParsing XML");
            processParsing(myParser, config);
            config.sync();

            // Close & delete
            if (stream != null) stream.close();
            stream = null;

            LogLine("Linphonerc Xml import succeeded. Settings are: ");
            String dump = config.dumpAsXml();
            largeLog(dump);
            result = true;

        } catch (Exception e) {
            LogLine("Error Import Linphonerc Xml Data failed: " + e.getMessage());
        }
        return result;
    }


    private String sectionKey = "section";
    private String entryKey = "entry";

    private void processParsing(XmlPullParser parser, Config lpConfig)
            throws IOException, XmlPullParserException {

        int eventType = parser.getEventType();
        SettingClb current = null;
        String overwrite = null;
        LogLine("Write settings:");

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String eltName = null;

            switch (eventType) {
                case XmlPullParser.START_TAG:
                    eltName = parser.getName();

                    if (sectionKey.equals(eltName)) {
                        current = new SettingClb();
                        current.section = parser.getAttributeValue(null, "name");
                    } else if (current != null) {
                        if (entryKey.equals(eltName)) {
                            current.entry = parser.getAttributeValue(null, "name");
                            overwrite = parser.getAttributeValue(null, "overwrite");

                            // Try Get entry value
                            try {
                                    current.value = parser.nextText();
                            } catch (Exception e) {
                                String message = String.format("Error entry: %s-%s, Reading value: %s", current.section, current.entry, e.getMessage());
                                LogLine(message);
                            }

                            // Try Store entry value
                            try {
                                // Attrib "overwrite" = true ?  => Overwrite value in config file.
                                if ("true".equalsIgnoreCase(overwrite)) {
                                    lpConfig.setString(current.section, current.entry, current.value);
                                    String message =
                                            String.format(
                                                    "- write: %s - %s = %s",
                                                    current.section, current.entry, current.value);
                                    LogLine(message);
                                }
                            } catch (Exception e) {
                                String message = String.format("Error entry: %s - %s, Parsing value: %s", current.section, current.entry, e.getMessage());
                                LogLine(message);
                            }
                        }
                    }
                    break;
            }

            eventType = parser.next();
        }
    }

    // largeLog => Logging of large string > logcat buffer (=4096)
    public void largeLog(String content) {
        if (content.length() > 4000) {
            String substring = content.substring(0, 4000);
            int index = substring.lastIndexOf("</section>");
            if (index == -1) index = substring.length();
            else index = index + 10;

            LogLine("content:" + content.length() + ", " + index);
            LogLine(" " + content.substring(0, index));
            largeLog(content.substring(index));
        } else {

            LogLine("content:" + content.length());
            LogLine(" " + content);
        }
    }

    private void LogException(String message, Exception... e) {
        if (e == null) LogLine(message);
        else LogLine(message + e.toString());
    }



    public boolean HasLogInfo(){

        return logLines.isEmpty() == false;
    }

    public void WriteLogLines() {
        // Logging of ini/xml file is postponed ,cause on start of proces, Linphone logging is not
        // active.

        if (logLines.isEmpty()) {
            for (String path : dataPaths) {
                Log.i(tag, "No settings files found at location: " + path);
            }
            return;
        }

        // Yes found files => log proces
        Log.i(tag, "  ** Linphone rc: Setting changed: **");

        for (String logLine : logLines) {
            Log.i(tag, ' ' + logLine); // => Linphone log
        }
        logLines.clear();
    }

    private void LogLine(String logLine) {
        logLines.add(logLine); // => Linphone log
       // android.util.Log.i(tag, logLine); // => android log
    }

    class SettingClb {
        String section = null;
        String entry = null;
        String value = null;
    }
}
