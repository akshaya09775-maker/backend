package app.service;

import app.ai.MatchResult;
import app.ai.SkillMatcher;
import app.entity.Job;
import app.entity.JobSeekerProfile;
import app.util.SkillUtil;
import org.springframework.stereotype.Service;

/** Adapter between the JPA entities and the pure SkillMatcher algorithm. */
@Service
public class MatchingService {

    public MatchResult match(JobSeekerProfile p, Job j) {
        String seekerText = join(p.getHeadline(), p.getAbout(), p.getExperience(), p.getEducation());
        int years = p.getExperienceYears() == null ? 0 : p.getExperienceYears();
        int minExp = j.getMinExperience() == null ? 0 : j.getMinExperience();
        return SkillMatcher.match(
                SkillUtil.parse(p.getSkills()), seekerText, p.getEducation(), years,
                SkillUtil.parse(j.getRequiredSkills()), join(j.getTitle(), j.getDescription()),
                j.getRequiredEducation(), minExp);
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String s : parts) if (s != null) sb.append(s).append(' ');
        return sb.toString();
    }
}
