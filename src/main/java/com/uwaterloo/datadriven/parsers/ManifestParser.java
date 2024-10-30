package com.uwaterloo.datadriven.parsers;

import com.uwaterloo.datadriven.model.accesscontrol.misc.ProtectionLevel;
import com.uwaterloo.datadriven.model.accesscontrol.ManifestAccessControl;
import com.uwaterloo.datadriven.model.functional.CleanupFunction;
import com.uwaterloo.datadriven.utils.FileUtils;
import com.uwaterloo.datadriven.utils.PropertyUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class ManifestParser extends DefaultHandler {

    private static final String MANIFEST_FILE = "AndroidManifest.xml";

    private static final String PERMISSION_ELEM = "permission";
    private static final String PROTECTED_BROADCAST_ELEM = "protected-broadcast";
    private static final String RECEIVER_ELEM = "receiver";
    private static final String INTENT_FILTER_ELEM = "intent-filter";
    private static final String ACTION_ELEM = "action";
    private static final String CATEGORY_ELEM = "category";

    private static final String NAME_ATTR = "android:name";
    private static final String BG_PERM_ATTR = "android:backgroundPermission";
    private static final String PROTECTION_LVL_ATTR = "android:protectionLevel";
    private final String EXPORTED_ATTR = "android:exported";
    private static final String PERMISSION_ATTR = "android:permission";
    
    private static ManifestParser manifestParser = null;

    // Variables to store info about the component currently being parsed
    // Everything declared here must be cleaned up in corresponding end-element callback
    // Simply add the cleanup code in the functional array at the end, and it will automatically be called
    private String name = "";
    private boolean isExported = false;
    private boolean isIntentFilterRegistered = false;
    private String permission = "";
    private final HashSet<String> actions = new HashSet<>();
    private final HashSet<String> categories = new HashSet<>();
    private final List<CleanupFunction> cleanupFunctions = Arrays.asList(
            () -> name = "",
            () -> isExported = false,
            () -> isIntentFilterRegistered = false,
            () -> permission = "",
            actions::clear,
            categories::clear
    );

    private final HashMap<String, ProtectionLevel> protectionLevelMap = new HashMap<>();
    private final HashMap<String, ManifestAccessControl> frameworkManifestReceivers = new HashMap<>();
    
    private ManifestParser() {
        // Cannot be invoked from outside
    }

    public static ManifestParser parseFrameworkManifests() {
        if (manifestParser == null) {
            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            try {
                List<String> inPaths = FileUtils.findFileNamesFromParentPath(PropertyUtils.getPath(), MANIFEST_FILE);
                for (String inPath : inPaths) {
                    try {
                        File manifestFile = new File(inPath);
                        SAXParser parser = saxParserFactory.newSAXParser();
                        manifestParser = new ManifestParser();
                        parser.parse(manifestFile, manifestParser);
                    } catch (ParserConfigurationException | SAXException e) {
//                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
//                e.printStackTrace();
            }
        }

        return manifestParser;
    }

    public HashMap<String, ProtectionLevel> getPermBroadMap() {
        return protectionLevelMap;
    }
    
    public HashMap<String, ManifestAccessControl> getFrameworkManifestReceivers() {
        return frameworkManifestReceivers;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        try {
            switch (qName) {
                case PROTECTED_BROADCAST_ELEM ->
                        protectionLevelMap.put(attributes.getValue(NAME_ATTR), ProtectionLevel.SYS_OR_SIG);
                case PERMISSION_ELEM -> {
                    ProtectionLevel protectionLvl = ProtectionLevel
                            .toProtectionLevel(attributes.getValue(PROTECTION_LVL_ATTR));
                    protectionLevelMap.put(attributes.getValue(NAME_ATTR), protectionLvl);
                    String bgPerm = attributes.getValue(BG_PERM_ATTR);
                    if (bgPerm != null && !bgPerm.isBlank())
                        protectionLevelMap.put(bgPerm, protectionLvl);
                }
                case INTENT_FILTER_ELEM -> isIntentFilterRegistered = true;
                case ACTION_ELEM -> actions.add(attributes.getValue(NAME_ATTR));
                case CATEGORY_ELEM -> categories.add(attributes.getValue(NAME_ATTR));
                case RECEIVER_ELEM -> {
                    name = attributes.getValue(NAME_ATTR);
                    isExported = (attributes.getValue(EXPORTED_ATTR) != null
                            && attributes.getValue(EXPORTED_ATTR).equals("true"));
                    if (attributes.getValue(PERMISSION_ATTR) != null)
                        permission = attributes.getValue(PERMISSION_ATTR);
                }
            }
        } catch (Exception e) {
            //ignore
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (qName.equals(RECEIVER_ELEM)) {
            if (name != null && !name.isEmpty()) {
                frameworkManifestReceivers.put("L" + name.replace('.', '/'),
                        new ManifestAccessControl(isExported, isIntentFilterRegistered, permission, actions, categories));
            }
            for (CleanupFunction f : cleanupFunctions) {
                f.cleanup();
            }
        }
    }
}
