package app.ai;

import java.util.List;

public record MatchResult(
        int score,
        List<String> matchedSkills,
        List<String> missingSkills,
        int skillFit,
        int experienceFit,
        int educationFit,
        String explanation) {

    public MatchResult(int score, List<String> matchedSkills, List<String> missingSkills) {
        this(score, matchedSkills, missingSkills, score, score, score,
             score >= 80 ? "Excellent match for your skill profile and experience" :
             score >= 50 ? "Moderate match with good core foundations" :
                           "Developing match with potential growth areas");
    }
}
