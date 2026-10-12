package app.service;

import app.entity.Company;
import app.entity.Job;
import app.entity.RecruiterProfile;
import app.entity.User;
import app.repository.CompanyRepository;
import app.repository.JobRepository;
import app.repository.RecruiterRepository;
import app.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports real, current job openings from companies' public careers boards (Greenhouse and Lever
 * public job-board APIs) so the portal is never empty. Imported jobs are linked to their company page,
 * get an AI match score like any other job, and send candidates to the company's own application page.
 */
@Slf4j
@Service
public class JobImportService {

    public record ImportResult(int added, int closed, int failedBoards, String message) {}

    private static final String SYSTEM_EMAIL = "job-import@system.invalid";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST =
            new ParameterizedTypeReference<>() {};

    private static final List<String> SKILLS = List.of(
            "Java", "Spring Boot", "Spring", "Kotlin", "Scala", "Python", "Django", "Flask", "FastAPI", "JavaScript",
            "TypeScript", "React", "Angular", "Vue", "Node.js", "Next.js", "HTML", "CSS", "Go", "Golang", "Rust",
            "C++", "C#", ".NET", "Ruby", "Ruby on Rails", "PHP", "Swift", "Objective-C", "Android", "iOS", "SQL",
            "MySQL", "PostgreSQL", "MongoDB", "Redis", "Kafka", "Elasticsearch", "GraphQL", "REST", "Microservices",
            "AWS", "Azure", "GCP", "Docker", "Kubernetes", "Terraform", "CI/CD", "Linux", "Git", "Machine Learning",
            "Deep Learning", "TensorFlow", "PyTorch", "Data Science", "Data Engineering", "Spark", "Hadoop", "Airflow",
            "Tableau", "Power BI", "Excel", "Figma", "Product Management", "Agile", "Scrum", "Selenium", "Testing",
            "Security", "DevOps", "SRE", "Salesforce", "SAP");

    private static final Map<String, Pattern> SKILL_PATTERNS = new LinkedHashMap<>();
    private static final Pattern YEARS = Pattern.compile("(\\d{1,2})\\s*\\+?\\s*(?:-\\s*\\d{1,2}\\s*)?years?", Pattern.CASE_INSENSITIVE);

