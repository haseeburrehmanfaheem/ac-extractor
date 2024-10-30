package com.uwaterloo.datadriven.analyzers;

import com.ibm.wala.util.collections.Pair;
import com.uwaterloo.datadriven.model.accesscontrol.misc.AccessControlSource;
import com.uwaterloo.datadriven.model.framework.field.AccessType;
import com.uwaterloo.datadriven.model.framework.field.ComplexField;
import com.uwaterloo.datadriven.model.framework.field.FrameworkField;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class MemberAccessExtractor {

    // Generic vs Specific
    public static Collection<Pair<AccessControlSource,AccessControlSource>> extractAllAccess(ComplexField field){
        // traverse over all members
        Collection<Pair<AccessControlSource,AccessControlSource>> allAccess = new HashSet<>();
        //TODO: Fix this
//        for(AccessType t : AccessType.values()){
//            HashMap<AccessControlSource, HashSet<FrameworkField>> revMap = new HashMap<>();
//            for(String fieldId: field.members.keySet()){ // traversing over all members
//                Map<AccessType, HashSet<AccessControlSource>> fieldAccessMap = field.members.get(fieldId).snd.accessTypeMap;
//                FrameworkField fieldMember = field.members.get(fieldId).fst;
//                if(fieldAccessMap.get(t) != null){
//                    for(AccessControlSource acSource: fieldAccessMap.get(t)){ // traversing over Hashset of ACS
//                        if(!revMap.containsKey(acSource))
//                            revMap.put(acSource, new HashSet<>());
//                        if(fieldId.equals(FrameworkField.SELF)){
//                            for(String fieldId2: field.members.keySet()){
//                                revMap.get(acSource).add(field.members.get(fieldId2).fst);
//                            }
//                        }
//                        else{
//                            revMap.get(acSource).add(fieldMember);
//                        }
//                    }
//                }
//            }
//            for (AccessControlSource entry1 : revMap.keySet()) {
//                for (AccessControlSource entry2 : revMap.keySet()) {
//                    if(!entry1.equals(entry2)){ // not comparing to itself
//                        if(revMap.get(entry1).containsAll(revMap.get(entry2))){ // entry1 is superset of entry2
//                            allAccess.add(Pair.make(entry1, entry2)); // generic vs specific
//                        }
//                        // do not need another else-if for other way around cuz deduplication
//                    }
//                }
//            }
//        }
        return allAccess;
    }
}
