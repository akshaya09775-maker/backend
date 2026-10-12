package app.service;

import app.dto.CompanyDetailResponse;
import app.dto.CompanyDto;
import app.dto.CompanyResponse;
import app.dto.JobResponse;
import app.entity.Company;
import app.entity.CompanyFollow;
import app.entity.Job;
import app.entity.User;
import app.repository.CompanyFollowRepository;
import app.repository.CompanyRepository;
import app.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyFollowRepository followRepository;
    private final JobRepository jobRepository;

    public List<CompanyResponse> search(String q, String industry, Boolean verified, User currentUser) {
        List<Company> list = companyRepository.search(q, industry, verified);
        List<CompanyResponse> out = new ArrayList<>();
        for (Company c : list) {
            out.add(toResponse(c, currentUser));
        }
        return out;
    }

    public CompanyDetailResponse getDetails(String slugOrId, User currentUser) {
        Company c = findBySlugOrId(slugOrId);
        long followerCount = followRepository.countByCompanyId(c.getId());
        boolean following = currentUser != null && followRepository.existsByUserIdAndCompanyId(currentUser.getId(), c.getId());

        List<JobResponse> jobResponses = new ArrayList<>();
        List<Job> activeJobs = jobRepository.findByCompanyIdOrderByCreatedAtDesc(c.getId());
        for (Job j : activeJobs) {
            if (j.isActive() && !JobService.expired(j)) {
                jobResponses.add(JobResponse.builder()
                        .id(j.getId())
                        .title(j.getTitle())
                        .description(j.getDescription())
                        .location(j.getLocation())
                        .jobType(j.getJobType())
                        .workMode(j.getWorkMode())
                        .experienceLevel(j.getExperienceLevel())
                        .salaryMin(j.getSalaryMin())
                        .salaryMax(j.getSalaryMax())
                        .currency(j.getCurrency())
                        .openings(j.getOpenings())
                        .active(j.isActive())
                        .status(j.getStatus())
                        .deadline(j.getDeadline())
                        .createdAt(j.getCreatedAt())
                        .companyId(c.getId())
                        .companyName(c.getName())
                        .companySlug(c.getSlug())
                        .companyLogoUrl(c.getLogoUrl())
                        .companyIndustry(c.getIndustry())
                        .build());
            }
        }

        return CompanyDetailResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .logoUrl(c.getLogoUrl())
                .coverImageUrl(c.getCoverImageUrl())
                .tagline(c.getTagline())
                .about(c.getAbout())
                .industry(c.getIndustry())
                .companySize(c.getCompanySize())
                .website(c.getWebsite())
                .headquarters(c.getHeadquarters())
                .foundedYear(c.getFoundedYear())
                .specialties(c.getSpecialties())
                .verified(c.isVerified())
                .followerCount(followerCount)
                .following(following)
                .jobs(jobResponses)
                .createdAt(c.getCreatedAt())
                .build();
    }

    public Company findBySlugOrId(String slugOrId) {
        if (slugOrId == null || slugOrId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found");
        }
        try {
            Long id = Long.parseLong(slugOrId);
            return companyRepository.findById(id)
                    .orElseGet(() -> companyRepository.findBySlug(slugOrId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found")));
        } catch (NumberFormatException e) {
            return companyRepository.findBySlug(slugOrId)
                    .orElseGet(() -> companyRepository.findByNameIgnoreCase(slugOrId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found")));
        }
    }

    public Company findById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    @Transactional
    public Company getOrCreate(String name, String industry, String website, String location, String description) {
        if (name == null || name.isBlank()) return null;
        String cleanName = name.trim();
        return companyRepository.findByNameIgnoreCase(cleanName)
                .orElseGet(() -> {
                    String slug = cleanName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
                    if (companyRepository.findBySlug(slug).isPresent()) {
                        slug = slug + "-" + System.currentTimeMillis();
                    }
                    Company comp = Company.builder()
                            .name(cleanName)
                            .slug(slug)
                            .industry(industry)
                            .website(website)
                            .headquarters(location)
                            .about(description)
                            .verified(false)
                            .build();
                    return companyRepository.save(comp);
                });
    }

    @Transactional
    public boolean follow(User user, Long companyId) {
        Company company = findById(companyId);
        if (followRepository.existsByUserIdAndCompanyId(user.getId(), company.getId())) {
            return true;
        }
        followRepository.save(CompanyFollow.builder().user(user).company(company).build());
        return true;
    }

    @Transactional
    public boolean unfollow(User user, Long companyId) {
        followRepository.deleteByUserIdAndCompanyId(user.getId(), companyId);
        return false;
    }

    public List<CompanyResponse> getFollowed(User user) {
        List<CompanyFollow> follows = followRepository.findByUserIdOrderByFollowedAtDesc(user.getId());
        List<CompanyResponse> out = new ArrayList<>();
        for (CompanyFollow f : follows) {
            out.add(toResponse(f.getCompany(), user));
        }
        return out;
    }

    public CompanyResponse toResponse(Company c, User currentUser) {
        long followerCount = followRepository.countByCompanyId(c.getId());
        boolean following = currentUser != null && followRepository.existsByUserIdAndCompanyId(currentUser.getId(), c.getId());
        long activeJobCount = jobRepository.findByCompanyIdOrderByCreatedAtDesc(c.getId()).stream()
                .filter(j -> j.isActive() && !JobService.expired(j))
                .count();

        return CompanyResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .logoUrl(c.getLogoUrl())
                .coverImageUrl(c.getCoverImageUrl())
                .tagline(c.getTagline())
                .industry(c.getIndustry())
                .companySize(c.getCompanySize())
                .website(c.getWebsite())
                .headquarters(c.getHeadquarters())
                .foundedYear(c.getFoundedYear())
                .specialties(c.getSpecialties())
                .verified(c.isVerified())
                .followerCount(followerCount)
                .activeJobCount(activeJobCount)
                .following(following)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
