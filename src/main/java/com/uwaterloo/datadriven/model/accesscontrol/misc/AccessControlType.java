package com.uwaterloo.datadriven.model.accesscontrol.misc;

import java.util.*;

public enum AccessControlType {
    Permission(
            new HashSet<>(List.of()),
            new HashSet<>(List.of(
                    "checkPermission",
                    "checkCallingPermission",
                    "checkCallingOrSelfPermission",
                    "checkSelfPermission",
                    "checkUriPermission",
                    "checkCallingUriPermission",
                    "checkCallingOrSelfUriPermission",
                    "checkComponentPermission",
                    "checkUidPermission",
                    "checkAnyPermissionOf")),
            new HashSet<>(List.of(
                    "enforcePermission",
                    "enforceCallingPermission",
                    "enforceCallingOrSelfPermission",
                    "enforceUriPermission",
                    "enforceCallingUriPermission",
                    "enforceCallingOrSelfUriPermission",
                    "enforceAnyPermissionOf"))
    ),
    Uid(
            new HashSet<>(List.of(
                    "getUid",
                    "getCallingUid",
                    "getUidForName",
                    "getUidForPid",
                    "myUid")),
            new HashSet<>(List.of(
                    "isCoreUid",
                    "isApplicationUid",
                    "isApp",
                    "isCore",
                    "isIsolated",
                    "isSameApp"
            )),
            new HashSet<>(List.of())
    ),
    Pid(
            new HashSet<>(List.of(
                    "getPid",
                    "getCallingPid",
                    "myPid",
                    "myPpid",
                    "getParentPid"
            )),
            new HashSet<>(List.of()),
            new HashSet<>(List.of())
    ),
    Tid(
            new HashSet<>(List.of(
                    "myTid"
            )),
            new HashSet<>(List.of()),
            new HashSet<>(List.of())
    ),
    UserId(
            new HashSet<>(List.of(
                    "getIdentifier",
                    "getUserId",
                    "getCallingUserId",
                    "getCurrentUser"
            )),
            new HashSet<>(List.of()),
            new HashSet<>(List.of())
    ),
    Gid(
            new HashSet<>(List.of(
                    "getGidForName",
                    "getUserGid",
                    "getSharedAppGid",
                    "getCacheAppGid"
            )),
            new HashSet<>(List.of(
                    "isSharedAppGid"
            )),
            new HashSet<>(List.of())
    ),
    AppOps(
            new HashSet<>(List.of()),
            new HashSet<>(List.of()),
            new HashSet<>(List.of(
                    "noteOp",
                    "startOp",
                    "checkOp"
            ))
    ),
    Signature(
            new HashSet<>(List.of()),
            new HashSet<>(List.of(
                    "compareSignatures",
                    "checkSignatures",
                    "checkSignature"
            )),
            new HashSet<>(List.of())
    ),
    Package(
            new HashSet<>(List.of(
                    "getInstantAppPackageName",
                    "getPackageUid",
                    "getPackageName",
                    "getPackageNameForUid",
                    "getPackageFromAppProcesses",
                    "genericPackageNameGettingMethod"
            )),
            new HashSet<>(List.of()),
            new HashSet<>(List.of(
                    "checkPackage"
            ))
    ),
    AppId(
            new HashSet<>(List.of(
                    "getCallingAppId",
                    "getAppId",
                    "getAppIdFromSharedAppGid"
            )),
            new HashSet<>(List.of()),
            new HashSet<>(List.of())
    ),
    UserRestriction(
            new HashSet<>(List.of(
                    "getUserRestriction",
                    "hasUserRestriction",
                    "hasUserRestrictionOnAnyUser",
                    "hasUserRestrictionForUser",
                    "isSettingRestrictedForUser"
            )),
            new HashSet<>(List.of()),
            new HashSet<>(List.of())
    ),
    User(
            new HashSet<>(List.of(
                    "handleIncomingUser"
            )),
            new HashSet<>(List.of(
                    "isSameProfileGroup",
                    "isAdminUser",
                    "isSystemUser",
                    "isPrimaryUser",
                    "isSameUser"
            )),
            new HashSet<>(List.of())
    ),
    Process(
            new HashSet<>(List.of(
                    "getRunningAppProcesses"
            )),
            new HashSet<>(List.of()),
            new HashSet<>(List.of())
    ),
    CrossUser(
            new HashSet<>(List.of()),
            new HashSet<>(List.of()),
            new HashSet<>(List.of(
                    "enforceCrossUserPermission",
                    "enforceCrossUserOrProfilePermission"
            ))
    )
    ;

    public final Map<AcMethodType, Set<String>> methodSets;

    AccessControlType(HashSet<String> gettingMethods, HashSet<String> checkingMethods, HashSet<String> enforcingMethods) {
        this.methodSets = Map.of(
                AcMethodType.GETTING, Collections.unmodifiableSet(gettingMethods),
                AcMethodType.CHECKING, Collections.unmodifiableSet(checkingMethods),
                AcMethodType.ENFORCING, Collections.unmodifiableSet(enforcingMethods)
        );
    }

    public enum AcMethodType {
        GETTING,
        CHECKING,
        ENFORCING
        ;
    }
}
