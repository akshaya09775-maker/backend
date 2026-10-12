package app.controller;

import app.dto.ApplicationResponse;
import app.dto.ApplyRequest;
import app.dto.JobResponse;
import app.dto.SavedJobResponse;
import app.dto.SeekerProfileDto;
import app.security.CurrentUser;
import app.service.ApplicationService;
import app.service.FileStorageService;
import app.service.JobService;
import app.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seeker")
@RequiredArgsConstructor
public class SeekerController {

    private final CurrentUser currentUser;
    private final ProfileService profileService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final FileStorageService storageService;

    @GetMapping("/profile")
    public SeekerProfileDto profile() {
        return profileService.getSeeker(currentUser.get());
    }

    @PutMapping("/profile")
    public SeekerProfileDto updateProfile(@Valid @RequestBody SeekerProfileDto dto) {
        return profileService.updateSeeker(currentUser.get(), dto);
    }

    @PostMapping("/resume")
    public SeekerProfileDto uploadResume(@RequestParam("file") MultipartFile file) {
        return profileService.uploadResume(currentUser.get(), file);
    }

    @GetMapping("/resume")
    public ResponseEntity<Resource> downloadResume() {
        ApplicationService.ResumeFile f = applicationService.myResume(currentUser.get());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                        .filename(f.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.resource());
    }

    @PostMapping("/avatar")
    public SeekerProfileDto uploadAvatar(@RequestParam("file") MultipartFile file) {
        return profileService.uploadAvatar(currentUser.get(), file);
    }

    @GetMapping("/avatar/{fileName}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String fileName) {
        Resource r = storageService.loadImage(fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storageService.contentType(fileName)))
                .header("Cache-Control", "max-age=86400")
                .body(r);
    }

    /** AI job recommendations with match score. */
    @GetMapping("/recommendations")
    public List<JobResponse> recommendations() {
        return jobService.recommend(currentUser.get());
    }

    @GetMapping("/applications")
    public List<ApplicationResponse> applications() {
        return applicationService.mine(currentUser.get());
    }

    @DeleteMapping("/applications/{id}")
    public void withdraw(@PathVariable Long id) {
        applicationService.withdraw(currentUser.get(), id);
    }

    @PostMapping("/jobs/{jobId}/apply")
    public ApplicationResponse apply(@PathVariable Long jobId,
                                     @Valid @RequestBody(required = false) ApplyRequest body) {
        return applicationService.apply(currentUser.get(), jobId, body);
    }

    @PostMapping("/jobs/{jobId}/save")
    public Map<String, Object> saveJob(@PathVariable Long jobId) {
        boolean saved = jobService.saveJob(currentUser.get(), jobId);
        return Map.of("saved", saved, "jobId", jobId);
    }

    @DeleteMapping("/jobs/{jobId}/save")
    public Map<String, Object> unsaveJob(@PathVariable Long jobId) {
        boolean saved = jobService.unsaveJob(currentUser.get(), jobId);
        return Map.of("saved", saved, "jobId", jobId);
    }

    @GetMapping("/saved-jobs")
    public List<SavedJobResponse> savedJobs() {
        return jobService.listSaved(currentUser.get());
    }
}
