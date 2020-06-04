package org.linphone.clb;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import org.linphone.core.Config;
import org.linphone.core.Factory;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

    public void CheckOnLocalIniFile(Context context) {

        // BasePath => Where Linphone is
        basePath = context.getFilesDir().getAbsolutePath();

        // Data folder on Android (=> Download folder!)
        dataPaths = GetDataPaths(context);
        for (String dataPath : dataPaths) {

            // Found local linphonerc? => Overwrite settings
            File linphonerc = new File(dataPath + "/linphonerc");
            if (linphonerc.exists()) {
                HandleLocalRcFile(linphonerc);
            }
        }
    }

    private List<String> GetDataPaths(Context context) {

        List<String> paths = new ArrayList<String>();

        // Now Download folder
        String path =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        .getAbsolutePath();
        paths.add(path);
        return paths;

        //      Old way  => /sdcard
        //        if (Environment.getExternalStorageState() != null){
        //            path = Environment.getExternalStorageDirectory().getAbsolutePath();
        //            paths.add(path);

        //            path =
        // Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        //            paths.add(path);

        //            String result = System.getenv("EXTERNAL_STORAGE");
        //            if(result != null)
        //                paths.add(result);
        //        }
    }

    public void CheckOnLocalXmlFile(Context context) {
        //    public void CheckOnLocalXmlFile(Config config) {

        for (String dataPath : dataPaths) {
            // Found local linphonerc.Xml? => Overwrite settings
            File linphoneXml = new File(dataPath + "/linphonerc.xml");
            if (linphoneXml.exists()) {

                try {
                    String origin = basePath + "/.linphonerc";
                    Config config = Factory.instance().createConfig(origin);

                    HandleLocalXmlFile(linphoneXml, config);
                } catch (Exception ex) {
                    LogLine("CheckOnLocalXmlFile failed: " + ex.getMessage());
                }
            }
        }
    }

    public void ExportLinphoneRcFile(Context context) {

        Config config = LinphonePreferences.instance().getConfig();
        String export = config.dump();

        // Data folder on Android (=> Download folder!)
        dataPaths = GetDataPaths(context);
        for (String dataPath : dataPaths) {

            String fileName = dataPath + "/linphonerc.bak";
            LogLine("Export filename is: " + fileName);

            // Found local linphonerc.bak => Delete
            File linphonerc = new File(fileName);
            if (linphonerc.exists()) {
                TryDeleteFile(linphonerc);
            }

            // Write Linphone dump => linphonerc.bak
            LogLine("Export Linphonerc to: " + fileName);
            try (PrintStream out = new PrintStream(new FileOutputStream(fileName))) {
                out.print(export);
                LogLine("Export Linphonerc successfull");
            } catch (Exception e) {
                Log("Export LinphoneRc fails", e);
            }
        }
        LogSettingChanges();
    }

    /* HandleLocalRcFile
     * Found local linphonerc file, copy over existing (in app files location)
     * use 'classic' copy cause linphone supports api 16
     */
    private void HandleLocalRcFile(File linphonerc) {

        String local = linphonerc.getAbsolutePath();
        String origin = basePath + "/.linphonerc";
        Log("HandleLocalRcFile for: " + local);

        FileChannel sourceChannel = null;
        FileChannel destChannel = null;
        try {
            sourceChannel = new FileInputStream(local).getChannel();
            destChannel = new FileOutputStream(origin).getChannel();
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
            sourceChannel.close();
            sourceChannel = null;
            destChannel.close();

            TryDeleteFile(linphonerc);

        } catch (FileNotFoundException e) {
            Log("HandleLocalRcFile failed, not found: ", e);
        } catch (IOException e) {
            Log("HandleLocalRcFile failed, IO: ", e);
        } catch (Exception e) {
            Log("HandleLocalRcFile failed: ", e);
        } finally {
        }
    }

    /* HandleLocalXmlFile
     * Found local linphonerc XML file, copy values over existing Config(ini)
     */
    private void HandleLocalXmlFile(File linphoneXml, Config config) {

        LogLine("HandleLocalXmlFile for: " + linphoneXml.getAbsolutePath());

        Config lpConfig = config; // LinphonePreferences.instance().getConfig();

        if (lpConfig == null) {
            LogLine("HandleLocalXmlFile failed: config is null");
            return;
        }

        // Read xml settings file
        FileInputStream stream = null;
        XmlPullParserFactory xmlFactoryObject = null;
        try {
            /*
                       CLB: lpConfig.loadFromXmlFile ....
                       Yes, that's what we want, but ..... doesn't work yet.....(grr)

                        Log.e("CLB loadfrom xml start");
                        String fileName = linphoneXml.getAbsolutePath();
                        lpConfig.loadFromXmlFile(fileName);
                        lpConfig.sync();
            */

            // Open Xml Parser from file
            stream = new FileInputStream(linphoneXml.getAbsolutePath());
            xmlFactoryObject = XmlPullParserFactory.newInstance();
            XmlPullParser myParser = xmlFactoryObject.newPullParser();
            myParser.setInput(stream, null);

            // Proces
            LogLine("processParsing XML");
            processParsing(myParser, lpConfig);
            lpConfig.sync();

            // Close & delete
            if (stream != null) stream.close();
            stream = null;

            TryDeleteFile(linphoneXml);

            LogLine("Validate Xml settings dump: ");
            String dump = lpConfig.dumpAsXml();
            largeLog(dump);

        } catch (Exception e) {
            LogLine("clb HandleLocalXmlFile failed: " + e.getMessage());
        }
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
                            current.value = parser.nextText();

                            try {
                                // Attrib "overwrite" = true ?  => Overwrite value in config file.
                                if ("true".equalsIgnoreCase(overwrite)) {
                                    lpConfig.setString(
                                            current.section, current.entry, current.value);
                                    String message =
                                            String.format(
                                                    "- write: %s, %s, %s",
                                                    current.section, current.entry, current.value);
                                    LogLine(message);
                                }
                            } catch (Exception e) {
                                LogLine("Parsing exception: " + e.getMessage());
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

    private void Log(String message, Exception... e) {
        if (e == null) LogLine(message);
        else LogLine(message + e.toString());
    }

    private void TryDeleteFile(File linphonerc) {

        String path = linphonerc.getAbsolutePath();
        if (linphonerc.delete()) Log("Successfull removed file: " + path);
        else Log("Failed Remove file: " + path);
    }

    public void LogSettingChanges() {
        // Logging of ini/xml file is postponed ,cause on start of proces, Linphone logging is not
        // active.

        if (logLines.isEmpty()) {
            for (String path : dataPaths) {
                Log.i(tag, "No settings files found at location: " + path);
            }
            return;
        }

        // Yes found files => log proces
        Log.i(tag, "** Local settings file (ini/xml):");

        for (String logLine : logLines) {
            Log.i(tag, ' ' + logLine); // => Linphone log
        }
        logLines.clear();
    }

    private void LogLine(String logLine) {
        logLines.add(logLine); // => Linphone log
        android.util.Log.i(tag, logLine); // => android log
    }

    class SettingClb {
        String section = null;
        String entry = null;
        String value = null;
    }
}
