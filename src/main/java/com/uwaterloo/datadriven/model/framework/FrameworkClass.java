package com.uwaterloo.datadriven.model.framework;

import com.uwaterloo.datadriven.model.accesscontrol.misc.AccessControlSource;
import com.uwaterloo.datadriven.model.framework.field.*;
import com.uwaterloo.datadriven.model.functional.EdgeConsumer;
import com.uwaterloo.datadriven.model.functional.FieldConsumer;
import com.uwaterloo.datadriven.utils.CsvUtils;

import java.util.*;

public class FrameworkClass {
    public final String classPath;
    // A map from FieldPath to accessMap
    public final HashMap<ArrayList<String>, FieldAccessMap<AccessControlSource>> fieldAccesses = new HashMap<>();
    public final HashMap<String, FrameworkField> fields = new HashMap<>();
    public final HashMap<String, AccessControlSource> apiToAc = new HashMap<>();

    public FrameworkClass(String classPath) {
        this.classPath = classPath;
    }

    public void addField(FrameworkField field) {
        if (fields.containsKey(field.id))
            return;
        fields.put(field.id, field);
    }

    public void addApi(String apiSignature, AccessControlSource apiAc, HashSet<FieldAccess> accesses) {
        if (apiToAc.containsKey(apiSignature))
            return;
        apiToAc.put(apiSignature, apiAc);
        for (FieldAccess access : accesses) {
            if (!fieldAccesses.containsKey(access.fieldPath()))
                fieldAccesses.put(access.fieldPath(), new FieldAccessMap<>());
            fieldAccesses.get(access.fieldPath()).accessTypeMap.get(access.accessType()).add(apiAc);
        }
    }
    public HashSet<AccessControlSource> getOperatingMethods(FrameworkField field, AccessType type) {
        HashSet<ArrayList<String>> fieldPaths = findPaths(field);
        HashSet<AccessControlSource> retVal = new HashSet<>();
        for (ArrayList<String> path : fieldPaths) {
            if (fieldAccesses.containsKey(path))
                retVal.addAll(fieldAccesses.get(path).accessTypeMap.get(type));
        }
        return retVal;
    }

    private HashSet<ArrayList<String>> findPaths(FrameworkField field) {
        HashSet<ArrayList<String>> paths = new HashSet<>();
        for (FrameworkField root : fields.values()) {
            HashSet<ArrayList<String>> curPaths = getDfsPaths(root, field);
            if (!curPaths.isEmpty())
                paths.addAll(curPaths);
        }
        return paths;
    }

    private HashSet<ArrayList<String>> getDfsPaths(FrameworkField root, FrameworkField field) {
        if(root == null || field == null){
            return new HashSet<>();
        }
        HashSet<ArrayList<String>> paths = new HashSet<>();
        if (root.equals(field)) {
            paths.add(new ArrayList<>(List.of(field.id)));
            return paths;
        } else if (!(root instanceof PrimitiveField)) {
            if (root instanceof ComplexField complexField) {
                for (FrameworkField member : complexField.members.values()) {
                    HashSet<ArrayList<String>> childPaths = getDfsPaths(member, field);
                    if (!childPaths.isEmpty()) {
                        childPaths.forEach(childPath -> childPath.add(0, root.id));
                        paths.addAll(childPaths);
                    }
                }
            } else if (root instanceof CollectionField collectionField) {
                for (String memberId : collectionField.members.keySet()) {
                    CollectionMember member = collectionField.members.get(memberId);
                    HashSet<ArrayList<String>> childIndexPaths = getDfsPaths(member.indexDummy(), field);
                    if (!childIndexPaths.isEmpty())
                        childIndexPaths.forEach(childPath ->
                                childPath.addAll(0, List.of(root.id,
                                        CollectionField.INDEX_MEMBER)));
                    if (!childIndexPaths.isEmpty())
                        paths.addAll(childIndexPaths);
                    HashSet<ArrayList<String>> childValPaths = getDfsPaths(member.valueDummy(), field);
                    if (!childValPaths.isEmpty())
                        childValPaths.forEach(childPath ->
                                childPath.addAll(0, List.of(root.id,
                                        CollectionField.VALUE_MEMBER)));
                    if (!childValPaths.isEmpty())
                        paths.addAll(childValPaths);
                }
            }
        }
        return paths;
    }

