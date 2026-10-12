package app.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "recruiters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecruiterProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    @Builder.Default
    private boolean isCompanyAdmin = true;

    private String title;

    private String companyName;
    private String industry;
    private String website;
    private String location;

    @Column(length = 2000)
    private String description;

    /** Set by an admin after checking the company; unverified recruiters cannot post jobs. */
    @Builder.Default
    private boolean verified = false;

    public String getEffectiveCompanyName() {
        if (company != null && company.getName() != null) return company.getName();
        return companyName != null ? companyName : "";
    }
}
