package com.uwaterloo.datadriven.model.functional;

import com.uwaterloo.datadriven.model.framework.field.FrameworkField;

public interface EdgeConsumer {
    void consumeEdge(FrameworkField p, FrameworkField c);
}
