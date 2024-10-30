package com.uwaterloo.datadriven.rules;

import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.util.collections.Pair;
import com.uwaterloo.datadriven.analyzers.FrameworkAnalyzer;
import com.uwaterloo.datadriven.analyzers.InstanceExtractor;
import com.uwaterloo.datadriven.analyzers.SimilarityCalculator;
import com.uwaterloo.datadriven.model.accesscontrol.misc.AccessControlSource;
import com.uwaterloo.datadriven.model.accesscontrol.misc.ProtectionLevel;
import com.uwaterloo.datadriven.model.framework.field.*;
import com.uwaterloo.datadriven.utils.AccessControlUtils;
import com.uwaterloo.datadriven.utils.CsvUtils;
import com.uwaterloo.datadriven.utils.PropertyUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.uwaterloo.datadriven.utils.AccessControlUtils.getMaxProtectionLevel;

public class RuleGenerator {
    public static final String HEADER_PATH = "prob-inference/prob-rules-header.pl";
    public static final String QUERY_PATH = "prob-inference/prob-rules-query.pl";

//    private static AtomicInteger fwAcRuleNum = new AtomicInteger(0);
//    private static AtomicInteger annotatedFieldRuleNum = new AtomicInteger(0);
//    private static AtomicInteger parentRuleNum = new AtomicInteger(0);
//    private static AtomicInteger similarSiblingRuleNum = new AtomicInteger(0);
//    private static AtomicInteger apiPerformsRuleNum = new AtomicInteger(0);
    private static final AtomicInteger ruleNum = new AtomicInteger(1);
    private static int fwAcRuleNum = 0;
    private static int singleChildRuleNum = 0;
    private static int nChildRuleNum = 0;
    private static int similarSiblingRuleNum = 0;
    private static int apiPerformsRuleNum = 0;
    private static int instanceRulesNum = 0;
    private static final HashMap<String, HashSet<String>> opRelations = new HashMap<>();
    private static final HashSet<String> collections = new HashSet<>();
    private static final HashSet<String> allPcRules = new HashSet<>();

    private static int getAndUpdateRuleNum() {
        return ruleNum.getAndIncrement();
    }

    private static void resetRuleNum() {
        ruleNum.set(1);
    }

