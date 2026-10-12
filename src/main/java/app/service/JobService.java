package app.service;

import app.ai.MatchResult;
import app.dto.JobRequest;
import app.dto.JobResponse;
import app.dto.SavedJobResponse;
import app.entity.*;
import app.repository.ApplicationRepository;
import app.repository.JobRepository;
import app.repository.SavedJobRepository;
import app.security.CurrentUser;
import app.util.SkillUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final SavedJobRepository savedJobRepository;
    private final ProfileService profileService;
    private final MatchingService matchingService;
    private final CompanyService companyService;
    private final CurrentUser currentUser;

    // ---------- browsing (public) ----------

    public List<JobResponse> search(String keyword, String location, String jobType,
                                    Integer minSalary, String sort) {
        return searchAdvanced(keyword, location, jobType, null, null, minSalary, null, sort);
    }

    public List<JobResponse> searchAdvanced(String keyword, String location, String jobType,
                                            String workMode, String experienceLevel,
                                            Integer minSalary, Long companyId, String sort) {
        List<Job> jobs;
        if (companyId != null) {
            jobs = jobRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        } else if ((workMode != null && !workMode.isBlank()) || (experienceLevel != null && !experienceLevel.isBlank())) {
            jobs = jobRepository.advancedSearch(
                    nz(keyword), nz(location), nz(jobType),
                    (workMode != null && !workMode.isBlank()) ? workMode.trim() : null,
                    (experienceLevel != null && !experienceLevel.isBlank()) ? experienceLevel.trim() : null,
                    minSalary == null ? 0 : minSalary);
        } else {
            jobs = jobRepository.search(nz(keyword), nz(location), nz(jobType),
                    minSalary == null ? 0 : minSalary);
        }

        User viewer = currentUser.getOrNull();
        JobSeekerProfile seeker = seekerOf(viewer);
        Set<Long> applied = appliedIds(seeker);
        Set<Long> saved = savedIds(viewer);

        List<JobResponse> out = new ArrayList<>();
        for (Job j : jobs) {
            out.add(toResponse(j, seeker == null ? null : matchingService.match(seeker, j),
                    seeker == null ? null : applied.contains(j.getId()),
                    viewer == null ? false : saved.contains(j.getId()), null));
        }
        if ("match".equals(sort) && seeker != null) {
            out.sort(Comparator.comparing(j -> j.matchScore() != null ? j.matchScore() : 0, Comparator.reverseOrder()));
        } else if ("salary".equals(sort)) {
            out.sort(Comparator.comparing(j -> j.salaryMax() != null ? j.salaryMax() : 0, Comparator.reverseOrder()));
        }
        return out;
    }

    public JobResponse get(Long id) {
        Job j = find(id);
        User viewer = currentUser.getOrNull();
        JobSeekerProfile seeker = seekerOf(viewer);
        // closed, expired or unverified jobs are only visible to the owner, admins and people who applied
        if (!j.isActive() || !j.getRecruiter().isVerified() || expired(j)) {
            boolean owner = viewer != null && j.getRecruiter().getUser().getId().equals(viewer.getId());
            boolean admin = viewer != null && viewer.getRole() == User.Role.ADMIN;
            boolean applied = seeker != null
                    && applicationRepository.existsByJobIdAndSeekerId(j.getId(), seeker.getId());
            if (!owner && !admin && !applied) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
            }
        }
        boolean isApplied = seeker != null && applicationRepository.existsByJobIdAndSeekerId(j.getId(), seeker.getId());
        boolean isSaved = viewer != null && savedJobRepository.existsByUserIdAndJobId(viewer.getId(), j.getId());
        return toResponse(j, seeker == null ? null : matchingService.match(seeker, j),
                seeker == null ? null : isApplied,
                isSaved, applicationRepository.countByJobId(j.getId()));
    }

    /** AI recommendations: active jobs the seeker has not applied to, best match first. */
    public List<JobResponse> recommend(User user) {
        JobSeekerProfile seeker = profileService.seekerFor(user);
        Set<Long> applied = appliedIds(seeker);
        Set<Long> saved = savedIds(user);
        List<JobResponse> out = new ArrayList<>();
        for (Job j : jobRepository.findByActiveTrueAndRecruiterVerifiedTrue()) {
            if (applied.contains(j.getId()) || expired(j)) continue;
            MatchResult m = matchingService.match(seeker, j);
            if (m.score() > 0) out.add(toResponse(j, m, false, saved.contains(j.getId()), null));
        }
        out.sort(Comparator.comparing(j -> j.matchScore() != null ? j.matchScore() : 0, Comparator.reverseOrder()));
        return out.size() > 25 ? new ArrayList<>(out.subList(0, 25)) : out;
    }

    // ---------- saved jobs ----------

    @Transactional
    public boolean saveJob(User user, Long jobId) {
        Job job = find(jobId);
        if (savedJobRepository.existsByUserIdAndJobId(user.getId(), job.getId())) {
            return true;
        }
        savedJobRepository.save(SavedJob.builder().user(user).job(job).build());
        return true;
    }

    @Transactional
    public boolean unsaveJob(User user, Long jobId) {
        savedJobRepository.deleteByUserIdAndJobId(user.getId(), jobId);
        return false;
    }

    public List<SavedJobResponse> listSaved(User user) {
        List<SavedJob> list = savedJobRepository.findByUserIdOrderBySavedAtDesc(user.getId());
        JobSeekerProfile seeker = seekerOf(user);
        Set<Long> applied = appliedIds(seeker);

        List<SavedJobResponse> out = new ArrayList<>();
        for (SavedJob sj : list) {
            Job j = sj.getJob();
            JobResponse jr = toResponse(j, seeker == null ? null : matchingService.match(seeker, j),
                    seeker == null ? null : applied.contains(j.getId()),
                    true, applicationRepository.countByJobId(j.getId()));
            out.add(SavedJobResponse.builder()
                    .id(sj.getId())
                    .job(jr)
                    .savedAt(sj.getSavedAt())
                    .build());
        }
        return out;
    }

    // ---------- recruiter ----------

    public List<JobResponse> listMine(User user) {
        RecruiterProfile rp = profileService.recruiterFor(user);
        List<JobResponse> out = new ArrayList<>();
        for (Job j : jobRepository.findByRecruiterIdOrderByCreatedAtDesc(rp.getId())) {
            out.add(toResponse(j, null, null, false, applicationRepository.countByJobId(j.getId())));
        }
        return out;
    }

    public JobResponse create(User user, JobRequest r) {
        RecruiterProfile rp = profileService.recruiterFor(user);
        if (rp.getEffectiveCompanyName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please complete your company profile before posting a job");
        }
        if (!rp.isVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your company is waiting for admin verification. You can post jobs once it is approved");
        }
        validate(r);
        Job job = Job.builder().recruiter(rp).build();
        apply(job, r);

        // Ensure company is linked
        if (job.getCompany() == null) {
            if (rp.getCompany() != null) {
                job.setCompany(rp.getCompany());
            } else {
                Company comp = companyService.getOrCreate(rp.getCompanyName(), rp.getIndustry(),
                        rp.getWebsite(), rp.getLocation(), rp.getDescription());
                rp.setCompany(comp);
                job.setCompany(comp);
            }
        }

        job = jobRepository.save(job);
        return toResponse(job, null, null, false, 0L);
    }

    public JobResponse update(User user, Long id, JobRequest r) {
        Job job = owned(user, id);
        validate(r);
        apply(job, r);
        job = jobRepository.save(job);
        return toResponse(job, null, null, false, applicationRepository.countByJobId(id));
    }

    public void delete(User user, Long id) {
        jobRepository.delete(owned(user, id));
    }

    // ---------- admin ----------

    public List<JobResponse> listAll() {
        List<JobResponse> out = new ArrayList<>();
        for (Job j : jobRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))) {
            out.add(toResponse(j, null, null, false, applicationRepository.countByJobId(j.getId())));
        }
        return out;
    }

    public void deleteAsAdmin(Long id) {
        jobRepository.delete(find(id));
    }

    // ---------- helpers ----------

    public Job find(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    private Job owned(User user, Long id) {
        Job job = find(id);
        if (!job.getRecruiter().getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own jobs");
        }
        return job;
    }

    private void validate(JobRequest r) {
        if (isBlank(r.title())) bad("Job title is required");
        if (isBlank(r.description())) bad("Job description is required");
        if (isBlank(r.location())) bad("Location is required");
        if (r.deadline() != null && r.deadline().isBefore(LocalDate.now())) {
            bad("The application deadline cannot be in the past");
        }
        if (r.salaryMin() != null && r.salaryMax() != null && r.salaryMin() > r.salaryMax()) {
            bad("Minimum salary cannot be greater than maximum salary");
        }
    }

    private void apply(Job job, JobRequest r) {
        job.setTitle(r.title().trim());
        job.setDescription(r.description().trim());
        job.setLocation(r.location().trim());
        job.setJobType(isBlank(r.jobType()) ? "Full-time" : r.jobType().trim());
        job.setSalaryMin(r.salaryMin());
        job.setSalaryMax(r.salaryMax());
        job.setRequiredSkills(SkillUtil.join(r.requiredSkills()));
        job.setRequiredEducation(r.requiredEducation() == null ? null : r.requiredEducation().trim());
        job.setMinExperience(r.minExperience());
        job.setDeadline(r.deadline());
        job.setActive(r.active() == null || r.active());

        if (r.workMode() != null && !r.workMode().isBlank()) job.setWorkMode(r.workMode().trim());
        if (r.experienceLevel() != null && !r.experienceLevel().isBlank()) job.setExperienceLevel(r.experienceLevel().trim());
        if (r.currency() != null && !r.currency().isBlank()) job.setCurrency(r.currency().trim());
        if (r.openings() != null) job.setOpenings(Math.max(1, r.openings()));
        if (r.status() != null && !r.status().isBlank()) {
            job.setStatus(r.status().trim().toUpperCase());
            job.setActive("ACTIVE".equalsIgnoreCase(job.getStatus()));
        } else {
            job.setStatus(job.isActive() ? "ACTIVE" : "CLOSED");
        }
        if (r.companyId() != null) {
            job.setCompany(companyService.findById(r.companyId()));
        }
    }

    private JobResponse toResponse(Job j, MatchResult m, Boolean applied, Boolean saved, Long applicantCount) {
        Company comp = j.getCompany();
        String cName = comp != null && comp.getName() != null ? comp.getName() : j.getRecruiter().getEffectiveCompanyName();
        String cIndustry = comp != null && comp.getIndustry() != null ? comp.getIndustry() : j.getRecruiter().getIndustry();

        return JobResponse.builder()
                .id(j.getId())
                .title(j.getTitle())
                .description(j.getDescription())
                .location(j.getLocation())
                .jobType(j.getJobType())
                .workMode(j.getWorkMode() != null ? j.getWorkMode() : "On-site")
                .experienceLevel(j.getExperienceLevel() != null ? j.getExperienceLevel() : "Mid-Senior level")
                .salaryMin(j.getSalaryMin())
                .salaryMax(j.getSalaryMax())
                .currency(j.getCurrency() != null ? j.getCurrency() : "USD")
                .openings(j.getOpenings() != null ? j.getOpenings() : 1)
                .requiredSkills(SkillUtil.parse(j.getRequiredSkills()))
                .requiredEducation(j.getRequiredEducation())
                .minExperience(j.getMinExperience())
                .deadline(j.getDeadline())
                .active(j.isActive())
                .status(j.getStatus() != null ? j.getStatus() : (j.isActive() ? "ACTIVE" : "CLOSED"))
                .createdAt(j.getCreatedAt())
                .companyId(comp != null ? comp.getId() : null)
                .companySlug(comp != null ? comp.getSlug() : null)
                .companyLogoUrl(comp != null ? comp.getLogoUrl() : null)
                .companyName(cName)
                .companyIndustry(cIndustry)
                .matchScore(m == null ? null : m.score())
                .matchedSkills(m == null ? null : m.matchedSkills())
                .missingSkills(m == null ? null : m.missingSkills())
                .skillFit(m == null ? null : m.skillFit())
                .experienceFit(m == null ? null : m.experienceFit())
                .educationFit(m == null ? null : m.educationFit())
                .matchExplanation(m == null ? null : m.explanation())
                .applied(applied)
                .saved(saved != null ? saved : false)
                .applicantCount(applicantCount)
                .externalUrl(j.getExternalUrl())
                .source(j.getSource())
                .build();
    }

    private JobSeekerProfile seekerOf(User viewer) {
        return viewer != null && viewer.getRole() == User.Role.JOB_SEEKER
                ? profileService.seekerFor(viewer) : null;
    }

    private Set<Long> appliedIds(JobSeekerProfile seeker) {
        return seeker == null ? Set.of() : new HashSet<>(applicationRepository.findAppliedJobIds(seeker.getId()));
    }

    private Set<Long> savedIds(User user) {
        if (user == null) return Set.of();
        Set<Long> ids = new HashSet<>();
        for (SavedJob sj : savedJobRepository.findByUserIdOrderBySavedAtDesc(user.getId())) {
            ids.add(sj.getJob().getId());
        }
        return ids;
    }

    public static boolean expired(Job j) {
        return j.getDeadline() != null && j.getDeadline().isBefore(LocalDate.now());
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
