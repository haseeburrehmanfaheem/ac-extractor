package com.uwaterloo.datadriven.model.accesscontrol.misc;

import com.uwaterloo.datadriven.model.accesscontrol.AccessControl;
import com.uwaterloo.datadriven.utils.Counters;

import java.util.Objects;

public class AccessControlSource {
    public final String apiIdForProbRules = "api" + Counters.apiCounter.getAndIncrement();
    private final String fromApi;
    private final AccessControl ac;

    public AccessControlSource(String fromApi, AccessControl ac) {
        this.fromApi = fromApi;
        this.ac = ac;
    }
    public String fromApi() {
        return fromApi;
    }
    public AccessControl ac() {
        return ac;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessControlSource that = (AccessControlSource) o;
        return Objects.equals(fromApi, that.fromApi) && Objects.equals(ac, that.ac);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromApi, ac);
    }
}
