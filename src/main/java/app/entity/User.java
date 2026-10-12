package app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /** null means "yes" so accounts created before this option existed keep receiving emails. */
    private Boolean emailNotifications;

    /** When the user accepted the Terms and Privacy Policy. */
    private LocalDateTime termsAcceptedAt;

    /** Tokens issued before this moment are rejected (set on password change / reset). */
    private LocalDateTime passwordChangedAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    public boolean wantsEmails() {
        return emailNotifications == null || emailNotifications;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum Role {
        JOB_SEEKER, RECRUITER, ADMIN
    }
}
