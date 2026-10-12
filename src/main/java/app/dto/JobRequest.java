package app.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public record JobRequest(
        @NotBlank(message = "Job title is required") @Size(max = 150, message = "Title is too long") String title,
        @NotBlank(message = "Job description is required")
        @Size(max = 5000, message = "Description must be at most 5000 characters") String description,
        @NotBlank(message = "Location is required") @Size(max = 150, message = "Location is too long") String location,
        @Size(max = 50, message = "Job type is too long") String jobType,
        @PositiveOrZero(message = "Salary cannot be negative") Integer salaryMin,
        @PositiveOrZero(message = "Salary cannot be negative") Integer salaryMax,
        @Size(max = 30, message = "At most 30 skills") List<@Size(max = 50, message = "A skill name is too long") String> requiredSkills,
        @Size(max = 200, message = "Education text is too long") String requiredEducation,
        @PositiveOrZero(message = "Experience cannot be negative") @Max(value = 50, message = "Experience is too high") Integer minExperience,
        LocalDate deadline,
        Boolean active,
        String workMode,
        String experienceLevel,
        String currency,
        Integer openings,
        String status,
        Long companyId) {

    public JobRequest(String title, String description, String location, String jobType,
                      Integer salaryMin, Integer salaryMax, List<String> requiredSkills,
                      String requiredEducation, Integer minExperience, LocalDate deadline, Boolean active) {
        this(title, description, location, jobType, salaryMin, salaryMax, requiredSkills,
             requiredEducation, minExperience, deadline, active, "On-site", "Mid-Senior level", "USD", 1, "ACTIVE", null);
    }
}