    private static void writeRules(String parentName,
                                   ArrayList<String> observations,
                                   HashSet<String> apiStrings)
            throws IOException {
        Path filePath = Path.of(PropertyUtils.getOutPath(), "prob-rules", parentName);
        Files.createDirectories(filePath);
        Path obsfilePath = Path.of(filePath.toAbsolutePath().toString(), "observations");
        Path apisfilePath = Path.of(filePath.toAbsolutePath().toString(), "apis");
        Files.deleteIfExists(obsfilePath);
        Files.deleteIfExists(apisfilePath);
        Files.createFile(obsfilePath);
        Files.createFile(apisfilePath);

//        ArrayList<String> rules = new ArrayList<>(Files.readAllLines(Path.of(HEADER_PATH)));
//        rules.addAll(observations);
//        rules.addAll(Files.readAllLines(Path.of(QUERY_PATH)));
        Files.write(obsfilePath, observations);//rules);
        Files.write(apisfilePath, apiStrings);
    }


//    public static void generateAndWriteRulesParallel(String parentName,
//                                                     Collection<Pair<AccessControlSource, HashSet<Pair<FrameworkField, FieldAccess>>>> apis,
//                                                     ClassHierarchy cha, int threads) throws InterruptedException, ExecutionException, IOException {
//        ExecutorService executor = Executors.newFixedThreadPool(threads);
//        List<Future<List<String>>> futures = new ArrayList<>();
//        resetRuleNum();
//        for (Pair<AccessControlSource, HashSet<Pair<FrameworkField, FieldAccess>>> api : apis) {
//            Callable<List<String>> task = () -> processApi(api, cha);
//            futures.add(executor.submit(task));
//        }
//
//        ArrayList<String> observations = new ArrayList<>();
//        for (Future<List<String>> future : futures) {
//            List<String> cur = future.get();
//            observations.addAll(cur);
//        }
//
//        executor.shutdown();
//        System.out.println("Total fw_ac rules: " + fwAcRuleNum);
//        System.out.println("Total annotated_field rules: " + annotatedFieldRuleNum);
//        System.out.println("Total parent rules: " + parentRuleNum);
//        System.out.println("Total similar_sibling rules: " + similarSiblingRuleNum);
//        System.out.println("Total api_performs rules: " + apiPerformsRuleNum);
//        System.out.println("Total rules: " + observations.size());
//
//        writeRules(parentName, observations);
//    }

//    private static List<String> processApi(Pair<AccessControlSource, HashSet<Pair<FrameworkField, FieldAccess>>> api, ClassHierarchy cha) {
//        String curApiId = api.fst.apiIdForProbRules;
//
//        ArrayList<String> observations = new ArrayList<>();
//        ProtectionLevel maxProtectionLevel = ProtectionLevel.NONE;
//        if (api.fst.ac() instanceof ConjunctiveAccessControl || api.fst.ac() instanceof ProgrammaticAccessControl) {
//            maxProtectionLevel = getMaxProtectionLevel(api.fst.ac());
//        }
//        if(maxProtectionLevel != ProtectionLevel.NONE){
//            fwAcRuleNum.incrementAndGet();
//            observations.add(generateFwAcRule(api.fst, curApiId));
//        }
//        Collection<String> parentRule = generateParentRules(api.snd);
//        if(!parentRule.isEmpty()){
//            parentRuleNum.incrementAndGet();
//            observations.addAll(parentRule);
//        }
//        Collection<String> siblingRule = generateSiblingRules(api.snd, cha);
//        if(!siblingRule.isEmpty()){
//            similarSiblingRuleNum.incrementAndGet();
//            observations.addAll(siblingRule);
//        }
//        Collection<String> apiPerformsRule = generateApiPerformsRules(curApiId, api.snd);
//        if(!apiPerformsRule.isEmpty()){
//            apiPerformsRuleNum.incrementAndGet();
//            observations.addAll(apiPerformsRule);
//        }
//        System.out.println(curApiId + "::" + api.fst.fromApi());
//        return observations;
//    }

