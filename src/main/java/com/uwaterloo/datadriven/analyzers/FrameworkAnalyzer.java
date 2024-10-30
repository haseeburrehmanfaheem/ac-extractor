package com.uwaterloo.datadriven.analyzers;

import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.util.collections.Pair;
import com.opencsv.CSVWriter;
import com.uwaterloo.datadriven.analyzers.detector.ReceiverDetector;
import com.uwaterloo.datadriven.analyzers.detector.ServiceDetector;
import com.uwaterloo.datadriven.model.accesscontrol.misc.AccessControlSource;
import com.uwaterloo.datadriven.model.framework.*;
import com.uwaterloo.datadriven.model.accesscontrol.ManifestAccessControl;
import com.uwaterloo.datadriven.model.accesscontrol.misc.ProtectionLevel;
import com.uwaterloo.datadriven.model.framework.field.FieldAccess;
import com.uwaterloo.datadriven.model.framework.field.FrameworkField;
import com.uwaterloo.datadriven.rules.RuleGenerator;
import com.uwaterloo.datadriven.utils.CsvUtils;
import com.uwaterloo.datadriven.utils.PropertyUtils;
import com.uwaterloo.datadriven.utils.ScopeUtil;
import java.util.concurrent.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static com.uwaterloo.datadriven.utils.AccessControlUtils.getMaxProtectionLevel;

public class FrameworkAnalyzer {
    private final ClassHierarchy cha;
    private final HashMap<String, ProtectionLevel> curProtections;
    private final HashMap<String, ManifestAccessControl> frameworkManifestReceivers;
    private final HashMap<String, FrameworkClass> frameworkFieldsMap;
    private final HashMap<String,ProtectionLevel> protectionLevelHashMap = new HashMap<>();

    private HashSet<FrameworkReceiver> systemReceivers = null;
    private HashSet<FrameworkService> systemServices = null;
    public static final ArrayList<String[]> apisCsvStringsList = new ArrayList<>();
    public static final ArrayList<String[]> fieldsCsvStringsList = new ArrayList<>();

    public FrameworkAnalyzer(HashMap<String, ProtectionLevel> curProtections,
                             HashMap<String, ManifestAccessControl> frameworkManifestReceivers) {
        try {
            cha = ClassHierarchyFactory.make(ScopeUtil.makeScope());
            this.curProtections = curProtections;
            this.frameworkManifestReceivers = frameworkManifestReceivers;
            this.frameworkFieldsMap = new HashMap<>();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error building class hierarchy!");
        }
    }
    public void writeDataStructures(String initialPath) {
        for (FrameworkClass frameworkClass : frameworkFieldsMap.values()) {
            String path = initialPath + frameworkClass.classPath.substring(frameworkClass.classPath.lastIndexOf('/'))+ ".csv";
            try {
                CSVWriter outFile = new CSVWriter(java.nio.file.Files.newBufferedWriter(Path.of(path)));
                outFile.writeNext(CsvUtils.csvHeadersFields);
                ArrayList<String[]> data = frameworkClass.toCsvStrings();
                outFile.writeAll(data);
                outFile.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public void writeFrameworkEps(CSVWriter outFile) {
        outFile.writeNext(new String[] {"Type", "Entrypoint", "Parent Class", "Manifest Access Control", "Access Control Level"});
        for (FrameworkService service : systemServices) {
            outFile.writeAll(service.toCsvStrings(protectionLevelHashMap));
        }
        for (FrameworkReceiver receiver : systemReceivers) {
            outFile.writeAll(receiver.toCsvStrings(protectionLevelHashMap));
        }
        try {
            outFile.close();
        } catch (Exception e) {
            //ignore
        }
    }

    public void serialTest() throws IOException { // testing for serialisation, ignore
        try {
            String outPath = PropertyUtils.getOutPath();
            if (!outPath.endsWith("/"))
                outPath += "/";
            FileOutputStream fos = new FileOutputStream(outPath+"service.ser");
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(curProtections);
            oos.close();
            fos.close();
            // Will work for MethodReference because of the function MethodReference.findOrCreate, all parameters are serializable
            System.out.println("Serialized HashMap data is saved in service.ser");
            FileInputStream fis = new FileInputStream(outPath + "service.ser");
            ObjectInputStream ois = new ObjectInputStream(fis);
            HashMap<String, ProtectionLevel> cp = (HashMap<String, ProtectionLevel>) ois.readObject();

            ois.close();
            fis.close();
//
//            return fwAcMap;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void launch(int threads) throws IOException, InterruptedException, ExecutionException {
        systemReceivers = (new ReceiverDetector(cha, frameworkManifestReceivers)).detectReceivers();
        systemServices = (new ServiceDetector(cha)).detectFrameworkServices();

        HashSet<FrameworkParent> parents = new HashSet<>(systemServices);
        System.out.println("found all services");
        System.out.println("Services size : " + systemServices.size());
        parents.addAll(systemReceivers);
        EntrypointAnalyzer epAnalyzer = new EntrypointAnalyzer(cha, curProtections);
        int total = parents.stream().mapToInt(parent -> parent.eps.size()).sum(); // Total number of FrameworkEp items
        int count = 0;
        for (FrameworkParent parent : parents) {
            HashMap<String, Pair<AccessControlSource, HashSet<Pair<FrameworkField, FieldAccess>>>> apis = new HashMap<>();


            for (FrameworkEp ep : parent.eps) {
                try{
                    System.out.println("Analyzing: " + ep.epMethod.getMethod().getSignature());
                    Pair<AccessControlSource, HashSet<Pair<FrameworkField, FieldAccess>>> map = epAnalyzer.analyze(ep);
                    protectionLevelHashMap.put(ep.epMethod.getMethod().getSignature(), getMaxProtectionLevel(map.fst.ac()));
                    apis.put(ep.epMethod.getMethod().getSignature(), map);
                } catch (Exception e) {
                    System.out.println("Error analyzing " + ep.epMethod.getMethod().getSignature());
                }
                count++;
                int progress = (int) ((double) count / total * 100);
                System.out.print("\rProgress: " + progress + "% (" + count + "/" + total + ")");
            }
        }
        System.out.println("\nDone all.");
    }

    private void addAllFields(Collection<FrameworkField> inFields) {
        for (FrameworkField field : inFields) {
            addField(field);
        }
    }

    private void addField(FrameworkField field) {
        String parentClassPath = field.parent.getName().toString();
        if (!frameworkFieldsMap.containsKey(parentClassPath)) {
            frameworkFieldsMap.put(parentClassPath, new FrameworkClass(parentClassPath));
        }
        FrameworkClass parentClass = frameworkFieldsMap.get(parentClassPath);
        field.setParentClass(parentClass);
        parentClass.addField(field);
    }

//    private void addAllApis(HashMap<String, Pair<AccessControlSource, HashSet<Pair<FrameworkField, FieldAccess>>>> apis) {
//        for (Map.Entry<String, Pair<AccessControlSource, HashSet<Pair<FrameworkField, FieldAccess>>>> api : apis.entrySet()) {
//            String apiSignature = api.getKey();
//            AccessControlSource apiAc = api.getValue().fst;
//            HashSet<Pair<FrameworkField, FieldAccess>> fieldAccesses = api.getValue().snd;
//            HashSet<FieldAccess> fieldAcs = new HashSet<>();
//            fieldAccesses.forEach(fac -> fieldAcs.add(fac.snd));
//            for (Pair<FrameworkField, FieldAccess> fieldAccess : fieldAccesses) {
//                fieldAccess.fst.parentClass.addApi(apiSignature, apiAc, fieldAcs);
//            }
//        }
//    }
}
