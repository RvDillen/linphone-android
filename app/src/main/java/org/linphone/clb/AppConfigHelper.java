package org.linphone.clb;

import android.content.Context;
import android.content.RestrictionsManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import org.linphone.core.Config;
import org.linphone.core.CorePreferences;
import org.linphone.core.CoreContext;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.w3c.dom.*;

import javax.xml.XMLConstants;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.parsers.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class AppConfigHelper {

    private final String tag = "AppConfigHelper";
    // LinphonePreferencesCLB internally uses an InputStream to parse/execute XML settings
    // An extra public method accepting an InputStream directly could come in handy.
    // This AppConfigParser should take the settings bundle, extract the string values
    // convert them to an InputStream and call the LinphonePreferencesCLB class to apply the settings
    private final String linphoneRc_key = "Linphonerc";
    private final String linphoneRcXml_key = "LinphonercXml";

    private Context _context;
    private CorePreferences _corePreferences;

    private Bundle _bundle;
    private String _rcString;
    private String _rcXmlString;
    private String _rcHash;
    private String _rcXmlHash;


    public AppConfigHelper(Context context, CorePreferences corePreferences) {
        _context = context;
        _corePreferences = corePreferences;

        _rcString = "";
        _rcHash = null;
        _rcXmlString = "";
        _rcXmlHash = null;
    }

    public void checkAppConfig(boolean testEnvironment, String fileContents) {

        log("Checking RestrictionsManager for settings.");

        RestrictionsManager rm = (RestrictionsManager)_context.getSystemService(Context.RESTRICTIONS_SERVICE);
        _bundle = rm.getApplicationRestrictions();

        // For testing!!!
        // Inject XML string for testing
        if (_bundle.isEmpty() && testEnvironment) {
            _bundle.putString(linphoneRc_key, fileContents);
            _bundle.putString(linphoneRcXml_key, fileContents);
        }

        // Do parse! Even when bundle is empty, so internal variables get correct values
        parseConfiguration(_bundle);
    }

    public String checkRemoteProvisioning(boolean testEnvironment, CoreContext coreContext, String filePath) {

        String contents = downloadFile(filePath);

        if (contents != null && contents.length() > 0) {
            log("Remote provisioning contents found. Parsing values...");

            // Erase all settings but the 'app' section.
            // Linphone SDK will take care of the rest.
            contents = removeNonAppSections(contents);

            return contents;
        } else {
            return "";
        }
    }

    public void checkAppConfig() {
        checkAppConfig(false, "");
    }

    public boolean linphoneRcHasChanges() {
        // Compare stored hash against calculated hash
        try {
            String storedHash = getHash(linphoneRc_key);

            log("Rc hash compare ["+storedHash+"] with ["+_rcHash+"]");

            if (_rcHash != null) {
                boolean areEqual = _rcHash.equals(storedHash);

                String hasChanges = areEqual ? "no" : "yes";
                log("linphoneRc has changes: " + hasChanges);

                return !areEqual;
            }

        } catch (Exception ex) {
            logError("Exception: " + ex.getMessage());
        }
        return false;
    }

    public boolean linphoneRcXmlHasChanges(String config) {
        try {
            String storedHash = getHash(linphoneRcXml_key);
            boolean areEqual = true;

            if (! _rcXmlString.isEmpty()) {
                log("RcXml Hash compare ["+storedHash+"] with ["+_rcXmlHash+"]");

                areEqual = _rcXmlHash.equals(storedHash);

                String hasChanges = areEqual ? "no": "yes";
                log("linphoneRcXml has changes: " + hasChanges);

            } else if (config != null && !config.isEmpty()) {
                String fileHash = getHash(config);
                log("RcXml passed as string. Compare ["+storedHash+"] with ["+fileHash+"]");

                areEqual = _rcXmlHash.equals(fileHash);

                String hasChanges = areEqual ? "no" : "yes";
                log("linphoneRcXml has changes: " + hasChanges);

            } else {
                log("linphoneRcXml is empty, ignoring configuration.");
            }

            return ( ! areEqual);

        } catch (Exception ex) {
            logError("Exception: " + ex.getMessage());
        }
        return false;
    }

    public String getLinphoneRc() {
        return parseLinphoneRc(_rcString);
    }

    public String getLinphoneRcXml() {
        return _rcXmlString;
    }

    public void storeRcHash() {
        if (linphoneRcHasChanges()) {
            storeHash(linphoneRc_key);
        }
    }

    public void storeRcXmlHash() {
        if (linphoneRcXmlHasChanges(null)) {
            storeHash(linphoneRcXml_key);
        }
    }

    public void updateShowSettingsToCorePreferences(Config config) {

        // IF the 'show_settings' setting is modified via the XML/RC
        // It SHOULD be present under the "app" section in the Linphone Config.
        // This is NOT a default Linphone setting AND we do not want to store it in Linphone.Config...
        // Also, when RC/XML changes are made and 'show_settings' is NOT defined, make sure the settings are accessible to the user.

        // Check setting section='app' name='show_settings' of Config
        // if it exists: Update corePreferences.block_settings_by_pin with the new value
        // Finally, erase it from the config.
        // The 'latest' value will be stored (persistently) in the corePreferences
        var newShowSettingsValue = config.getInt("app", "show_settings", -1);
        if (newShowSettingsValue != -1) {
            if (newShowSettingsValue == 0) {
                _corePreferences.setBlockSettingsByPin(1); // Show settings == 0 -> Block Settings == 1
            } else {
                _corePreferences.setBlockSettingsByPin(0); // Show settings == 1 -> Block Settings == 0
            }

            // Erase injected setting from Linphone Config
            config.cleanEntry("app", "show_settings");
            var keysList = config.getKeysNamesList("app");
            if (keysList.length == 0) {
                config.cleanSection("app");
            }
            config.sync();
        } else {
            // The current XML/RC did NOT contain a 'show_settings' config under the 'app' section.
            // Setting is NOT specified in this config, default to 'Unblocked' to prevent unintentionally blocked configuration.
            log("'Show settings' not defined: Making sure settings are accessible.");
            _corePreferences.setBlockSettingsByPin(0); // Settings are NOT blocked!
        }
    }

    private String calculateHash(final String key, final String value) {
        String hash = key + value;

        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            logError("MD5 algorithm not available for calculating hash. Abort!");
            return "";
        }

        digest.update(hash.getBytes());

        byte messageDigest[] = digest.digest();

        StringBuilder hexString = new StringBuilder();
        for (byte b : messageDigest) {
            String h = Integer.toHexString(0xFF & b);
            while(h.length() < 2) {
                h = "0" + h;
            }
            hexString.append(h);
        }

        log("calculateHash("+key+"): " + hexString.toString());
        return hexString.toString();
    }

    private void parseConfiguration(Bundle bundle) {

        Set<String> keys = bundle.keySet();
        int i=0;

        for (String key : keys) {
            log("Key("+i+"): " + key);
            log(key + " : " + bundle.getString(key));
            i++;
        }

        // Intended use: Full configuration, missing values will NOT be configured
        if (bundle.containsKey(linphoneRc_key)) {
            log("parsing linphoneRc config");
            _rcString = bundle.getString(linphoneRc_key);
            log("_rcString: " + _rcString);
            _rcHash = calculateHash(linphoneRc_key, _rcString);
        } else {
            log("Bundle does not contain linphoneRc key. Return empty string.");
            _rcString = "";
        }

        // Intended use: Partial configuration, missing values will fall back to defaults
        if (bundle.containsKey(linphoneRcXml_key)) {
            log("parsing linphoneRc XML config");
            _rcXmlString = bundle.getString(linphoneRcXml_key);
            log("_rcXmlString: " + _rcXmlString);
            _rcXmlHash = calculateHash(linphoneRcXml_key, _rcXmlString);
        } else {
            log("Bundle does not contain linphoneRcXml key. Return empty string.");
            _rcXmlString = "";
        }
    }

    private void storeHash(String key) {
        log("Try store hash for key: " + key);
        try {
            if (key.equals(linphoneRc_key)) {
                log("Storing hash for (" + key + "): " + _rcHash);
                _corePreferences.setLinphoneRcHash(_rcHash);
            }
            if (key.equals(linphoneRcXml_key)) {
                log("Storing hash for (" + key + "): " + _rcXmlHash);
                _corePreferences.setLinphoneRcXmlHash(_rcXmlHash);
            }
        } catch (Exception ex) {
            log("storeHash() Exception: " + ex.getMessage());
        }
    }

    private String getHash(String key) {
        String hash = null;
        if (key.equals(linphoneRc_key))
            hash = _corePreferences.getLinphoneRcHash();
        else if (key.equals(linphoneRcXml_key))
            hash = _corePreferences.getLinphoneRcXmlHash();

        log("Return hash for ("+key+"): " + hash);
        return hash == null ? "" : hash;
    }

    private void log(String text) {
        org.linphone.core.tools.Log.i(text);
        Log.i(tag, text);
    }
    private void logError(String text) {
        org.linphone.core.tools.Log.i(text);
        Log.e(tag, text);
    }

    /*
    linphoneRc is expected to contain a *.ini file format. The MDM (GoogleWorkspace) returns one long string without \r\n
    parseLinphoneRc re-injects line-endings at the desired locations so Linphone will correctly parse the string's contents.

    The entire string is loaded into a StringBuilder. The string is parsed back to front.
    Each time a change is made, a smaller substring is used from the StringBuilder's content, until no more changes are made.
    The resulting output is returned as string
    */
    public static String parseLinphoneRc(String rcString) {

        rcString = rcString.trim();

        int subLength = rcString.length();
        boolean hasChange = true;

        StringBuilder bob = new StringBuilder(rcString.substring(0, subLength));
        String subString = "";

        while (hasChange) {

            hasChange = false;

            // Take substring
            subString = bob.substring(0, subLength);

            // Search through entire rcString
            // Look for '=' or ']'
            // If ']' is found, '[' must be found WITHOUT ANY SPACES between them and ']' should be followed by a 'space'
            //      Replace 'space' after ']' with 'line feed'
            // If '=' is found,  search backwards until a 'space' is encountered.
            //      Replace 'space' with 'line feed'

            int endBraceIndex = subString.lastIndexOf("]");
            int equalsIndex = subString.lastIndexOf("=");


            if (endBraceIndex < equalsIndex) {

                if (equalsIndex != -1) {
                    // Parse 'equals':
                    // Search backwards until a space is encountered
                    int preSpaceIndex = equalsIndex;
                    while (subString.charAt(preSpaceIndex) != ' ' && preSpaceIndex >= 0)
                        preSpaceIndex--;

                    // Spaces inside a 'header' are NOT allowed. And header MUST be followed by a space
                    if (preSpaceIndex >= 0)
                        bob.replace(preSpaceIndex, preSpaceIndex + 1, "\r\n");

                    // For now, just skip
                    subLength = equalsIndex - 1;
                    hasChange = true;
                }

            } else if (endBraceIndex >= 0) {
                // Parse 'brace':
                // Find previous (opposing) brace
                int startBraceIndex = subString.lastIndexOf("[");
                String header = subString.substring(startBraceIndex, endBraceIndex+1);

                // Spaces inside a 'header' are NOT allowed. And header MUST be followed by a space
                if (!header.contains(" ") && subString.charAt(endBraceIndex + 1) == ' ') {
                    bob.replace(endBraceIndex + 1, endBraceIndex + 2, "\r\n");
                }
                // Also, replace a space _BEFORE_ the header if there is one (note that header could at the start of the file/string)
                if (startBraceIndex > 0 && subString.charAt(startBraceIndex-1) == ' ') {
                    bob.replace(startBraceIndex-1, startBraceIndex, "\r\n");
                    subLength = startBraceIndex -1;
                } else {
                    subLength = startBraceIndex;
                }
                hasChange = true;
            }
        }
        return bob.toString();
    }

    /* File Downloader for RemoteProvisioning support */
    // NOTE! This way of downloading a file is BLOCKING.
    // For the provisioning file, this is as intended as the file needs to be present BEFORE the Linphone core is started
    // by blocking the thread, the execution order can be assured.
    // DO NOT RUN ON THE UI THREAD!
    // Might need rework, but for now we will keep it this way.
    private String downloadFile(final String fileUrl) {

        String output = "";
        output = downloadWithJavaSocket(fileUrl);
        if (output == null || output.isEmpty()) {
            log("Failed to download file with Java Socket.");

            output = downloadWithHttpUrlConnection(fileUrl);
            if (output == null || output.isEmpty()) {
                log("Failed to download file with HttpUrlConnection.");
            }
        }
        return output;
    }

    public String downloadWithJavaSocket(final String fileUrl) {
        String tag = "provisioning";
        log("Attempting to download provisioningfile with Java Socket: " + fileUrl);

        String downloadUrl = fileUrl;
        if (!downloadUrl.startsWith("http://") && !downloadUrl.startsWith("https://")) {
            downloadUrl = "http://" + downloadUrl;
        }

        Uri uri = Uri.parse(downloadUrl);

        String host = uri.getHost();
        String path = uri.getPath();
        int port = uri.getPort();

        if (path == null || path.isEmpty()) {
            path = "/";
        }

        System.out.println("Downloading from host: " + host + " and path: " + path);
        log("Downloading from host: " + host + " and path: " + path);

        final String downloadPath = path;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> {

            int portNr = port == -1 ? 80 : port;
            java.net.Socket socket = new java.net.Socket(host, portNr);

            try {
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                String request =
                        "GET " + downloadPath + " HTTP/1.1\r\n" +
                                "Host: " + host + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n";

                out.write(request.getBytes("UTF-8"));
                out.flush();

                BufferedInputStream bis = new BufferedInputStream(in);

                // Read/Skip headers
                while ((bis.read()) != -1) {
                    // Empty, Skipping headers...
                }

                StringBuilder body = new StringBuilder();

                byte[] buffer = new byte[8192];
                int read;
                while ((read = bis.read(buffer)) != -1) {
                    body.append(new String(buffer, 0, read, "UTF-8"));
                }

                return body.toString();

            } catch (Exception ex) {
                log("Java Socket download failed: " + ex.getMessage());
                return "";
            } finally {
                socket.close();
            }
        });

        try {
            return future.get();
        } catch (Exception ex) {
            return "";
        } finally {
            executor.shutdown();
        }
    }

    private String downloadWithHttpUrlConnection(final String fileUrl) {

        System.out.println("Attempting to download provisioning file with HttpUrlConnection (only works from 'config.clb.nl'): " + fileUrl);

        String output = "";
        ExecutorService executor = Executors.newSingleThreadExecutor();

        // Download MUST run on an non-ui thread... (Android policy)
        Future<String> future = executor.submit(() -> {

            HttpURLConnection connection = null;
            try {
                String tag = "provisioning";

                URL url = new URL(fileUrl);
                connection = (HttpURLConnection) url.openConnection();

                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(15_000);
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    log("HTTP failed. Response: " + connection.getResponseCode());
                    throw new Exception("Server returned HTTP "
                            + connection.getResponseCode()
                            + " "
                            + connection.getResponseMessage());
                }

                InputStream input = new BufferedInputStream(connection.getInputStream());
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                try {
                    byte[] data = new byte[4096];
                    int n;

                    while ((n = input.read(data)) != -1) {
                        buffer.write(data, 0, n);
                    }
                } finally {
                    input.close();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return buffer.toString(StandardCharsets.UTF_8);
                } else {
                    return buffer.toString("UTF-8");
                }
            } catch (Exception ex) {
                log("Something went wrong: " + ex.getMessage());
                return null;
            } finally  {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });

        try {
            output = future.get();
            return output;
        } catch (Exception ex) {
            return output;
        } finally {
            executor.shutdown();
        }
    }

    private String removeNonAppSections(final String fileContents) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            // Fix: disable DOCTYPE and external entities
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ex) {
            Log.i(tag, "Failed to set XML parsing features: " + ex.getMessage());
        }

        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(fileContents.getBytes(StandardCharsets.UTF_8)));

            NodeList sections = doc.getElementsByTagName("section");
            for (int i = sections.getLength() - 1; i >=0; i--) {
                Element section = (Element)sections.item(i);
                String name = section.getAttribute("name");

                if (!"app".equals(name)) {
                    section.getParentNode().removeChild(section);
                }
            }

            if (!convertToOneLinerXml(doc)) {
                Log.i(tag, "Failed to compress XML to a one-liner.");
            }

            // Clean-up done. 'transform' to a one-liner xml format
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");

            StringWriter writer = new StringWriter();

            transformer.transform(
                    new DOMSource(doc),
                    new StreamResult(writer)
            );

            var xmlString = writer.toString();
            // Remove all empty lines
            xmlString = xmlString.replaceAll("(?m)^\\s*$\\R?", "");
            return xmlString;

        } catch (Exception ex) {
            logError("Parsing configXML (provisioning) failed: " + ex.getMessage());
            return "";
        }
    }

    private boolean convertToOneLinerXml(Document doc) {
        // Clean-up (remove empty lines and trailing spaces
        XPath xpath = XPathFactory.newInstance().newXPath();

        try {
            NodeList emptyNodes = (NodeList) xpath.evaluate(
                    "//text()[normalize-space(.)='']",
                    doc,
                    XPathConstants.NODESET
            );

            for (int i = 0; i < emptyNodes.getLength(); i++) {
                Node emptyNode = emptyNodes.item(i);
                emptyNode.getParentNode().removeChild(emptyNode);
            }

        } catch (Exception ex) {
            logError("Compressing XML to one-liner failed: " + ex.getMessage());
            return false;
        }

        return true;
    }
}