    public void traverseAllFields(FieldConsumer consumer) {
        HashSet<FrameworkField> visited = new HashSet<>();
        for (FrameworkField field : fields.values()) {
            doDfs(field, consumer, null, visited);
        }
    }

    public void doDfs(FrameworkField root, FieldConsumer fieldConsumer,
                       EdgeConsumer edgeConsumer,
                       HashSet<FrameworkField> visited) {
        if (!visited.contains(root)) {
            if (fieldConsumer != null)
                fieldConsumer.consume(root);
            visited.add(root);
            if (root instanceof ComplexField complexField) {
                complexField.members.values().forEach(member -> {
                    if (edgeConsumer != null)
                        edgeConsumer.consumeEdge(root, member);
                    doDfs(member, fieldConsumer, edgeConsumer, visited);
                });
            } else if (root instanceof CollectionField collectionField) {
                collectionField.members.values().forEach(member -> {
                    if (edgeConsumer != null) {
                        edgeConsumer.consumeEdge(root, member.indexDummy());
                        edgeConsumer.consumeEdge(root, member.valueDummy());
                    }
                    doDfs(member.indexDummy(), fieldConsumer, edgeConsumer, visited);
                    doDfs(member.valueDummy(), fieldConsumer,edgeConsumer, visited);
                });
            }
        }
    }

    public FrameworkField getParent(FrameworkField child, ArrayList<String> path) {
        if (path == null || child == null || path.size() < 2)
            return null;
        FrameworkField root = fields.get(path.get(0));
        boolean accessIndex = false;
        for (int i=1; i<path.size(); i++) {
            FrameworkField nextNode = root;
            if (root instanceof CollectionField cFld) {
                String cur = path.get(i);
                CollectionMember next;
                if (cur.equals(CollectionField.INDEX_MEMBER)) {
                    accessIndex = true;
                    continue;
                } else if (cur.equals(CollectionField.SOME_MEMBERS)) {
                    next = cFld.members.get(CollectionField.SOME_MEMBERS);
                } else {
                    next = cFld.members.get(CollectionField.ALL_MEMBERS);
                }
                if (next != null) {
                    if (accessIndex)
                        nextNode = next.indexDummy();
                    else
                        nextNode = next.valueDummy();
                } else {
                    return null;
                }
            } else if (root instanceof ComplexField compFld) {
                nextNode = compFld.members.get(path.get(i));
            }
            if (nextNode != null && !nextNode.equals(root) && nextNode.equals(child))
                return root;
        }
        return null;
    }

    public HashSet<FrameworkField> getSiblings(FrameworkField node, ArrayList<String> path) {
        HashSet<FrameworkField> siblings = new HashSet<>();
        FrameworkField parent = getParent(node, path);
        if (parent == null)
            return siblings;
        if (parent instanceof ComplexField compFld) {
            for (FrameworkField sibling : compFld.members.values()) {
                if (!sibling.equals(node))
                    siblings.add(sibling);
            }
        }
        return siblings;
    }

    public ArrayList<String[]> toCsvStrings() {
        ArrayList<String[]> csvData = new ArrayList<>();
        FieldConsumer toCsvConsumer = field -> {
            HashSet<ArrayList<String>> s = findPaths(field);
            for (ArrayList<String> path : s) {
                if(fieldAccesses.containsKey(path)){
                    List<String> firstRow = new ArrayList<>(field.toCsvString());
                    firstRow.add(""+path);
                    firstRow.addAll(fieldAccesses.get(path).toCsvString());
                    firstRow.addAll(field.getMemberCsvStrings());
                    csvData.add(firstRow.toArray(new String[0]));
                }
                else{
                    // for debugging
//                    List<String> firstRow = new ArrayList<>(field.toCsvString());
//                    firstRow.add(path.toString());
//                    firstRow.addAll(new FieldAccessMap<>().toCsvString());
//                    firstRow.addAll(field.getMemberCsvStrings());
//                    csvData.add(firstRow.toArray(new String[0]));
                }

            }
        };
        traverseAllFields(toCsvConsumer);
        return csvData;
    }
}
