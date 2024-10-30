package com.uwaterloo.datadriven.model.framework.field;

import com.ibm.wala.classLoader.IClass;
import com.uwaterloo.datadriven.analyzers.FrameworkAnalyzer;
import com.uwaterloo.datadriven.model.framework.FrameworkClass;
import com.uwaterloo.datadriven.utils.Counters;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public abstract class FrameworkField {
    public static final String PUBLIC_MOD = "PUBLIC";
    public static final String PROTECTED_MOD = "PROTECTED";
    public static final String PRIVATE_MOD = "PRIVATE";
    public static final String PACKAGE_PRIVATE_MOD = "PACKAGE-PRIVATE";
    public static final String STATIC_KEYWORD = "STATIC";
    public static final String VOLATILE_KEYWORD = "VOLATILE";
    public static final String FINAL_KEYWORD = "FINAL";
    // Package and class name (i.e., full identifier) of the parent class
    public FrameworkClass parentClass;
    public IClass parent;
    public IClass immediateParent;
    // The field name declared within the parent class
    public final String id;
    // The type of the field - e.g., String, Boolean, HashMap, etc...
    public final String type;
    public final HashSet<String> modsAndKeywords = new HashSet<>();
    public final String fieldIdForProbRules = "field" + Counters.fieldCounter.getAndIncrement();

    protected FrameworkField(IClass parent, IClass immediateParent, String id,
                             String type, HashSet<String> modsAndKeywords) {
        this.parent = parent;
        this.immediateParent = immediateParent;
        this.id = id;
        this.type = type;
        this.modsAndKeywords.addAll(modsAndKeywords);
        if (parent != null) {
            FrameworkAnalyzer.fieldsCsvStringsList.add(new String[]{fieldIdForProbRules,
                    id, type, parent.getName().getClassName().toString()});
        } else {
            FrameworkAnalyzer.fieldsCsvStringsList.add(new String[]{fieldIdForProbRules,
                    id, type, "Null parent"});
        }
    }

    public void setParentClass(FrameworkClass parentClass) {
        this.parentClass = parentClass;
    }
    public final ArrayList<String> toCsvString() {
        ArrayList<String> csvStrings = new ArrayList<>(List.of(id, parent.getName().toString(), immediateParent == null ? " " : immediateParent.getName().toString() , type));
        csvStrings.addAll(getChildCsvStrings());
        return csvStrings;
    }
    public boolean isPublic() {
        return modsAndKeywords.contains(PUBLIC_MOD);
    }
    public boolean isPrivate() {
        return modsAndKeywords.contains(PRIVATE_MOD);
    }
    public boolean isProtected() {
        return modsAndKeywords.contains(PROTECTED_MOD);
    }
    public boolean isPackagePrivate() {
        return modsAndKeywords.contains(PACKAGE_PRIVATE_MOD);
    }
    public boolean isStatic() {
        return modsAndKeywords.contains(STATIC_KEYWORD);
    }
    public boolean isVolatile() {
        return modsAndKeywords.contains(VOLATILE_KEYWORD);
    }
    public boolean isFinal() {
        return modsAndKeywords.contains(FINAL_KEYWORD);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FrameworkField field = (FrameworkField) o;
        return Objects.equals(parentClass, field.parentClass)
                && Objects.equals(id, field.id) && Objects.equals(type, field.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent.getName().toString()
                , immediateParent == null ? "" : immediateParent.getName().toString(),
                id, type);
    }

    protected abstract ArrayList<String> getChildCsvStrings();
    public abstract ArrayList<String> getMemberCsvStrings();


    public abstract String getNormalizedType();

    @Override
    public String toString() {
        return "Parent Class: " + parent.getName().toString()
                + " ID: " + id
                + " Type: " + type;
    }
}
