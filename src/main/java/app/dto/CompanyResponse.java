package app.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CompanyResponse(Long id, String name, String slug, String logoUrl,
                              String coverImageUrl, String tagline, String industry,
                              String companySize, String website, String headquarters,
                              Integer foundedYear, String specialties, boolean verified,
                              long followerCount, long activeJobCount, Boolean following,
                              LocalDateTime createdAt) {}
