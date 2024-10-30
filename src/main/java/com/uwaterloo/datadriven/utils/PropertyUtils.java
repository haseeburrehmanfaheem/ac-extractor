package com.uwaterloo.datadriven.utils;

public class PropertyUtils {
    public enum Property {
        PATH("path"),
        OUT_PATH("outPath"),
        THREADS("threads")
        ;
        public final String key;
        Property(String key) {
            this.key = key;
        }
    }

    public static String getProperty(Property property) {
        return System.getProperty(property.key);
    }

    public static String getPath() {
        return getProperty(Property.PATH);
    }

    public static String getOutPath() {
        return getProperty(Property.OUT_PATH);
    }
    public static int getThreads() {
        return Integer.parseInt(getProperty(Property.THREADS));
    }
}
