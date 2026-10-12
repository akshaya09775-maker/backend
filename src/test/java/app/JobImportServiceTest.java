package app;

import app.service.JobImportService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobImportServiceTest {

    @Test
    void convertsEscapedHtmlToReadableText() {
        String html = "&lt;p&gt;Build &amp;amp; scale APIs&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Java&lt;/li&gt;&lt;li&gt;SQL&lt;/li&gt;&lt;/ul&gt;";
        String text = JobImportService.plainText(html);
        assertTrue(text.contains("Build & scale APIs"), text);
        assertTrue(text.contains("• Java") && text.contains("• SQL"), text);
        assertFalse(text.contains("<") || text.contains("&lt;"), text);
    }

    @Test
    void detectsSkillsExperienceAndLevel() {
        String skills = JobImportService.skills("We use Java, Spring Boot, PostgreSQL and Docker on AWS. Not javascript-free.");
        assertTrue(skills.contains("Java") && skills.contains("Spring Boot") && skills.contains("PostgreSQL")
                && skills.contains("Docker") && skills.contains("AWS"), skills);
        assertEquals(5, JobImportService.years("5+ years of experience required"));
        assertNull(JobImportService.years("no experience mentioned"));
        assertEquals("Entry level", JobImportService.level("Software Engineer Intern"));
        assertEquals("Director", JobImportService.level("Director of Engineering"));
        assertEquals("Mid-Senior level", JobImportService.level("Senior Backend Engineer"));
        assertEquals("Remote", JobImportService.workMode("Remote - US", null));
        assertEquals("Internship", JobImportService.jobType("Data Intern", null));
    }
}
