package app.controller;

import app.dto.*;
import app.security.CurrentUser;
import app.service.ApplicationService;
import app.service.JobService;
import app.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recruiter")
@RequiredArgsConstructor
public class RecruiterController {

    private final CurrentUser currentUser;
    private final ProfileService profileService;
    private final JobService jobService;
    private final ApplicationService applicationService;

    @GetMapping("/company")
    public CompanyDto company() {
        return profileService.getCompany(currentUser.get());
    }

    @PutMapping("/company")
    public CompanyDto updateCompany(@Valid @RequestBody CompanyDto dto) {
        return profileService.updateCompany(currentUser.get(), dto);
    }

    @GetMapping("/jobs")
    public List<JobResponse> myJobs() {
        return jobService.listMine(currentUser.get());
    }

    @PostMapping("/jobs")
    public JobResponse createJob(@Valid @RequestBody JobRequest req) {
        return jobService.create(currentUser.get(), req);
    }

    @PutMapping("/jobs/{id}")
    public JobResponse updateJob(@PathVariable Long id, @Valid @RequestBody JobRequest req) {
        return jobService.update(currentUser.get(), id, req);
    }

    @DeleteMapping("/jobs/{id}")
    public void deleteJob(@PathVariable Long id) {
        jobService.delete(currentUser.get(), id);
    }

    /** Applicants for a job, ranked by AI match score. */
    @GetMapping("/jobs/{id}/applicants")
    public List<ApplicationResponse> applicants(@PathVariable Long id) {
        return applicationService.applicants(currentUser.get(), id);
    }

    @PutMapping("/applications/{id}/status")
    public ApplicationResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest req) {
        return applicationService.updateStatus(currentUser.get(), id, req.status(), req.note());
    }

    @GetMapping("/applications/{id}/resume")
    public ResponseEntity<Resource> resume(@PathVariable Long id) {
        ApplicationService.ResumeFile f = applicationService.applicantResume(currentUser.get(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                        .filename(f.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.resource());
    }
}
