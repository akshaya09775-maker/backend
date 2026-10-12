package app.dto;

public record ReportResponse(long totalUsers, long jobSeekers, long recruiters,
                             long totalJobs, long activeJobs, long totalApplications,
                             long applied, long shortlisted, long accepted, long rejected,
                             long pendingRecruiters) {}
