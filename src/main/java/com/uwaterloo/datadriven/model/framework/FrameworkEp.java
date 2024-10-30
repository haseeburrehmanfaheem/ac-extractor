package com.uwaterloo.datadriven.model.framework;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.uwaterloo.datadriven.model.accesscontrol.ManifestAccessControl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FrameworkEp {
    public final DefaultEntrypoint epMethod;
    public final IClass parentClass;
    public final ManifestAccessControl manifestAc;
    public FrameworkEp(IMethod epMethod, ClassHierarchy cha, IClass parentClass, ManifestAccessControl manifestAc) {
        this.parentClass = parentClass;
        this.manifestAc = manifestAc;
        this.epMethod = new DefaultEntrypoint(epMethod, cha);
    }
    public ArrayList<String> toCsvStrings() {
        ArrayList<String> csvStr = new ArrayList<>(List.of(
                epMethod.getMethod().getSignature(),
                parentClass.getName().toString()
        ));
        if (manifestAc != null)
            csvStr.add(manifestAc.toCsvString());
        else
            csvStr.add("");
        return csvStr;
    }
}
