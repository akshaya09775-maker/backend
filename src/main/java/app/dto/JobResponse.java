package app.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record JobResponse(
        Long id,
        String title,
        String description,
        String location,
        String jobType,
        Integer salaryMin,
        Integer salaryMax,
        List<String> requiredSkills,
        String requiredEducation,
        Integer minExperience,
        LocalDate deadline,
        boolean active,
        LocalDateTime createdAt,
        String companyName,
        String companyIndustry,
        Integer matchScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        Boolean applied,
        Long applicantCount,
        Long companyId,
        String companySlug,
        String companyLogoUrl,
        String workMode,
        String experienceLevel,
        String currency,
        Integer openings,
        String status,
        Boolean saved,
        Integer skillFit,
        Integer experienceFit,
        Integer educationFit,
        String matchExplanation,
        String externalUrl,
        String source) {}
