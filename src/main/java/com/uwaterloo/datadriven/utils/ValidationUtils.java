package com.uwaterloo.datadriven.utils;

import java.io.File;

public class ValidationUtils {

    private static void exitPrintingError() {
        System.out.println("usage: gradle startAnalysis -Dpath=[path to parent folder of analysis files] -DoutPath=[path to output folder] -Dthreads=[Number of threads ]");
        System.exit(0);
    }

    public static void validateProperties() {
        for (PropertyUtils.Property property : PropertyUtils.Property.values()) {
            String propVal = PropertyUtils.getProperty(property);
            if (propVal == null || propVal.isEmpty()) {
                exitPrintingError();
            }
        }
    }
    public static void validateDexParent() {
        File dexDir = new File(PropertyUtils.getPath());
        if (!dexDir.exists() || !dexDir.isDirectory()) {
            exitPrintingError();
        }
    }
}
