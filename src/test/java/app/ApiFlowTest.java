package app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end flow through the real security chain on an in-memory H2 database. */
@SpringBootTest
class ApiFlowTest {

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---- tiny helpers (no JSON library needed) ----

    private static String json(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(kv[i]).append("\":").append(kv[i + 1]);
        }
        return sb.append('}').toString();
    }

    private static String str(String v) { return "\"" + v + "\""; }

    private static String find(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        assertTrue(m.find(), "Pattern " + regex + " not found in: " + text);
        return m.group(1);
    }

    private String send(String method, String url, String token, String body, int expected) throws Exception {
        var req = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
        req.contentType(MediaType.APPLICATION_JSON);
        if (body != null) req.content(body);
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private String register(String email, String role) throws Exception {
        String res = send("POST", "/api/auth/register", null,
                json("name", str("User"), "email", str(email), "password", str("Passw0rdX"), "role", str(role), "acceptTerms", "true"), 200);
        return find(res, "\"token\":\"([^\"]+)\"");
    }

    private String login(String email, String pw, int expected) throws Exception {
        String res = send("POST", "/api/auth/login", null, json("email", str(email), "password", str(pw)), expected);
        return expected == 200 ? find(res, "\"token\":\"([^\"]+)\"") : null;
    }

    // ---- tests ----

    @Test
    void validationAndAuthRules() throws Exception {
        String err = send("POST", "/api/auth/register", null,
                json("name", str("A"), "email", str("a@x.com"), "password", str("short"), "role", str("JOB_SEEKER"), "acceptTerms", "true"), 400);
        assertTrue(err.contains("Password"), err);
        send("POST", "/api/auth/register", null,
                json("name", str("A"), "email", str("adm@x.com"), "password", str("Passw0rdX"), "role", str("ADMIN"), "acceptTerms", "true"), 403);
        send("GET", "/api/jobs", null, null, 401);                       // login required
        String seeker = register("seeker1@x.com", "JOB_SEEKER");
        send("GET", "/api/recruiter/jobs", seeker, null, 403);           // wrong role
    }

    @Test
    void loginLocksAfterRepeatedFailures() throws Exception {
        register("lock@x.com", "JOB_SEEKER");
        for (int i = 0; i < 3; i++) login("lock@x.com", "wrongPass1", 401);
        login("lock@x.com", "Passw0rdX", 429);                           // locked even with the right password
    }

    @Test
    void recruiterToSeekerHiringFlow() throws Exception {
        String admin = login("admin@test.com", "Admin@12345", 200);
        String rec = register("rec@x.com", "RECRUITER");
        String seeker = register("seek@x.com", "JOB_SEEKER");

        send("PUT", "/api/recruiter/company", rec, json("companyName", str("Acme"), "industry", str("IT")), 200);
        String job = json("title", str("Java Dev"), "description", str("Build APIs"),
                "location", str("Chennai"), "requiredSkills", "[\"Java\",\"MySQL\"]");
        send("POST", "/api/recruiter/jobs", rec, job, 403);              // company not verified yet

        String list = send("GET", "/api/admin/recruiters", admin, null, 200);
        String recId = find(list, "\"id\":(\\d+),[^{}]*?\"companyName\":\"Acme\"");
        send("PUT", "/api/admin/recruiters/" + recId + "/verified?value=true", admin, null, 200);

        String jobId = find(send("POST", "/api/recruiter/jobs", rec, job, 200), "\"id\":(\\d+)");

        send("POST", "/api/seeker/jobs/" + jobId + "/apply", seeker, "{}", 400);   // no skills yet
        send("PUT", "/api/seeker/profile", seeker,
                json("skills", "[\"Java\",\"MySQL\"]", "experienceYears", "1"), 200);

        mvc.perform(multipart("/api/seeker/resume")
                .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", "<html>x</html>".getBytes()))
                .header("Authorization", "Bearer " + seeker)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/seeker/resume")
                .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4 test".getBytes()))
                .header("Authorization", "Bearer " + seeker)).andExpect(status().isOk());

        String appId = find(send("POST", "/api/seeker/jobs/" + jobId + "/apply", seeker, "{}", 200), "\"id\":(\\d+)");
        send("POST", "/api/seeker/jobs/" + jobId + "/apply", seeker, "{}", 409);   // duplicate

        String url = "/api/recruiter/applications/" + appId + "/status";
        send("PUT", url, rec, json("status", str("ACCEPTED")), 200);
        send("PUT", url, rec, json("status", str("APPLIED")), 400);                // decisions are final

        String other = register("rec2@x.com", "RECRUITER");
        send("PUT", url, other, json("status", str("REJECTED")), 403);             // not their job
    }

    @Test
    void registrationRequiresAcceptedTerms() throws Exception {
        String err = send("POST", "/api/auth/register", null,
                json("name", str("T"), "email", str("terms@x.com"), "password", str("Passw0rdX"),
                        "role", str("JOB_SEEKER")), 400);
        assertTrue(err.contains("Terms"), err);
        send("POST", "/api/auth/register", null,
                json("name", str("T"), "email", str("terms@x.com"), "password", str("Passw0rdX"),
                        "role", str("JOB_SEEKER"), "acceptTerms", "false"), 400);
    }

    @Test
    void forgotPasswordNeverRevealsWhetherAnAccountExists() throws Exception {
        register("known@x.com", "JOB_SEEKER");
        String a = send("POST", "/api/auth/forgot-password", null, json("email", str("known@x.com")), 200);
        String b = send("POST", "/api/auth/forgot-password", null, json("email", str("nobody@x.com")), 200);
        assertEquals(a, b);
        send("POST", "/api/auth/reset-password", null,
                json("token", str("not-a-real-token"), "password", str("NewPassw0rd")), 400);
    }

    @Test
    void changingPasswordInvalidatesOldTokens() throws Exception {
        String token = register("change@x.com", "JOB_SEEKER");
        send("GET", "/api/auth/me", token, null, 200);
        send("POST", "/api/auth/change-password", token,
                json("currentPassword", str("wrongPass1"), "newPassword", str("NewPassw0rd")), 400);
        Thread.sleep(1100);   // token timestamps have one-second precision
        send("POST", "/api/auth/change-password", token,
                json("currentPassword", str("Passw0rdX"), "newPassword", str("NewPassw0rd")), 200);
        send("GET", "/api/auth/me", token, null, 401);                    // old token is dead
        login("change@x.com", "Passw0rdX", 401);
        String fresh = login("change@x.com", "NewPassw0rd", 200);
        send("GET", "/api/auth/me", fresh, null, 200);
    }

    @Test
    void seekerCanDeleteOwnAccount() throws Exception {
        String token = register("bye@x.com", "JOB_SEEKER");
        send("POST", "/api/auth/delete-account", token, json("password", str("wrong")), 400);
        send("POST", "/api/auth/delete-account", token, json("password", str("Passw0rdX")), 200);
        login("bye@x.com", "Passw0rdX", 401);
        register("bye@x.com", "JOB_SEEKER");                              // the email can be reused
    }

    @Test
    void adminSeesAuditLogAndEmailPreferenceCanBeChanged() throws Exception {
        String admin = login("admin@test.com", "Admin@12345", 200);
        String user = register("audit@x.com", "JOB_SEEKER");
        String res = send("PUT", "/api/auth/me/preferences", user, json("emailNotifications", "false"), 200);
        assertTrue(res.contains("\"emailNotifications\":false"), res);
        String log = send("GET", "/api/admin/audit-logs", admin, null, 200);
        assertTrue(log.contains("USER_REGISTERED"), log);
        send("GET", "/api/admin/audit-logs", user, null, 403);
    }

    @Test
    void jobSearchIsPagedAndUserCanExportData() throws Exception {
        String seeker = register("export@x.com", "JOB_SEEKER");
        String paged = send("GET", "/api/jobs?size=5&page=0", seeker, null, 200);
        assertEquals("[]", paged.trim());
        String data = send("GET", "/api/auth/me/export", seeker, null, 200);
        assertTrue(data.contains("export@x.com") && data.contains("\"profile\""), data);
    }
}
