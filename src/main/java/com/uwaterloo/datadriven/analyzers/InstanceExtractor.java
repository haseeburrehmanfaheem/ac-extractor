package com.uwaterloo.datadriven.analyzers;

import com.uwaterloo.datadriven.model.framework.field.FrameworkField;

import java.util.HashSet;

public class InstanceExtractor {
    public static HashSet<HashSet<FrameworkField>> extractInstances(HashSet<FrameworkField> allFields) {
        HashSet<HashSet<FrameworkField>> instances = new HashSet<>();
        HashSet<FrameworkField> fields = new HashSet<>(allFields);
        while (!fields.isEmpty()) {
            FrameworkField curField = fields.iterator().next();
            fields.remove(curField);
            HashSet<FrameworkField> ins = getInstances(curField, fields);
            if (ins.size() > 1) {
                instances.add(ins);
                fields.removeAll(ins);
            }
        }
        return instances;
    }

    private static HashSet<FrameworkField> getInstances(FrameworkField field, HashSet<FrameworkField> allFields) {
        HashSet<FrameworkField> fields = new HashSet<>();
        fields.add(field);
        try {
            for (FrameworkField f : allFields) {
                if (field.immediateParent.getName().toString()
                        .equals(f.immediateParent.getName().toString())
                        && !field.parent.getName().toString().equals(f.parent.getName().toString())
                        && field.id.equals(f.id))
                    fields.add(f);
            }
        } catch (Exception e) {
            //ignore
        }
        return fields;
    }
}
