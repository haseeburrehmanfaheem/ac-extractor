package com.uwaterloo.datadriven.model.accesscontrol;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class ManifestAccessControl extends AccessControl {
    public final boolean isExported;
    public final boolean isIntentFilterRegistered;
    public final String permission;
    public final Set<String> actions;
    public final Set<String> categories;

    public ManifestAccessControl(boolean isExported, boolean isIntentFilterRegistered, String permission,
                                 HashSet<String> actions, HashSet<String> categories) {
        this.isExported = isExported;
        this.isIntentFilterRegistered = isIntentFilterRegistered;
        this.permission = permission;
        this.actions = Collections.unmodifiableSet(actions);
        this.categories = Collections.unmodifiableSet(categories);
    }

    @Override
    public String toCsvString() {
        return "ManifestAccessControl{" +
                "isExported=" + isExported +
                ", isIntentFilterRegistered=" + isIntentFilterRegistered +
                ", permission='" + permission + '\'' +
                ", actions=" + actions +
                ", categories=" + categories +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ManifestAccessControl) obj;
        return this.isExported == that.isExported &&
                this.isIntentFilterRegistered == that.isIntentFilterRegistered &&
                Objects.equals(this.permission, that.permission) &&
                Objects.equals(this.actions, that.actions) &&
                Objects.equals(this.categories, that.categories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isExported, isIntentFilterRegistered, permission, actions, categories);
    }

    @Override
    public String toString() {
        return "ManifestAccessControl[" +
                "isExported=" + isExported + ", " +
                "isIntentFilterRegistered=" + isIntentFilterRegistered + ", " +
                "permission=" + permission + ", " +
                "actions=" + actions + ", " +
                "categories=" + categories + ']';
    }

}
