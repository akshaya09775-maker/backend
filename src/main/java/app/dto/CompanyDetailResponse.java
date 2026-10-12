package app.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record CompanyDetailResponse(Long id, String name, String slug, String logoUrl,
                                    String coverImageUrl, String tagline, String about,
                                    String industry, String companySize, String website,
                                    String headquarters, Integer foundedYear,
                                    String specialties, boolean verified,
                                    long followerCount, Boolean following,
                                    List<JobResponse> jobs,
                                    LocalDateTime createdAt) {}
