package com.uwaterloo.datadriven.model.framework.field;

import java.util.ArrayList;

public record FieldAccess(ArrayList<String> fieldPath, AccessType accessType) {
}
