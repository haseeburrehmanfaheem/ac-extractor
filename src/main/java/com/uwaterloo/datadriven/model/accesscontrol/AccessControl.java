package com.uwaterloo.datadriven.model.accesscontrol;

import java.util.Objects;

public abstract class AccessControl {
    public abstract String toCsvString();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessControl that = (AccessControl) o;
        return Objects.equals(toCsvString(), that.toCsvString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(toCsvString());
    }
}