    public static void generateAndWriteRules(String parentName,
                                             Collection<Pair<AccessControlSource, HashSet<Pair<FrameworkField, FieldAccess>>>> apis,
                                             ClassHierarchy cha) throws IOException {
        ArrayList<String> observations = new ArrayList<>();
        HashSet<String> apiStrings = new HashSet<>();
        HashSet<String> visitedFwRules = new HashSet<>();
        HashSet<String> visitedPcRules = new HashSet<>();
        HashSet<String> visitedSibRules = new HashSet<>();
        HashSet<String> visitedInsRules = new HashSet<>();
        HashSet<String> visitedPerfRules = new HashSet<>();
        resetRuleNum();
        for (Pair<AccessControlSource, HashSet<Pair<FrameworkField, FieldAccess>>> api : apis) {
            String curApi = api.fst.apiIdForProbRules;
            System.out.println(curApi + "::" + api.fst.fromApi());
            apiStrings.add(curApi);
            ArrayList<String> apiObs = new ArrayList<>();

            if (visitedFwRules.add(api.fst+curApi)) {
                fwAcRuleNum++;
                apiObs.add(generateFwAcRule(api.fst, curApi));
            }

            Collection<String> parentRule = generateParentRules(api.snd, visitedPcRules);
            if(!parentRule.isEmpty()){
                apiObs.addAll(parentRule);
            }
            Collection<String> siblingRule = generateSiblingRules(api.snd, cha, visitedSibRules);
            if(!siblingRule.isEmpty()){
                similarSiblingRuleNum += siblingRule.size();
                apiObs.addAll(siblingRule);
            }
            Collection<String> apiPerformsRule = generateApiPerformsRules(curApi, api.snd, visitedPerfRules);
            if(!apiPerformsRule.isEmpty()) {
                apiPerformsRuleNum += apiPerformsRule.size();
                apiObs.addAll(apiPerformsRule);
            }
            Collection<String> instanceRules = generateInstanceRules(api.snd, visitedInsRules);
            if(!instanceRules.isEmpty()) {
                instanceRulesNum += instanceRules.size();
                apiObs.addAll(instanceRules);
            }
            if (apiObs.size() > 1) {
                observations.addAll(apiObs);
                ArrayList<String> apiCsvStr = new ArrayList<>();
                apiCsvStr.add(api.fst.apiIdForProbRules);
                apiCsvStr.add(api.fst.fromApi());
                apiCsvStr.add(AccessControlUtils.getMaxProtectionLevel(api.fst.ac()).name());
                apiCsvStr.addAll(getTargetField(api.snd));
                FrameworkAnalyzer.apisCsvStringsList.add(apiCsvStr.toArray(new String[0]));
            }
        }
        allPcRules.addAll(visitedPcRules);
        System.out.println("Total fw_ac rules: " + fwAcRuleNum);
//        System.out.println("Total annotated_field rules: " + annotatedFieldRuleNum);
        System.out.println("Total 1 child parent rules: " + singleChildRuleNum);
        System.out.println("Total n child parent rules: " + nChildRuleNum);
        System.out.println("Total similar_sibling rules: " + similarSiblingRuleNum);
        System.out.println("Total is_instance rules: " + instanceRulesNum);
        System.out.println("Total api_performs rules: " + apiPerformsRuleNum);
        int total = fwAcRuleNum + singleChildRuleNum + nChildRuleNum
                + instanceRulesNum + similarSiblingRuleNum + apiPerformsRuleNum;
        System.out.println("Total rules: " + total);
        writeRules(parentName, observations, apiStrings);
    }

    public static void writeRuleStats() {
        ArrayList<String[]> ruleStats = new ArrayList<>();
        ruleStats.add(new String[] {"Total 1 child parent rules: ", ""+singleChildRuleNum});
        ruleStats.add(new String[] {"Total n child parent rules: ", ""+nChildRuleNum});
        ruleStats.add(new String[] {"Total sibling rules: ", ""+similarSiblingRuleNum});
        ruleStats.add(new String[] {"Total similarity rules: ",
                ""+(similarSiblingRuleNum+singleChildRuleNum+nChildRuleNum)});
        ruleStats.add(new String[] {"Total is_instance rules: ", ""+instanceRulesNum});
        ruleStats.add(new String[] {"Total api_performs rules: ", ""+apiPerformsRuleNum});
        ruleStats.add(new String[] {"Total spec-gen rules: ", ""+collections.size()});
        int opRelRules = 0;
        for (HashSet<String> opRel : opRelations.values()) {
            opRelRules += (opRel.size()*(opRel.size()-1))/2;
        }
        ruleStats.add(new String[] {"Total op relations rules: ", ""+opRelRules});
//        int total = fwAcRuleNum + singleChildRuleNum + nChildRuleNum
//                + instanceRulesNum + similarSiblingRuleNum + apiPerformsRuleNum;
//        ruleStats.add(new String[] {"Total rules: ", ""+total});
        CsvUtils.writeToCsv("prob-rules/rule_stats.csv", ruleStats);
    }

    private static ArrayList<String> getTargetField(HashSet<Pair<FrameworkField, FieldAccess>> field) {
        ArrayList<String> tFields = new ArrayList<>();
        for (Pair<FrameworkField, FieldAccess> fieldAccess : field) {
            tFields.add(getFieldInfo(fieldAccess));
        }
        return tFields;
    }

