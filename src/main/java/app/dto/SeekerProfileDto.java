package app.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;

import java.util.List;

@Builder
public record SeekerProfileDto(
        @Size(max = 20, message = "Phone number is too long") String phone,
        @Size(max = 150, message = "Location is too long") String location,
        @Size(max = 200, message = "Headline is too long") String headline,
        @Size(max = 500, message = "Education text is too long") String education,
        @Size(max = 3000, message = "Experience text is too long") String experience,
        @PositiveOrZero(message = "Experience cannot be negative") @Max(value = 60, message = "Experience is too high") Integer experienceYears,
        @Size(max = 50, message = "At most 50 skills") List<@Size(max = 50, message = "A skill name is too long") String> skills,
        String resumeFileName,
        @Size(max = 4000, message = "About is too long") String about,
        @Size(max = 250, message = "Portfolio URL is too long") String portfolioUrl,
        @Size(max = 250, message = "GitHub URL is too long") String githubUrl,
        @Size(max = 250, message = "LinkedIn URL is too long") String linkedinUrl,
        @Size(max = 500, message = "Avatar URL is too long") String avatarUrl,
        Integer completeness,
        List<String> completenessTips) {

    public SeekerProfileDto(String phone, String location, String headline, String education,
                            String experience, Integer experienceYears, List<String> skills,
                            String resumeFileName) {
        this(phone, location, headline, education, experience, experienceYears, skills, resumeFileName,
                null, null, null, null, null, null, null);
    }
}
