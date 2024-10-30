package com.uwaterloo.datadriven.analyzers.detector;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.uwaterloo.datadriven.model.accesscontrol.ManifestAccessControl;
import com.uwaterloo.datadriven.model.framework.FrameworkEp;
import com.uwaterloo.datadriven.model.framework.FrameworkReceiver;
import com.uwaterloo.datadriven.utils.ChaUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class ReceiverDetector {
    private final ClassHierarchy cha;
    private final HashMap<String, ManifestAccessControl> frameworkReceivers;
    public ReceiverDetector(ClassHierarchy cha, HashMap<String, ManifestAccessControl> frameworkReceivers) {
        this.cha = cha;
        this.frameworkReceivers = frameworkReceivers;
    }

    public HashSet<FrameworkReceiver> detectReceivers() {
        HashSet<FrameworkReceiver> receivers = new HashSet<>();
        for (Map.Entry<String, ManifestAccessControl> recv : frameworkReceivers.entrySet()) {
            IClass receiverClass = ChaUtils.getClassFromSignature(recv.getKey(), cha);
            if (receiverClass != null) {
                receivers.add(new FrameworkReceiver(receiverClass,
                        new FrameworkEp(getOnReceiveMethod(receiverClass), cha, receiverClass, recv.getValue())));
            }
        }

        return receivers;
    }

    private IMethod getOnReceiveMethod(IClass receiverClass) {
        return ChaUtils.getMethodFromSignature("onReceive(Landroid/content/Context;Landroid/content/Intent;)V",
                receiverClass);
    }
}
