package app.service;

import app.ai.MatchResult;
import app.dto.ApplicationResponse;
import app.dto.ApplyRequest;
import app.dto.StatusHistoryDto;
import app.entity.*;
import app.repository.ApplicationRepository;
import app.repository.ApplicationStatusHistoryRepository;
import app.util.SkillUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final JobService jobService;
    private final ProfileService profileService;
    private final MatchingService matchingService;
    private final FileStorageService storage;
    private final NotificationService notifications;

    /** A resume file plus the names needed to download it. */
    public record ResumeFile(Resource resource, String fileName, String contentType) {}

    // ---------- job seeker ----------

    @Transactional
    public ApplicationResponse apply(User user, Long jobId, ApplyRequest req) {
        Job job = jobService.find(jobId);
        if (job.getExternalUrl() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This job is hosted on the company's own website. Use \"Apply on company site\" to apply");
        }
        if (!job.isActive() || !job.getRecruiter().isVerified() || JobService.expired(job)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This job is no longer accepting applications");
        }
        JobSeekerProfile seeker = profileService.seekerFor(user);
        if (seeker.getSkills() == null || seeker.getSkills().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please add your skills to your profile before applying");
        }
        if (applicationRepository.existsByJobIdAndSeekerId(jobId, seeker.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already applied for this job");
        }
        Application a = Application.builder()
                .job(job)
                .seeker(seeker)
                .status(Application.Status.APPLIED)
                .coverLetter(req == null || req.coverLetter() == null ? null : req.coverLetter().trim())
                .build();
        a.addStatusHistory(Application.Status.APPLIED, "Application submitted", user.getName());
        Application saved = applicationRepository.save(a);
        notifications.send(job.getRecruiter().getUser(),
                user.getName() + " applied for " + job.getTitle(),
                "/recruiter/jobs/" + job.getId() + "/applicants");
        return toResponse(saved);
    }

    /** A seeker may withdraw an application until the recruiter has decided on it. */
    @Transactional
    public void withdraw(User user, Long applicationId) {
        Application a = find(applicationId);
        if (!a.getSeeker().getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your application");
        }
        if (a.getStatus() == Application.Status.ACCEPTED || a.getStatus() == Application.Status.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A decided application cannot be withdrawn");
        }
        Job job = a.getJob();
        a.addStatusHistory(Application.Status.WITHDRAWN, "Application withdrawn by candidate", user.getName());
        a.setStatus(Application.Status.WITHDRAWN);
        applicationRepository.save(a);
        notifications.send(job.getRecruiter().getUser(),
                user.getName() + " withdrew the application for " + job.getTitle(),
                "/recruiter/jobs/" + job.getId() + "/applicants");
    }

    public List<ApplicationResponse> mine(User user) {
        JobSeekerProfile seeker = profileService.seekerFor(user);
        List<ApplicationResponse> out = new ArrayList<>();
        for (Application a : applicationRepository.findBySeekerIdOrderByAppliedAtDesc(seeker.getId())) {
            out.add(toResponse(a));
        }
        return out;
    }

    public ResumeFile myResume(User user) {
        JobSeekerProfile p = profileService.seekerFor(user);
        return resumeOf(p);
    }

    // ---------- recruiter ----------

    /** Applicants of one of the recruiter's jobs, best AI match first. */
    public List<ApplicationResponse> applicants(User recruiter, Long jobId) {
        Job job = jobService.find(jobId);
        ensureOwner(recruiter, job);
        List<ApplicationResponse> out = new ArrayList<>();
        for (Application a : applicationRepository.findByJobId(jobId)) {
            out.add(toResponse(a));
        }
        out.sort(Comparator.comparing(ApplicationResponse::matchScore, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    public ApplicationResponse updateStatus(User recruiter, Long applicationId, Application.Status status) {
        return updateStatus(recruiter, applicationId, status, null);
    }

    @Transactional
    public ApplicationResponse updateStatus(User recruiter, Long applicationId, Application.Status status, String note) {
        if (status == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        Application a = find(applicationId);
        ensureOwner(recruiter, a.getJob());
        boolean changed = a.getStatus() != status;
        if (changed && !allowedTransition(a.getStatus(), status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An application cannot move from " + a.getStatus() + " to " + status);
        }
        if (changed) {
            String noteText = (note != null && !note.isBlank()) ? note.trim() : ("Status transitioned to " + status);
            a.addStatusHistory(status, noteText, recruiter.getName());
        }
        Application saved = applicationRepository.save(a);
        if (changed) {
            notifications.send(a.getSeeker().getUser(),
                    "Your application for " + a.getJob().getTitle() + " at "
                            + a.getJob().getResolvedCompanyName() + " is now " + status,
                    "/seeker/applications");
        }
        return toResponse(saved);
    }

    /** APPLIED -> UNDER_REVIEW -> SHORTLISTED -> INTERVIEW -> OFFERED -> ACCEPTED / REJECTED; decisions are final. */
    static boolean allowedTransition(Application.Status from, Application.Status to) {
        if (from == to) return false;
        return switch (from) {
            case APPLIED -> to != Application.Status.APPLIED;
            case UNDER_REVIEW -> to != Application.Status.APPLIED && to != Application.Status.UNDER_REVIEW;
            case SHORTLISTED -> to == Application.Status.INTERVIEW || to == Application.Status.OFFERED || to == Application.Status.ACCEPTED || to == Application.Status.REJECTED;
            case INTERVIEW -> to == Application.Status.OFFERED || to == Application.Status.ACCEPTED || to == Application.Status.REJECTED || to == Application.Status.SHORTLISTED;
            case OFFERED -> to == Application.Status.ACCEPTED || to == Application.Status.REJECTED;
            case ACCEPTED, REJECTED, WITHDRAWN -> false;
        };
    }

    public ResumeFile applicantResume(User recruiter, Long applicationId) {
        Application a = find(applicationId);
        ensureOwner(recruiter, a.getJob());
        return resumeOf(a.getSeeker());
    }

    // ---------- admin ----------

    public List<ApplicationResponse> listAll() {
        List<ApplicationResponse> out = new ArrayList<>();
        for (Application a : applicationRepository.findAllByOrderByAppliedAtDesc()) out.add(toResponse(a));
        return out;
    }

    // ---------- helpers ----------

    private Application find(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private void ensureOwner(User recruiter, Job job) {
        if (!job.getRecruiter().getUser().getId().equals(recruiter.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This job belongs to another recruiter");
        }
    }

    private ResumeFile resumeOf(JobSeekerProfile p) {
        Resource r = storage.load(p.getResumeFileName());
        String name = p.getResumeOriginalName() == null ? p.getResumeFileName() : p.getResumeOriginalName();
        return new ResumeFile(r, name, storage.contentType(name));
    }

    private ApplicationResponse toResponse(Application a) {
        JobSeekerProfile s = a.getSeeker();
        MatchResult m = matchingService.match(s, a.getJob());
        Job job = a.getJob();
        Company comp = job.getCompany();

        List<StatusHistoryDto> timeline = new ArrayList<>();
        if (a.getStatusHistories() != null) {
            for (ApplicationStatusHistory h : a.getStatusHistories()) {
                timeline.add(StatusHistoryDto.builder()
                        .id(h.getId())
                        .status(h.getStatus())
                        .note(h.getNote())
                        .updatedBy(h.getUpdatedBy())
                        .changedAt(h.getChangedAt())
                        .build());
            }
        }

        return ApplicationResponse.builder()
                .id(a.getId())
                .jobId(job.getId())
                .jobTitle(job.getTitle())
                .companyName(job.getResolvedCompanyName())
                .companyId(comp != null ? comp.getId() : null)
                .companyLogoUrl(comp != null ? comp.getLogoUrl() : null)
                .jobLocation(job.getLocation())
                .jobWorkMode(job.getWorkMode() != null ? job.getWorkMode() : "On-site")
                .seekerId(s.getId())
                .seekerName(s.getUser().getName())
                .seekerEmail(s.getUser().getEmail())
                .seekerPhone(s.getPhone())
                .seekerHeadline(s.getHeadline())
                .seekerEducation(s.getEducation())
                .seekerExperienceYears(s.getExperienceYears())
                .seekerSkills(SkillUtil.parse(s.getSkills()))
                .hasResume(s.getResumeFileName() != null)
                .resumeName(s.getResumeOriginalName())
                .status(a.getStatus())
                .matchScore(m.score())
                .matchedSkills(m.matchedSkills())
                .missingSkills(m.missingSkills())
                .skillFit(m.skillFit())
                .experienceFit(m.experienceFit())
                .educationFit(m.educationFit())
                .matchExplanation(m.explanation())
                .coverLetter(a.getCoverLetter())
                .timeline(timeline)
                .appliedAt(a.getAppliedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
