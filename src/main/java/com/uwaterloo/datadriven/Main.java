package com.uwaterloo.datadriven;

import com.opencsv.CSVWriter;
import com.uwaterloo.datadriven.analyzers.FrameworkAnalyzer;
import com.uwaterloo.datadriven.parsers.ManifestParser;
import com.uwaterloo.datadriven.utils.PropertyUtils;
import com.uwaterloo.datadriven.utils.ValidationUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static final String dataStructuresFile = "DataStructures.csv";
    public static final String epFile = "Entrypoints.csv";
    public static void main(String[] args) throws Exception {
        ValidationUtils.validateProperties();
        ManifestParser parser = ManifestParser.parseFrameworkManifests();
        FrameworkAnalyzer frameworkAnalyzer = new FrameworkAnalyzer(parser.getPermBroadMap(), parser.getFrameworkManifestReceivers());
        frameworkAnalyzer.launch(PropertyUtils.getThreads());
        String outPath = PropertyUtils.getOutPath();
        if (!outPath.endsWith("/"))
            outPath += "/";
        File outDir = new File(outPath);
        if (!outDir.exists())
            outDir.mkdirs();
        frameworkAnalyzer.writeFrameworkEps(new CSVWriter(Files.newBufferedWriter(Paths.get(outPath + epFile))));
//        frameworkAnalyzer.writeDataStructures(outPath);
    }
}
