package app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Append-only record of security-relevant and administrative actions. */
@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email of whoever did it ("system" / "anonymous" when there is no logged-in user). */
    @Column(nullable = false)
    private String actor;

    @Column(nullable = false, length = 60)
    private String action;

    private String target;

    @Column(length = 500)
    private String detail;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
