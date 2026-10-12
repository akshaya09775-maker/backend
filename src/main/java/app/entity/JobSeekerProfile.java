package app.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "job_seekers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSeekerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private String phone;
    private String location;
    private String headline;

    @Column(length = 4000)
    private String about;

    private String portfolioUrl;
    private String githubUrl;
    private String linkedinUrl;
    private String avatarUrl;

    @Column(length = 1000)
    private String education;

    @Column(length = 2000)
    private String experience;

    private Integer experienceYears;

    /** Comma separated, e.g. "Java, Spring Boot, MySQL" */
    @Column(length = 1000)
    private String skills;

    /** Name of the file on disk */
    private String resumeFileName;

    /** Original name shown to users */
    private String resumeOriginalName;
}
