package org.linphone.clb;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import org.linphone.LinphoneApplication;
import org.linphone.core.Config;
import org.linphone.core.CorePreferences;
import org.linphone.core.tools.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    private final String linphoneRc_key = "Linphonerc";
    private final String linphoneRcXml_key = "LinphonercXml";

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O // Oreo == API26 == Android 8
            && Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) { // Q == API29 == Android 10
            LogLine("O <= Android <= Q. Request write external storage permission.");

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

    public void MoveLinphoneRcFromDownloads(Context context, CorePreferences corePreferences) {
        // Try to read linephoneRc (i.e. old config file method)
        try {
            LogLine("Check if there is a LinphoneRc file in 'downloads' folder...");
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String rcConfigFile = downloadsDir.getAbsolutePath() + "/linphonerc";

            // Try to read LinphoneRcXml from file (the 'old' way)
            File configFile = new File(rcConfigFile);
            if (configFile.exists()) {
                LogLine("File (" + rcConfigFile + ") exists. Try to parse and load configuration.");
                try {
                    LinphonePreferencesCLB.instance().HandleLocalRcFile(context, configFile, corePreferences);
                } catch (Exception ex) {
                    LogLine("Failed to read config file (" + configFile + "). Ex:" + ex.getLocalizedMessage() + ". If permission was not yet granted, please restart the app.");
                } finally {
                    LogLine("Erase config file from disk.");
                    boolean result = configFile.delete();
                }
            } else {
                LogLine("File (" + rcConfigFile + ") not found. Continue...");
            }
        } catch (Exception ex) {
            LogLine("Reading config XML file failed: " + ex.getLocalizedMessage());
        }
    }

    public String ComputeHasCode(FileChannel channel, String key){

        String hash = "";
        ByteBuffer buff = ByteBuffer.allocate(1024);
        try {
            int noOfBytesRead = channel.read(buff);
            String fileContent = new String(buff.array(), StandardCharsets.UTF_8);
            hash = HashUtil.calculateHash(key,fileContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return hash;
    }

    public void HandleLocalRcFile(Context context, File linphonerc, CorePreferences corePreferences) {

        String local = linphonerc.getAbsolutePath();
        String origin = context.getFilesDir().getAbsolutePath() + "/linphonerc";
        LogLine("HandleLocalRcFile for: " + local);

        FileChannel sourceChannel = null;
        FileChannel destChannel = null;
        try {
            sourceChannel = new FileInputStream(local).getChannel();
            String hash = ComputeHasCode(sourceChannel, linphoneRc_key);
            String oldHash = corePreferences.getLinphoneRcHash();
            if (hash.equals(oldHash))
                return; // File alreay imported so ready

            // Copy file
            destChannel = new FileOutputStream(origin).getChannel();
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
            sourceChannel.close();
            sourceChannel = null;
            destChannel.close();

            TryDeleteFile(linphonerc);
            corePreferences.setLinphoneRcHash(hash);

        } catch (FileNotFoundException e) {
            LogLine("HandleLocalRcFile failed, not found: " + e.getLocalizedMessage());
        } catch (IOException e) {
            LogLine("HandleLocalRcFile failed, IO: " + e.getLocalizedMessage());
        } catch (Exception e) {
            LogLine("HandleLocalRcFile failed: " + e.getLocalizedMessage());
        } finally {
        }
    }

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

    private void TryDeleteFile(File linphonerc) {

        String path = linphonerc.getAbsolutePath();
        if (linphonerc.delete()) LogLine("Successfull removed file: " + path);
        else LogLine("Failed Remove file: " + path);
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


    public void ParseLocalXmlFileConfig(Config config, CorePreferences corePreferences) {
        LogLine("Check if there is a LinphoneRc.xml file in 'downloads' folder...");

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String rcXmlConfigFile = downloadsDir.getAbsolutePath() + "/linphonerc.xml";

        // Try to read LinphoneRcXml from file (the 'old' way)
        File configFile = new File(rcXmlConfigFile);
        if (configFile.exists()) {
            LogLine("File (" + rcXmlConfigFile + ") exists. Trying to parse and load configuration.");
            try {
                // Hash check (already imported ?
                FileChannel sourceChannel = new FileInputStream(configFile).getChannel();
                String hash = ComputeHasCode(sourceChannel, linphoneRcXml_key);
                String oldHash = corePreferences.getLinphoneRcXmlHash();
                if (hash.equals(oldHash))
                    return; // File alreay imported so ready

                // Import
                BufferedReader br = new BufferedReader(new FileReader(configFile));
                String data = new String();
                for (String line; (line = br.readLine()) != null; data += line); // Read all lines into 'data'
                UpdateFromLinphoneXmlData(data, config);

                corePreferences.setLinphoneRcXmlHash(hash);
            } catch (Exception ex) {
                LogLine("Failed to read config file (" + configFile + "). Ex:" + ex.getLocalizedMessage() + ". If permission was not yet granted, please restart the app.");
            } finally {
                LogLine("Erase config file from disk.");
                configFile.delete();
            }
        } else {
            LogLine("File (" + rcXmlConfigFile + ") not found. Continue...");
        }
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

        LogLine("Update Linphonerc from XML");
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