    static {
        for (String s : SKILLS) {
            SKILL_PATTERNS.put(s, Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(s) + "(?![A-Za-z0-9+#])",
                    Pattern.CASE_INSENSITIVE));
        }
    }

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RecruiterRepository recruiterRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestClient http;

    private final boolean enabled;
    private final Map<String, String> greenhouse;
    private final Map<String, String> lever;
    private final int maxPerCompany;
    private final String adzunaId;
    private final String adzunaKey;
    private final List<String> adzunaCountries;
    private final List<String> adzunaCategories;
    private final int adzunaPages;
    private final boolean museEnabled;
    private final List<String> museCategories;
    private final int musePages;
    private final Map<String, Long> onDemandSeen = new java.util.concurrent.ConcurrentHashMap<>();
    private final Deque<Long> onDemandCalls = new ArrayDeque<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public JobImportService(JobRepository jobRepository, CompanyRepository companyRepository,
                            UserRepository userRepository, RecruiterRepository recruiterRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${app.import.enabled:false}") boolean enabled,
                            @Value("${app.import.greenhouse:}") String greenhouse,
                            @Value("${app.import.lever:}") String lever,
                            @Value("${app.import.max-per-company:25}") int maxPerCompany,
                            @Value("${app.import.adzuna.app-id:}") String adzunaId,
                            @Value("${app.import.adzuna.app-key:}") String adzunaKey,
                            @Value("${app.import.adzuna.countries:in,us}") String adzunaCountries,
                            @Value("${app.import.adzuna.categories:it-jobs,engineering-jobs,graduate-jobs}") String adzunaCategories,
                            @Value("${app.import.adzuna.pages:2}") int adzunaPages,
                            @Value("${app.import.muse.enabled:true}") boolean museEnabled,
                            @Value("${app.import.muse.categories:Software Engineering,Data and Analytics,Computer and IT}") String museCategories,
                            @Value("${app.import.muse.pages:2}") int musePages) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.recruiterRepository = recruiterRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.greenhouse = parseBoards(greenhouse);
        this.lever = parseBoards(lever);
        this.maxPerCompany = Math.max(1, maxPerCompany);
        this.adzunaId = adzunaId == null ? "" : adzunaId.trim();
        this.adzunaKey = adzunaKey == null ? "" : adzunaKey.trim();
        this.adzunaCountries = csv(adzunaCountries);
        this.adzunaCategories = csv(adzunaCategories);
        this.adzunaPages = Math.max(1, Math.min(adzunaPages, 5));
        this.museEnabled = museEnabled;
        this.museCategories = csv(museCategories);
        this.musePages = Math.max(1, Math.min(musePages, 5));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder().requestFactory(factory)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "JobPortal-Importer/1.0")
                .build();
    }

    // ------------------------------------------------------------------ triggers

    @EventListener(ApplicationReadyEvent.class)
    public void importOnStartup() {
        if (enabled) CompletableFuture.runAsync(() -> {
            try {
                runImport();
            } catch (Exception e) {
                log.error("Startup job import crashed", e);
            }
        });
    }

    @Scheduled(cron = "${app.import.cron:0 15 */6 * * *}")
    public void importOnSchedule() {
        if (enabled) runImport();
    }

    /** Runs one import. Safe to call at any time; a second call while one is running does nothing. */
    public ImportResult runImport() {
        if (!running.compareAndSet(false, true)) {
            return new ImportResult(0, 0, 0, "An import is already running");
        }
        try {
            if (greenhouse.isEmpty() && lever.isEmpty() && !adzunaConfigured() && !museEnabled) {
                return new ImportResult(0, 0, 0, "No job sources are configured (Greenhouse, Lever or Adzuna)");
            }
            RecruiterProfile owner = systemRecruiter();
            int added = 0, closed = 0, failed = 0;
            added += seedIndiaSamples(owner);

            for (var e : greenhouse.entrySet()) {
                try {
                    int[] r = importGreenhouse(owner, e.getKey(), e.getValue());
                    added += r[0];
                    closed += r[1];
                } catch (Exception ex) {
                    failed++;
                    log.warn("Greenhouse board '{}' could not be imported: {}", e.getKey(), ex.getMessage());
                }
            }
            for (var e : lever.entrySet()) {
                try {
                    int[] r = importLever(owner, e.getKey(), e.getValue());
                    added += r[0];
                    closed += r[1];
                } catch (Exception ex) {
                    failed++;
                    log.warn("Lever board '{}' could not be imported: {}", e.getKey(), ex.getMessage());
                }
            }
            if (adzunaConfigured()) {
                try {
                    added += importAdzunaBulk(owner);
                    closed += closeOld("az:", 30);
                } catch (Exception ex) {
                    failed++;
                    log.warn("Adzuna import failed: {}", ex.getMessage());
                }
            }
            if (museEnabled) {
                try {
                    added += importMuseBulk(owner);
                    closed += closeOld("mu:", 30);
                } catch (Exception ex) {
                    failed++;
                    log.warn("The Muse import failed: {}", ex.getMessage());
                }
            }
            log.info("Job import finished: {} added, {} closed, {} boards failed", added, closed, failed);
            return new ImportResult(added, closed, failed,
                    "Import finished: " + added + " new jobs, " + closed + " closed, " + failed + " boards failed");
        } finally {
            running.set(false);
        }
    }

    // ------------------------------------------------------------------ sources

    private int[] importGreenhouse(RecruiterProfile owner, String token, String companyName) {
        Map<String, Object> body = http.get()
                .uri("https://boards-api.greenhouse.io/v1/boards/{t}/jobs?content=true", token)
                .retrieve().body(MAP);
        List<Map<String, Object>> jobs = listOf(body == null ? null : body.get("jobs"));
        Company company = company(companyName, "https://boards.greenhouse.io/" + token);
        String prefix = "gh:" + token + ":";
        Set<String> seen = new HashSet<>();
        int added = 0;
        for (Map<String, Object> j : jobs) {
            String id = str(j.get("id"));
            String url = str(j.get("absolute_url"));
            String title = str(j.get("title"));
            if (id == null || title == null || !isHttps(url)) continue;
            seen.add(prefix + id);
            if (seen.size() > maxPerCompany) { seen.remove(prefix + id); break; }
            String location = str(nested(j.get("location"), "name"));
            String description = plainText(str(j.get("content")));
            if (save(owner, company, prefix + id, "Greenhouse", url, title, location, description, null, null)) added++;
        }
        return new int[]{added, closeMissing(prefix, seen)};
    }

    private int[] importLever(RecruiterProfile owner, String site, String companyName) {
        List<Map<String, Object>> jobs = http.get()
                .uri("https://api.lever.co/v0/postings/{s}?mode=json", site)
                .retrieve().body(LIST);
        if (jobs == null) jobs = List.of();
        Company company = company(companyName, "https://jobs.lever.co/" + site);
        String prefix = "lv:" + site + ":";
        Set<String> seen = new HashSet<>();
        int added = 0;
        for (Map<String, Object> j : jobs) {
            String id = str(j.get("id"));
            String url = str(j.get("hostedUrl"));
            String title = str(j.get("text"));
            if (id == null || title == null || !isHttps(url)) continue;
            seen.add(prefix + id);
            if (seen.size() > maxPerCompany) { seen.remove(prefix + id); break; }
            Object cats = j.get("categories");
            String location = str(nested(cats, "location"));
            String description = str(j.get("descriptionPlain"));
            String extra = str(j.get("additionalPlain"));
            if (extra != null) description = (description == null ? "" : description + "\n\n") + extra;
            if (save(owner, company, prefix + id, "Lever", url, title, location, plainText(description),
                    str(nested(cats, "commitment")), str(j.get("workplaceType")))) added++;
        }
        return new int[]{added, closeMissing(prefix, seen)};
    }

    // ------------------------------------------------------------------ Adzuna (jobs from thousands of employers)

    public boolean adzunaConfigured() {
        return !adzunaId.isEmpty() && !adzunaKey.isEmpty() && !adzunaCountries.isEmpty();
    }

    private int importAdzunaBulk(RecruiterProfile owner) {
        int added = 0;
        for (String country : adzunaCountries) {
            for (String category : adzunaCategories) {
                for (int page = 1; page <= adzunaPages; page++) {
                    added += adzunaSearch(owner, country, page, 50, null, null, null, category);
                }
            }
        }
        return added;
    }

    /**
     * Fetches current openings matching what a visitor just searched for (a company name such as "Cisco", or a
     * keyword) so that ANY employer can be found, not only the pre-configured ones. Results of the same search are
     * reused for 6 hours and the number of live look-ups per minute is limited.
     */
    public int importForQuery(String keyword, String location) {
        if (!(adzunaConfigured() || museEnabled) || keyword == null || keyword.isBlank() || keyword.length() > 80) return 0;
        String key = (keyword.trim() + "|" + (location == null ? "" : location.trim())).toLowerCase();
        if (!allowOnDemand(key)) return 0;
        try {
            RecruiterProfile owner = systemRecruiter();
            int added = 0;
            if (adzunaConfigured()) {
                for (String country : adzunaCountries) {
                    added += adzunaSearch(owner, country, 1, 30, keyword.trim(), null, location, null);
                    added += adzunaSearch(owner, country, 1, 30, null, keyword.trim(), location, null);
                }
            }
            if (museEnabled) {
                String name = keyword.trim();
                added += museSearch(owner, 0, name, null);
                String title = titleCase(name);
                if (!title.equals(name)) added += museSearch(owner, 0, title, null);
            }
            return added;
        } catch (Exception e) {
            log.warn("Live job lookup for '{}' failed: {}", keyword, e.getMessage());
            return 0;
        }
    }

    private synchronized boolean allowOnDemand(String key) {
        long now = System.currentTimeMillis();
        Long last = onDemandSeen.get(key);
        if (last != null && now - last < 6 * 3600_000L) return false;
        while (!onDemandCalls.isEmpty() && now - onDemandCalls.peekFirst() > 60_000L) onDemandCalls.pollFirst();
        if (onDemandCalls.size() >= 3) return false;
        if (onDemandSeen.size() > 1000) onDemandSeen.clear();
        onDemandSeen.put(key, now);
        onDemandCalls.addLast(now);
        return true;
    }

    private int adzunaSearch(RecruiterProfile owner, String country, int page, int perPage,
                             String what, String company, String where, String category) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("country", country);
        vars.put("page", page);
        vars.put("id", adzunaId);
        vars.put("key", adzunaKey);
        vars.put("n", perPage);
        Map<String, Object> body;
        try {
            body = http.get().uri(b -> {
                b.scheme("https").host("api.adzuna.com").path("/v1/api/jobs/{country}/search/{page}")
                        .queryParam("app_id", "{id}").queryParam("app_key", "{key}")
                        .queryParam("results_per_page", "{n}").queryParam("content-type", "application/json");
                if (what != null) { b.queryParam("what", "{what}"); vars.put("what", what); }
                if (company != null) { b.queryParam("company", "{company}"); vars.put("company", company); }
                if (where != null && !where.isBlank()) { b.queryParam("where", "{where}"); vars.put("where", where); }
                if (category != null) { b.queryParam("category", "{category}"); vars.put("category", category); }
                return b.build(vars);
            }).retrieve().body(MAP);
        } catch (Exception e) {
            log.warn("Adzuna request ({}, {}) failed: {}", country, what != null ? what : company != null ? company : category, e.getMessage());
            return 0;
        }
        int added = 0;
        for (Map<String, Object> j : listOf(body == null ? null : body.get("results"))) {
            String id = str(j.get("id"));
            String url = str(j.get("redirect_url"));
            String title = str(j.get("title"));
            String companyName = str(nested(j.get("company"), "display_name"));
            if (id == null || title == null || companyName == null || !isHttps(url)) continue;
            String externalId = "az:" + country + ":" + id;
            if (jobRepository.existsByExternalId(externalId)) continue;
            String location = str(nested(j.get("location"), "display_name"));
            String description = plainText(str(j.get("description")));
            String commitment = String.valueOf(j.get("contract_time")) + " " + String.valueOf(j.get("contract_type"));
            if (saveJob(owner, company(cut(companyName, 120), null), externalId, "Adzuna", url, title, location,
                    description == null ? null : description + "\n\nThis is a summary. The full description is on the employer's page.",
                    commitment.replace('_', ' '), null, salary(j.get("salary_min")), salary(j.get("salary_max")),
                    currencyOf(country))) added++;
        }
        return added;
    }

    private int closeOld(String prefix, int days) {
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(days);
        int n = 0;
        for (Job j : jobRepository.findByExternalIdStartingWithAndActiveTrue(prefix)) {
            if (j.getCreatedAt() != null && j.getCreatedAt().isBefore(cutoff)) {
                j.setActive(false);
                j.setStatus("CLOSED");
                jobRepository.save(j);
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ The Muse (no account or key needed)

    private int importMuseBulk(RecruiterProfile owner) {
        int added = 0;
        for (String category : museCategories) {
            for (int page = 0; page < musePages; page++) {
                added += museSearch(owner, page, null, category);
            }
        }
        return added;
    }

    /** One call to The Muse public jobs API, filtered by company name and/or category. */
    private int museSearch(RecruiterProfile owner, int page, String company, String category) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("page", page);
        Map<String, Object> body;
        try {
            body = http.get().uri(b -> {
                b.scheme("https").host("www.themuse.com").path("/api/public/jobs").queryParam("page", "{page}");
                if (company != null) { b.queryParam("company", "{company}"); vars.put("company", company); }
                if (category != null) { b.queryParam("category", "{category}"); vars.put("category", category); }
                return b.build(vars);
            }).retrieve().body(MAP);
        } catch (Exception e) {
            log.warn("The Muse request ({}) failed: {}", company != null ? company : category, e.getMessage());
            return 0;
        }
        int added = 0;
        for (Map<String, Object> j : listOf(body == null ? null : body.get("results"))) {
            String id = str(j.get("id"));
            String title = str(j.get("name"));
            String companyName = str(nested(j.get("company"), "name"));
            String url = str(nested(j.get("refs"), "landing_page"));
            if (id == null || title == null || companyName == null || !isHttps(url)) continue;
            String externalId = "mu:" + id;
            if (jobRepository.existsByExternalId(externalId)) continue;
            String location = null;
            List<Map<String, Object>> locs = listOf(j.get("locations"));
            if (!locs.isEmpty()) location = str(locs.get(0).get("name"));
            String level = null;
            List<Map<String, Object>> levels = listOf(j.get("levels"));
            if (!levels.isEmpty()) level = str(levels.get(0).get("name"));
            String description = plainText(str(j.get("contents")));
            String type = str(j.get("type"));
            if (saveJob(owner, company(cut(companyName, 120), null), externalId, "The Muse", url,
                    title + (level != null && level.toLowerCase().contains("intern") ? " (Intern)" : ""),
                    location, description, type, location != null && location.toLowerCase().contains("flexible")
                            ? "remote" : null, null, null, "USD")) added++;
        }
        return added;
    }

    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.trim().split("\\s+")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private static Integer salary(Object o) {
        try {
            return o == null ? null : (int) Math.round(Double.parseDouble(String.valueOf(o)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String currencyOf(String country) {
        return switch (country.toLowerCase()) {
            case "in" -> "INR";
            case "gb" -> "GBP";
            case "ca" -> "CAD";
            case "au" -> "AUD";
            case "nz" -> "NZD";
            case "sg" -> "SGD";
            case "za" -> "ZAR";
            case "de", "fr", "it", "nl", "es", "at" -> "EUR";
            default -> "USD";
        };
    }

    private static List<String> csv(String s) {
        List<String> out = new ArrayList<>();
        if (s != null) for (String p : s.split(",")) if (!p.isBlank()) out.add(p.trim());
        return out;
    }

    // ------------------------------------------------------------------ India sample listings

    /**
     * Sample listings for well-known employers in Indian cities, so searches such as "Hyderabad" or "Bengaluru"
     * always find something. They are marked "Sample" and are only added once (safe to run on every import).
     */
    private int seedIndiaSamples(RecruiterProfile owner) {
        // company | title | location | skills | type | min INR/year | max INR/year
        String[][] rows = {
            {"TCS", "Java Full Stack Developer", "Hyderabad, India", "Java, Spring Boot, React, MySQL", "Full-time", "500000", "900000"},
            {"TCS", "Software Engineer Trainee", "Chennai, India", "Java, SQL, Git", "Full-time", "350000", "500000"},
            {"Infosys", "Systems Engineer", "Hyderabad, India", "Java, SQL, REST", "Full-time", "400000", "650000"},
            {"Infosys", "Full Stack Developer", "Pune, India", "React, Node.js, MongoDB", "Full-time", "600000", "1100000"},
            {"Wipro", "Java Developer", "Bengaluru, India", "Java, Spring Boot, Microservices", "Full-time", "500000", "1000000"},
            {"Wipro", "Python Developer Intern", "Hyderabad, India", "Python, Django, SQL", "Internship", "180000", "240000"},
            {"HCLTech", "Software Engineer", "Noida, India", "Java, Spring, MySQL", "Full-time", "450000", "800000"},
            {"HCLTech", "Cloud Engineer", "Chennai, India", "AWS, Docker, Kubernetes", "Full-time", "700000", "1400000"},
            {"Tech Mahindra", "Associate Software Engineer", "Hyderabad, India", "Java, SQL, Testing", "Full-time", "400000", "600000"},
            {"Tech Mahindra", "Data Engineer", "Pune, India", "Python, Spark, SQL", "Full-time", "800000", "1500000"},
            {"Zoho", "Member Technical Staff", "Chennai, India", "Java, JavaScript, SQL", "Full-time", "600000", "1200000"},
            {"Zoho", "Frontend Developer", "Chennai, India", "JavaScript, React, CSS", "Full-time", "500000", "1000000"},
            {"Flipkart", "Software Development Engineer", "Bengaluru, India", "Java, Microservices, Kafka", "Full-time", "1800000", "3200000"},
            {"Flipkart", "Data Analyst", "Bengaluru, India", "SQL, Python, Tableau", "Full-time", "900000", "1800000"},
            {"Swiggy", "Backend Engineer", "Bengaluru, India", "Java, Kotlin, Redis", "Full-time", "1500000", "2800000"},
            {"Swiggy", "Android Developer", "Remote, India", "Kotlin, Android, REST", "Full-time", "1200000", "2400000"},
            {"Razorpay", "Software Engineer", "Bengaluru, India", "Go, PostgreSQL, Docker", "Full-time", "1600000", "3000000"},
            {"Razorpay", "QA Engineer", "Hyderabad, India", "Selenium, Testing, SQL", "Full-time", "700000", "1300000"},
            {"Amazon India", "Software Development Engineer", "Hyderabad, India", "Java, AWS, REST", "Full-time", "1800000", "3500000"},
            {"Microsoft India", "Software Engineer", "Hyderabad, India", "C#, .NET, Azure", "Full-time", "1800000", "3500000"},
        };
        int added = 0;
        for (int i = 0; i < rows.length; i++) {
            String[] r = rows[i];
            String desc = "Sample listing for demonstration (not a live job posting).\n\n"
                    + "Role: " + r[1] + " at " + r[0] + ", " + r[2] + ".\n"
                    + "Skills: " + r[3] + ".\n"
                    + "Check the company's own careers page for real openings.";
            if (saveJob(owner, company(r[0], null), "sample:in:" + (i + 1), "Sample", null, r[1], r[2], desc,
                    r[4], r[2].toLowerCase().contains("remote") ? "remote" : null,
                    Integer.valueOf(r[5]), Integer.valueOf(r[6]), "INR")) added++;
        }
        return added;
    }

    // ------------------------------------------------------------------ persistence

    private boolean save(RecruiterProfile owner, Company company, String externalId, String source, String url,
                         String title, String location, String description, String commitment, String workplace) {
        return saveJob(owner, company, externalId, source, url, title, location, description, commitment, workplace,
                null, null, "USD");
    }

    private boolean saveJob(RecruiterProfile owner, Company company, String externalId, String source, String url,
                            String title, String location, String description, String commitment, String workplace,
                            Integer salaryMin, Integer salaryMax, String currency) {
        if (jobRepository.existsByExternalId(externalId)) return false;
        try {
            String desc = description == null || description.isBlank()
                    ? "See the full description on the company's website." : description;
            if (desc.length() > 3900) desc = desc.substring(0, 3900) + "…";
            String loc = location == null || location.isBlank() ? "Not specified" : cut(location, 150);
            jobRepository.save(Job.builder()
                    .recruiter(owner)
                    .company(company)
                    .title(cut(title, 150))
                    .description(desc)
                    .location(loc)
                    .jobType(jobType(title, commitment))
                    .workMode(workMode(loc, workplace))
                    .experienceLevel(level(title))
                    .requiredSkills(skills(title + " " + desc))
                    .minExperience(years(desc))
                    .salaryMin(salaryMin)
                    .salaryMax(salaryMax)
                    .currency(currency)
                    .externalId(externalId)
                    .externalUrl(url)
                    .source(source)
                    .active(true)
                    .status("ACTIVE")
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("Skipped job {}: {}", externalId, e.getMessage());
            return false;
        }
    }

    /** Jobs that disappeared from the company's board are closed here too. */
    private int closeMissing(String prefix, Set<String> seen) {
        int n = 0;
        for (Job j : jobRepository.findByExternalIdStartingWithAndActiveTrue(prefix)) {
            if (!seen.contains(j.getExternalId())) {
                j.setActive(false);
                j.setStatus("CLOSED");
                jobRepository.save(j);
                n++;
            }
        }
        return n;
    }

    private Company company(String name, String website) {
        return companyRepository.findByNameIgnoreCase(name).orElseGet(() -> {
            String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            if (companyRepository.findBySlug(slug).isPresent()) slug = slug + "-" + System.currentTimeMillis();
            return companyRepository.save(Company.builder()
                    .name(name).slug(slug).website(website).verified(true).build());
        });
    }

    /** One non-login recruiter account that owns all imported jobs (jobs need an owner). */
    private RecruiterProfile systemRecruiter() {
        User user = userRepository.findByEmail(SYSTEM_EMAIL).orElseGet(() -> userRepository.save(User.builder()
                .name("Imported listings")
                .email(SYSTEM_EMAIL)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(User.Role.RECRUITER)
                .enabled(false)              // nobody can sign in as this account
                .emailNotifications(false)
                .build()));
        return recruiterRepository.findByUserId(user.getId()).orElseGet(() -> recruiterRepository.save(
                RecruiterProfile.builder().user(user).companyName("Imported listings").verified(true).build()));
    }

    // ------------------------------------------------------------------ text helpers

    public static String jobType(String title, String commitment) {
        String t = (title + " " + (commitment == null ? "" : commitment)).toLowerCase();
        if (t.contains("intern")) return "Internship";
        if (t.contains("part-time") || t.contains("part time")) return "Part-time";
        if (t.contains("contract") || t.contains("temporary")) return "Contract";
        return "Full-time";
    }

    public static String workMode(String location, String workplace) {
        String t = ((workplace == null ? "" : workplace) + " " + (location == null ? "" : location)).toLowerCase();
        if (t.contains("remote")) return "Remote";
        if (t.contains("hybrid")) return "Hybrid";
        return "On-site";
    }

    public static String level(String title) {
        String t = title.toLowerCase();
        if (t.contains("intern") || t.contains("junior") || t.contains("entry") || t.contains("graduate")
                || t.contains("new grad") || t.contains("associate")) return "Entry level";
        if (t.contains("chief") || t.contains("vp ") || t.contains("vice president")) return "Executive";
        if (t.contains("director") || t.contains("head of")) return "Director";
        return "Mid-Senior level";
    }

    public static String skills(String text) {
        List<String> found = new ArrayList<>();
        for (var e : SKILL_PATTERNS.entrySet()) {
            if (e.getValue().matcher(text).find()) found.add(e.getKey());
            if (found.size() >= 12) break;
        }
        return String.join(", ", found);
    }

    public static Integer years(String text) {
        Matcher m = YEARS.matcher(text == null ? "" : text);
        if (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (y >= 0 && y <= 20) return y;
        }
        return null;
    }

    /** Turns (possibly escaped) HTML into readable plain text with paragraph breaks. */
    public static String plainText(String html) {
        if (html == null) return null;
        String s = unescape(html);                                   // Greenhouse delivers escaped HTML
        s = s.replaceAll("(?i)<\\s*(br|/p|/li|/h[1-6]|/div|/ul|/ol)\\s*/?>", "\n")
             .replaceAll("(?i)<\\s*li[^>]*>", "• ")
             .replaceAll("<[^>]+>", " ");
        s = unescape(s).replace(' ', ' ');
        s = s.replaceAll("[ \\t]+", " ").replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&nbsp;", " ").replace("&rsquo;", "'").replace("&amp;", "&");
    }

    private static boolean isHttps(String url) {
        return url != null && url.startsWith("https://");
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static Object nested(Object map, String key) {
        return map instanceof Map<?, ?> m ? m.get(key) : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Object o) {
        return o instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    /** "stripe:Stripe,airbnb:Airbnb" -> {stripe=Stripe, airbnb=Airbnb} (the name part is optional). */
    private static Map<String, String> parseBoards(String config) {
        Map<String, String> out = new LinkedHashMap<>();
        if (config == null) return out;
        for (String part : config.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            String[] kv = p.split(":", 2);
            String token = kv[0].trim();
            String name = kv.length > 1 && !kv[1].isBlank() ? kv[1].trim()
                    : Character.toUpperCase(token.charAt(0)) + token.substring(1);
            out.put(token, name);
        }
        return out;
    }
}