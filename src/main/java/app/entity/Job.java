package app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private RecruiterProfile recruiter;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(nullable = false)
    private String location;

    private String jobType;

    @Builder.Default
    private String workMode = "On-site"; // Remote, Hybrid, On-site

    @Builder.Default
    private String experienceLevel = "Mid-Senior level"; // Entry level, Associate, Mid-Senior level, Director, Executive

    private Integer salaryMin;
    private Integer salaryMax;

    @Builder.Default
    private String currency = "USD";

    @Builder.Default
    private Integer openings = 1;

    /** Comma separated skills */
    @Column(length = 1000)
    private String requiredSkills;

    private String requiredEducation;
    private Integer minExperience;

    /** Last day to apply (optional). */
    private LocalDate deadline;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, CLOSED, DRAFT

    /** Set for jobs imported from a company's careers page: identifies the listing at its source. */
    @Column(unique = true, length = 190)
    private String externalId;

    /** Where candidates apply for an imported job (the company's own website). */
    @Column(length = 1000)
    private String externalUrl;

    /** Display name of the import source, e.g. "Greenhouse" or "Lever". */
    @Column(length = 40)
    private String source;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "job", cascade = CascadeType.REMOVE)
    private List<Application> applications = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = active ? "ACTIVE" : "CLOSED";
        if (company == null && recruiter != null && recruiter.getCompany() != null) {
            company = recruiter.getCompany();
        }
    }

    public String getResolvedCompanyName() {
        if (company != null && company.getName() != null) return company.getName();
        if (recruiter != null && recruiter.getCompanyName() != null) return recruiter.getCompanyName();
        return "";
    }
}
