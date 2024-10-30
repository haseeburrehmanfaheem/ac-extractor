package com.uwaterloo.datadriven.model.accesscontrol.misc;
import java.util.HashMap;
import java.util.Map;
public class AccessControlParameterMapping {
    private static final Map<String, Integer> permissionMap = new HashMap<>();

    static {
        permissionMap.put("checkPermission", 0);
        permissionMap.put("checkCallingPermission", 0);
        permissionMap.put("checkCallingOrSelfPermission", 0);
        permissionMap.put("checkSelfPermission", 0);
        permissionMap.put("checkComponentPermission", 0);
        permissionMap.put("checkUidPermission", 0);
        permissionMap.put("enforcePermission", 0);
        permissionMap.put("enforceCallingPermission", 0);
        permissionMap.put("enforceCallingOrSelfPermission", 0);
        permissionMap.put("hasUserRestriction", 0);
        permissionMap.put("hasUserRestrictionOnAnyUser", 0);
        permissionMap.put("hasUserRestrictionForUser", 0);
        permissionMap.put("isSettingRestrictedForUser", 0);
        permissionMap.put("enforceCrossUserOrProfilePermission", 4);
    }

    public static int getPermissionValue(String key) {
        return permissionMap.getOrDefault(key,-1);
    }
}
