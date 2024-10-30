package com.uwaterloo.datadriven.model.framework;

import com.ibm.wala.classLoader.IClass;

import java.util.List;

public class FrameworkReceiver extends FrameworkParent{
    public FrameworkReceiver(IClass receiverClass, FrameworkEp onReceive) {
        super(receiverClass, List.of(onReceive));
    }
    public FrameworkEp getEp() {
        return eps.get(0);
    }
    @Override
    String getParentType() {
        return "RECEIVER";
    }
}
