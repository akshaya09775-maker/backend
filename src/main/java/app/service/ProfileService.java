package app.service;

import app.dto.CompanyDto;
import app.dto.SeekerProfileDto;
import app.entity.Company;
import app.entity.JobSeekerProfile;
import app.entity.RecruiterProfile;
import app.entity.User;
import app.repository.CompanyRepository;
import app.repository.JobSeekerRepository;
import app.repository.RecruiterRepository;
import app.util.SkillUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final JobSeekerRepository seekerRepository;
    private final RecruiterRepository recruiterRepository;
    private final CompanyRepository companyRepository;
    private final FileStorageService storage;

    // ---------- job seeker ----------

    /** Returns the seeker profile, creating an empty one on first use. */
    public JobSeekerProfile seekerFor(User user) {
        return seekerRepository.findByUserId(user.getId())
                .orElseGet(() -> seekerRepository.save(JobSeekerProfile.builder().user(user).build()));
    }

    public SeekerProfileDto getSeeker(User user) {
        return toDto(seekerFor(user));
    }

    public SeekerProfileDto updateSeeker(User user, SeekerProfileDto dto) {
        JobSeekerProfile p = seekerFor(user);
        p.setPhone(trim(dto.phone()));
        p.setLocation(trim(dto.location()));
        p.setHeadline(trim(dto.headline()));
        p.setEducation(trim(dto.education()));
        p.setExperience(trim(dto.experience()));
        p.setExperienceYears(dto.experienceYears() == null ? null : Math.max(0, dto.experienceYears()));
        p.setSkills(SkillUtil.join(dto.skills()));
        if (dto.about() != null) p.setAbout(trim(dto.about()));
        if (dto.portfolioUrl() != null) p.setPortfolioUrl(trim(dto.portfolioUrl()));
        if (dto.githubUrl() != null) p.setGithubUrl(trim(dto.githubUrl()));
        if (dto.linkedinUrl() != null) p.setLinkedinUrl(trim(dto.linkedinUrl()));
        if (dto.avatarUrl() != null) p.setAvatarUrl(trim(dto.avatarUrl()));
        return toDto(seekerRepository.save(p));
    }

    public SeekerProfileDto uploadResume(User user, MultipartFile file) {
        JobSeekerProfile p = seekerFor(user);
        String stored = storage.store(file, user.getId());
        storage.delete(p.getResumeFileName());
        p.setResumeFileName(stored);
        p.setResumeOriginalName(file.getOriginalFilename());
        return toDto(seekerRepository.save(p));
    }

    public SeekerProfileDto uploadAvatar(User user, MultipartFile file) {
        JobSeekerProfile p = seekerFor(user);
        String stored = storage.storeImage(file, user.getId());
        p.setAvatarUrl("/api/seeker/avatar/" + stored);
        return toDto(seekerRepository.save(p));
    }

    public SeekerProfileDto toDto(JobSeekerProfile p) {
        int score = 0;
        List<String> tips = new ArrayList<>();

        if (p.getHeadline() != null && !p.getHeadline().isBlank()) score += 15;
        else tips.add("Add a headline describing your current role or target title");

        if (p.getAbout() != null && !p.getAbout().isBlank()) score += 15;
        else tips.add("Write a short summary about your background and interests");

        if (p.getLocation() != null && !p.getLocation().isBlank()) score += 10;
        else tips.add("Add your location so recruiters can match local/hybrid roles");

        if (p.getPhone() != null && !p.getPhone().isBlank()) score += 10;
        else tips.add("Add a contact phone number");

        List<String> skills = SkillUtil.parse(p.getSkills());
        if (skills.size() >= 3) score += 20;
        else if (!skills.isEmpty()) { score += 10; tips.add("Add at least 3 skills to improve AI match precision"); }
        else tips.add("Add your technical and domain skills");

        if (p.getExperience() != null && !p.getExperience().isBlank()) score += 15;
        else tips.add("Detail your past work experience");

        if (p.getEducation() != null && !p.getEducation().isBlank()) score += 10;
        else tips.add("Add your education details");

        if (p.getResumeFileName() != null && !p.getResumeFileName().isBlank()) score += 5;
        else tips.add("Upload your PDF resume");

        return SeekerProfileDto.builder()
                .phone(p.getPhone())
                .location(p.getLocation())
                .headline(p.getHeadline())
                .education(p.getEducation())
                .experience(p.getExperience())
                .experienceYears(p.getExperienceYears())
                .skills(skills)
                .resumeFileName(p.getResumeOriginalName())
                .about(p.getAbout())
                .portfolioUrl(p.getPortfolioUrl())
                .githubUrl(p.getGithubUrl())
                .linkedinUrl(p.getLinkedinUrl())
                .avatarUrl(p.getAvatarUrl())
                .completeness(Math.min(100, score))
                .completenessTips(tips)
                .build();
    }

    // ---------- recruiter ----------

    public RecruiterProfile recruiterFor(User user) {
        return recruiterRepository.findByUserId(user.getId())
                .orElseGet(() -> recruiterRepository.save(RecruiterProfile.builder().user(user).build()));
    }

    public CompanyDto getCompany(User user) {
        return toDto(recruiterFor(user));
    }

    @Transactional
    public CompanyDto updateCompany(User user, CompanyDto dto) {
        RecruiterProfile r = recruiterFor(user);
        r.setCompanyName(trim(dto.companyName()));
        r.setIndustry(trim(dto.industry()));
        r.setWebsite(trim(dto.website()));
        r.setLocation(trim(dto.location()));
        r.setDescription(trim(dto.description()));

        if (dto.companyName() != null && !dto.companyName().isBlank()) {
            String cName = dto.companyName().trim();
            Company comp = companyRepository.findByNameIgnoreCase(cName)
                    .orElseGet(() -> {
                        String slug = cName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
                        if (companyRepository.findBySlug(slug).isPresent()) {
                            slug = slug + "-" + System.currentTimeMillis();
                        }
                        Company c = Company.builder()
                                .name(cName)
                                .slug(slug)
                                .industry(dto.industry())
                                .website(dto.website())
                                .headquarters(dto.location())
                                .about(dto.description())
                                .verified(r.isVerified())
                                .build();
                        return companyRepository.save(c);
                    });
            if (dto.logoUrl() != null && !dto.logoUrl().isBlank()) comp.setLogoUrl(dto.logoUrl().trim());
            if (dto.coverImageUrl() != null && !dto.coverImageUrl().isBlank()) comp.setCoverImageUrl(dto.coverImageUrl().trim());
            if (dto.tagline() != null && !dto.tagline().isBlank()) comp.setTagline(dto.tagline().trim());
            if (dto.companySize() != null && !dto.companySize().isBlank()) comp.setCompanySize(dto.companySize().trim());
            if (dto.headquarters() != null && !dto.headquarters().isBlank()) comp.setHeadquarters(dto.headquarters().trim());
            if (dto.foundedYear() != null) comp.setFoundedYear(dto.foundedYear());
            if (dto.specialties() != null && !dto.specialties().isBlank()) comp.setSpecialties(dto.specialties().trim());
            comp.setVerified(r.isVerified());
            companyRepository.save(comp);
            r.setCompany(comp);
        }

        return toDto(recruiterRepository.save(r));
    }

    private CompanyDto toDto(RecruiterProfile r) {
        Company comp = r.getCompany();
        String cName = comp != null ? comp.getName() : r.getCompanyName();
        String ind = comp != null ? comp.getIndustry() : r.getIndustry();
        String web = comp != null ? comp.getWebsite() : r.getWebsite();
        String loc = comp != null ? comp.getHeadquarters() : r.getLocation();
        String desc = comp != null ? comp.getAbout() : r.getDescription();
        String logo = comp != null ? comp.getLogoUrl() : null;
        String cover = comp != null ? comp.getCoverImageUrl() : null;
        String tagline = comp != null ? comp.getTagline() : null;
        String size = comp != null ? comp.getCompanySize() : null;
        Integer founded = comp != null ? comp.getFoundedYear() : null;
        String specs = comp != null ? comp.getSpecialties() : null;

        return new CompanyDto(cName, ind, web, loc, desc, r.isVerified(), logo, cover, tagline, size, loc, founded, specs);
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
