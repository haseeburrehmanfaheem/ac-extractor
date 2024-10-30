package com.uwaterloo.datadriven.model.accesscontrol;

public class DisjunctiveAccessControl extends AccessControl{
    public final AccessControl ac1, ac2;
    public DisjunctiveAccessControl(AccessControl ac1, AccessControl ac2) {
        this.ac1 = ac1;
        this.ac2 = ac2;
    }

    @Override
    public String toCsvString() {
        return "(" + ac1.toCsvString() + ") OR (" + ac2.toCsvString() + ")";
    }
}