    private static String getFieldInfo(Pair<FrameworkField, FieldAccess> fieldAccess) {
        if (fieldAccess == null)
            return "";
        return fieldAccess.fst.fieldIdForProbRules + "::"
                + fieldAccess.fst.id + "::"
                + fieldAccess.fst.type + "::"
                + fieldAccess.snd.accessType().toString().toLowerCase() + "::"
                + getGranularity(fieldAccess.snd);
    }

    private static Collection<String> generateInstanceRules(HashSet<Pair<FrameworkField, FieldAccess>> accesses,
                                                            HashSet<String> visitedInsRules) {
        HashSet<FrameworkField> allFields = new HashSet<>();
        HashSet<String> insRules = new HashSet<>();
        accesses.forEach(access -> access.fst.parentClass.traverseAllFields(allFields::add));
        for (HashSet<FrameworkField> insSet : InstanceExtractor.extractInstances(allFields)) {
            for (Pair<FrameworkField, FrameworkField> insPair : getAllPairs(insSet)) {
                if (visitedInsRules.add(insPair.fst.fieldIdForProbRules+insPair.snd.fieldIdForProbRules)) {
                    insRules.add("is_instance(" + insPair.fst.fieldIdForProbRules + ", "
                            + insPair.snd.fieldIdForProbRules + ").");
                }
            }
        }
        return insRules;
    }

    private static Collection<Pair<FrameworkField, FrameworkField>> getAllPairs(HashSet<FrameworkField> insSet) {
        HashSet<Pair<FrameworkField, FrameworkField>> allPairs = new HashSet<>();
        if (insSet != null && insSet.size() > 1) {
            ArrayList<FrameworkField> list = new ArrayList<>(insSet);
            for (int i=0; i<list.size(); i++) {
                for (int j=i+1; j<list.size(); j++) {
                    allPairs.add(Pair.make(list.get(i), list.get(j)));
                }
            }
        }
        return allPairs;
    }

    private static Collection<String> generateSiblingRules(HashSet<Pair<FrameworkField, FieldAccess>> accesses, ClassHierarchy cha, HashSet<String> visitedSibRules) {
        ArrayList<String> rules = new ArrayList<>();
        for (Pair<FrameworkField, FieldAccess> access : accesses) {
            for (FrameworkField s : access.fst.parentClass.getSiblings(access.fst, access.snd.fieldPath())) {
                if (visitedSibRules.add(access.fst.fieldIdForProbRules+s.fieldIdForProbRules)
                        && visitedSibRules.add(s.fieldIdForProbRules+access.fst.fieldIdForProbRules)) {
                    rules.add("similar_sibling("
                            + access.fst.fieldIdForProbRules + ", "
                            + s.fieldIdForProbRules + ", "
                            + SimilarityCalculator.calculateSimScore(access.fst, s, cha) + ", "
                            + getAndUpdateRuleNum() + ").");
                }
            }
        }
        return rules;
    }

    private static Collection<String> generateParentRules(HashSet<Pair<FrameworkField, FieldAccess>> accesses,
                                                          HashSet<String> parentRules) {
        ArrayList<String> rules = new ArrayList<>();
        for (Pair<FrameworkField, FieldAccess> access : accesses) {
            FrameworkField curRoot = getRoot(access.fst, access.snd.fieldPath());
            curRoot.parentClass.doDfs(curRoot, null, (parent, child) -> {
                if (parent != null && child != null
                        && !parent.fieldIdForProbRules.equals(child.fieldIdForProbRules)) {
                    if (!parentRules.contains(child.fieldIdForProbRules + parent.fieldIdForProbRules)) {
                        String ruleType = getParentRule(parent, child);
                        String pRule = ruleType + "("
                                + child.fieldIdForProbRules + ", "
                                + parent.fieldIdForProbRules + ", "
                                + getAndUpdateRuleNum() + ").";
                        parentRules.add(child.fieldIdForProbRules + parent.fieldIdForProbRules);
                        rules.add(pRule);
                        if (allPcRules.add(child.fieldIdForProbRules + parent.fieldIdForProbRules)) {
                            if (ruleType.equals("parent_one_child"))
                                singleChildRuleNum++;
                            else
                                nChildRuleNum++;
                        }
                    }
                }
            }, new HashSet<>());
        }
        return rules;
    }

