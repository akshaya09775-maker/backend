package app;

import app.ai.MatchResult;
import app.ai.SkillMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMatcherTest {

    @Test
    void partialSkillMatchGivesHighButNotPerfectScore() {
        MatchResult r = SkillMatcher.match(
                List.of("Java", "Spring Boot", "MySQL", "React"), "Full stack developer",
                "B.Tech Computer Science", 1,
                List.of("Java", "Spring Boot", "MySQL", "Docker"),
                "Java backend developer building REST APIs with Spring Boot",
                "B.Tech Computer Science", 0);
        assertEquals(List.of("Docker"), r.missingSkills());
        assertTrue(r.score() > 70 && r.score() < 100);
    }

    @Test
    void seekerWithoutSkillsScoresZero() {
        MatchResult r = SkillMatcher.match(List.of(), "", "", 0, List.of("Java"), "x", "", 0);
        assertEquals(0, r.score());
    }
}
