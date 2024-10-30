package com.uwaterloo.datadriven.model.framework;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.uwaterloo.datadriven.analyzers.detector.ApiDetector;

public class FrameworkService extends FrameworkParent{

    public FrameworkService(IClass serviceClass, ClassHierarchy cha) {
        super(serviceClass);
        this.eps = (new ApiDetector(cha)).findPublicApis(this);
    }

    @Override
    String getParentType() {
        return "SERVICE";
    }
}
