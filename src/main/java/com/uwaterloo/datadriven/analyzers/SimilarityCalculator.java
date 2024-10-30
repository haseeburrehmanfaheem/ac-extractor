package com.uwaterloo.datadriven.analyzers;

import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.uwaterloo.datadriven.model.framework.field.AccessType;
import com.uwaterloo.datadriven.model.framework.field.CollectionField;
import com.uwaterloo.datadriven.model.framework.field.ComplexField;
import com.uwaterloo.datadriven.model.framework.field.FrameworkField;
import com.uwaterloo.datadriven.utils.CollectionUtils;
import com.uwaterloo.datadriven.utils.ExecutionUtils;
import com.uwaterloo.datadriven.utils.FieldUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;

public class SimilarityCalculator {
    private static final boolean USE_MODEL_FOR_COMPLEX_FIELD_TYPE_SIM = false;

    private static final double TYPE_SIM_SCALE = 0.25;
    private static final double NAMING_SIM_SCALE = 0.25;
    private static final double MOD_KEYWORD_SIM_SCALE = 0.25;
    private static final double STRUCTURAL_SIM_SCALE = 0.25;

    private static final double MAX_SIM = 1;
    private static final double MIN_SIM = 0;

    private static final double SAME_NORMAL_TYPE_SIM = 0.5;
    private static final double SAME_COLL_TYPE_SIM = 0.75;

    private static final double SAME_MOD_SIM = 0.25;
    private static final double SAME_KEYWORD_SIM = 0.25;

    private static final double SAME_NUM_GETTERS_SIM = 0.33;
    private static final double SAME_NUM_SETTERS_SIM = 0.33;
    private static final double SAME_NUM_OTHERS_SIM = 0.34;

    public static double calculateSimScore(FrameworkField f1, FrameworkField f2, ClassHierarchy cha) {
        return TYPE_SIM_SCALE * deriveTypeSim(f1, f2, cha)
                + NAMING_SIM_SCALE * deriveNamingSim(f1, f2)
                + MOD_KEYWORD_SIM_SCALE * deriveModKeywordSim(f1, f2)
                + STRUCTURAL_SIM_SCALE * deriveStructuralSim(f1, f2);
    }

    private static double deriveTypeSim(FrameworkField f1, FrameworkField f2, ClassHierarchy cha) {
        if (f1.type.equals(f2.type)) return MAX_SIM;
        if (!f1.getNormalizedType().equals(f2.getNormalizedType())) return MIN_SIM;
        if (f1 instanceof ComplexField) {
            if (USE_MODEL_FOR_COMPLEX_FIELD_TYPE_SIM) {
                String cls1 = FieldUtils.getClassContentAsString(f1.type, cha);
                String cls2 = FieldUtils.getClassContentAsString(f2.type, cha);
                return deriveStringSim(cls1, cls2);
            }
            return MIN_SIM;
        }
        if (f1 instanceof CollectionField) {
            String outerT1 = CollectionUtils.getInstance(cha).getOuterTypeStr((CollectionField) f1);
            String outerT2 = CollectionUtils.getInstance(cha).getOuterTypeStr((CollectionField) f2);
            if (outerT1.equals(outerT2)) return SAME_COLL_TYPE_SIM;
        }

        return SAME_NORMAL_TYPE_SIM;
    }

    private static double deriveNamingSim(FrameworkField f1, FrameworkField f2) {
        return deriveStringSim(f1.id, f2.id);
    }

    private static double deriveModKeywordSim(FrameworkField f1, FrameworkField f2) {
        double simScore = MIN_SIM;
        if ((f1.isPublic() && f2.isPublic())
                || (f1.isProtected() && f2.isProtected())
                || (f1.isPackagePrivate() && f2.isPackagePrivate())
                || (f1.isPrivate() && f2.isPrivate()))
            simScore += SAME_MOD_SIM;
        if ((f1.isStatic() && f2.isStatic())
                || (!f1.isStatic() && !f2.isStatic()))
            simScore += SAME_KEYWORD_SIM;
        if ((f1.isVolatile() && f2.isVolatile())
                || (!f1.isVolatile() && !f2.isVolatile()))
            simScore += SAME_KEYWORD_SIM;
        if ((f1.isFinal() && f2.isFinal())
                || (!f1.isFinal() && !f2.isFinal()))
            simScore += SAME_KEYWORD_SIM;

        return simScore;
    }

    private static double deriveStructuralSim(FrameworkField f1, FrameworkField f2) {
        double simScore = MIN_SIM;
        if (f1.parentClass.getOperatingMethods(f1, AccessType.GET).size()
                == f2.parentClass.getOperatingMethods(f2, AccessType.GET).size())
            simScore += SAME_NUM_GETTERS_SIM;
        if (f1.parentClass.getOperatingMethods(f1, AccessType.SET).size()
                == f2.parentClass.getOperatingMethods(f2, AccessType.SET).size())
            simScore += SAME_NUM_SETTERS_SIM;
        if (getNumAllOpMethods(f1) == getNumAllOpMethods(f2))
            simScore += SAME_NUM_OTHERS_SIM;

        return simScore;
    }

    private static int getNumAllOpMethods(FrameworkField f) {
        int total = 0;
        for (AccessType accessType : AccessType.values())
            total += f.parentClass.getOperatingMethods(f, accessType).size();
        return total;
    }

    private static double deriveStringSim(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isBlank() || s2.isBlank())
            return MIN_SIM;
//        String similarityCmd = "python3 -W ignore NLP/similarity_detector.py " + s1 + " " + s2;
        double sim = getNormalizedLevenshteinDistance(s1, s2);//ExecutionUtils.executeForDouble(similarityCmd);
        return Math.max(sim, MIN_SIM);
    }

    private static double getNormalizedLevenshteinDistance(String s1, String s2) {
        double maxLen = Math.max(s1.length(), s2.length());
        return (maxLen - (double)LevenshteinDistance.getDefaultInstance().apply(s1, s2))/maxLen;
    }
}
