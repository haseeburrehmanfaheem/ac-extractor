package com.uwaterloo.datadriven.model.functional;

import com.uwaterloo.datadriven.model.framework.field.FrameworkField;

public interface FieldConsumer {
    void consume(FrameworkField field);
}
