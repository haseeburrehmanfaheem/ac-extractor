package com.uwaterloo.datadriven.model.accesscontrol.misc;

public enum ProtectionLevel {
    NONE,
    NORMAL,
    DANGEROUS,
    SYS_OR_SIG,
    ;

    public static ProtectionLevel toProtectionLevel(String levelStr) {
        if (levelStr.contains("system")
                || levelStr.contains("signature")
                || levelStr.contains("privileged"))
            return SYS_OR_SIG;
        else if (levelStr.contains("dangerous")) {
            return DANGEROUS;
        }

        return NORMAL;
    }
}
