package app.dto;

import jakarta.validation.constraints.Size;

public record CompanyDto(
        @Size(max = 150, message = "Company name is too long") String companyName,
        @Size(max = 100, message = "Industry is too long") String industry,
        @Size(max = 200, message = "Website is too long") String website,
        @Size(max = 150, message = "Location is too long") String location,
        @Size(max = 4000, message = "Description must be at most 4000 characters") String description,
        Boolean verified,
        String logoUrl,
        String coverImageUrl,
        String tagline,
        String companySize,
        String headquarters,
        Integer foundedYear,
        String specialties) {

    public CompanyDto(String companyName, String industry, String website, String location, String description, Boolean verified) {
        this(companyName, industry, website, location, description, verified, null, null, null, null, null, null, null);
    }
}
