package app.dto;

import app.entity.Application;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ApplicationResponse(
        Long id,
        Long jobId,
        String jobTitle,
        String companyName,
        Long seekerId,
        String seekerName,
        String seekerEmail,
        String seekerPhone,
        String seekerHeadline,
        String seekerEducation,
        Integer seekerExperienceYears,
        List<String> seekerSkills,
        boolean hasResume,
        String resumeName,
        Application.Status status,
        Integer matchScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        String coverLetter,
        LocalDateTime appliedAt,
        Long companyId,
        String companyLogoUrl,
        String jobLocation,
        String jobWorkMode,
        Integer skillFit,
        Integer experienceFit,
        Integer educationFit,
        String matchExplanation,
        List<StatusHistoryDto> timeline,
        LocalDateTime updatedAt) {}
