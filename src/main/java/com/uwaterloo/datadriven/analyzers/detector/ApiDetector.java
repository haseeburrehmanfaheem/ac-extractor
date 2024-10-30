package com.uwaterloo.datadriven.analyzers.detector;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.uwaterloo.datadriven.model.framework.FrameworkEp;
import com.uwaterloo.datadriven.model.framework.FrameworkService;

import java.util.ArrayList;
import java.util.List;

public class ApiDetector {
    private final ClassHierarchy cha;

    public ApiDetector(ClassHierarchy cha) {
        this.cha = cha;
    }

    public List<FrameworkEp> findPublicApis(FrameworkService service) {
        List<FrameworkEp> apis = new ArrayList<>();
        for (IMethod method : service.fwParentClass.getAllMethods()) {
            if (isPublicApi(method, service.fwParentClass))
                apis.add(new FrameworkEp(method, cha, service.fwParentClass, null));
        }

        return apis;
    }

    private boolean isPublicApi(IMethod method, IClass serviceClass) {
        IClass binderInterface = getBinderInterface(serviceClass);
        if (binderInterface == null)
            return false;
        for (IMethod m : binderInterface.getDeclaredMethods())
            if (method.getSelector().toString().equals(m.getSelector().toString()))
                return true;
        return false;
    }

    private IClass getBinderInterface(IClass serviceClass) {
        for (IClass intfc : serviceClass.getAllImplementedInterfaces()) {
            if (extendsIinterface(intfc)
                    && !intfc.getName().toString().equals("Landroid/os/IInterface")
                    && !intfc.getName().toString().equals("Landroid/os/IBinder"))
                return intfc;
        }

        return null;
    }

    private boolean extendsIinterface(IClass infc) {
        for (IClass impl : infc.getAllImplementedInterfaces())
            if (impl.isInterface() && impl.getName().toString().equals("Landroid/os/IInterface"))
                return true;

        return false;
    }
}
