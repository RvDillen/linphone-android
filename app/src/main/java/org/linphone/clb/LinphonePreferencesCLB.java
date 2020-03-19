package org.linphone.clb;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import org.linphone.core.Config;
import org.linphone.mediastream.Log;
import org.linphone.settings.LinphonePreferences;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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

    public void CheckOnLocalXmlFile() {

        for (String dataPath : dataPaths) {
            // Found local linphonerc.Xml? => Overwrite settings
            File linphoneXml = new File(dataPath + "/linphonerc.xml");
            if (linphoneXml.exists()) {
                HandleLocalXmlFile(linphoneXml);
            }
        }
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
    private void HandleLocalXmlFile(File linphoneXml) {

        Log("HandleLocalXmlFile for: " + linphoneXml.getAbsolutePath());

        Config lpConfig = LinphonePreferences.instance().getConfig();

        // Read xml settings file
        FileInputStream stream = null;
        XmlPullParserFactory xmlFactoryObject = null;
        try {

            // Open Xml Parser from file
            stream = new FileInputStream(linphoneXml.getAbsolutePath());
            xmlFactoryObject = XmlPullParserFactory.newInstance();
            XmlPullParser myParser = xmlFactoryObject.newPullParser();
            myParser.setInput(stream, null);

            // Proces
            processParsing(myParser, lpConfig);

            // Close & delete
            if (stream != null) stream.close();
            stream = null;

            TryDeleteFile(linphoneXml);

        } catch (Exception e) {
            Log("HandleLocalXmlFile failed: ", e);
        }
    }

    private String sectionKey = "section";
    private String entryKey = "entry";

    private void processParsing(XmlPullParser parser, Config lpConfig)
            throws IOException, XmlPullParserException {

        int eventType = parser.getEventType();
        SettingClb current = null;
        String overwrite = null;

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
                                if ("true".equalsIgnoreCase(overwrite))
                                    lpConfig.setString(
                                            current.section, current.entry, current.value);
                            } catch (Exception e) {
                                Log("Parsing exception: ", e);
                            }
                        }
                    }
                    break;
            }

            eventType = parser.next();
        }
    }

    private void Log(String message, Exception... e) {
        if (e == null) logLines.add(message);
        else logLines.add(message + e.toString());
    }

    private void TryDeleteFile(File linphonerc) {

        String path = linphonerc.getAbsolutePath();
        if (linphonerc.delete()) Log("Successfull removed file: " + path);
        else Log("Failed Remove file: " + path);
    }

    public void LogSettingChanges() {
        // Logging is postponed ,cause on start of proces, Linphone logging is not active.

        LogTwice("** Local settings file (ini/xml):");

        // No files found:
        if (logLines.isEmpty()) {
            for (String path : dataPaths) {
                LogTwice("No files found at location: " + path);
            }
            return;
        }

        // Yes found files => log proces
        for (String logLine : logLines) {
            LogTwice(logLine);
        }
        logLines.clear();
    }

    private void LogTwice(String logLine) {
        Log.i(tag, logLine); // => Linphone log
        android.util.Log.i(tag, logLine); // => android log
    }

    class SettingClb {
        String section = null;
        String entry = null;
        String value = null;
    }
}
