package com.uwaterloo.datadriven.model.accesscontrol;

import com.uwaterloo.datadriven.model.accesscontrol.misc.AccessControlType;
import com.uwaterloo.datadriven.model.accesscontrol.misc.ProtectionLevel;

import java.util.Collection;
import java.util.Objects;

public final class ProgrammaticAccessControl extends AccessControl {
    public final AccessControlType acType;
    public final ProtectionLevel level;
    public final Operator operator;
    public final Collection<String> values;

    public ProgrammaticAccessControl(AccessControlType acType, ProtectionLevel level, Operator operator,
                                     Collection<String> values) {
        this.acType = acType;
        if (acType.equals(AccessControlType.Permission))
            this.level = level;
        else
            this.level = ProtectionLevel.SYS_OR_SIG;
        this.operator = operator;
        this.values = values;
    }

    @Override
    public String toCsvString() {
        return "ProgrammaticAccessControl{" +
                "acType=" + acType.name() +
                ", level=" + level.name() +
                ", operator=" + operator.name() +
                ", values=" + values +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ProgrammaticAccessControl) obj;
        return Objects.equals(this.acType, that.acType) &&
                Objects.equals(this.level, that.level) &&
                Objects.equals(this.operator, that.operator) &&
                Objects.equals(this.values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(acType, level, operator, values);
    }

    @Override
    public String toString() {
        return "ProgrammaticAccessControl[" +
                "acType=" + acType + ", " +
                "level=" + level + ", " +
                "operator=" + operator + ", " +
                "values=" + values + ']';
    }


    public enum Operator {
        GT,
        LT,
        EQ;
    }
}