    private static FrameworkField getRoot(FrameworkField node, ArrayList<String> path) {
        if (path == null || path.isEmpty())
            return null;
        if (path.size()==1)
            return node;
        for (FrameworkField field : node.parentClass.fields.values()) {
            if (field.id.equals(path.get(0)))
                return field;
        }
        return null;
    }

    private static String getParentRule(FrameworkField parent, FrameworkField child) {
        boolean isSingleChild = false;
        try {
            if (parent instanceof ComplexField compParent) {
                isSingleChild = compParent.members.size() > 1;
            } else if (parent instanceof CollectionField collParent) {
                FrameworkField idFwFld = collParent.members.get(CollectionField.ALL_MEMBERS).indexDummy();
                FrameworkField valFwFld = collParent.members.get(CollectionField.ALL_MEMBERS).valueDummy();
                if (idFwFld != null && idFwFld.equals(child))
                    isSingleChild = true;
                if (valFwFld != null && valFwFld.equals(child))
                    isSingleChild = true;
            } else if (parent instanceof PrimitiveField) {
                throw new RuntimeException("Something weird happened!");
            }
        } catch (Exception e) {
            //ignore
        }
        if (isSingleChild) {
            return "parent_one_child";
        } else {
            return "parent_n_child";
        }
    }

    private static Collection<String> generateApiPerformsRules(String curApi,
                                                               HashSet<Pair<FrameworkField, FieldAccess>> accesses, HashSet<String> visitedPerfRules) {
        ArrayList<String> rules = new ArrayList<>();
        for (Pair<FrameworkField, FieldAccess> access : accesses) {
            if (visitedPerfRules.add(curApi+access.fst.fieldIdForProbRules+access.snd.accessType()+getGranularity(access.snd))) {
                String gran = getGranularity(access.snd);
                rules.add("api_performs(r"
                        + curApi + ", "
                        + access.fst.fieldIdForProbRules + ", r"
                        + access.fst.fieldIdForProbRules + ", "
                        + access.snd.accessType().toString().toLowerCase() + ", "
                        + gran + ", "
                        + getAndUpdateRuleNum() + ").");
                if (access.fst instanceof CollectionField)
                    collections.add(access.fst.fieldIdForProbRules);
                if (!opRelations.containsKey(access.fst.fieldIdForProbRules + gran))
                    opRelations.put(access.fst.fieldIdForProbRules + gran, new HashSet<>());
                opRelations.get(access.fst.fieldIdForProbRules + gran).add(access.snd.accessType().toString().toLowerCase());
            }
        }
        return rules;
    }

    private static String generateFwAcRule(AccessControlSource acs, String curApi) {
        return "fw_ac(" + curApi + ", r" + curApi + ", " +
                getAc(acs) + ", " + getAndUpdateRuleNum() + ").";
    }

    private static String getAc(AccessControlSource acs) {
        ProtectionLevel maxP = getMaxProtectionLevel(acs.ac());
        if (maxP.equals(ProtectionLevel.NORMAL))
            return "normal";
        else if (maxP.equals(ProtectionLevel.DANGEROUS)) {
            return "dangerous";
        } else if (maxP.equals(ProtectionLevel.SYS_OR_SIG)) {
            return "system";
        }
        return "no_ac";
    }

    private static String getGranularity(FieldAccess access) {
        if (access == null || access.fieldPath().isEmpty())
            return "all";
        if (access.fieldPath().contains(CollectionField.SOME_MEMBERS))
            return "some";
        return "all";
    }
}
