package app.ai;

import java.util.*;

/**
 * Skill based job matching (the "AI module").
 *
 * Score (0-100) = 65% skill coverage + 10% text similarity (cosine, TF vectors)
 *               + 12.5% education fit + 12.5% experience fit.
 *
 * A required skill counts as matched when it is listed in the seeker's skills or
 * appears in the seeker's headline / experience / education text.
 */
public final class SkillMatcher {

    private static final double W_SKILLS = 0.65;
    private static final double W_TEXT = 0.10;
    private static final double W_EDU = 0.125;
    private static final double W_EXP = 0.125;

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "and", "or", "of", "in", "on", "at", "to", "for", "with", "is", "are",
            "we", "you", "our", "your", "will", "be", "as", "by", "from", "this", "that", "it",
            "have", "has", "ability", "work", "working", "team", "role", "looking", "good", "strong",
            "degree", "bachelor", "bachelors", "master", "masters", "any", "related", "field");

    private SkillMatcher() {}

    public static MatchResult match(List<String> seekerSkills, String seekerText, String seekerEducation,
                                    int seekerYears, List<String> jobSkills, String jobText,
                                    String requiredEducation, int minExperience) {

        List<String> required = dedupe(jobSkills);
        if (seekerSkills == null || seekerSkills.isEmpty()) {
            return new MatchResult(0, new ArrayList<>(), required, 0, 0, 0,
                    "No skills listed on your profile. Add relevant skills to see personalized AI match results.");
        }

        Set<String> owned = new HashSet<>();
        for (String s : seekerSkills) owned.add(s.trim().toLowerCase());
        String profileText = " " + String.join(" ", tokens(nz(seekerText))) + " ";

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : required) {
            String key = skill.toLowerCase();
            String phrase = " " + String.join(" ", tokens(key)) + " ";
            if (owned.contains(key) || profileText.contains(phrase)) matched.add(skill);
            else missing.add(skill);
        }

        double skillScore = required.isEmpty() ? 0.5 : (double) matched.size() / required.size();

        List<String> seekerTokens = new ArrayList<>(tokens(nz(seekerText)));
        for (String s : seekerSkills) seekerTokens.addAll(tokens(s));
        double textScore = Math.min(1.0, cosine(tf(seekerTokens), tf(tokens(nz(jobText)))) * 2.5);

        double eduScore = educationScore(seekerEducation, requiredEducation);
        double expScore = minExperience <= 0 ? 1.0 : Math.min(1.0, seekerYears / (double) minExperience);

        double total = W_SKILLS * skillScore + W_TEXT * textScore + W_EDU * eduScore + W_EXP * expScore;
        int score = (int) Math.round(Math.max(0, Math.min(1, total)) * 100);

        int skillFit = (int) Math.round(Math.max(0, Math.min(1, skillScore)) * 100);
        int expFit = (int) Math.round(Math.max(0, Math.min(1, expScore)) * 100);
        int eduFit = (int) Math.round(Math.max(0, Math.min(1, eduScore)) * 100);

        String explanation;
        if (score >= 85) {
            explanation = "Outstanding match (" + score + "%): You possess " + matched.size() + "/" + required.size() +
                    " required skills and closely match experience & education criteria.";
        } else if (score >= 70) {
            explanation = "Strong candidate (" + score + "%): High alignment in " +
                    String.join(", ", matched.stream().limit(3).toList()) +
                    (missing.isEmpty() ? "." : ". Picking up " + missing.get(0) + " will make your profile stand out even more.");
        } else if (score >= 50) {
            explanation = "Moderate fit (" + score + "%): Good baseline foundation with " + matched.size() +
                    " matching skills. Recommended focus: " +
                    (missing.isEmpty() ? "specialized domain depth" : String.join(", ", missing.stream().limit(2).toList())) + ".";
        } else {
            explanation = "Developing fit (" + score + "%): Key prerequisites needed: " +
                    (missing.isEmpty() ? "domain fundamentals" : String.join(", ", missing.stream().limit(3).toList())) + ".";
        }

        return new MatchResult(score, matched, missing, skillFit, expFit, eduFit, explanation);
    }

    private static double educationScore(String seekerEducation, String requiredEducation) {
        if (requiredEducation == null || requiredEducation.isBlank()) return 1.0;
        List<String> req = new ArrayList<>();
        for (String t : tokens(requiredEducation)) if (!STOP.contains(t)) req.add(t);
        if (req.isEmpty()) return 1.0;
        if (seekerEducation == null || seekerEducation.isBlank()) return 0.0;
        Set<String> have = new HashSet<>(tokens(seekerEducation));
        int hit = 0;
        for (String t : req) if (have.contains(t)) hit++;
        return (double) hit / req.size();
    }

    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.toLowerCase().split("[^a-z0-9+#.]+")) {
            String t = raw.replaceAll("^\\.+|\\.+$", "");
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static Map<String, Integer> tf(List<String> tokens) {
        Map<String, Integer> m = new HashMap<>();
        for (String t : tokens) if (!STOP.contains(t)) m.merge(t, 1, Integer::sum);
        return m;
    }

    private static double cosine(Map<String, Integer> a, Map<String, Integer> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        double dot = 0, na = 0, nb = 0;
        for (Map.Entry<String, Integer> e : a.entrySet()) {
            na += e.getValue() * e.getValue();
            Integer o = b.get(e.getKey());
            if (o != null) dot += e.getValue() * o;
        }
        for (int v : b.values()) nb += v * v;
        return dot == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static List<String> dedupe(List<String> skills) {
        Map<String, String> m = new LinkedHashMap<>();
        if (skills != null) for (String s : skills) {
            if (s != null && !s.isBlank()) m.putIfAbsent(s.trim().toLowerCase(), s.trim());
        }
        return new ArrayList<>(m.values());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
