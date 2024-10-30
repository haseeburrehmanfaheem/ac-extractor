package com.uwaterloo.datadriven.utils;

import com.uwaterloo.datadriven.model.accesscontrol.AccessControl;
import com.uwaterloo.datadriven.model.accesscontrol.ConjunctiveAccessControl;
import com.uwaterloo.datadriven.model.accesscontrol.ProgrammaticAccessControl;
import com.uwaterloo.datadriven.model.accesscontrol.misc.AccessControlType;
import com.uwaterloo.datadriven.model.accesscontrol.misc.ProtectionLevel;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

public class AccessControlUtils {
    public static boolean isAcMethod(String methodName) {
        return isGettingMethod(methodName)
                || isCheckingMethod(methodName)
                || isEnforcingMethod(methodName);
    }

    public static boolean isEnforcingMethod(String methodName) {
        return containsMethodType(AccessControlType.AcMethodType.ENFORCING, methodName);
    }

    public static boolean isEnforcingMethod(String methodName, AccessControlType acType) {
        return acType.methodSets.get(AccessControlType.AcMethodType.ENFORCING).contains(methodName);
    }

    public static boolean isCheckingMethod(String methodName) {
        return containsMethodType(AccessControlType.AcMethodType.CHECKING, methodName);
    }

    public static boolean isCheckingMethod(String methodName, AccessControlType acType) {
        return acType.methodSets.get(AccessControlType.AcMethodType.CHECKING).contains(methodName);
    }

    public static boolean isGettingMethod(String methodName) {
        return containsMethodType(AccessControlType.AcMethodType.GETTING, methodName);
    }

    private static boolean containsMethodType(AccessControlType.AcMethodType methodType, String methodName) {
        for (AccessControlType acType : AccessControlType.values()) {
            if (acType.methodSets.get(methodType).contains(methodName)) {
                return true;
            }
        }

        return false;
    }

    public static AccessControlType findTypeByMethodName(String methodName) {
        for (AccessControlType type : AccessControlType.values()) {
            for (Set<String> methods : type.methodSets.values()) {
                if (methods.contains(methodName)) {
                    return type;
                }
            }
        }
        return null;
    }

    public static ProgrammaticAccessControl.Operator findOperatorByString(String operator) {
        return switch (operator) {
            case "ge", "gt" -> ProgrammaticAccessControl.Operator.GT;
            case "lt", "le" -> ProgrammaticAccessControl.Operator.LT;
            case "ne", "eq" -> ProgrammaticAccessControl.Operator.EQ;
            default -> null;
        };
    }

    public static AccessControl mergeConjunctiveAc(AccessControl ac1, AccessControl ac2) {
        if (ac1 == null)
            return ac2;
        if (ac2 == null)
            return ac1;
        return new ConjunctiveAccessControl(ac1, ac2);
    }

    public static ProtectionLevel getProtectionLevel(String key, HashMap<String, ProtectionLevel> curProtection) {
        return curProtection.getOrDefault(key, ProtectionLevel.NORMAL);
    }
    public static ProtectionLevel getProtectionLevel(AccessControlType acType, Collection<String> keys, HashMap<String, ProtectionLevel> curProtection) {
        if (acType.equals(AccessControlType.Permission)) {
                ProtectionLevel maxLevel = ProtectionLevel.NORMAL;
                for (String key : keys) {
                    ProtectionLevel currentLevel = curProtection.get(key);
                    if (currentLevel != null && currentLevel.compareTo(maxLevel) > 0) {
                        maxLevel = currentLevel;
                    }
                }
                return maxLevel;
        }
        return ProtectionLevel.SYS_OR_SIG;
    }

    public static ProtectionLevel getMaxProtectionLevel(ConjunctiveAccessControl ac){
        ProtectionLevel max1 = null, max2 = null;
        if(ac.ac1 instanceof ConjunctiveAccessControl){
            max1 = getMaxProtectionLevel((ConjunctiveAccessControl) ac.ac1);
        }
        else{
            ProgrammaticAccessControl s = ((ProgrammaticAccessControl) ac.ac1);
            max1 = s==null ? ProtectionLevel.NONE : s.level;
        }
        if(ac.ac2 instanceof ConjunctiveAccessControl){
            max2 = getMaxProtectionLevel((ConjunctiveAccessControl) ac.ac2);
        }
        else{
            ProgrammaticAccessControl s = ((ProgrammaticAccessControl) ac.ac2);
            max2 = s==null ? ProtectionLevel.NONE : s.level;
        }
        return max1.compareTo(max2) > 0 ? max1 : max2;
    }

    public static ProtectionLevel getMaxProtectionLevel(AccessControl ac){
        if(ac instanceof ConjunctiveAccessControl){
            return getMaxProtectionLevel((ConjunctiveAccessControl) ac);
        }
        else{
            ProgrammaticAccessControl s = ((ProgrammaticAccessControl) ac);
            return s==null ? ProtectionLevel.NONE : s.level;
        }
    }
}
