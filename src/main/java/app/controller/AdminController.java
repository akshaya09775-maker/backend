package app.controller;

import app.dto.ApplicationResponse;
import app.dto.JobResponse;
import app.dto.RecruiterAdminResponse;
import app.dto.ReportResponse;
import app.dto.UserResponse;
import app.service.AdminService;
import app.service.ApplicationService;
import app.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final app.service.AuditService auditService;
    private final app.service.JobImportService importService;

    @GetMapping("/users")
    public List<UserResponse> users() {
        return adminService.users();
    }

    @PutMapping("/users/{id}/enabled")
    public UserResponse setEnabled(@PathVariable Long id, @RequestParam boolean value) {
        return adminService.setEnabled(id, value);
    }

    @GetMapping("/recruiters")
    public List<RecruiterAdminResponse> recruiters() {
        return adminService.recruiters();
    }

    @PutMapping("/recruiters/{id}/verified")
    public RecruiterAdminResponse setVerified(@PathVariable Long id, @RequestParam boolean value) {
        return adminService.setVerified(id, value);
    }

    @GetMapping("/jobs")
    public List<JobResponse> jobs() {
        return jobService.listAll();
    }

    @DeleteMapping("/jobs/{id}")
    public void deleteJob(@PathVariable Long id) {
        jobService.deleteAsAdmin(id);
        auditService.record("JOB_DELETED_BY_ADMIN", "job#" + id, null);
    }

    @GetMapping("/applications")
    public List<ApplicationResponse> applications() {
        return applicationService.listAll();
    }

    /** Fetches current openings from the configured company careers boards right now. */
    @PostMapping("/import-jobs")
    public java.util.Map<String, Object> importJobs() {
        var r = importService.runImport();
        auditService.record("JOBS_IMPORTED", "careers boards",
                "added=" + r.added() + " closed=" + r.closed() + " failedBoards=" + r.failedBoards());
        return java.util.Map.of("added", r.added(), "closed", r.closed(),
                "failedBoards", r.failedBoards(), "message", r.message());
    }

    @GetMapping("/audit-logs")
    public List<app.dto.AuditLogResponse> auditLogs() {
        return adminService.auditLog();
    }

    @GetMapping("/reports")
    public ReportResponse reports() {
        return adminService.report();
    }
}
