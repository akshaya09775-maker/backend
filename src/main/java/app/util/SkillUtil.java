package app.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Skills are stored in the database as one comma separated string. */
public final class SkillUtil {

    private SkillUtil() {}

    public static List<String> parse(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        return clean(List.of(csv.split(",")));
    }

    public static List<String> clean(List<String> skills) {
        Map<String, String> unique = new LinkedHashMap<>();
        if (skills != null) {
            for (String s : skills) {
                if (s == null) continue;
                String t = s.trim().replaceAll("\\s+", " ");
                if (!t.isEmpty()) unique.putIfAbsent(t.toLowerCase(), t);
            }
        }
        return new ArrayList<>(unique.values());
    }

    public static String join(List<String> skills) {
        return String.join(", ", clean(skills));
    }
}
